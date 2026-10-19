package dev.philhendry.live.push;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Session-lifetime recipient IDs let a backend thread target all signed-in dashboards. */
@ApplicationScoped
public class PushAudience {
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();
    public void register(String id) { sessions.add(id); }
    public void unregister(String id) { sessions.remove(id); }
    public Set<String> snapshot() { return Set.copyOf(sessions); }
}
