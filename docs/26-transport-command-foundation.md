# Transport Command Service — Strangler Phase 1

## Scope

This phase extracts the vehicle and route administration write paths from
`school-bus-core` into `school-bus-transport-command`:

- `GET/POST /api/v1/admin/vehicles`
- `PATCH /api/v1/admin/vehicles/{vehicleId}/status`
- `GET/POST /api/v1/admin/routes`
- `PATCH /api/v1/admin/routes/{routeId}/status`

Trip publication and cancellation remain in Core. They are intentionally not
moved in this phase because publication initializes Booking inventory and trip
cancellation starts a RabbitMQ-based settlement workflow. Moving those paths
without first defining explicit cross-service contracts would merely hide a
distributed monolith behind another process.

## Runtime ownership

Gateway routes vehicle and route administration paths to:

```text
lb://school-bus-transport-command
```

The Cloud profile sets:

```yaml
school-bus:
  transport:
    admin:
      embedded:
        enabled: false
```

Core therefore removes its vehicle/route controllers and management services.
Local modular-monolith mode remains enabled by default. Actuator `info` exposes
`transportAdminOwner=disabled` in Core and
`transportAdminOwner=transport-command` in the command service, so deployment
ownership is observable rather than inferred from logs.

## Security and routing policy

- The command service validates RS256 JWT signature, issuer and audience using
  only the IAM public key.
- Controller methods continue to require `ROLE_ADMIN`.
- Gateway removes untrusted identity headers before forwarding.
- Write routes do not use the Query retry filter. Retrying a non-idempotent
  create or status mutation at the Gateway could duplicate side effects.

## Transaction and persistence boundary

Vehicle creation still writes the vehicle and its seat layout in one local
MySQL transaction. Route and vehicle status changes retain optimistic locking.
During this Strangler phase the new service shares the existing MySQL schema;
this is a transitional deployment boundary, not database autonomy.

An architecture guard verifies that phase 1 does not access Booking, Payment or
Trip tables. This keeps the extracted service limited to vehicle and route
persistence even before physical database separation.

## Verification

```powershell
mvn -f .\cloud\transport-command-service\pom.xml test
mvn -f .\cloud\gateway-service\pom.xml test
mvn test
.\scripts\cloud\verify-transport-command-foundation.tests.ps1
.\scripts\cloud\verify-transport-command-foundation.ps1
.\scripts\security\check-no-private-keys.ps1
git diff --check
```

The unit, controller, route, ownership and architecture tests are automated.
The real acceptance script additionally checks Nacos registration, Gateway
cutover, Core ownership shutdown, anonymous/student/admin authorization,
vehicle plus seat-layout transactionality, route persistence and cleanup.

On 2026-08-30 the acceptance harness was executed and produced
`target/transport-command-foundation-20260830-180813/report.json`. The report is
honestly marked `BLOCKED` at `Assert-Docker` because Docker Desktop was not
running; no temporary business rows were created and cleanup was successful.
Real Nacos/Gateway/MySQL acceptance therefore remains pending and must not be
represented as completed until a later report is `PASSED`.

## Next phase

Before extracting trip commands, define explicit contracts for:

1. initializing Booking seat inventory after a trip is published;
2. notifying Booking when a trip is cancelled;
3. handling event idempotency and compensation;
4. removing direct cross-service table access;
5. only then assigning independent database ownership.
