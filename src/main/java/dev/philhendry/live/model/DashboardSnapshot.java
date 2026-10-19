package dev.philhendry.live.model;

import java.time.Instant;
import java.util.List;

public record DashboardSnapshot(long version, Instant updatedAt, List<NodeState> nodes, List<Activity> activity) {
    public DashboardSnapshot {
        nodes = List.copyOf(nodes);
        activity = List.copyOf(activity);
    }
    public double averageLoad() { return nodes.stream().mapToDouble(NodeState::load).average().orElse(0); }
    public long onlineCount() { return nodes.stream().filter(n -> n.status() == NodeState.Status.ONLINE).count(); }
}
