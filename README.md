# Pulse Client Mobile

Android client ("Sentry") for the [pulse](https://github.com/julecko/pulse) server monitor. It is a
mostly read-only fleet view: hosts, live CPU / memory / disk / load, a 24-snapshot-style timeline you
can scrub, snapshot comparison, and each host's auth log. Pairing agents can be approved, revoked and
removed from the app. Alert rules can be created and managed from the app, alerts fired by the server
show up in an Alerts feed and can be acknowledged, and the device can register for push notifications.

## Talking to the server

The app uses the server's user-facing endpoints:

| Endpoint | Purpose |
| --- | --- |
| `GET /healthz` | Reachability check (unauthenticated) |
| `POST /auth/login` | Username + password → session bearer token |
| `GET /agents` | Fleet list |
| `GET /agents/{id}/metrics?limit=N` | Metric snapshots (CPU, memory, disks, load, uptime) |
| `GET /agents/{id}/auth-events` | PAM session / auth-failure log |
| `POST /agents/{id}/approve`, `POST /agents/{id}/revoke`, `POST /agents/{id}/unrevoke`, `DELETE /agents/{id}` | Agent lifecycle |
| `GET` / `PUT /agents/pairing` | Open or close the pairing window |
| `GET /users/me` | The signed-in user |
| `GET/POST /alert-rules`, `PATCH`/`DELETE /alert-rules/{id}` | Alert rule management |
| `GET /alerts`, `POST /alerts/{id}/acknowledge` | Alert feed |
| `GET/POST /push-devices`, `DELETE /push-devices/{id}` | This device's push registration |
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

Settings lists every host with approve / revoke / unrevoke / remove (removal is a two-step confirm
and deletes the host's stored metrics), and controls the **pairing window** — new agents can only
pair while it is open, optionally for a fixed time.

## Alerts & push notifications

The **Alerts** tab has two sub-tabs:

- **Alerts** — the feed of alerts the server's rules have fired (`GET /alerts`), newest first, with
  ALL / CRIT / WARN / ACK filters. Tapping a card expands it (trigger/resolve/ack times, which rule)
  with actions to open the host or acknowledge it.
- **Rules** — every alert rule (`GET /alert-rules`), each watching one metric
  (`cpu_usage_percent`, `memory_used_percent`, `swap_used_percent`, `disk_used_percent`,
  `load_avg_one/five/fifteen`) against a threshold for a minimum duration, scoped to one agent or
  every agent. Rules can be enabled/disabled, have push toggled, or be deleted; "+ NEW RULE" creates
  one.

Push notifications go through Firebase Cloud Messaging. The app registers this device's FCM token
with `POST /push-devices` once signed in (and again on token refresh); Settings → **NOTIFICATIONS**
lists every device registered for the account and can remove one. This needs:

- `app/google-services.json` for this Firebase project (gitignored — each developer/environment
  provides their own; the app won't build without one present).
- The server side configured per its own README (`[push] fcm_service_account`) — without it, alerts
  are still recorded and shown in the feed, just never pushed.
- Android 13+ also needs the notification permission, which the app asks for on first launch.

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
├── push/        FCM messaging service, token registration, notification channel
├── ui/
│   ├── connect/     first-run server + login
│   ├── fleet/       host list with live usage bars
│   ├── host/        host detail: timeline, overview, CPU, auth, snapshots
│   ├── alerts/      alert feed + alert rule management
│   ├── settings/    server address, hosts, notification devices, sign out
│   ├── components/  severity markers, meters, charts
│   └── theme/       Sentry palette and type
└── util/        timestamp and metric helpers
```

## Design

Mono ink on ink; red only where something is wrong. A host is *warning* above 74 % on CPU, memory
or disk and *critical* above 88 %.
