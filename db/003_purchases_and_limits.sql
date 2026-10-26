-- Apply after 001/002. No client is granted payment, ownership, or counter-table access.
BEGIN;
ALTER TABLE books ADD COLUMN IF NOT EXISTS price_paise INTEGER NOT NULL DEFAULT 0 CHECK(price_paise BETWEEN 0 AND 1000000);
ALTER TABLE books ADD COLUMN IF NOT EXISTS currency TEXT NOT NULL DEFAULT 'INR' CHECK(currency='INR');
ALTER TABLE books ADD COLUMN IF NOT EXISTS android_product_id TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS uq_book_android_product ON books(android_product_id) WHERE android_product_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS purchase_orders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES profiles(id),
  book_id UUID NOT NULL REFERENCES books(id),
  request_key UUID NOT NULL,
  environment TEXT NOT NULL CHECK(environment IN ('test','live')),
  provider_order_id TEXT UNIQUE,
  amount INTEGER NOT NULL CHECK(amount>0),
  currency TEXT NOT NULL CHECK(currency='INR'),
  status TEXT NOT NULL DEFAULT 'created' CHECK(status IN ('created','paid','revoked')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(user_id,request_key)
);
CREATE TABLE IF NOT EXISTS book_purchases (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES profiles(id),
  book_id UUID NOT NULL REFERENCES books(id),
  provider TEXT NOT NULL CHECK(provider IN ('google_play','razorpay')),
  environment TEXT NOT NULL CHECK(environment IN ('test','live')),
  reference_hash TEXT NOT NULL,
  provider_payment_id TEXT,
  product_id TEXT,
  encrypted_token TEXT,
  amount INTEGER,
  currency TEXT,
  status TEXT NOT NULL CHECK(status IN ('active','revoked')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  checked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(provider,reference_hash)
);
CREATE INDEX IF NOT EXISTS idx_purchases_owner_book ON book_purchases(user_id,book_id,environment,status);
CREATE INDEX IF NOT EXISTS idx_purchases_recheck ON book_purchases(checked_at) WHERE status='active';
CREATE TABLE IF NOT EXISTS purchase_revocations (
  provider TEXT NOT NULL,
  reference_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(provider,reference_hash)
);
CREATE TABLE IF NOT EXISTS api_rate_limits (
  bucket_key TEXT PRIMARY KEY,
  window_start BIGINT NOT NULL,
  used INTEGER NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_limit_expiry ON api_rate_limits(expires_at);
CREATE TABLE IF NOT EXISTS revoked_access_tokens (
  token_hash TEXT PRIMARY KEY,
  expires_at TIMESTAMPTZ NOT NULL
);
DO $$
DECLARE item TEXT;
BEGIN
  FOREACH item IN ARRAY ARRAY['purchase_orders','book_purchases','purchase_revocations','api_rate_limits','revoked_access_tokens'] LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',item);
    EXECUTE format('REVOKE ALL ON TABLE public.%I FROM PUBLIC,anon,authenticated',item);
    IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='readiq_backend') THEN
      EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON TABLE public.%I TO readiq_backend',item);
      IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename=item AND policyname='readiq_server_access') THEN
        EXECUTE format('CREATE POLICY readiq_server_access ON public.%I TO readiq_backend USING(true) WITH CHECK(true)',item);
      END IF;
    END IF;
  END LOOP;
  IF to_regclass('storage.buckets') IS NOT NULL THEN
    INSERT INTO storage.buckets(id,name,public) VALUES('book-files','book-files',false) ON CONFLICT(id) DO UPDATE SET public=false;
    -- Restrictive policies also block access when an older broad permissive policy exists.
    IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='storage' AND tablename='objects' AND policyname='readiq_private_book_files') THEN
      CREATE POLICY readiq_private_book_files ON storage.objects AS RESTRICTIVE FOR ALL TO anon,authenticated
        USING(bucket_id<>'book-files') WITH CHECK(bucket_id<>'book-files');
    END IF;
  END IF;
END $$;
COMMIT;
