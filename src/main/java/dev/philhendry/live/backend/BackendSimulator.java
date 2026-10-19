package dev.philhendry.live.backend;

import dev.philhendry.live.service.TelemetryService;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;

/** A container-managed producer, replacing the external system for the runnable demo. */
@Singleton
@Startup
public class BackendSimulator {
    @Inject private TelemetryService service;

    @Schedule(hour = "*", minute = "*", second = "*/3", persistent = false)
    public void receiveNextUpdate() {
        if (service.isSimulatorRunning()) service.simulateOneUpdate();
    }
}
