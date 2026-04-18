# Scan Service Idempotency Implementation

## Overview
Implemented request-level idempotency for scan-service to prevent duplicate image uploads and database operations when requests are retried.

When identical requests are submitted multiple times (e.g., due to network timeouts, user double-clicks), the cached response is returned instead of re-processing the scan.

## Architecture

```
Client Request (with X-Idempotency-Key or X-Correlation-Id)
        ↓
[API Gateway] IdempotencyKeyFilter (deterministic key generation)
        ↓
[Scan Service] IdempotencyKeyFilter @ ScanController
        ↓
[AOP Middleware] Check if request hash exists
        ↓
    ┌──────────────────────────────┐
    │ Cached? (within 24h TTL)     │
    └──────────────────────────────┘
        │ YES                │ NO
        ↓                    ↓
  ┌──────────────┐    ┌────────────┐
  │ Return Cache │    │ Process    │
  │ (instant)    │    │ Upload     │
  └──────────────┘    │ + Save DB  │
        ↓             └────────────┘
     Headers:              ↓
   X-From-Cache: true  [Cache Response]
   422 if conflict         ↓
                    Return Response
```

## Components

### 1. **Entity** (`entity/IdempotencyRecord.java`)
```java
IdempotencyRecord
├─ id: UUID (PK)
├─ idempotency_key: String (unique, indexed)
├─ user_id: UUID (indexed)
├─ method: String (POST, PUT, DELETE, PATCH)
├─ path: String
├─ request_hash: String (SHA-256)
├─ response_body: String (JSON)
├─ response_status: int
├─ created_at: Instant
└─ expires_at: Instant (24h) [indexed for cleanup]
```

### 2. **Repository** (`repository/IdempotencyRecordRepository.java`)
- `findByIdempotencyKeyAndUserId()` → query cache
- `deleteExpiredRecords()` → TTL cleanup

### 3. **AOP Annotation** (`aop/Idempotent.java`)
```java
@Idempotent(ttlSeconds = 3600)  // Configurable TTL
@PostMapping
public ResponseEntity<...> save(...) { }
```

### 4. **AOP Aspect** (`aop/IdempotencyAspect.java`)
Intercepts @Idempotent methods:
1. Extract X-Idempotency-Key + X-User-Id
2. Compute SHA-256 hash of request
3. Query DB for existing record
4. Return cached response or 422 (conflict)
5. Execute method + cache response

### 5. **Service** (`service/HttpIdempotencyService.java`)
Business logic:
- `getExistingRecord()` → find cache
- `validateRequestConsistency()` → detect conflicts
- `recordProcessedRequest()` → persist response
- `cleanupExpiredRecords()` → TTL cleanup
- `computeHash()` → SHA-256

### 6. **Scheduler** (`config/IdempotencyCleanupTask.java`)
Runs every 6 hours to cleanup expired records (TTL=24h)

### 7. **Database Migration** (`db/migration/V2__create_idempotency_records_table.sql`)
Creates `idempotency_records` table with 4 indexes

## Protected Endpoints

| Endpoint | Method | TTL | Purpose |
|----------|--------|-----|---------|
| `/api/v1/scans` | **POST** | 1h | Image upload + ML inference |
| `/api/v1/scans/{id}/conv` | **PATCH** | 24h | Update conversation |
| `/api/v1/scans/{id}` | **DELETE** | 1h | Delete scan record |

## How It Works

### Request Flow - POST /api/v1/scans (Upload Image)

**First Request:**
```bash
POST /api/v1/scans
Header: X-Idempotency-Key: scan-2024-04-15-abc123
Header: X-User-Id: user-456

Form:
  image: [binary 5MB image file]
  disease: "Leaf Spot"
  confidence: 0.92
  confidentEnough: true

Response:
  Status: 201
  Body: { id: "scan-uuid", imageUrl: "gs://...", ... }
  Header: X-Idempotency-Key: scan-2024-04-15-abc123
```

**Idempotency Hit (Retry after timeout):**
```bash
# Same request with SAME key
POST /api/v1/scans
Header: X-Idempotency-Key: scan-2024-04-15-abc123 # ← Same key!
Header: X-User-Id: user-456

# Middleware detects: (user-456, request_hash) exists + not expired
# Returns cached response instantly

Response:
  Status: 201 (cached!)
  Body: { id: "scan-uuid", imageUrl: "gs://...", ... }
  Header: X-From-Cache: true  ← Cached indicator!
```

**Conflict Detection (Same key, different body):**
```bash
# Same key, DIFFERENT image/params
POST /api/v1/scans
Header: X-Idempotency-Key: scan-2024-04-15-abc123 # ← Same key
Header: X-User-Id: user-456

Form:
  image: [DIFFERENT 3MB image]  ← Different file!
  disease: "Bacterial Wilt"     ← Different disease!
  confidence: 0.87
  confidentEnough: false

# Middleware detects: key exists, but request_hash mismatch
# Returns 422 Unprocessable Entity (reject conflicting request)

Response:
  Status: 422 Unprocessable Entity
  Body: "Payload conflict for idempotency" (cached original response)
```

## Key Features

### Deterministic Key Generation
- Uses X-Idempotency-Key from header (preferred)
- Falls back to X-Correlation-Id (from API Gateway)
- Last resort: generates UUID (less reliable for retries)

