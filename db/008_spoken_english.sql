-- Additive migration. Run before deploying the Spoken English backend.
BEGIN;
CREATE TABLE IF NOT EXISTS public.spoken_english_lessons (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title TEXT NOT NULL CHECK (char_length(btrim(title)) BETWEEN 1 AND 160),
  category TEXT NOT NULL CHECK (category IN ('conversation','pronunciation','speaking')),
  summary TEXT NOT NULL DEFAULT '' CHECK (char_length(summary) <= 280),
  content TEXT NOT NULL CHECK (char_length(btrim(content)) BETWEEN 1 AND 40000),
  lesson_order INTEGER NOT NULL DEFAULT 0 CHECK (lesson_order BETWEEN 0 AND 100000),
  is_published BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS spoken_english_catalog_order
  ON public.spoken_english_lessons(category,lesson_order,created_at,id) WHERE is_published;
ALTER TABLE public.spoken_english_lessons ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.spoken_english_lessons FROM PUBLIC, anon, authenticated;
DO $$ BEGIN
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='service_role') THEN
    GRANT ALL ON public.spoken_english_lessons TO service_role;
  END IF;
END $$;
COMMIT;
