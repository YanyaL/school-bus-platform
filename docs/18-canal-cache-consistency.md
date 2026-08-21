# Canal CDC cache consistency

## Status

The implementation is intentionally introduced as a shadow consistency path.
A real local verification passed on 2026-08-21, but the existing
application-side trip cache eviction remains enabled until repeated failure
drills and CDC-lag observation also pass. MySQL remains the source of truth.

### Real verification evidence (2026-08-21)

| Check | Result |
|---|---|
| MySQL Binlog | enabled, `ROW`, `FULL`, `server_id=1` |
| Canal | `canal/canal-server:v1.1.8`, destination `schoolbus` |
| `transport_trip` change | stale Redis List key changed from present to absent |
| `event_consumed` INSERT | Redis value `DONE`, remaining TTL `2,591,986` seconds |
| Published CDC events | actuator counter delta/count observed as `3` |
| CDC module tests | 6 passed, 0 failed |
| Payment tests | 40 passed, 0 failed |
| Core tests | 533 passed, 0 failed, 8 skipped |

The verification row and Redis marker were removed afterwards. The Canal and
CDC processes were stopped; no test data or listening process was left behind.

## Why this is not one universal cache strategy

Two database tables have different semantics:

| MySQL change | Redis projection | Reason |
|---|---|---|
| `transport_trip` INSERT/UPDATE/DELETE | Delete `school-bus:transport:bookable-trips` | A later read rebuilds the list through Cache Aside; deleting avoids writing an incomplete list from one row change. |
| `event_consumed` INSERT | Set `school-bus:mq:consumed:{consumer}:{eventId}=DONE` with TTL | The row is an immutable positive fact, so a positive Redis projection can avoid repeated MySQL lookups. |

The `event_consumed` primary key remains the final idempotency guarantee. A
Redis outage falls back to MySQL, and CDC delay only causes extra database
reads. It must not cause the same business effect to be committed twice.

## Data flow

```text
MySQL transaction commits
  -> ROW/FULL Binlog
  -> Canal Server (schoolbus destination)
  -> school-bus-cdc-cache-sync Canal Client
  -> RabbitMQ publisher confirm
  -> ACK Canal batch
  -> Redis projection consumer
```

If RabbitMQ does not confirm every event in a Canal batch, the client rolls the
batch back. A partially published batch can therefore be delivered again. Both
Redis operations (`DEL` and deterministic `SET DONE`) are idempotent by design.

## Local startup

Prerequisites: the existing MySQL, RabbitMQ and Redis containers are running.

```powershell
.\scripts\cdc\prepare-canal-mysql.ps1
docker compose -f .\cloud\docker-compose-cdc.yml up -d

$env:CANAL_CLIENT_ENABLED='true'
mvn -f .\cloud\cdc-cache-sync-service\pom.xml spring-boot:run
```

The client is disabled by default, so adding the module cannot accidentally
subscribe to a developer or production database.

## Cutover rule

Do not remove the existing `BookableTripCacheInvalidationListener` during the
first release. Observe CDC lag, RabbitMQ confirms and Redis projection metrics.
Only after repeated real verification should CDC become the sole invalidation
path. The duplicate `DEL` during shadow operation is harmless.

One successful run proves the implementation path, not production reliability.
Before cutover, repeat broker outage, Redis outage, Canal restart and consumer
replay drills, and define an alert for sustained CDC lag.

## Interview summary

This design uses Binlog CDC to decouple database writes from cache maintenance.
For mutable list data it uses Cache Aside and invalidation, not asynchronous
write-through. For immutable idempotency facts it builds a positive Redis
projection while preserving a MySQL unique key as the correctness boundary.
Publisher Confirm is completed before the Canal batch is acknowledged, so a
broker failure causes replay instead of silent loss. Consumers are idempotent
because CDC provides at-least-once rather than exactly-once delivery.