### Request Body Hashing
- SHA-256 hash of request JSON
- Detects conflicts: same key, different body
- Returns 422 to reject conflicting requests

### TTL Configuration
- POST (upload): 1 hour (conservative, high resource cost)
- PATCH (update): 24 hours
- DELETE: 1 hour
- All configurable via `@Idempotent(ttlSeconds=...)`

### Automatic Cleanup
- Scheduled task runs every 6 hours
- Deletes expired records
- Keeps `idempotency_records` table bounded

### Database Indexes
- `(user_id, method, path)` → fast lookup by request type
- `(request_hash)` → conflict detection
- `(expires_at)` → efficient cleanup queries

## Benefits

| Scenario | Without Idempotency | With Idempotency |
|----------|-------------------|-----------------|
| Network timeout + retry | 2 uploads to Firebase | 1 upload  |
| User clicks "Scan" twice | Duplicate records | 1 record  |
| Browser refresh | Restart from scratch | Cached result  |
| **ResourceSavings** | Upload cost 2x | Save 50%  |

## Testing

### Manual Test with curl

```bash
# 1st request (processes, caches)
curl -X POST http://localhost:8080/api/v1/scans \
  -H "X-User-Id: user-123" \
  -H "X-Idempotency-Key: test-scan-001" \
  -F "image=@durian_leaf.jpg" \
  -F "disease=Leaf Spot" \
  -F "confidence=0.92" \
  -F "confidentEnough=true"

Response Headers:
  X-Idempotency-Key: test-scan-001
  (no X-From-Cache header)

# 2nd request (identical, cached)
curl -X POST http://localhost:8080/api/v1/scans \
  -H "X-User-Id: user-123" \
  -H "X-Idempotency-Key: test-scan-001" \
  -F "image=@durian_leaf.jpg" \
  -F "disease=Leaf Spot" \
  -F "confidence=0.92" \
  -F "confidentEnough=true"

Response Headers:
  X-From-Cache: true  ← Cached!
  (instant return)

# 3rd request (same key, different body → conflict)
curl -X POST http://localhost:8080/api/v1/scans \
  -H "X-User-Id: user-123" \
  -H "X-Idempotency-Key: test-scan-001" \
  -F "image=@different_leaf.jpg"  ← Different image
  -F "disease=Bacterial Wilt"      ← Different disease
  -F "confidence=0.87"
  -F "confidentEnough=false"

Response:
  Status: 422 Unprocessable Entity
  Body: (original cached response)
```

### Database Verification

```sql
-- Check cached responses
SELECT 
    idempotency_key,
    user_id,
    method,
    path,
    response_status,
    created_at,
    expires_at,
    (expires_at > NOW()) AS is_valid
FROM idempotency_records
WHERE user_id = 'user-123'::uuid
ORDER BY created_at DESC;

-- Check expired records (cleanup candidates)
SELECT COUNT(*) FROM idempotency_records
WHERE expires_at <= NOW();
```

## Configuration

### Via Annotation (Per-Endpoint)
```java
// Short TTL for expensive operations
@PostMapping
@Idempotent(ttlSeconds = 1800)  // 30 minutes
public ResponseEntity<...> save(...) { }

// Long TTL for safe operations
@PatchMapping
@Idempotent(ttlSeconds = 604800)  // 7 days
public ResponseEntity<...> update(...) { }
```

### Via Scheduler (Global cleanup)
```java
// IdempotencyCleanupTask.java
@Scheduled(fixedRate = 21600000)  // Every 6 hours
public void cleanupExpiredRecords() { }
```

## Consistency with Other Services

Scan Service idempotency now matches:
-  Auth Service (same pattern)
-  Community Service (same pattern)
-  User Service (same pattern)

All services use:
- AOP @Idempotent annotation
- PostgreSQL persistence
- SHA-256 conflict detection
- 6-hour cleanup scheduler
- X-Idempotency-Key header

## Files Added/Modified

### New Files
- `entity/IdempotencyRecord.java`
- `repository/IdempotencyRecordRepository.java`
- `aop/Idempotent.java`
- `aop/IdempotencyAspect.java`
- `service/HttpIdempotencyService.java`
- `config/IdempotencyCleanupTask.java`
- `resources/db/migration/V2__create_idempotency_records_table.sql`

### Modified Files
- `pom.xml` (added spring-boot-starter-aop)
- `controller/ScanController.java` (added @Idempotent to 3 endpoints)

## Troubleshooting

### Cache Not Working
1. Check X-Idempotency-Key header is present
2. Check X-User-Id header is present
3. Verify database migration V2 was executed
4. Check logs: `Idempotency hit: key=...`

### Excessive Cleanup
1. Increase `@Scheduled(fixedRate=...)` interval
2. Increase TTL in `@Idempotent(ttlSeconds=...)`
3. Monitor `idempotency_records` table size

### Conflict Rejection (422)
- Intentional! Same key + different body = reject to prevent silent data loss
- Use different idempotency key for truly different request

## Next Steps

1. Deploy database migration V2
2. Deploy code changes (requires Maven build)
3. Monitor logs during traffic ramp-up
4. Check database size: `SELECT count(*) FROM idempotency_records;`
5. Verify API Gateway forwards X-Idempotency-Key correctly
