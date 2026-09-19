# JDK 17 FCC Verification Rules

## Scope

- This project is the predecessor FCC protocol/flow verification service. The current product backend is `demo-2026/chandler26-jdk21-fcc`.
- Keep this project for intentional protocol regression, migration comparison, or isolated demonstrations. New FCC product behavior belongs in JDK 21 and must not be implemented twice.
- The service controls FreeSWITCH only through NATS and the Go Sidecar; never add direct ESL access.

## Change Rules

- Preserve compatibility with the Sidecar `FNode.*` contract and node-scoped NATS subjects.
- Hardcoded flow definitions, in-memory sessions, and verification controllers are prototype boundaries, not patterns for the current backend.
- Do not introduce new persistence, authentication, admin, or frontend contracts here to mimic JDK 21.
- If a protocol fix must be backported, document why both backends require it, add Java/Go fixtures, and define rollout order.
- Keep call ID, control ID, Channel UUID, node ID, command ID, and event ID distinct.
- All classes and methods require clear Javadoc; DTO fields require Chinese `@Schema` descriptions when OpenAPI DTOs are introduced; enums require Chinese descriptions and getters.

## Configuration And Security

- Override the default service port when the Sidecar also runs on `8088`.
- The Java target node ID must exactly match Sidecar `NODE_ID`.
- NATS, Sidecar, FreeSWITCH, SIP, and database credentials must not be committed, logged, or returned.
- Verification endpoints are not a production authorization boundary and must remain isolated.

## Verification

- Run `mvn -q -DskipTests compile` and `mvn -q test` with JDK 17.
- Separate unit results from NATS/Sidecar/FreeSWITCH integration results; do not report missing external dependencies as passed.
- Run `git diff --check` and keep generated output, recordings, and local runtime artifacts out of delivery.

## Focused Review

- For any Sidecar/FNode/event change, use `../../demo-2026/agents/fcc-realtime-reliability.md` when the sibling product workspace is available.
- For documentation and cross-repository scope, use `../../demo-2026/agents/repository-integrity.md`.
