#!/bin/bash

# Docker 환경에서 데이터베이스 마이그레이션을 실행하는 스크립트

set -e

echo "Docker 환경에서 데이터베이스 마이그레이션을 실행합니다..."

# docker-compose를 사용하는 경우
if command -v docker-compose &> /dev/null; then
    echo "docker-compose를 사용하여 마이그레이션을 실행합니다..."
    docker-compose exec -T postgres psql -U jiaa -d jiaa_auth <<EOF
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
    echo "마이그레이션이 완료되었습니다!"
else
    echo "docker-compose를 찾을 수 없습니다. 수동으로 실행해주세요."
    echo "MIGRATION.md 파일을 참고하세요."
    exit 1
fi
