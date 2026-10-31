# Move the existing backend to Northflank

The backend repository is `book-backend-spring`. Keep Supabase Auth, PostgreSQL and private
Storage in the existing project. This migration moves the Java service only. It does not
create a new database, copy book files, or require Node/Express.

Current Northflank public origin: `https://p01--srenglishtak--zygbg6n9xsw6.code.run`.
Client API base: `https://p01--srenglishtak--zygbg6n9xsw6.code.run/api`.
Health paths are at the origin root, without `/api`.

## Service and container

1. Connect this backend repository and the branch containing these changes. Select Dockerfile
   build, Dockerfile `Dockerfile`, build context repository root. Do not select the Flutter,
   Node, or archived Spring repository. Never inject production secrets into build arguments.
2. Create a combined build/deployment service or a deployment linked to this build. Keep the
   image's default command/entrypoint; remove old npm, Maven dev, shell or Render overrides.
3. Set runtime variables from `deploy/northflank.env.example`. In the Northflank UI, enter
   values without surrounding shell quotes. Enable `SPRING_PROFILES_ACTIVE=northflank` and
   `PORT=8080`. The profile binds `0.0.0.0`, limits database wait time, and enables graceful
   shutdown. Allow at least 30 seconds for termination where configurable.
4. Networking: internal port **8080**, protocol **HTTP**, public. TLS terminates at Northflank;
   do not expose PostgreSQL or add TLS configuration to Tomcat. A generated HTTPS hostname
   is provided; a custom domain is optional.
5. Start with one instance and sufficient memory for PDFBox raster rendering. **1 GiB is a
   starting allocation to measure**, not a proven capacity guarantee. JVM heap is capped at
   60% of container memory, leaving space for images/native buffers/threads. Load-test with
   actual books and concurrent readers before scaling. Rendering is limited to one page
   operation per instance and may return a retryable busy response. No persistent disk is
   needed for book storage; books stay in Supabase.

## Secrets and connections

- Copy the same production Supabase URL, keys, database and raw database password. Keep DB
  credentials separate from `DB_URL`. Do not paste the password into the JDBC URL; special
  characters such as `@` or `#` are literal in the separate Northflank `DB_PASSWORD` value.
- Prefer Supabase **session pooler** port 5432 if IPv4 connectivity is needed. Direct hosts
  may require IPv6; transaction pooler settings have different session semantics. Choose a
  deployment region near the database. Budget **all replicas + rollout overlap + old Render
  instances** against the Supabase connection limit (`DB_POOL_SIZE` is per instance).
- Use verified TLS. The example uses `sslmode=verify-full` with the Java trust store. If the
  database certificate authority is not trusted there, install its verified CA in a Java
  truststore or mount the CA and use `sslmode=verify-full&sslrootcert=/path/to/root.crt`
  without `sslfactory`. Do not fix certificate errors by disabling verification.
- Set `PAYMENTS_MODE=live` for existing real purchases. Preserve
  `PAYMENT_TOKEN_ENCRYPTION_KEY` **exactly**; generating a new key makes encrypted receipt
  tokens unreadable. Keep a secure backup outside Git. No new payment is needed to test
  an existing entitlement.
- Recommended portable credential source: put the original service-account JSON in the
  secret runtime variable `GOOGLE_PLAY_CREDENTIALS_JSON`; leave
  `GOOGLE_PLAY_CREDENTIALS_FILE` empty. The JSON is parsed in memory with the restricted
  service-account loader, never written into the image or returned by the API. Preserve
  embedded private-key `\n` escapes by using the original JSON, not a doubly escaped string.
- Mounted files remain supported: set `GOOGLE_PLAY_CREDENTIALS_FILE` to the absolute mount
  path and leave the JSON variable empty. Northflank secret files can be root-owned; verify
  that runtime UID **10001** can read the mount without making the key public. Do not run
  the whole application as root to fix permissions.
- The protected reader inherits the payment account by default. Preserve its project
  number, Play **app-signing** SHA-256, minimum supported version and package name. For a
  different reader account, explicitly set both `PLAY_INTEGRITY_CREDENTIALS_FILE` and
  `PLAY_INTEGRITY_CREDENTIALS_JSON`, leaving the unused source empty. Conflicting sources
  fail at startup. Parsing validates the key locally; Google API permissions/network
  availability still require functional checks.
