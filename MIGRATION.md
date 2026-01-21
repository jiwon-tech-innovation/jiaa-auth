# 데이터베이스 마이그레이션 가이드

## 문제 설명

Hibernate가 자동으로 스키마를 업데이트하려고 할 때, PostgreSQL에서 `integer` 타입을 `bigint`로 자동 변환할 수 없어 에러가 발생합니다.

에러 메시지:
```
ERROR: column "id" cannot be cast automatically to type bigint
Hint: You might need to specify "USING id::bigint".
```

## 해결 방법

### 방법 1: 마이그레이션 스크립트 실행 (권장)

#### Docker 환경에서 실행

```bash
# PostgreSQL 컨테이너에 접속
docker exec -it <postgres-container-name> psql -U jiaa -d jiaa_auth

# 또는 docker-compose를 사용하는 경우
docker-compose exec postgres psql -U jiaa -d jiaa_auth
```

그 다음 다음 SQL을 실행:

```sql
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
```

#### 로컬 환경에서 실행

```bash
# 마이그레이션 스크립트 실행
./migrate-db.sh

# 또는 직접 psql 실행
psql -h localhost -p 5432 -U jiaa -d jiaa_auth -f src/main/resources/db/migration/V1__migrate_id_to_bigint.sql
```

### 방법 2: Docker Compose를 통한 자동 실행

`docker-compose.yml`에 초기화 스크립트를 추가하여 컨테이너 시작 시 자동으로 마이그레이션을 실행할 수 있습니다.

### 방법 3: Hibernate 설정 변경 (임시 해결)

`application.yml`에서 `ddl-auto`를 `validate`로 변경하여 Hibernate가 스키마를 변경하지 않도록 할 수 있습니다:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # update 대신 validate 사용
```

**주의**: 이 방법은 스키마 변경을 막을 뿐, 실제 마이그레이션은 수행하지 않습니다. 수동으로 마이그레이션을 실행해야 합니다.

## 확인

마이그레이션이 성공적으로 완료되었는지 확인:

```sql
SELECT table_name, column_name, data_type 
FROM information_schema.columns 
WHERE table_name IN ('users', 'google_tokens', 'refresh_tokens')
  AND column_name IN ('id', 'user_id')
ORDER BY table_name, column_name;
```

모든 `id`와 `user_id` 컬럼이 `bigint` 타입이어야 합니다.
