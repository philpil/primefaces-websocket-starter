# PrimeFaces WebSocket Lab

A runnable Jakarta Faces application with PrimeFaces, real login, a live dashboard, and three server-to-browser push examples. The browser does **not** poll for dashboard data.

## Run it

Install **JDK 25** and set `JAVA_HOME`. Maven is provided by the wrapper.

```bash
./mvnw clean package
java -jar target/payara-micro.jar --deploy target/websocket-lab.war --contextroot / --port 8080 --nocluster
```

On Windows, use `mvnw.cmd clean package`, then the same `java` command. In IntelliJ, open `pom.xml`, choose JDK 25 as the project SDK and Maven runner JRE, and select the Maven wrapper.

Open **http://localhost:8080**.

| Username | Default password | Role |
| --- | --- | --- |
| `alice` | `Demo123!` | Administrator: view data and change settings |
| `bob` | `Demo123!` | Viewer: view live data and receive private notifications |

Set `DEMO_PASSWORD` before starting the server to replace the default password for both users. Accounts are intentionally in memory, with PBKDF2 password hashing and container-managed authentication. Replace `DemoIdentityStore` with your database or identity provider for a real deployment. Application data resets when the server restarts.

## Versions

Stable releases checked on **19 October 2026**:

| Component | Version | Where it is configured |
| --- | --- | --- |
| PrimeFaces | **16.0.1** | `pom.xml` |
| Jakarta EE Web Profile | **11.0.0** | `pom.xml`, provided by the server |
| Payara Micro | **7.2026.10** | `pom.xml`, copied into `target` during packaging |
| Java | **25**, tested with Temurin **25.0.4.1+1** | compiler release, `.java-version`, CI |
| Maven | **3.10.0** | Maven wrapper |
| JUnit Jupiter | **6.1.3** | `pom.xml` |
| Playwright | **1.64.1** | `e2e/package.json` |

Java 27 is the newest feature release, but Payara's current supported JDK list is Java 21 and 25. This project uses the newest supported LTS, Java 25, rather than an unsupported server/JDK combination. There are no snapshot or release-candidate dependencies. Jakarta Faces, CDI, Jakarta Security, JSON-P and WebSocket implementations come from the selected Payara distribution; do not bundle another Faces implementation into this WAR.

