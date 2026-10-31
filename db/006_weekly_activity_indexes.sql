-- Optional performance indexes for the per-user, current-week activity query.
-- No learning data, users, books or purchases are modified. Run as the database owner.
CREATE INDEX IF NOT EXISTS idx_reading_sessions_user_started ON public.reading_sessions(user_id, started_at);
CREATE INDEX IF NOT EXISTS idx_grammar_progress_user_completed ON public.grammar_progress(user_id, completed_at) WHERE is_completed=true;
CREATE INDEX IF NOT EXISTS idx_quiz_attempts_user_completed ON public.quiz_attempts(user_id, completed_at);
CREATE INDEX IF NOT EXISTS idx_challenge_results_user_completed ON public.challenge_results(user_id, completed_at);
