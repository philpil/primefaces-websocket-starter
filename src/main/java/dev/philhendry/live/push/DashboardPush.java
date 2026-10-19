package dev.philhendry.live.push;

import dev.philhendry.live.events.DashboardChanged;
import dev.philhendry.live.events.PrivateNotification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.faces.push.Push;
import jakarta.faces.push.PushContext;
import jakarta.faces.event.WebsocketEvent;
import jakarta.inject.Inject;
import java.io.Serializable;
import java.util.Map;

/** The only class that knows about WebSocket transport. Payloads remain small. */
@ApplicationScoped
public class DashboardPush {
    @Inject @Push(channel = "dashboard") private PushContext dashboard;
    @Inject @Push(channel = "personal") private PushContext personal;
    @Inject private PushAudience audience;

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
        if (recipient == null) return;
        Map<String, String> ready = Map.of("type", "channel-ready");
        if ("dashboard".equals(event.getChannel())) dashboard.send(ready, recipient);
        if ("personal".equals(event.getChannel())) personal.send(ready, recipient);
    }
}
