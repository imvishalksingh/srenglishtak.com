# Protected page latency follow-up

Phone verification: Play-installed app 1.0.10 (16). Backend source began clean at commit `0c65800`. Live protected responses contained the prior release's separate server stages, confirming that the earlier backend changes were deployed.

## Measured live evidence before this fix

Only numerical reader diagnostics were collected through USB. No tokens, signatures, PDF originals or page images were exported.

| Operation | Device total | Controller total | Authorization | Rendering |
| --- | ---: | ---: | ---: | ---: |
| Slow activation | 7267 ms | 5278 ms | 721 ms | 3790 ms |
| Slow next page | 4773 ms | 2944 ms | 341 ms | 2603 ms |
| Warm page | 2000 ms | 748 ms | 385 ms | 362 ms |
| Later next page | 3327 ms | 1592 ms | 556 ms | 1035 ms |
| Previous/cached page | 1999 ms | 624 ms | 299 ms | 325 ms |

Google's server Integrity verification was 62–145 ms in these activations. The render phase, rather than Integrity verification or hardware signing, was the largest measured controller cost. The difference between device and controller time includes authentication/rate-limit middleware, transport and response transfer; it is not a pure network benchmark.

## Root causes

The previous patch cached parsed documents and a few uncompressed base rasters. It did not prepare a page ahead of a Next request, and it repeated watermark/JPEG encoding for every request, including Previous. A 16 MiB uncompressed raster budget held only about two typical full-sized pages, so longer books caused repeated PDF rendering. Authentication also performed separate database queries for revocation and role on every request.

## Backend-only implementation

- Prepare at most the next adjacent page in a single bounded server worker after a valid foreground read. A foreground request for the same page joins that work. Only the latest queued adjacent page is retained; no whole-book batch job runs.
- Retain already-watermarked JPEGs in server RAM, keyed by book, source edition, page, user and reader session. Global budget: 8 MiB; hard lifetime: five minutes. Returned response buffers are separate from cache buffers so eviction cannot corrupt a response in flight.
- Keep neutral raster pixels with lossless fast compression inside the existing global 16 MiB budget. Resolution, watermark and image quality remain unchanged. This retains more pages and avoids repeating PDF rendering when a different valid session needs an already-rendered page.
- Keep current authorization, purchase/refund checks, private-bucket checks, device signatures, session expiry, nonce replay rejection and page quotas before serving prepared bytes. Caching never grants access. Originals stay private; no disk cache, public URL or additional client-side image cache is introduced.
- Read fresh revocation and database-owned role together in one database query. Neither authorization decision is cached.

No app update or database migration is needed. App 1.0.10 (16) already understands the endpoints and has safe timing diagnostics enabled. The changes remain local in the actual `book-backend-spring` repository for the owner to deploy.

## Verification and remaining live check

Tests cover scoped cached JPEGs, next-page preparation and shared work, optional preparation failure, bounded pending work, memory eviction, invalidation, lossless pixel round trips, real PDF rendering with the production worker, fresh role/revocation checks, and the existing protected-reader security integration suite.

Final verification: **124 tests passed, zero failures/errors/skips; Maven package succeeded** against disposable localhost PostgreSQL. The local synthetic PDF/production-worker test measured first render 118 ms, the prepared next page 72 ms (joining the remaining preparation), and Previous under 1 ms. These are workstation fixture measurements, not claimed phone/Render timings.

Production files changed in the actual backend: `auth/AuthService.java`, `reader/BookPageRenderer.java`, plus new `reader/PreparedReaderPages.java` and `reader/CompressedReaderRaster.java`. Tests changed: `AuthServiceTest.java`, `BackendIntegrationTest.java`, `reader/BookPageRendererTest.java`, plus new `reader/PreparedReaderPagesTest.java` and `reader/CompressedReaderRasterTest.java`.

Deployment from the actual backend repository (owner performs these commands):

```powershell
cd C:\Users\ASUS\Documents\Playground\book-backend-spring
git status --short
git add src docs/READER-PERFORMANCE.md
git commit -m "Prepare protected pages ahead and reuse personalized images"
git push
```

Wait for the existing Render service to finish deploying that commit. No app rebuild, secret change or SQL migration is needed. Optional operator fallback: `READER_PREPARE_NEXT_PAGE=false` disables background preparation while retaining protected JPEG reuse, compressed neutral rasters and fresh authorization. Do not disable access or signature checks for performance testing.

A local fixture benchmark is not a Render benchmark. After deploying these backend files, close/reopen the book and measure first open, rapid Next, Next after reading briefly, and Previous separately on the existing Play app. The first uncached page still needs real rendering; under heavy CPU contention or when turning faster than preparation can finish, waiting can remain. Do not report a numeric live improvement until that updated deployment is measured.
