package dev.philhendry.live.security;

import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.SecurityContext;
import jakarta.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Named
@RequestScoped
public class LoginBean {
    @Inject private SecurityContext security;
    private String username;
    private String password;

    public void login() throws IOException {
        FacesContext faces = FacesContext.getCurrentInstance();
        HttpServletRequest request = (HttpServletRequest) faces.getExternalContext().getRequest();
        HttpServletResponse response = (HttpServletResponse) faces.getExternalContext().getResponse();
        AuthenticationStatus status = security.authenticate(request, response,
                AuthenticationParameters.withParams().newAuthentication(true)
                        .credential(new UsernamePasswordCredential(username, password)));
        password = null;
        if (status == AuthenticationStatus.SUCCESS) {
            request.changeSessionId();
            faces.getExternalContext().redirect(request.getContextPath() + "/app/dashboard.xhtml");
            faces.responseComplete();
        } else if (status == AuthenticationStatus.SEND_CONTINUE) {
            faces.responseComplete();
        } else {
            // End a failed caller-initiated authentication dialog before the next
            // protected request. Do not retain its continuation state in the session.
            faces.getExternalContext().invalidateSession();
            faces.getExternalContext().getFlash().setKeepMessages(true);
            faces.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Sign-in failed", "Check your username and password."));
            faces.getExternalContext().redirect(request.getContextPath() + "/login.xhtml");
            faces.responseComplete();
        }
    }

    public void logout() throws Exception {
        FacesContext faces = FacesContext.getCurrentInstance();
        HttpServletRequest request = (HttpServletRequest) faces.getExternalContext().getRequest();
        request.logout();
        request.getSession().invalidate();
        faces.getExternalContext().redirect(request.getContextPath() + "/login.xhtml");
        faces.responseComplete();
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
