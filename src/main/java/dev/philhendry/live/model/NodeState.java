package dev.philhendry.live.model;

/** Immutable row shared safely between the backend producer and Faces requests. */
public record NodeState(String id, String name, double load, Status status, long updates) {
    public enum Status { ONLINE, DEGRADED, OFFLINE }
    public String getId() { return id; }
    public String getName() { return name; }
    public double getLoad() { return load; }
    public String getStatus() { return status.name(); }
    public long getUpdates() { return updates; }
    public String getSeverity() {
        return switch (status) { case ONLINE -> "success"; case DEGRADED -> "warning"; case OFFLINE -> "danger"; };
    }
}
