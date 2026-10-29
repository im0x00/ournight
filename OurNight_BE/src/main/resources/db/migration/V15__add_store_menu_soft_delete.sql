ALTER TABLE store_menus
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_store_menus_store_deleted
    ON store_menus (store_id, is_deleted);
