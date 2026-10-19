package dev.philhendry.live.controller;

import dev.philhendry.live.model.DashboardSnapshot;
import dev.philhendry.live.model.NodeState;
import dev.philhendry.live.service.TelemetryService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.SecurityContext;
import java.util.List;

/** Request-facing layer: apply authorization here before accepting a UI setting change. */
@ApplicationScoped
public class DashboardController {
    @Inject private TelemetryService service;
    @Inject private SecurityContext security;

    public DashboardSnapshot snapshot() { return service.snapshot(); }
    public List<String> notifications() { return service.notificationsFor(security.getCallerPrincipal().getName()); }
    public boolean isSimulatorRunning() { return service.isSimulatorRunning(); }
    public void notifyMe() { service.notifyUser(security.getCallerPrincipal().getName()); }

    public void update(String nodeId, double load, String status) {
        requireAdmin();
        service.update(nodeId, load, NodeState.Status.valueOf(status), "UI command by " + security.getCallerPrincipal().getName());
    }

    public void toggleSimulator() { requireAdmin(); service.toggleSimulator(); }
    public void triggerUpdate() { requireAdmin(); service.simulateOneUpdate(); }

    private void requireAdmin() {
        if (!security.isCallerInRole("ADMIN")) throw new SecurityException("An administrator is required for this action.");
    }
}
