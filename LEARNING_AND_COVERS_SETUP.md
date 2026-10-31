# Learning progress and book covers

## Release behavior

- Authenticated `GET /api/user/weekly-goal` returns the current calendar week and unique active dates in `APP_TIMEZONE` (default Asia/Kolkata). It counts nonempty reading sessions, completed grammar lessons, quiz attempts and challenge results for the authenticated user only. Weeks start Monday; future and previous-week activity is excluded.
- Existing `GET /api/user/insights` stays compatible and adds `weekly_goal`. `GET /api/grammar/overview` supplies completion data without downloading full lesson content.
- `GET /api/books/{id}/cover` serves re-encoded JPEG pixels from stored image URLs in this project's legacy books or dedicated book-covers bucket. It never returns original bytes, document URLs, redirects, metadata or PDF content. Hidden books require an admin or an active purchase; missing/inaccessible books return 404. Cover input is limited to 8 MiB with bounded decoding and concurrency.
- New cover uploads use public `book-covers`; PDFs remain in private `book-files`. Never make the legacy `books` bucket public to repair covers.
- Books without uploaded cover art display a local book placeholder. To show actual artwork, upload a cover through the admin editor.

## Deployment

Deploy the reviewed backend changes to the existing Render service. Keep current credentials, payment token encryption key and Play Integrity settings unchanged. These features do not require new environment variables. Keep `APP_TIMEZONE=Asia/Kolkata` for the current app.

`db/005_admin_cover_storage.sql` secures cover uploads (already applied for this installation). `db/006_weekly_activity_indexes.sql` is optional for query performance; it only creates indexes and does not change learning/purchase data. For a large live database, plan index creation in a quiet period.

Publish the matching Flutter 1.0.7 (13) bundle through Play internal testing after the backend deploy. Older API clients retain their existing response fields; the new client falls back only on a missing endpoint, never on permission or server failures.

## Verification

The complete local backend suite passed 88 tests against disposable PostgreSQL, including real Spring Security/MVC integration. Checks cover user isolation, week boundaries, duplicate activities, cover byte sanitization, private-book cover permissions, redirect/host/path rejection and protected document streaming remaining disabled. The optional indexes were also applied successfully to the disposable database. Production learning/receipt data was not modified for testing.
