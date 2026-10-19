package dev.philhendry.live.service;

import dev.philhendry.live.events.DashboardChanged;
import dev.philhendry.live.events.PrivateNotification;
import dev.philhendry.live.model.DashboardSnapshot;
import dev.philhendry.live.model.NodeState;
import dev.philhendry.live.repository.TelemetryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Both UI commands and external producers enter here, then publish after state is saved. */
@ApplicationScoped
public class TelemetryService {
    @Inject private TelemetryRepository repository;
    @Inject private Event<DashboardChanged> changes;
    @Inject private Event<PrivateNotification> privateChanges;
    private final AtomicBoolean simulatorRunning = new AtomicBoolean(true);
    private final AtomicLong notificationSequence = new AtomicLong();
    private final ConcurrentHashMap<String, ArrayDeque<String>> notifications = new ConcurrentHashMap<>();

    public DashboardSnapshot snapshot() { return repository.snapshot(); }
    public boolean isSimulatorRunning() { return simulatorRunning.get(); }

    public void update(String nodeId, double load, NodeState.Status status, String source) {
        long version = repository.update(nodeId, load, status, source, Instant.now());
        // Never call FacesContext or a view-scoped bean from a background thread.
        changes.fire(new DashboardChanged(version, source));
    }

    public void simulateOneUpdate() {
        var random = ThreadLocalRandom.current();
        String id = "node-" + random.nextInt(1, 7);
        double load = random.nextInt(5, 99);
        update(id, load, load > 80 ? NodeState.Status.DEGRADED : NodeState.Status.ONLINE, "Backend simulator");
    }

    public void toggleSimulator() {
        simulatorRunning.set(!simulatorRunning.get());
        changes.fire(new DashboardChanged(repository.snapshot().version(), "Simulator state changed"));
    }

    public void notifyUser(String username) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("A signed-in user is required.");
        long sequence = notificationSequence.incrementAndGet();
        ArrayDeque<String> queue = notifications.computeIfAbsent(username, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addFirst("Your private notification #" + sequence + " arrived.");
            while (queue.size() > 10) queue.removeLast();
        }
        privateChanges.fire(new PrivateNotification(username, sequence));
    }

    public List<String> notificationsFor(String username) {
        ArrayDeque<String> queue = notifications.get(username);
        if (queue == null) return List.of();
        synchronized (queue) { return List.copyOf(queue); }
    }
}
