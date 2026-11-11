-- Run once on the production project before deploying the phone-onboarding backend.
-- Re-running is safe: existing completed profiles are grandfathered only when
-- the completion column is first introduced, never on later migration runs.
-- This preserves earlier class/goal setup only. Accounts without a mobile
-- number still receive onboarding_completed=false until they save one.
BEGIN;
DO $$
DECLARE introducing_completion BOOLEAN;
BEGIN
  SELECT NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='profiles' AND column_name='onboarding_completed_at'
  ) INTO introducing_completion;
  ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS phone_number TEXT;
  ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMPTZ;
  IF introducing_completion THEN
    UPDATE public.profiles SET onboarding_completed_at=now()
    WHERE onboarding_completed_at IS NULL AND nullif(btrim(class),'') IS NOT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint
      WHERE conrelid='public.profiles'::regclass AND conname='profiles_phone_number_e164_check') THEN
    ALTER TABLE public.profiles ADD CONSTRAINT profiles_phone_number_e164_check
      CHECK (phone_number IS NULL OR phone_number ~ '^\+[1-9][0-9]{7,14}$');
  END IF;
END $$;
COMMIT;