Version references: [PrimeFaces Maven releases](https://repo.maven.apache.org/maven2/org/primefaces/primefaces/maven-metadata.xml), [Payara Maven releases](https://repo.maven.apache.org/maven2/fish/payara/extras/payara-micro/maven-metadata.xml), [Payara supported JDKs](https://docs.azul.com/payara-community/general-info/supported-platforms), [Maven downloads](https://maven.apache.org/download.cgi).

## Try the examples

1. Sign in as **alice**. Open an incognito/private browser window and sign in as **bob**. Every three seconds the server-side simulator changes a node. Both dashboards update without reloading the page.
2. Pause the simulator as Alice, then click **Send one update** or edit a node. Bob sees the same confirmed backend state. The setting change uses a normal PrimeFaces HTTP action, not a WebSocket command.
3. Click **Send me a notification** as Alice. Alice's other signed-in tabs receive it. Bob does not. This is user-targeted push, not client-side filtering of a public broadcast.
4. Disconnect your network briefly and reconnect. Faces retries previously established connections. A server-side `channel-ready` message causes a fresh snapshot read after every reconnect, including changes missed while the connection was down. If the initial connection fails or the session has expired, the dashboard tells you to refresh and sign in again.
5. Send a telemetry update from the external API example below. It enters the same service layer as the simulator and UI commands.

The green connection indicator represents the actual dashboard and private WebSocket channels. In browser developer tools, open **Network → WS** and inspect `/jakarta.faces.push/dashboard` and `/jakarta.faces.push/personal`. After a message arrives, one coalesced Faces AJAX request updates the relevant dashboard components. An HTTP refresh after a push message is expected; a recurring HTTP polling request is not used.

## External-system example

The ingestion endpoint is disabled until you configure an ingestion key. Choose a long, random value and keep it outside Git.

```bash
export DEMO_INGEST_KEY='your-local-development-ingestion-key'
java -jar target/payara-micro.jar --deploy target/websocket-lab.war --contextroot / --port 8080 --nocluster
```

In another terminal, using the same configured value:

```bash
curl -i http://localhost:8080/api/ingest \
  -H "X-Ingest-Key: $DEMO_INGEST_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"nodeId":"node-2","load":88,"status":"DEGRADED"}'
```

A successful update returns `202 Accepted`. Missing or incorrect keys return `401`; an unconfigured endpoint returns `503`; invalid node IDs, load values or statuses return `400`. API ingestion authenticates the external producer with its own key, independently from browser login. The key is compared in constant time. Use HTTPS and a managed service identity or OAuth2 for a deployed integration.

`GET /api/health` returns a simple readiness response. All six demo node IDs range from `node-1` to `node-6`.

## How the code fits together

The normal UI path stays familiar:

```text
DashboardBean → DashboardController → TelemetryService → TelemetryRepository
```

The repository is a thread-safe, in-memory stand-in for your API client or shared cache. The simulator and ingestion endpoint also call `TelemetryService`. After saving a state change, the service fires a CDI event. `DashboardPush` observes that event and sends a small invalidation message. Browser JavaScript coalesces a burst of messages and invokes `p:remoteCommand`; the view bean then reads a consistent, authoritative snapshot through the same controller/service path.

| File or package | Responsibility |
| --- | --- |
| `security/` | Form login, password verification and logout |
| `view/DashboardBean.java` | One tab's presentation model and HTTP action methods |
| `controller/DashboardController.java` | UI command authorization and service delegation |
| `service/TelemetryService.java` | Shared backend operations and CDI event publication |
| `repository/TelemetryRepository.java` | Thread-safe state and immutable snapshots |
| `backend/BackendSimulator.java` | Container-managed example of unsolicited backend updates |
| `api/TelemetryResource.java` | External producer ingestion and readiness |
| `events/` | Transport-independent change events |
| `push/DashboardPush.java` | `@Push` / `PushContext` transport adapter |
| `push/PushSession.java`, `PushAudience.java` | Active session recipient lifecycle |
| `app/dashboard.xhtml` | PrimeFaces components, `f:websocket` and `p:remoteCommand` |
| `resources/app/dashboard.js` | Connection state, coalescing and reconnect resynchronization |
| `WEB-INF/web.xml` | Faces servlet, WebSocket activation and protected URL patterns |
| `WEB-INF/payara-web.xml` | Explicit mapping of identity-store groups to application roles |

There are two session-scoped channels. Dashboard recipients use opaque session IDs, allowing a backend thread to target every active authenticated session. Personal recipients use the authenticated username, allowing all of that user's sessions to receive a notification. Both close when their HTTP session is destroyed. `PushAudience` removes recipient IDs on session destruction.

Faces' browser `onopen` callback is only called for the initial successful connection. The server observes `@WebsocketEvent.Opened` and sends `channel-ready` for every new physical connection; that is why reconnects also refresh the snapshot. This avoids relying on a callback that does not fire again.

The ready handshake is dispatched on the container-managed scheduler after the endpoint's opening callback returns. A bounded retry handles a transport that is not yet open; this is connection setup, not recurring data polling.

Do not access `FacesContext`, `PrimeFaces.current()` or a view-scoped bean from a background thread. The backend updates shared state and emits an event; a normal Faces request updates the view.

To plug in your application, replace the contents of `TelemetryRepository` with your service API/cache adapter and call `TelemetryService.update(...)` from your existing inbound listener. Keep the event publication after the authoritative state has been updated. For large tables, add a section/key to the invalidation and refresh only that section; keep full initial reads and reconnect recovery available over HTTP.

## Tests

```bash
./mvnw verify                 # Java compilation, repository/lifecycle tests, WAR packaging
node --test tests/*.test.cjs  # client coalescing and connection-state tests (Node 20+)
java tests/SmokeCheck.java    # live HTTP/WebSocket checks using only the JDK
```

Full browser integration tests start the real server and cover login, shared push, private notifications, ingestion, reconnect recovery and logout:

```bash
cd e2e
npm ci
npx playwright install chromium
npm test
```

Build the WAR first. Both integration runners start and stop their own Payara process on port `8080`; stop an existing development server before running them. Test ingestion uses a local test key configured by the runner. GitHub Actions runs the Java, JavaScript, HTTP/WebSocket and browser checks on a normal runner and uploads the WAR and browser reports.

## Docker

```bash
docker compose up --build
```

Open `http://localhost:8080`. You can supply `DEMO_PASSWORD` and `DEMO_INGEST_KEY` through an untracked `.env` file. The image runs as an unprivileged user. No database or separately installed application server is required.

## Deployment notes

This is a single-instance teaching application, with in-memory state and two demo accounts. For a deployed application, use your identity provider/database, HTTPS, secret management, rate limits on authentication/ingestion, and a shared broker/cache if multiple instances must receive the same updates. Configure the reverse proxy to allow WebSocket upgrade requests and long-lived idle connections. Browser sessions and WebSocket upgrade URLs are both protected by the container.

Useful official references: [Faces WebSocket tag](https://jakarta.ee/specifications/faces/4.1/vdldoc/f/websocket.html), [Faces push API](https://jakarta.ee/specifications/faces/4.1/apidocs/jakarta.faces/jakarta/faces/push/push), [PrimeFaces documentation](https://primefaces.org/).
