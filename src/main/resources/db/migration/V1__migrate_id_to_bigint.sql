-- Migration script to convert integer/serial columns to bigint
-- This script handles the conversion of id and user_id columns from integer to bigint

-- Migrate users table
ALTER TABLE IF EXISTS users 
    ALTER COLUMN id TYPE bigint USING id::bigint;

-- Migrate google_tokens table
ALTER TABLE IF EXISTS google_tokens 
    ALTER COLUMN id TYPE bigint USING id::bigint;

ALTER TABLE IF EXISTS google_tokens 
    ALTER COLUMN user_id TYPE bigint USING user_id::bigint;

-- Migrate refresh_tokens table
ALTER TABLE IF EXISTS refresh_tokens 
    ALTER COLUMN id TYPE bigint USING id::bigint;

ALTER TABLE IF EXISTS refresh_tokens 
    ALTER COLUMN user_id TYPE bigint USING user_id::bigint;
