# Admin dashboard integration

Admin URL: https://srenglishtakadmin.netlify.app
Frontend repository: https://github.com/imvishalksingh/admin.srenglishtak
Frontend folder: C:/Users/ASUS/Documents/Playground/SREnglishTak/sr_admin_web

Deploy the current dashboard and this backend together. Preserve existing `ALLOWED_ORIGINS` and include `https://srenglishtakadmin.netlify.app` without a trailing slash. Read-only production preflight checks passed after the setting was updated on 27 October 2026.

Apply `db/005_admin_cover_storage.sql` as postgres in Supabase SQL Editor before using cover uploads. The restrictive policy prevents direct client reads/writes to storage.objects entries for `book-covers`, even if an old permissive policy exists. Supabase's public object endpoint still serves the intended public cover images. Never make `books` or `book-files` public.

`StorageService` uploads public images to the separate `book-covers` bucket, creating it on the first upload if it does not exist. The bucket limits MIME types to images and uploads to 50 MiB. An existing private bucket is never changed to public. Source PDFs stay in private `book-files`; public document uploads and original PDF streaming remain blocked.

Existing cover URLs still pointing at the private legacy `books` bucket need their covers uploaded again through Edit Book after deployment. Existing books, purchase records, source PDFs and storage objects were not migrated or deleted automatically.

No new environment secrets, payment-mode changes or Flutter rebuild are required. Keep production PAYMENTS_MODE=live and the existing payment encryption key.

Dashboard behavior and deployment instructions are in the frontend README.md. Current checks: 15 frontend regression tests; production build and lint; 51 Spring tests against disposable localhost PostgreSQL; additional cover-policy regression after migration 005. Browser checks used isolated fixtures with network requests intercepted, not production writes.

Live temporary admin/content test creation was rejected by automatic approval review and was not executed. Complete final deployed login/upload/CRUD checks with the existing administrator account, or with explicitly approved temporary production fixtures.

Owner confirmed migration 005 ran successfully on 27 October 2026. The owner requested local code/build only and no temporary production test data. No commit, push or deployment was performed. Final live checks will use the existing admin account after deployment.
