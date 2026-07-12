# Family Tree Platform API Contract

> Phase 1 contract. Interactive OpenAPI documentation is served at `/api/docs` while the backend is running.

## Authentication

- `POST /auth/register` — public; accepts `email`, `displayName`, and a password of at least 10 characters.
- `POST /auth/login` — public; accepts `email` and `password`.
- `GET /auth/me` — requires `Authorization: Bearer <accessToken>`.
- Access tokens expire after one hour. `JWT_SECRET` is mandatory in production.
- The legacy `x-user-id` header is not accepted as authentication.

## Space roles

| Capability | OWNER | ADMIN | EDITOR | VIEWER |
|---|:---:|:---:|:---:|:---:|
| Read people, relationships, and activity | Yes | Yes | Yes | Yes |
| Create/update people and relationships | Yes | Yes | Yes | No |
| Soft-delete a person | Yes | Yes | No | No |
| Create a claim for own account | Yes | Yes | Yes | Yes |
| Verify claims | Yes | Yes | No | No |
| Add VIEWER/EDITOR | Yes | Yes | No | No |
| Add ADMIN | Yes | No | No | No |
| Create invitations for VIEWER/EDITOR | Yes | Yes | No | No |
| Create invitations for ADMIN | Yes | No | No | No |
| Export a Family Space | Yes | Yes | No | No |

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
- `/changes`
- `/export/space`
- `/spaces`, `/spaces/members`, and `/spaces/invitations`

UUID response fields are explicit: `userId`, `spaceId`, `memberId`, `personId`, `relationshipId`, `claimId`, and `changeId`.

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
