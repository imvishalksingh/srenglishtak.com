-- Apply to the existing Supabase project before starting this backend.
-- All app table access goes through Spring. Supabase Auth and public Storage URLs still work.
-- Review any OTHER applications using these tables before applying these permission changes.
BEGIN;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS question_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE challenge_questions ADD COLUMN IF NOT EXISTS question_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE quizzes ADD COLUMN IF NOT EXISTS is_visible BOOLEAN DEFAULT TRUE;
CREATE TABLE IF NOT EXISTS challenge_attempt_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  challenge_id UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  UNIQUE(user_id,challenge_id)
);
CREATE INDEX IF NOT EXISTS idx_sessions_user_started ON reading_sessions(user_id,started_at DESC);
CREATE INDEX IF NOT EXISTS idx_progress_user_last_read ON user_progress(user_id,last_read_at DESC);
CREATE INDEX IF NOT EXISTS idx_attempts_user_quiz ON quiz_attempts(user_id,quiz_id);
CREATE INDEX IF NOT EXISTS idx_challenge_leaderboard ON challenge_results(challenge_id,score DESC,time_taken_ms);
CREATE INDEX IF NOT EXISTS idx_questions_quiz_order ON questions(quiz_id,question_order);
CREATE INDEX IF NOT EXISTS idx_challenge_questions_order ON challenge_questions(challenge_id,question_order);
DO $$
DECLARE item TEXT;
BEGIN
  FOREACH item IN ARRAY ARRAY['profiles','books','quizzes','questions','user_progress','reading_sessions',
    'bookmarks','notes','user_achievements','quiz_results','quiz_attempts','vocabulary','daily_tips',
    'challenges','challenge_questions','challenge_results','grammar_units','grammar_lessons',
    'grammar_progress','cbse_categories','cbse_materials','module_interests','challenge_attempt_sessions']
  LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',item);
    EXECUTE format('REVOKE ALL ON TABLE public.%I FROM PUBLIC, anon, authenticated',item);
    -- Optional dedicated server role. Create it with a password separately; never use it in clients.
    IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='readiq_backend') THEN
      EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.%I TO readiq_backend',item);
      IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename=item AND policyname='readiq_server_access') THEN
        EXECUTE format('CREATE POLICY readiq_server_access ON public.%I TO readiq_backend USING (true) WITH CHECK (true)',item);
      END IF;
    END IF;
  END LOOP;
END $$;
COMMIT;
