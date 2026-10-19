package dev.philhendry.live.push;

import dev.philhendry.live.events.DashboardChanged;
import dev.philhendry.live.events.PrivateNotification;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.faces.push.Push;
import jakarta.faces.push.PushContext;
import jakarta.faces.event.WebsocketEvent;
import jakarta.inject.Inject;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** The only class that knows about WebSocket transport. Payloads remain small. */
@ApplicationScoped
public class DashboardPush {
    @Inject @Push(channel = "dashboard") private PushContext dashboard;
    @Inject @Push(channel = "personal") private PushContext personal;
    @Inject private PushAudience audience;
    @Resource(lookup = "java:comp/DefaultManagedScheduledExecutorService")
    private ManagedScheduledExecutorService scheduler;

    public void broadcast(@Observes DashboardChanged event) {
        dashboard.send(Map.of("type", "dashboard-changed", "version", event.version(), "reason", event.reason()), audience.snapshot());
    }

    public void sendToUser(@Observes PrivateNotification event) {
        personal.send(Map.of("type", "private-notification", "sequence", event.sequence()), event.username());
    }

    /** Faces' browser onopen callback only runs on the first connection. Send a
     * ready message on every server-side open so reconnects also resync the UI. */
    public void opened(@Observes @WebsocketEvent.Opened WebsocketEvent event) {
        Serializable recipient = event.getUser();
        String channel = event.getChannel();
        if (recipient == null || !("dashboard".equals(channel) || "personal".equals(channel))) return;
        // The CDI event runs inside the endpoint's opening callback. Dispatch
        // after that callback returns, when the transport can accept frames.
        scheduler.schedule(() -> ready(channel, recipient, 0), 100, TimeUnit.MILLISECONDS);
    }

    private void ready(String channel, Serializable recipient, int attempt) {
        PushContext context = "dashboard".equals(channel) ? dashboard : personal;
        var deliveries = context.send(Map.of("type", "channel-ready"), recipient);
        // A connection can still be completing (or have already closed).
        // Retry only this bounded connection handshake, never dashboard data.
        if (deliveries.isEmpty() && attempt < 4) {
            scheduler.schedule(() -> ready(channel, recipient, attempt + 1), 100, TimeUnit.MILLISECONDS);
        }
    }
}
