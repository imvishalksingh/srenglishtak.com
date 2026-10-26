# Book purchases: deployment and testing

Current scope: one-time purchases for each book on Android (Google Play) and the website (Razorpay). iOS purchasing is deliberately disabled for a later integration. The Node backend is not used.

Code and local automated tests do not configure payment accounts or prove real provider connectivity. A Google service-account file has been supplied and validated with OAuth. After the user enabled Android Publisher on 27 October 2026, a read-only product-catalog check returned HTTP 200. A Razorpay account/test key pair has not been supplied. Checkout stays unavailable until the corresponding provider is configured. Never substitute a fake payment-success response in the deployed service.

## 1. Database and private book storage

Back up the database. Apply `db/003_purchases_and_limits.sql` after migrations 001/002 and **before deploying the upgraded backend**. Existing projects that already applied 001/002 only need 003. Hibernate does not modify the schema at startup.

Migration 003 creates the purchase ledger, checkout orders, revocation records, shared rate-limit counters, and logout-token revocations. It creates a private Supabase bucket named `book-files` and a restrictive Storage policy that prevents direct `anon`/`authenticated` access to that bucket, including when an older permissive policy exists. The backend's server role gets table access when that role was provisioned as described in README.

Use the dashboard's **Book File** upload to put PDF documents in `book-files`. Public uploads now accept cover/other images only; PDF/EPUB public uploads are rejected. Protected editions must be PDF. Do not make `book-files` public or grant browser/mobile roles direct read policies. Publishing and page rendering check the bucket's private status; publishing checks that the private object exists.

Public catalog/progress/recommendation responses do not reveal book file locations. `/api/books/{id}/stream` is retired with HTTP 410, including for purchasers/admins. The protected Android reader uses short-lived, attested sessions and device-signed requests for server-watermarked page images. Follow [PROTECTED_READER_SETUP.md](PROTECTED_READER_SETUP.md), including migration 004 and the coordinated Play/backend rollout. Website purchases unlock reading in the Android app; the website no longer offers original downloads.

Existing books keep price 0 until an admin changes them. To sell a previously public book, upload a private copy and remove its old public Storage object first. The publishing check rejects a previously public URL that remains reachable. Keep backups privately; do not leave equivalent copies publicly hosted. An object already downloaded cannot be recalled. Historical free access is not converted into a purchase.

## 2. Google Play server credentials

1. In Google Cloud Console, create/select a project and enable **Google Play Android Developer API**.
2. Create an IAM service account for this backend and a JSON key. Store it in a server secret mount, for example `/etc/secrets/google-play-service-account.json`; do not put it in Flutter assets, a Vite variable, Git, or the Docker image.
3. In Play Console **Users and permissions**, invite the service-account email and grant the app-specific purchase/order permissions required by the Android Publisher API. Give access to the app with package `com.srenglishtak.app`. No project-owner permission is required for the app itself. Google’s billing setup guide lists “View financial data, orders, and cancellation survey responses” and “Manage orders and subscriptions”; grant those for this app.
4. Set `GOOGLE_PLAY_CREDENTIALS_FILE` to the mounted path. Set `GOOGLE_PLAY_PACKAGE_NAME=com.srenglishtak.app`.
5. Generate a random 32-byte key, encode it as base64, and set `PAYMENT_TOKEN_ENCRYPTION_KEY`. For example, generate it privately on the server with `openssl rand -base64 32`. Keep this key stable and backed up: existing encrypted purchase tokens cannot be reconciled if it is lost or replaced without re-encryption.

The backend uses Google's credential library to obtain Android Publisher OAuth access. It verifies ProductPurchaseV2 with Google, requiring PURCHASED state, the correct product, quantity 1, no refund, no rental/preorder, the configured environment and the app-account binding. It acknowledges non-consumable purchases without consuming them. Google tokens are encrypted with AES-GCM; the database stores a SHA-256 identity for deduplication.

## Render activation for Android test purchases

