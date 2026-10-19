package dev.philhendry.live.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public record Activity(long sequence, Instant time, String message) {
    public long getSequence() { return sequence; }
    public String getTime() { return DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC).format(time); }
    public String getMessage() { return message; }
}
