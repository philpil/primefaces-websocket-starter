package dev.philhendry.live.events;

/** The event carries an invalidation, not the complete authoritative dashboard. */
public record DashboardChanged(long version, String reason) { }