- Preserve Razorpay secrets only if website payments are enabled. Update its webhook URL
  to the new API origin and verify delivery before retiring Render.
- `ALLOWED_ORIGINS` must contain actual HTTPS **frontend** origins, including
  `https://srenglishtakadmin.netlify.app` and the public website's actual origin, without
  paths/trailing slashes. A native Android app does not require CORS. The backend hostname
  alone does not authorize dashboard requests. Keep public admin registration and API docs
  disabled. Keep the password-reset URL pointed at a working frontend route, matching the
  Supabase redirect allowlist; the backend hostname is not automatically that frontend.

## Forwarded client addresses

Keep `server.forward-headers-strategy=none`: the rate limiter processes the proxy chain
itself. `TRUSTED_PROXY_IPS` accepts exact numeric IPs and verified ingress CIDRs. It reads
all `X-Forwarded-For` fields from right to left and stops at the first untrusted hop. A
client cannot choose an address through its own leftmost header. Invalid headers fall
back to the socket peer; wildcard and `/0` trust rules are rejected.

Ask Northflank for the actual ingress peer address/range and confirm that the ingress
appends or replaces client-supplied forwarding headers before configuring trust. Do not
guess `10.0.0.0/8`, trust all private networks, or assume Render's addresses carry over.
Blank trust remains secure but can group users behind the same proxy into one IP bucket
(including the login limit); this must be checked before production cutover. Verify that
different real client networks get separate limits, and spoofed forwarded headers cannot
reset a limit. Never log bearer tokens, full forwarding headers or service-account JSON.

## Health checks and deploy verification

Configure the three checks in `deploy/northflank-healthchecks.json` through the service's
Health checks page (the file is a settings reference, not a complete service template).
Startup waits up to about five minutes. Liveness checks the Java application only, so a
database outage does not restart every instance. Readiness checks Spring's accepting-
traffic state and a bounded database query against the request-protection table; failures
return minimal `503 {"status":"DOWN"}`. Probe requests bypass database rate limiting.
`/health` retains its existing response for compatibility and does not verify the database.

After deployment, require all of these before switching live clients:

1. Runtime logs show the active `northflank` profile and Tomcat started on 8080. Check for
   restarts, OOMs, missing runtime variables, JDBC TLS/auth errors and credential-file access
   failures. “Running” alone only confirms a container process.
2. `GET /health` returns 200; `GET /health/live` and `/health/ready` return 200/UP. No auth
   header is needed. A text response from `istio-envoy` saying **connection refused** means
   ingress could not reach a listener: check the port, active deployment and startup logs.
   Do not bypass readiness or weaken authentication to hide it.
3. `GET /api/books` returns a valid catalogue; an unauthenticated private API returns 401.
   Check admin and public-site CORS preflights from their real origins. Ensure DB migrations
   001–005 required by the current app are already applied; these changes introduce no new
   schema migration and never run production SQL automatically.
4. Sign in with an existing user/admin. With a **Play-installed** app, verify an existing
   real purchase/restore and protected book pages, then exercise normal learning progress.
   Use test-mode credentials/accounts separately for new test payments; avoid charging again
   to diagnose an entitlement. Health probes do not validate Google/Play/Storage permissions.
5. Rebuild Flutter with `--dart-define=API_BASE_URL=https://p01--srenglishtak--zygbg6n9xsw6.code.run/api`.
   Set Vite/Netlify `VITE_API_URL` to that same base and rebuild both web clients. The admin
   CSP must allow the new origin in `connect-src` (updated in this workspace). Hosted
   environment overrides can supersede repository defaults. Flutter URLs are baked into
   the installed binary; older versions continue to call Render.
6. Keep Render available until installed-app migration and callbacks are accounted for.
   Record a rollback route/build using the old origin; keep the same DB and encryption key.
   Monitor 5xx, latency, memory, DB pool pressure and payment verification failures throughout
   the rollout. Choose a version code higher than Play Console's latest before publishing.

## References

- [Northflank public ports and generated domains](https://northflank.com/docs/v1/application/network/configure-ports)
- [Northflank probes](https://northflank.com/docs/v1/application/observe/configure-health-checks)
- [Northflank runtime secret files and permissions](https://northflank.com/docs/v1/application/secure/upload-secret-files)
- [PostgreSQL JDBC TLS verification](https://jdbc.postgresql.org/documentation/ssl/)
