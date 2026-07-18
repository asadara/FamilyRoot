# Family Tree Platform API Contract

> Canonical contract. Interactive OpenAPI documentation is served at `/api/docs` in non-production environments only.

## Health and request correlation

- `GET /health` is public and returns only service status, version, and timestamp.
- Every response includes `X-Request-Id`. A safe caller-supplied value is preserved; otherwise the server creates a UUID.
- Structured request events contain request ID, method, path without query, status, and duration. They deliberately exclude request/response bodies, query values, authentication data, user IDs, and family values.

## Authentication

- `POST /auth/register` — public; accepts `email`, `displayName`, and a password of at least 10 characters.
- `POST /auth/login` — public; accepts `email` and `password`.
- `POST /auth/refresh` — public; accepts the opaque `refreshToken`, rotates it, and returns a new access/refresh pair.
- `POST /auth/logout` — public; revokes the refresh-token family and returns `204`.
- `GET /auth/me` — requires `Authorization: Bearer <accessToken>`.
- Login/register/refresh responses contain `accessToken`, `refreshToken`, `expiresIn`, `refreshExpiresIn`, and the authenticated `user`.
- Access tokens expire after one hour; refresh sessions expire after 30 days. Only SHA-256 token digests are stored server-side.
- Refresh tokens are single-use. Reuse of a rotated token revokes the active token family and returns `401`.
- `JWT_SECRET` is mandatory in production.
- The legacy `x-user-id` header is not accepted as authentication.

## Space roles

| Capability | OWNER | ADMIN | EDITOR | VIEWER |
|---|:---:|:---:|:---:|:---:|
| Read people, relationships, and activity | Yes | Yes | Yes | Yes |
| Create/update people and relationships | Yes | Yes | Yes | No |
| Soft-delete a person | Yes | Yes | No | No |
| Create a claim for own account | Yes | Yes | Yes | Yes |
| Verify claims | Yes | Yes | No | No |
| Review proposals and merge duplicates | Yes | Yes | No | No |
| Create sources, media metadata, and proposals | Yes | Yes | Yes | Proposal only |
| Add VIEWER/EDITOR | Yes | Yes | No | No |
| Add ADMIN | Yes | No | No | No |
| Create invitations for VIEWER/EDITOR | Yes | Yes | No | No |
| Create invitations for ADMIN | Yes | No | No | No |
| Export a Family Space | Yes | Yes | No | No |
| Import GEDCOM or restore backup | Yes | Yes | No | No |

`POST /spaces` creates the authenticated creator's OWNER membership in the same transaction. `GET /spaces` lists only spaces belonging to the authenticated user.

## Family Space invitations

Invitation endpoints require authentication. Creating an invitation also requires membership and role authorization on the target `spaceId`; previewing and accepting an invitation only require a valid logged-in account.

- `POST /spaces/invitations` — OWNER or ADMIN creates an invitation. Body: `spaceId`, `role`, optional `expiresInDays` from 1 to 30. ADMIN cannot invite another ADMIN.
- `GET /spaces/invitations/:token` — preview a usable invitation before accepting. Returns `spaceId`, `spaceName`, `role`, and `expiresAt`.
- `POST /spaces/invitations/accept` — accepts a token and creates membership for the authenticated user.

Invitation tokens are single-use, expire, and are audited. Accepting an invitation creates a `MEMBERSHIP` audit log entry.

## Family data

Every family-data request requires a Bearer token and a valid `spaceId` in the query or body. The authenticated account must hold one of the permitted roles for that operation. Person IDs used by a relationship or claim must refer to active persons in the same Family Space.

Primary endpoint groups:

- `/persons`
- `/relationships`
- `/claims`
- `/proposals`
- `/persons/:personId/sources`
- `/persons/:personId/media`
- `/changes`
- `/export/space`
- `/export/space/gedcom`, `/export/space/gedcom/import`
- `/export/space/backup`, `/export/space/backup/restore`
- `/spaces`, `/spaces/members`, and `/spaces/invitations`

UUID response fields are explicit: `userId`, `spaceId`, `memberId`, `personId`, `relationshipId`, `claimId`, and `changeId`.

Phase 3 core endpoints:

- `GET /persons/duplicates?spaceId=...` — lists duplicate candidates.
- `POST /persons/merge` — OWNER/ADMIN merges `sourcePersonId` into `targetPersonId` and audits the merge.
- `GET /relationships/path?spaceId=...&fromPersonId=...&toPersonId=...` — returns the shortest relationship path.
- `GET|POST /persons/:personId/sources` — reads or creates source/citation records for facts.
- `GET|POST /persons/:personId/media` — reads or creates media metadata/URI records.
- `GET|POST /proposals` — reads or creates edit proposals.
- `POST /proposals/approve` and `POST /proposals/reject` — OWNER/ADMIN review proposal changes.

Phase 4 concurrency contract (initial slice):

- `PATCH /persons/:personId/life` requires `spaceId`, `lifeStatus`, `expectedVersion`, and UUID `clientMutationId`; `deceasedAt` remains optional.
- `PATCH /persons/:personId/profile` uses the same contract for offline-safe edits to `birthPlace` and `notes`.
- A successful mutation increments `version`. Repeating the same request with the same `clientMutationId` returns the stored successful response without repeating side effects or audit entries.
- Reusing a mutation ID for different input, or sending a stale `expectedVersion`, returns `409 CONFLICT`.
- A stale-version response includes `details` with the current version and relevant server fields so clients can present an explicit resolution choice.
- `POST /persons/parent-child` and `POST /relationships/spouse` require UUID `clientMutationId`; replaying an identical relationship creation returns the originally stored relationship without duplicating graph edges or audit entries.
- `GET /relationships?spaceId=...` returns both `PARENT_CHILD` and `SPOUSE` edges, including `type`, dates, and metadata, so clients can maintain one offline graph cache.

Phase 4 data portability contract:

- `GET /export/space/gedcom?spaceId=...` returns UTF-8 GEDCOM 5.5.1 content; `POST /export/space/gedcom/import` accepts up to 5 MB and imports the supported individual/family subset.
- `GET /export/space/backup?spaceId=...` returns `familyroot-backup` schema version `1`, including people, graph relationships, sources, and media metadata. Binary media content is not embedded.
- `POST /export/space/backup/restore` validates format, version, size limits, and references, remaps identifiers, and audits the operation.
- GEDCOM import and backup restore are transactional and require an empty target Family Space. This prevents an accidental merge, overwrite, or duplicate restore; OWNER/ADMIN can create a fresh space before recovery.

## Error envelope

```json
{
  "statusCode": 403,
  "code": "FORBIDDEN",
  "message": "Role VIEWER is not allowed for this operation",
  "timestamp": "2026-07-13T00:00:00.000Z"
}
```

Stable top-level codes are `VALIDATION_ERROR`, `UNAUTHENTICATED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, and `INTERNAL_ERROR`. Validation messages may be an array.

## Development environment

- Backend port: `3001`.
- Android emulator base URL: `http://10.0.2.2:3001/`.
- Production transport must use HTTPS.
- Copy `.env.example` to `.env` and replace the JWT secret; do not commit secrets.
