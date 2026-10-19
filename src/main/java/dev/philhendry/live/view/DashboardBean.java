package dev.philhendry.live.view;

import dev.philhendry.live.controller.DashboardController;
import dev.philhendry.live.model.Activity;
import dev.philhendry.live.model.DashboardSnapshot;
import dev.philhendry.live.model.NodeState;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** One tab's presentation model. Only an HTTP/Faces request refreshes this bean. */
@Named
@ViewScoped
public class DashboardBean implements Serializable {
    private static final long serialVersionUID = 1L;
    @Inject private DashboardController controller;
    private transient DashboardSnapshot snapshot;
    private List<String> notifications;
    private String editedNodeId = "node-1";
    private double editedLoad = 45;
    private String editedStatus = "ONLINE";

    @PostConstruct
    public void init() { refresh(); }

    public void refresh() { snapshot = controller.snapshot(); notifications = controller.notifications(); }
    private DashboardSnapshot current() { if (snapshot == null) refresh(); return snapshot; }
    public void notifyMe() { controller.notifyMe(); refresh(); }
    public void triggerUpdate() { controller.triggerUpdate(); refresh(); }
    public void toggleSimulator() { controller.toggleSimulator(); refresh(); }

    public void saveNode() {
        try {
            controller.update(editedNodeId, editedLoad, editedStatus);
            refresh();
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage("Node updated", "Other dashboards receive a WebSocket notification."));
        } catch (IllegalArgumentException exception) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Invalid update", exception.getMessage()));
        }
    }

    public List<NodeState> getNodes() { return current().nodes(); }
    public List<Activity> getActivity() { return current().activity(); }
    public List<String> getNotifications() { return notifications; }
    public long getVersion() { return current().version(); }
    public long getOnlineCount() { return current().onlineCount(); }
    public String getAverageLoad() { return String.format(Locale.ROOT, "%.0f", current().averageLoad()); }
    public String getLastUpdated() { return DateTimeFormatter.ofPattern("HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC).format(current().updatedAt()); }
    public boolean isSimulatorRunning() { return controller.isSimulatorRunning(); }
    public String getEditedNodeId() { return editedNodeId; }
    public void setEditedNodeId(String value) { editedNodeId = value; }
    public double getEditedLoad() { return editedLoad; }
    public void setEditedLoad(double value) { editedLoad = value; }
    public String getEditedStatus() { return editedStatus; }
    public void setEditedStatus(String value) { editedStatus = value; }
}
