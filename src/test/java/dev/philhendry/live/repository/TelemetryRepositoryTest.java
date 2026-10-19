package dev.philhendry.live.repository;

import dev.philhendry.live.model.NodeState;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class TelemetryRepositoryTest {
    @Test
    void snapshotsStayConsistentAndIndependentAfterAnUpdate() {
        var repository = new TelemetryRepository();
        var before = repository.snapshot();
        Instant now = Instant.parse("2026-10-19T12:00:00Z");
        repository.update("node-1", 91, NodeState.Status.DEGRADED, "External API", now);
        var after = repository.snapshot();
        assertEquals(0, before.version());
        assertEquals(20, before.nodes().getFirst().load());
        assertEquals(1, after.version());
        assertEquals(91, after.nodes().getFirst().load());
        assertEquals(NodeState.Status.DEGRADED, after.nodes().getFirst().status());
        assertEquals(now, after.updatedAt());
        assertTrue(after.activity().getFirst().message().contains("External API"));
        assertThrows(UnsupportedOperationException.class, () -> after.nodes().clear());
    }

    @Test
    void invalidCommandsLeaveStateUntouched() {
        var repository = new TelemetryRepository();
        var before = repository.snapshot();
        for (double load : new double[]{-1, 101, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> repository.update("node-1", load, NodeState.Status.ONLINE, "Test", Instant.now()));
        }
        assertThrows(IllegalArgumentException.class, () -> repository.update("missing", 30, NodeState.Status.ONLINE, "Test", Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> repository.update("node-1", 30, null, "Test", Instant.now()));
        assertEquals(before, repository.snapshot());
    }

    @Test
    void simultaneousBackendUpdatesDoNotLoseVersionsOrGrowTheActivityLog() throws Exception {
        var repository = new TelemetryRepository();
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < 100; i++) {
                executor.submit(() -> repository.update("node-1", 50, NodeState.Status.ONLINE, "Concurrent source", Instant.now()));
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        var snapshot = repository.snapshot();
        assertEquals(100, snapshot.version());
        assertEquals(100, snapshot.nodes().getFirst().updates());
        assertEquals(30, snapshot.activity().size());
        assertEquals(100, snapshot.activity().getFirst().sequence());
    }
}
