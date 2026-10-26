-- Apply after 003. Original book objects are server-only for FREE and PAID books.
BEGIN;
CREATE TABLE IF NOT EXISTS reader_challenges (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  book_id UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
  public_key TEXT NOT NULL,
  request_hash TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_reader_challenge_expiry ON reader_challenges(expires_at);
CREATE TABLE IF NOT EXISTS reader_sessions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL UNIQUE REFERENCES profiles(id) ON DELETE CASCADE,
  book_id UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
  public_key TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_reader_session_expiry ON reader_sessions(expires_at);
CREATE TABLE IF NOT EXISTS reader_request_nonces (
  session_id UUID NOT NULL REFERENCES reader_sessions(id) ON DELETE CASCADE,
  nonce UUID NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY(session_id,nonce)
);
CREATE INDEX IF NOT EXISTS idx_reader_nonce_expiry ON reader_request_nonces(expires_at);
DO $$
DECLARE item TEXT;
BEGIN
  FOREACH item IN ARRAY ARRAY['reader_challenges','reader_sessions','reader_request_nonces'] LOOP
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
    UPDATE storage.buckets SET public=false WHERE id='book-files';
    IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='storage' AND tablename='objects' AND policyname='readiq_private_book_files') THEN
      CREATE POLICY readiq_private_book_files ON storage.objects AS RESTRICTIVE FOR ALL TO anon,authenticated
        USING(bucket_id<>'book-files') WITH CHECK(bucket_id<>'book-files');
    END IF;
  END IF;
END $$;
COMMIT;
