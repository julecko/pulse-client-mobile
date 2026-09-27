# Pulse Client Mobile

Android client for the [pulse](https://github.com/julecko/pulse) server monitor. It is a
mostly read-only view of your hosts, live CPU / memory / disk / load, a 24-snapshot-style timeline you
can scrub, snapshot comparison, and each host's auth log. Pairing agents can be approved, revoked and
removed from the app. Alert rules can be created and managed from the app, alerts fired by the server
show up in an Alerts feed and can be acknowledged, and the device can register for push notifications.
Offline checks, geo alerts, per-host login notifications and the server's data retention are all
configurable from the app too.

## Talking to the server

The app uses the server's user-facing endpoints:

| Endpoint | Purpose |
| --- | --- |
| `GET /healthz` | Reachability check (unauthenticated) |
| `POST /auth/login` | Username + password → session bearer token |
| `GET /agents` | Fleet list |
| `GET /agents/{id}/metrics?limit=N` | Metric snapshots (CPU, memory, disks, load, uptime) |
| `GET /agents/{id}/auth-events` | PAM session / auth-failure log, with the client IP's location when the server has GeoIP |
| `POST /agents/{id}/approve`, `POST /agents/{id}/revoke`, `POST /agents/{id}/unrevoke`, `DELETE /agents/{id}` | Agent lifecycle |
| `GET` / `PUT /agents/pairing` | Open or close the pairing window |
| `GET /users/me` | The signed-in user |
| `GET/POST /alert-rules`, `PATCH`/`DELETE /alert-rules/{id}` | Alert rule management |
| `GET /alerts`, `POST /alerts/{id}/acknowledge` | Alert feed |
| `GET/POST /push-devices`, `DELETE /push-devices/{id}` | This device's push registration |
| `GET /agents/offline-alerts`, `PUT /agents/{id}/offline-alert` | Per-host offline check |
| `GET/PUT /agents/{id}/pam-notifications` | Which of a host's login events are pushed |
| `GET/PUT /geo-alerts/settings` | Allowed login countries for geo alerts |
| `GET /retention`, `PUT /retention/{data}` | How long the server keeps metrics, auth events and alerts |
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
  one. Below the rules:
  - **Offline alerts** — per approved host, how long it may go without sending metrics before the
    server raises a critical alert (off, 2 min … 1 day). Hosts that are offline right now are marked,
    here and in the host list.
  - **Geo alerts** — the countries SSH logins may come from, whether failed logins count, and whether
    geo alerts are pushed. Changes are sent together on SAVE, since the server replaces them all at
    once. Shows which GeoIP database the server loaded, or warns that none is (then nothing is checked).

Geo alerts show the login's user, IP and location when expanded, and resolve when acknowledged.
Offline alerts resolve by themselves once the host sends metrics again.

Each host's **AUTH** tab chooses which of its PAM events are pushed (logins, failures, logouts) and
shows where each remote login came from, when the server could locate it.

Settings → **AUTO REFRESH** sets how often the host list, host details and alerts reload while open
(5 s, 10 s by default, 30 s, 1 min or 5 min). It's stored on the device and kept across sign-outs.

Settings → **DATA RETENTION** sets how long the server keeps metrics, auth events and resolved alerts
(7 days … 1 year, or forever), or resets one to the server config's default. Shortening a period
deletes the older data on the server right away, so it asks for a second tap first.

Push notifications go through Firebase Cloud Messaging. Alert pushes (rules, offline and geo alerts)
use the *Alerts* channel and open the Alerts tab; plain pushes (host logins and
`pulse-agent-cli notify` messages) use the quieter *Host notifications* channel. The app registers this device's FCM token
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
│   ├── fleet/       PULSE tab: host list with live usage bars
│   ├── host/        host detail: timeline, overview, CPU, auth, snapshots
│   ├── alerts/      alert feed + alert rule management
│   ├── settings/    server address, hosts, notification devices, sign out
│   ├── components/  severity markers, meters, charts
│   └── theme/       Pulse palette and type
└── util/        timestamp and metric helpers
```

## Design

Mono ink on ink; red only where something is wrong. A host is *warning* above 74 % on CPU, memory
or disk and *critical* above 88 %.
