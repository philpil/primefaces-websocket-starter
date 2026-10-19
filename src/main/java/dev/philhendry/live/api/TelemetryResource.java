package dev.philhendry.live.api;

import dev.philhendry.live.model.NodeState;
import dev.philhendry.live.service.TelemetryService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** External producer example. This route uses a configured API key, not a browser session. */
@Path("/")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class TelemetryResource {
    @Inject private TelemetryService service;

    @GET @Path("health")
    public JsonObject health() { return Json.createObjectBuilder().add("status", "UP").build(); }

    @POST @Path("ingest") @Consumes(MediaType.APPLICATION_JSON)
    public Response ingest(@HeaderParam("X-Ingest-Key") String suppliedKey, JsonObject body) {
        String expected = System.getenv("DEMO_INGEST_KEY");
        if (expected == null || expected.isBlank()) throw new WebApplicationException("Ingestion is not configured.", 503);
        if (suppliedKey == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new WebApplicationException("Invalid ingestion key.", 401);
        }
        try {
            service.update(body.getString("nodeId"), body.getJsonNumber("load").doubleValue(),
                    NodeState.Status.valueOf(body.getString("status")), "External API");
            return Response.accepted(Json.createObjectBuilder().add("accepted", true).build()).build();
        } catch (IllegalArgumentException | NullPointerException | ClassCastException exception) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Json.createObjectBuilder()
                    .add("error", "Supply a known nodeId, a load from 0 to 100, and ONLINE, DEGRADED or OFFLINE status.").build()).build();
        }
    }
}
