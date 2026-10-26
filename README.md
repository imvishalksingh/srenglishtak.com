# ReadIQ / SR English Tak Spring backend

Java 21, Spring Boot 3.5.16, PostgreSQL, Supabase Auth and Storage.
Node/Express is not required to run this backend. The Flutter app, its built-in admin screens,
and the React admin dashboard use this API.

## Setup

1. Configure the same Supabase project that contains the current app data.
2. For an existing database, apply db/002_security_and_contract.sql using a database owner.
   For a fresh Supabase project, apply db/001_schema.sql, then db/002_security_and_contract.sql.
   These scripts are not executed automatically at startup.
3. Copy .env.example to .env and fill in the server-only credentials. The .env file is
   read as Java properties: use plain KEY=value lines without shell quotes.
4. In Supabase Auth, enable Google and configure the Google web client ID used by Flutter and
   the dashboard. Google ID tokens use Supabase's native ID-token grant.
   Configure the password-reset redirect in Supabase's allowlist and this service.
5. Set ALLOWED_ORIGINS to the actual dashboard/Flutter web origins. Native Flutter does not
   need an origin entry. Start with mvn spring-boot:run, or build and run the JAR:

~~~powershell
mvn -B verify
java -jar target/backend-1.0.0.jar
~~~

GET /health confirms the HTTP service is running. It is a liveness response, not a database/
Supabase readiness probe. Never put the database password or Supabase service-role key in
Flutter, Vite variables, a browser, or version control.

The JDBC connection must use the database owner or a dedicated server role that can access
all app tables. To use readiq_backend, create that login role and password separately,
grant USAGE on schema public, then reapply migration 002; it grants app-table DML and server
RLS policies. Keep database connectivity private and use TLS with certificate verification.
The migration never promotes a user profile to admin.

## Security and client contract

- Existing admins are resolved from profiles.role after Supabase validates the access token.
  Public registration creates regular users; admin registration is disabled by default.
  Provision trusted admins through a controlled database operation.
- Writes use explicit field allowlists and bound SQL parameters. User resources derive their
  owner from the authenticated principal. Transactions protect question replacement,
  bulk imports, scoring, progress updates, and challenge submissions.
- IDs remain flat: book_id, user_id, unit_id, category_id. Create endpoints return 201
  where Flutter expects it. Public catalogs remain available without login.
- Students never receive quiz/challenge answer keys or explanations containing answers.
  Admin responses retain the keys needed for editing. Quiz scores are computed on the server.
- Challenge list/detail responses contain question_count and an empty challenge_questions
  array for students. POST /api/challenges/{id}/start starts/reuses a timed attempt and returns
  started_at, expires_at, and challenge with redacted questions. Submit
  {answers:[{question_id,selected_option_index}]}. Unanswered questions count as wrong.
  Scores award 10 points per correct answer; elapsed time comes from the server.
  A legacy caller-supplied score is rejected. Duplicate submissions return 409. The deadline
  allows a ten-second network delivery grace. A challenge cannot be edited after attempts start.
- The updated Flutter client confirms answers locally and displays the server's final score.
  It no longer determines correctness from downloaded answer keys.
- Migration 002 revokes direct anon/authenticated access to all application tables,
  including answer keys and profile roles. All app data must go through Spring.
  Review other applications that directly use those tables before applying it.
  Supabase Auth APIs and existing public Storage object URLs remain separate.
- Uploads allow PDF, EPUB, PNG, JPEG, GIF and WebP up to 50 MiB, with content signature checks.
  JSON requests are bounded to 1 MiB. Storage streaming restricts credentialed downloads to
  this project's books bucket and does not follow redirects.
- Authentication attempts and uploads have bounded per-IP rate limits. Forwarded IP headers
  are trusted only from configured proxy IPs. For multiple replicas, configure distributed
  rate limiting at the ingress; in-memory buckets are local to each instance.

All 69 Node route method/path pairs are retained. Spring also keeps the additional useful
read/stream/question routes and adds challenge update/start. The contract fixture is in
src/test/resources/node-contract-routes.txt. Challenge submission and answer disclosure are
intentional security changes: deploy the updated Flutter client with this backend.

## Configure the clients

Flutter development uses localhost:8080 (Android emulator: 10.0.2.2:8080). For a physical device,
provide a reachable development URL. Release builds require an explicit HTTPS URL:

~~~powershell
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api
flutter build apk --dart-define=API_BASE_URL=https://YOUR_BACKEND/api
~~~

The dashboard reads VITE_API_URL; local development uses http://localhost:8080/api.
For a production build:

~~~powershell
$env:VITE_API_URL='https://YOUR_BACKEND/api'
npm run build
~~~

Set VITE_GOOGLE_CLIENT_ID to override the existing public OAuth client ID. The dashboard refreshes
sessions and clears them on logout/invalid refresh. Flutter serializes concurrent token refreshes
to avoid rotating the same refresh token in parallel.

The marketing website currently has no backend calls. Node source remains as a historical
reference; do not route new client builds to it.

## Tests

Unit tests run with mvn test. The integration suite is opt-in because it resets its test data.
It accepts only a **disposable localhost PostgreSQL database named readiq_test**, and refuses
other targets before initialization. It creates an auth.users stub to test Supabase foreign
keys and applies the real baseline/security migrations. The test database login needs permission
to create the test schema and stub anon/authenticated roles.

~~~powershell
$env:READIQ_TEST_DB_URL='jdbc:postgresql://127.0.0.1:5432/readiq_test'
$env:READIQ_TEST_DB_USER='YOUR_TEST_DB_OWNER'
$env:READIQ_TEST_DB_PASSWORD='YOUR_TEST_DB_PASSWORD'
mvn -B verify
~~~

Integration tests exercise the full MVC/security stack with real PostgreSQL. Only Supabase's
network boundary is mocked. Live Google/OAuth, Supabase Auth/Storage and production database
connectivity require the deployment configuration and are not proven by these tests.

## Deploy

Use the Dockerfile or run the JAR on a Java 21 host. Set environment variables, apply the reviewed
database migration, configure HTTPS/CORS/Google provider, and smoke-test user/admin flows before
directing a release build to it. A switch to Spring does not itself deploy the service or move the
existing Vercel domain.

Dependency baseline: [Spring Boot requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html).
Google flow: [Supabase ID token sign-in](https://supabase.com/docs/reference/javascript/auth-signinwithidtoken).
