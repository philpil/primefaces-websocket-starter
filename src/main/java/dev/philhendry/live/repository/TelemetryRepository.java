package dev.philhendry.live.repository;

import dev.philhendry.live.model.Activity;
import dev.philhendry.live.model.DashboardSnapshot;
import dev.philhendry.live.model.NodeState;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;

/** In-memory stand-in for an API/cache. All writes and snapshots share one lock. */
@ApplicationScoped
public class TelemetryRepository {
    private final LinkedHashMap<String, NodeState> nodes = new LinkedHashMap<>();
    private final ArrayDeque<Activity> activity = new ArrayDeque<>();
    private long version;
    private Instant updatedAt = Instant.now();

    public TelemetryRepository() {
        String[] names = {"North relay", "Coastal gateway", "West terminal", "Valley uplink", "East relay", "Central hub"};
        for (int i = 0; i < names.length; i++) {
            String id = "node-" + (i + 1);
            nodes.put(id, new NodeState(id, names[i], 20 + i * 9, NodeState.Status.ONLINE, 0));
        }
        activity.add(new Activity(0, updatedAt, "Dashboard ready. Waiting for backend updates."));
    }

    public synchronized DashboardSnapshot snapshot() {
        return new DashboardSnapshot(version, updatedAt, nodes.values().stream().toList(), activity.stream().toList());
    }

    public synchronized long update(String nodeId, double load, NodeState.Status status, String source, Instant time) {
        if (!Double.isFinite(load) || load < 0 || load > 100 || status == null) {
            throw new IllegalArgumentException("Load must be between 0 and 100 and status must be valid.");
        }
        NodeState current = nodes.get(nodeId);
        if (current == null) throw new IllegalArgumentException("Unknown node: " + nodeId);
        nodes.put(nodeId, new NodeState(current.id(), current.name(), load, status, current.updates() + 1));
        version++;
        updatedAt = time;
        activity.addFirst(new Activity(version, time, source + " updated " + current.name()));
        while (activity.size() > 30) activity.removeLast();
        return version;
    }
}
