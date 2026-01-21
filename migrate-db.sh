#!/bin/bash

# Database migration script to convert integer columns to bigint
# This script should be run before starting the application

set -e

echo "Starting database migration..."

# Database connection details (adjust if needed)
DB_HOST=${POSTGRES_HOST:-localhost}
DB_PORT=${POSTGRES_PORT:-5432}
DB_NAME=${POSTGRES_DB:-jiaa_auth}
DB_USER=${POSTGRES_USER:-jiaa}
DB_PASSWORD=${POSTGRES_PASSWORD:-jiaa1234}

# Export password for psql
export PGPASSWORD=$DB_PASSWORD

echo "Connecting to database: $DB_NAME@$DB_HOST:$DB_PORT"

# Run migration SQL
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" <<EOF
-- Migration script to convert integer/serial columns to bigint

-- Migrate users table
DO \$\$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns 
               WHERE table_name = 'users' AND column_name = 'id' 
               AND data_type != 'bigint') THEN
        ALTER TABLE users ALTER COLUMN id TYPE bigint USING id::bigint;
        RAISE NOTICE 'Migrated users.id to bigint';
    ELSE
        RAISE NOTICE 'users.id is already bigint or table does not exist';
    END IF;
END \$\$;

-- Migrate google_tokens table
DO \$\$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns 
               WHERE table_name = 'google_tokens' AND column_name = 'id' 
               AND data_type != 'bigint') THEN
        ALTER TABLE google_tokens ALTER COLUMN id TYPE bigint USING id::bigint;
        RAISE NOTICE 'Migrated google_tokens.id to bigint';
    ELSE
        RAISE NOTICE 'google_tokens.id is already bigint or table does not exist';
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.columns 
               WHERE table_name = 'google_tokens' AND column_name = 'user_id' 
               AND data_type != 'bigint') THEN
        ALTER TABLE google_tokens ALTER COLUMN user_id TYPE bigint USING user_id::bigint;
        RAISE NOTICE 'Migrated google_tokens.user_id to bigint';
    ELSE
        RAISE NOTICE 'google_tokens.user_id is already bigint or column does not exist';
    END IF;
END \$\$;

-- Migrate refresh_tokens table
DO \$\$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns 
               WHERE table_name = 'refresh_tokens' AND column_name = 'id' 
               AND data_type != 'bigint') THEN
        ALTER TABLE refresh_tokens ALTER COLUMN id TYPE bigint USING id::bigint;
        RAISE NOTICE 'Migrated refresh_tokens.id to bigint';
    ELSE
        RAISE NOTICE 'refresh_tokens.id is already bigint or table does not exist';
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.columns 
               WHERE table_name = 'refresh_tokens' AND column_name = 'user_id' 
               AND data_type != 'bigint') THEN
        ALTER TABLE refresh_tokens ALTER COLUMN user_id TYPE bigint USING user_id::bigint;
        RAISE NOTICE 'Migrated refresh_tokens.user_id to bigint';
    ELSE
        RAISE NOTICE 'refresh_tokens.user_id is already bigint or column does not exist';
    END IF;
END \$\$;

EOF

echo "Migration completed successfully!"
