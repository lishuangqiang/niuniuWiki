UPDATE apps
SET settings = COALESCE(settings, '{}'::jsonb)
    || '{"home_page_setting":"custom"}'::jsonb,
    updated_at = now()
WHERE type = 1
  AND NULLIF(BTRIM(settings ->> 'home_page_setting'), '') IS NULL;
