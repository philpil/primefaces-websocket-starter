package dev.philhendry.live.push;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.UUID;

/** This bean is first accessed on the protected dashboard and is removed on logout/expiry. */
@Named
@SessionScoped
public class PushSession implements Serializable {
    private static final long serialVersionUID = 1L;
    @Inject private PushAudience audience;
    private final String id = UUID.randomUUID().toString();
    @PostConstruct public void register() { audience.register(id); }
    @PreDestroy public void unregister() { audience.unregister(id); }
    public String getId() { return id; }
}
