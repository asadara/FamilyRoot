# Phase 4 Production Review

Review date: 2026-07-18

## Privacy and data handling

- The Android app stores family profiles, relationships, and offline mutations in its private Room database.
- Refresh tokens are AES-256-GCM ciphertext backed by a non-exportable Android Keystore key; access tokens remain memory-only.
- Android system cloud backup and device-transfer backup are disabled. Portability is explicit through GEDCOM, PDF/PNG, and schema-versioned JSON backup.
- Space Settings exposes **Clear offline data**. It removes only the current space's device cache and refuses to run while any pending, failed, or conflicted mutation could be lost.
- HTTP logging is `BASIC` in debug and disabled in release. Request/response bodies, authorization headers, passwords, refresh tokens, query values, user IDs, and family values are excluded from observability events.
- Export files leave the app through Android's Storage Access Framework. Users control the destination; exported files are not claimed to be encrypted by FamilyRoot.
- Media portability currently covers metadata/URI references, not binary media payloads. This limitation must remain visible in release notes.

## Security release gate

- Production network traffic is HTTPS-only and Android cleartext traffic is denied outside the debug manifest.
- Release builds enable R8 minification, resource shrinking, baseline-profile packaging, lint, unit tests, and a 25 MiB bundle-size gate.
- The backend removes framework disclosure, adds restrictive response headers, disables Swagger in production, validates DTOs with a whitelist, and uses request IDs without logging sensitive values.
- Production JWT configuration rejects a missing secret. Release signing material and the production API URL are supplied only by CI secrets and are never stored in the repository.
- The tag/manual release workflow verifies signing and publishes an AAB plus SHA-256 checksum as a CI artifact. Store publication remains a deliberate human-controlled step.

## Measured budgets

| Journey | Budget / gate | Execution |
|---|---:|---|
| Select initial graph focus for 10,000 people / 9,999 relationships | <= 1,500 ms | JVM unit test on every CI push/PR |
| Render PNG for 60 people | <= 8,000 ms | Android instrumentation on the reference SM-T225 |
| Release Android App Bundle | <= 25 MiB | CI and release build gate |
| Cold app launch | <= 5,000 ms | USB smoke measurement on the reference SM-T225 |

Budgets are intentionally generous for the low-end reference tablet and should be tightened after production telemetry provides representative, privacy-safe distributions.

## Deployment prerequisites not stored in Git

Configure `FAMILY_TREE_RELEASE_API_BASE_URL`, signing keystore/password/alias secrets, a random production `JWT_SECRET`, database persistence/backup, TLS termination, and the allowed browser origins (if a web client is deployed). No production credential or signing key belongs in source control.