The app message `Book purchases are not configured yet` means `/api/payments/context` returned `android_enabled: false`. This requires a non-disabled payment mode, the Google credentials file path, and the token encryption key. Setting these enables checkout; Google API access, Play permissions and product activation must also pass before verification works.

1. Enable [Google Play Android Developer API in project srenglishtak](https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com?project=srenglishtak). The initial credential check returned `403 SERVICE_DISABLED`. After enablement on 27 October 2026, the catalog request returns HTTP 200. This confirms catalog access; an actual purchase-verification request is still required to validate the full billing flow.
2. Grant the service-account email access to `com.srenglishtak.app` in Play Console, with the two billing/order permissions listed above. Read the email locally from the supplied JSON file; never upload the private key to Play Console.
3. Render service **Environment > Secret Files**: add filename `google-play-service-account.json`, with the contents of the ignored local `secrets/google-play-service-account.json`. Render mounts it at `/etc/secrets/google-play-service-account.json`.
4. The ignored local `secrets/render-google-play-test.env` contains only the four Android test settings, including the existing local encryption key. Import its declarations into the intended test service. If Render already has an encryption key, preserve that existing value. Do not import the entire local `.env`: its blank local DB values must not overwrite production DB configuration. Keep this file and the JSON out of Git and chat.
5. Deploy the updated Dockerfile. Its non-root app user also belongs to group `1000`, as Render requires for runtime secret-file access. Never copy the JSON into the image.
6. Add the phone's purchasing Google account as a Play Console **license tester**, and confirm the mapped one-time product and its purchase option are active. The installed debug APK can use Play test billing for a license tester with the matching package name. Complete purchase, restart/restore, and refund tests with Google's test payment instruments before live activation.

Expected context after configuration: `environment: test`, `android_enabled: true`. Razorpay remains disabled until its real test keys and webhook secret are supplied. The existing paid book uses `book_english_grammar_01`, with an ACTIVE buy option, legacy compatibility and India availability. Each additional paid book needs its own Google Play one-time product ID. Create/activate that product and its buy option in Play Console, then use the exact ID in the book form. A product ID cannot be assigned to two books. Existing purchased mappings cannot be changed.

