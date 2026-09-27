# Pulse Client Mobile

Android client ("Sentry") for the [pulse](https://github.com/julecko/pulse) server monitor. It is a
read-only fleet view: hosts, live CPU / memory / disk / load, a 24-snapshot-style timeline you can
scrub, snapshot comparison, and each host's auth log. Pairing agents can be approved, revoked and
removed from the app.

## Talking to the server

The app uses the server's user-facing endpoints:

| Endpoint | Purpose |
| --- | --- |
| `GET /healthz` | Reachability check (unauthenticated) |
| `POST /auth/login` | Username + password → session bearer token |
| `GET /agents` | Fleet list |
| `GET /agents/{id}/metrics?limit=N` | Metric snapshots (CPU, memory, disks, load, uptime) |
| `GET /agents/{id}/auth-events` | PAM session / auth-failure log |
| `POST /agents/{id}/approve`, `POST /agents/{id}/revoke`, `DELETE /agents/{id}` | Agent lifecycle (revoked agents can't be re-approved) |
| `GET` / `PUT /agents/pairing` | Open or close the pairing window |
| `POST /auth/logout` | Ends the session on sign out |

On first launch you enter the server address (e.g. `https://10.0.0.5:8443`), a username and a
password. The app health-checks the server, logs in, and stores the address and credentials in the
app's private DataStore so it can log in again when a session expires. Users are created on the
server with `pulse-server-cli users add`; there is no registration in the app.

### TLS

The server certificate is always verified — the app has no "skip verification" switch, because that
would let anyone on the network capture your password. A server with a certificate from a public CA
just works. For the self-signed certificate `pulse-server-gen-cert` creates, the connect screen shows
the certificate's SHA-256 fingerprint; compare it with

```sh
openssl x509 -noout -fingerprint -sha256 -in /etc/pulse-server/certs/cert.pem
```

and tap *Trust & connect*. Only that exact certificate is accepted from then on (if the server's
certificate changes, the app refuses to connect until you sign out and trust the new one).

### Login limits

The server allows 5 failed logins per minute per IP (`429` + `Retry-After`); successful logins don't
count. If the saved password stops working (e.g. it was changed), the app stops retrying and asks
you to sign out and sign in again rather than locking itself out.

### Managing hosts

Settings lists every host with approve / revoke / remove (removal is a two-step confirm and deletes
the host's stored metrics), and controls the **pairing window** — new agents can only pair while it
is open, optionally for a fixed time.

## Building

Requirements: JDK 17+ and the Android SDK (Android Studio installs both).

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew installDebug         # to a connected device / emulator
./gradlew testDebugUnitTest    # unit tests
```

Android Studio writes your SDK path to `local.properties`, which is git-ignored.

## Project layout

```
app/src/main/java/sk/dilino/pulseclientmobile/
├── data/        ConnectionStore (server + credentials), PulseApiClient, JSON models
├── ui/
│   ├── connect/     first-run server + login
│   ├── fleet/       host list with live usage bars
│   ├── host/        host detail: timeline, overview, CPU, auth, snapshots
│   ├── settings/    server address, sign out
│   ├── components/  severity markers, meters, charts
│   └── theme/       Sentry palette and type
└── util/        timestamp and metric helpers
```

## Design

Mono ink on ink; red only where something is wrong. A host is *warning* above 74 % on CPU, memory
or disk and *critical* above 88 %.
