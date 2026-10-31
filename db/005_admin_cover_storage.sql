-- Apply after 004, before using cover uploads from the admin dashboard.
-- Public image reads use Supabase's public object endpoint. Direct client metadata access,
-- inserts, replacements and deletions must still be server-only, including with old broad policies.
BEGIN;
DO $$
BEGIN
  IF to_regclass('storage.objects') IS NOT NULL THEN
    IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='storage' AND tablename='objects'
                  AND policyname='readiq_server_managed_covers') THEN
      CREATE POLICY readiq_server_managed_covers ON storage.objects AS RESTRICTIVE FOR ALL
        TO anon,authenticated USING(bucket_id<>'book-covers') WITH CHECK(bucket_id<>'book-covers');
    END IF;
  END IF;
END $$;
COMMIT;