References: [Render runtime secret files](https://render.com/docs/docker-secrets#accessing-secret-files-at-runtime), [Render environment settings](https://render.com/docs/configure-environment-variables), [Google Play API setup](https://developers.google.com/android-publisher/getting_started), [Google Play license testing](https://developer.android.com/google/play/billing/test).

## 3. Configure each book in Play Console and the admin dashboard

Create an active **one-time / non-consumable product** for each book, with a permanent unique product ID such as `readiq.book.english_basics`. Set pricing, availability and the purchase option in Play Console. The old `sr_english_tak_premium` global product is not used by this integration.

In the dashboard Books form, upload the private PDF, set the INR website price, enter that book's Google Play product ID, then publish it. Google Play's displayed price comes from Play Console and may differ from the website's INR price. One product ID cannot map to multiple books, and a purchased product mapping cannot be changed. Books with payment records must be hidden rather than deleted.

Use `PAYMENTS_MODE=test` on a dedicated test deployment and add a license tester in Play Console. Prefer an internal testing track with the correct application ID and signing. Use Google's test payment instruments; do not use real money on the test deployment. Buy while signed into the intended SR English Tak account. Purchases are bound to that account and cannot be reassigned by changing phones or submitting the receipt under another login.

Test successful purchase, pending/declined payment, app restart, restore, another device with the same app account, wrong account, duplicate verification and refund/revoke. The app completes purchases only after the backend verifies and records them. If verification cannot finish, return to the same account and use **Restore purchases**.

## 4. Razorpay test checkout

Razorpay requires an account even for its real Test Mode API. Create an account, switch to Test Mode, and generate its `rzp_test_...` key ID and key secret. Set these **only on the backend**:

```dotenv
PAYMENTS_MODE=test
RAZORPAY_KEY_ID=rzp_test_REPLACE_WITH_YOUR_KEY
RAZORPAY_KEY_SECRET=REPLACE_WITH_YOUR_SECRET
RAZORPAY_WEBHOOK_SECRET=REPLACE_WITH_A_SEPARATE_RANDOM_SECRET
```

Configure automatic payment capture. Register an HTTPS webhook at `https://YOUR_BACKEND/api/payments/razorpay/webhook`, using the same webhook secret. Subscribe to `payment.captured`, `order.paid`, `refund.processed`, and `payment.dispute.created`. Signatures are checked against the raw JSON body before processing. Keep old webhook secrets available operationally when planning rotation/retries; this implementation accepts one configured webhook secret at a time.

The website never supplies the authoritative amount. The backend creates an order using its database price and INR currency. Checkout creation reuses a per-user request key. Verification checks the checkout HMAC and independently fetches the payment/order from Razorpay, requiring captured status, matching order/receipt, full expected amount and currency. A browser callback alone never grants ownership.

Test checkout at `/books` on the website. Sign in with the same account as Android. The website uses Google sign-in when `VITE_GOOGLE_CLIENT_ID` is set, or email/password. Add the website origin to Google OAuth's authorized origins and to backend `ALLOWED_ORIGINS`. Build with `VITE_API_URL=https://YOUR_BACKEND/api`. No Razorpay secret belongs in Vite variables: the backend returns only the public checkout key ID.

## 5. Refunds, disputes, and verification availability

Refunds and disputes revoke the corresponding purchase. Replayed capture/verification cannot reactivate a revoked receipt. Other independent valid purchases can still grant ownership. A disputed purchase stays revoked pending manual support review; automatic dispute-win reinstatement is not implemented. Issue refunds in Play Console or Razorpay's dashboard, not by editing client preferences.

The scheduled worker rechecks active purchases older than five minutes in batches of 100. A paid-file access request also rechecks stale ownership before delivering content, so an overloaded/offline worker does not bypass verification. Provider failures deny stale access temporarily with an unavailable response; they do not grant permanent access. Refund detection is not instantaneous: allow up to the verification freshness interval and the provider's own state propagation. Monitor the reconciliation warning logs and the admin Purchases page.

## 6. Live release

After provider tests pass, deploy the backend and database migration first, then the matching Flutter/dashboard/website builds. Set `PAYMENTS_MODE=live`, use `rzp_live_...` keys plus a live-mode webhook, and confirm Google Play real-purchase verification. Live mode does not grant access from test receipts or test ledger entries. Retest the purchase/restore/refund flow with the correct deployment environment. Keep API docs disabled unless intentionally enabled.

Do not mark live checkout verified until the account setup, product activation, private Storage policy, mounted keys and provider smoke tests have actually passed. This change does not publish the app, modify Render environment variables, run production migrations, or submit any store release automatically.

## References

- [Google Android Publisher setup](https://developers.google.com/android-publisher/getting_started)
- [Google Play billing security](https://developer.android.com/google/play/billing/security)
- [Google Play billing tests](https://developer.android.com/google/play/billing/test)
- [Google ProductPurchaseV2](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.productsv2)
- [Razorpay API keys](https://razorpay.com/docs/payments/dashboard/account-settings/api-keys/)
- [Razorpay checkout integration](https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/integration-steps/)
- [Razorpay webhook validation](https://razorpay.com/docs/webhooks/validate-test/)

Protected-reader changes supersede earlier raw-stream instructions. Existing purchased access remains server-side; test and live payment environments remain separate.

### Book creation: product ID already used

The phone's full create payload was checked against the live backend using temporary hidden fixtures: free PDF and paid PDF with a new product ID both returned 201. Reusing that ID for a second paid book reproduced `400 Invalid data or referenced resource`; the user confirmed reusing the existing product ID. All temporary books and the disposable account were removed.

The backend now trims product IDs and returns an explicit 409 conflict before inserting/updating a duplicate mapping. The database unique index is retained; a concurrent unique-index conflict gets the same actionable response. Other database errors remain generic. The actual backend passed 47 tests, including the mobile payload, duplicate create/update and conflict fallback. This error-message improvement needs a backend commit/deploy; the current app can already create a book with a distinct correctly configured product ID, without a new APK/AAB.
