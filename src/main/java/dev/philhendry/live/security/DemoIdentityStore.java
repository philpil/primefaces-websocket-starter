package dev.philhendry.live.security;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.credential.Credential;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStore;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;
import java.util.Map;
import java.util.Set;

/** Two in-memory demo users. Replace this store with your database or identity provider. */
@ApplicationScoped
public class DemoIdentityStore implements IdentityStore {
    @Inject private Pbkdf2PasswordHash passwordHash;
    private String encodedPassword;

    @PostConstruct
    public void initialize() {
        passwordHash.initialize(Map.of("Pbkdf2PasswordHash.Iterations", "210000"));
        String password = System.getenv().getOrDefault("DEMO_PASSWORD", "Demo123!");
        encodedPassword = passwordHash.generate(password.toCharArray());
    }

    @Override
    public CredentialValidationResult validate(Credential credential) {
        if (!(credential instanceof UsernamePasswordCredential supplied)) {
            return CredentialValidationResult.NOT_VALIDATED_RESULT;
        }
        String user = supplied.getCaller();
        boolean passwordMatches = passwordHash.verify(supplied.getPasswordAsString().toCharArray(), encodedPassword);
        if ((!"alice".equals(user) && !"bob".equals(user)) || !passwordMatches) {
            return CredentialValidationResult.INVALID_RESULT;
        }
        return new CredentialValidationResult(user, "alice".equals(user) ? Set.of("USER", "ADMIN") : Set.of("USER"));
    }
}
