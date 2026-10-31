# Protected reader performance update

Deploy the existing Render backend from this actual repository, then publish Flutter 1.0.8 (14) through Play internal testing. No database migration or new environment variable is required for this reader update. Existing payment, private storage, Play Integrity and signing settings stay in place.

## Server behavior

The renderer retains one parsed PDF in server RAM only (original input at most 50 MiB). The cached document expires after 2 idle minutes or 5 minutes total and is also cleared on replacement, invalid storage privacy and shutdown. Background cleanup runs every 30 seconds. The raw buffer is zeroed when released. Rendered base pages share a 16 MiB LRU pixel budget. PDFBox input/stream caches and JPEG output stay in memory; the optimization introduces no PDF/page disk cache or public endpoint.

Every protected page still passes the original purchase check, session binding, device signature, timestamp, nonce/replay check and rate limit before consulting the cache. Bucket privacy is checked even on cache hits. Base rasters stay inside the server; a fresh JPEG is composed with the authenticated reader's watermark for every response, retaining HTTP no-store headers. Watermarks now use two faint diagonal lines at 8 percent opacity plus a compact ownership/session footer outside document content.

## Mobile behavior

Only the current watermarked page is retained in RAM, with no multi-page prefetch or disk cache. Progress restoration starts alongside verification. Successful background renewal keeps the image and zoom; an independent hard-expiry timer wipes and blocks the image if verification cannot finish in time. Permission denial wipes immediately. Recovery retries are bounded with backoff. The hard deadline uses monotonic verification-request age as well as the server expiry, so clock changes and slow responses cannot extend the verified lifetime. Backgrounding, close, stale responses and account changes discard/clear page buffers; reopening still requires verification. The original secure-window/screenshot/recording protection remains in the Android code.

## Verification and limitations

Tests cover download/raster reuse, idle/hard expiration, replacement files, pixel-memory eviction, concurrent render limits, storage privacy failures, reader-specific watermark isolation, warm-cache revocation/replay denial and original stream blocking. Widget tests cover no-blank renewal, hard expiration on outages, permission denial, background/resume, late pages and closure. Visual review uses a synthetic PDF only. The final complete suite passed 108 Flutter tests and 98 backend tests; static analysis reported zero errors/warnings. Both the full backend package and signed Android app bundle were built locally. Live latency and the new Play-installed release still need checking after deployment.

These changes remove repeated download/parse/render work but do not guarantee a specific live latency; the first uncached open still requires Play Integrity, private storage and database access. The single-document cache and single render permit intentionally bound resource use on the small backend instance; a larger multi-reader workload may still need more resources or a separate rendering tier. No DRM system can make visible page pixels impossible to extract from a fully compromised device. The original PDF itself is never provided to the app by this protocol.
