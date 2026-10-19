package dev.philhendry.live.push;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PushAudienceTest {
    @Test
    void logoutRemovesTheSessionWithoutChangingAnExistingRecipientSnapshot() {
        var audience = new PushAudience();
        audience.register("alice-session-one");
        audience.register("alice-session-two");
        audience.register("bob-session-one");
        var beforeLogout = audience.snapshot();
        audience.unregister("alice-session-one");
        assertEquals(3, beforeLogout.size());
        assertEquals(Set.of("alice-session-two", "bob-session-one"), audience.snapshot());
        assertThrows(UnsupportedOperationException.class, () -> beforeLogout.clear());
    }
}
