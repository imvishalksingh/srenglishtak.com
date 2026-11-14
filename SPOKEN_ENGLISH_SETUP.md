# Spoken English setup

Apply `db/008_spoken_english.sql` to the Supabase database used by this backend, then deploy the local backend changes. The migration is additive and safe to repeat. It creates the lesson table and catalog index, enables row-level security and blocks direct `anon`/`authenticated` database access. It preserves existing users, books, purchases and learning data.

Deploy the updated `sr_admin_web` build through the existing Netlify workflow, then release the Flutter **1.0.15 (21)** bundle through Play internal testing. Apply the migration before deploying the backend.

## Managing content

Use **Spoken English → New lesson** in the web dashboard or **Manage Spoken English** in the mobile admin panel. Choose Daily Conversations, Pronunciation or Speaking Lessons. Enter a title, optional short description, lesson text and display order. New lessons default to Draft. Enable Published and save when ready for learners. Editing, moving between sections, unpublishing and deletion are supported.

Content is plain text suitable for conversation scripts, pronunciation guidance and speaking exercises. Line breaks are preserved. The module starts empty until an administrator adds and publishes lessons; the app does not include invented teaching content.

## API

All routes require a valid bearer session. Admin routes also require the server-confirmed ADMIN role.

| Method | Route | Result |
| --- | --- | --- |
| GET | `/api/spoken-english/lessons` | Published lesson summaries, ordered by category, display order, creation time and ID. Optional `category` filter. |
| GET | `/api/spoken-english/lessons/{id}` | Published lesson text. A draft or missing ID returns 404. |
| GET | `/api/admin/spoken-english/lessons` | All lesson summaries, including drafts. |
| GET | `/api/admin/spoken-english/lessons/{id}` | Full lesson for editing. |
| POST | `/api/admin/spoken-english/lessons` | Create a lesson; returns 201. |
| PUT | `/api/admin/spoken-english/lessons/{id}` | Replace editable fields. |
| DELETE | `/api/admin/spoken-english/lessons/{id}` | Delete a lesson; returns 204. |

Editable fields: `title` (required, up to 160), `category` (`conversation`, `pronunciation`, `speaking`), `summary` (up to 280), `content` (required, up to 40000), `lesson_order` (integer 0–100000) and `is_published` (boolean; defaults false). IDs and timestamps are server-owned. Entities remain inside the data layer; a validated DTO and explicit field allowlist control writes.

GET responses use private/no-store caching. Catalog responses omit the lesson body, which is fetched when the learner opens the selected lesson. Error responses retain the existing `{ "error": "..." }` contract. Both clients use the existing safe messages and read-only retry handling; content mutations are not automatically replayed.

## Verified locally

- Backend: **134 tests passed**, Maven package succeeded against disposable localhost PostgreSQL.
- Flutter: **167 tests passed**, including malformed responses, draft rejection, all three sections, navigation, outage recovery and admin create/edit/delete with input retention.
- Admin web: **49 tests passed**, production build succeeded. New page/form/error-contract lint checks pass.
- Browser verification used isolated mock data: edited/published a lesson, checked friendly failed-save feedback, preserved form text and verified unsaved-draft protection. No production test content was created.

After deployment, add a real lesson using the existing admin account and confirm it appears under its chosen section after publishing. Physical-device/Play-installed validation remains an owner rollout check.
