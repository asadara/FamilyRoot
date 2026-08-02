# Family Tree Platform API Contract

> Canonical contract. Interactive OpenAPI documentation is served at `/api/docs` in non-production environments only.

## Health and request correlation

- `GET /health` is public and returns only service status, version, and timestamp.
- Every response includes `X-Request-Id`. A safe caller-supplied value is preserved; otherwise the server creates a UUID.
- Structured request events contain request ID, method, path without query, status, and duration. They deliberately exclude request/response bodies, query values, authentication data, user IDs, and family values.

## Android release compatibility

- `GET /app-compatibility/android` is public so it can run before login. Required
  query values are `versionCode`, `apiContractVersion`, and `channel`; `versionName`
  is informational.
- Channels are `DEBUG`, `PILOT`, and `PRODUCTION`. Integer `versionCode` and
  `apiContractVersion` are authoritative; display `versionName` is never used for
  ordering.
- The response status is one of `COMPATIBLE`, `UPDATE_AVAILABLE`, `APP_TOO_OLD`,
  `APP_TOO_NEW`, or `API_CONTRACT_MISMATCH`. `blocking` is true for an incompatible
  status only when that channel's `enforcementEnabled` policy is active.
- `PUT /app-compatibility/android/policy` requires a valid access token and a user ID
  listed in server-only `SYSTEM_ADMIN_USER_IDS`. A Family Space OWNER/ADMIN is not a
  system administrator merely because of that workspace role.
- Policy updates require `minimumSupportedVersionCode <= latestVersionCode`, a
  positive API contract version, `enforcementEnabled`, and an optional HTTPS update
  URL. Every update is written to `app_release_policy_audit`.
- When no database policy exists, the backend uses the channel-specific environment
  defaults. This preserves build `1`, API contract `1`, unless explicitly configured.
- Android checks before session restoration and again on activity resume. A compatible
  result may be cached for at most 24 hours and only for the exact combination of
  version code, API contract, and channel.
- While enforcement is disabled, update/incompatibility results and an unverifiable
  first launch present a warning that may be acknowledged for the current app
  process. An incompatible result becomes a hard block only after server enforcement
  is enabled.
- New clients attach `X-App-Version-Code`, `X-Api-Contract-Version`, and
  `X-Release-Channel` to every request. Server enforcement remains disabled during
  rollout; after it is enabled, missing or incompatible headers return
  `426 UPGRADE_REQUIRED`. This prevents legacy clients from bypassing the UI gate.
- Production/pilot deployments should restrict accepted client channels with
  `ANDROID_ACCEPTED_RELEASE_CHANNELS` (for example `PILOT`) so a different channel
  header cannot bypass the active policy.

## Authentication

- `POST /auth/register` — public; accepts `email`, `displayName`, and a password of at least 10 characters.
- `POST /auth/login` — public; accepts `email` and `password`.
- `POST /auth/google` — public; accepts a Google `idToken`, verifies it against
  `GOOGLE_OAUTH_CLIENT_ID`, and creates or resumes the matching account.
- `POST /auth/refresh` — public; accepts the opaque `refreshToken`, rotates it, and returns a new access/refresh pair.
- `POST /auth/logout` — public; revokes the refresh-token family and returns `204`.
- `GET /auth/me` — requires `Authorization: Bearer <accessToken>`.
- Login/register/refresh responses contain `accessToken`, `refreshToken`, `expiresIn`, `refreshExpiresIn`, and the authenticated `user`.
- Access tokens expire after one hour; refresh sessions expire after 30 days. Only SHA-256 token digests are stored server-side.
- Refresh tokens are single-use. Reuse of a rotated token revokes the active token family and returns `401`.
- `JWT_SECRET` is mandatory in production.
- Google Sign-In stores the stable Google `sub` separately from mutable email data.
  Gmail/Workspace identities may safely link to an existing matching password
  account; other third-party-email Google accounts require an explicit account-link
  flow when a matching password account already exists.
- The legacy `x-user-id` header is not accepted as authentication.

Account lifecycle:

- `GET /users/me/deletion-impact` returns ownership blockers, membership/claim/
  active-session/invitation counts, and spaces the account may export.
- `DELETE /users/me` requires the exact confirmation `HAPUS AKUN`. An OWNER receives
  `409 CONFLICT` until every active/archived space has transferred ownership.
- Successful deletion revokes all refresh sessions and outstanding invitations,
  removes memberships, claims, and Google identity, then anonymizes the retained user
  tombstone. Existing Person records and family audit history are not deleted.
- The JWT guard checks active account state on every authenticated request, so a
  previously issued access token stops working immediately after account deletion.

Personal action notifications:

- Successful `POST`/`PATCH`/`DELETE` family mutations produce a privacy-safe personal
  receipt. Validation failures, conflicts, and handler errors also produce generic
  status receipts when the request has passed authentication/authorization guards.
- Receipt copy never includes Person names, relationship values, invitation tokens,
  emails, request bodies, or raw error text.
- `GET /notifications?limit=...` returns only the authenticated account's latest
  receipts plus `unreadCount`. Legacy values through 100 are accepted, but output is
  always clamped to at most 10 items.
- `PATCH /notifications/:notificationId/read` can mark only the authenticated
  account's own receipt. `POST /notifications/read-all` marks all its unread receipts.
- Notification-maintenance endpoints and read-only requests never create another
  receipt. Personal notification history is deleted during account deletion.

## Space roles

| Capability | OWNER | ADMIN | EDITOR | VIEWER |
|---|:---:|:---:|:---:|:---:|
| Read people, relationships, and activity | Yes | Yes | Yes | Yes |
| Create/update people and relationships | Yes | Yes | Yes | No |
| Soft-delete a person | Yes | Yes | No | No |
| Request person deletion | No | No | Yes | No |
| Create a claim for own account | Yes | Yes | Yes | Yes |
| Verify claims | Yes | Yes | No | No |
| Review proposals and merge duplicates | Yes | Yes | No | No |
| Create sources, media metadata, and proposals | Yes | Yes | Yes | Proposal only |
| Add VIEWER/EDITOR | Yes | Yes | No | No |
| Add ADMIN | Yes | No | No | No |
| List memberships | Yes | Yes | Yes | Yes |
| Change VIEWER/EDITOR role | Yes | Yes | No | No |
| Change ADMIN role | Yes | No | No | No |
| Remove VIEWER/EDITOR | Yes | Yes | No | No |
| Remove ADMIN | Yes | No | No | No |
| Transfer ownership | Yes | No | No | No |
| Leave the Family Space | After transfer | Yes | Yes | Yes |
| Create invitations for VIEWER/EDITOR | Yes | Yes | No | No |
| Create invitations for ADMIN | Yes | No | No | No |
| Export a Family Space | Yes | Yes | No | No |
| Import GEDCOM or restore backup | Yes | Yes | No | No |

`POST /spaces` creates the authenticated creator's OWNER membership in the same transaction. `GET /spaces` lists only spaces belonging to the authenticated user.

Membership lifecycle endpoints:

- `GET /spaces/:spaceId/members` — all members can read display name, role, join
  time, and current-user marker. Login email is deliberately excluded.
- `PATCH /spaces/:spaceId/members/:memberId` — OWNER may assign
  `ADMIN`/`EDITOR`/`VIEWER`; ADMIN may only switch EDITOR and VIEWER. OWNER role is
  never assigned through this endpoint.
- `DELETE /spaces/:spaceId/members/:memberId` — OWNER may remove any non-owner;
  ADMIN may only remove EDITOR/VIEWER. Self-removal uses the leave endpoint.
- `POST /spaces/:spaceId/ownership-transfer` — current OWNER supplies
  `targetMemberId`. In one transaction the previous OWNER becomes ADMIN and the
  target becomes OWNER.
- `POST /spaces/:spaceId/leave` — a non-owner removes their own membership. OWNER
  receives `409 CONFLICT` until ownership is transferred.
- A partial unique database index permits only one OWNER per Family Space. Role
  changes, ownership transfer, removal, and leave all write `MEMBERSHIP` audit logs.
- Android blocks voluntary leave while offline mutations remain. After confirmed
  leave or detected revocation, it purges that space's Room graph, mutation queue,
  and profile-photo URL cache before returning to space selection.

Family Space lifecycle:

- `GET /spaces/:spaceId/lifecycle-impact` is OWNER-only and returns status plus counts
  for people, relationships, members, claims, media, sources, pending proposals, and
  active invitations.
- `POST /spaces/:spaceId/archive` changes an active space to read-only and revokes
  every outstanding invitation. All mutation endpoints reject archived spaces.
- `POST /spaces/:spaceId/restore` reactivates an archived space.
- `DELETE /spaces/:spaceId` is OWNER-only, requires the archived state, the exact
  space name, and `acknowledgeExport: true`. It is a soft-delete retaining audit and
  family records; deleted spaces disappear from membership lists and API access.

## Family Space invitations

Invitation endpoints require authentication. Creating an invitation also requires membership and role authorization on the target `spaceId`; previewing and accepting an invitation only require a valid logged-in account.

- `POST /spaces/invitations` — OWNER or ADMIN creates an invitation. Body: `spaceId`,
  `role`, optional `expiresInDays` from 1 to 30, and optional normalized
  `targetEmail`. ADMIN cannot invite another ADMIN.
- `GET /spaces/invitations/:token` — preview a usable invitation before accepting.
  A targeted invitation returns `403` to a different account without consuming the
  token. Responses expose only `maskedTargetEmail`.
- `POST /spaces/invitations/accept` — accepts a token and creates membership for the authenticated user.
- `GET /spaces/:spaceId/invitations?status=...` — OWNER/ADMIN lists safe invitation
  history without tokens. ADMIN cannot inspect ADMIN invitations.
- `DELETE /spaces/:spaceId/invitations/:inviteId` — revokes an active invitation
  idempotently. ADMIN cannot revoke an ADMIN invitation.

Invitation tokens are single-use, expire, and are audited. Accepting an invitation creates a `MEMBERSHIP` audit log entry.

## Family data

Every family-data request requires a Bearer token and a valid `spaceId` in the query or body. The authenticated account must hold one of the permitted roles for that operation. Person IDs used by a relationship or claim must refer to active persons in the same Family Space.

Person privacy pilot:

- Every active Person has `visibility`: `FAMILY`, `LIMITED`, or `PRIVATE`.
  Newly created living/unknown people default to `LIMITED`; deceased people default
  to `FAMILY`.
- Responses include `privacyAccess` (`FULL`, `STRUCTURE`, or `MINIMUM`) and
  `canManageVisibility`. `LIMITED` remains editable by OWNER/ADMIN/EDITOR but is
  structurally redacted for VIEWER. `PRIVATE` is fully available only to the
  verified claimant, or temporarily to OWNER/ADMIN while no verified claim exists.
- `PATCH /persons/:personId/visibility` requires `spaceId`, `visibility`, and
  `expectedVersion`. Once a verified claim exists, only that claimant manages the
  Person visibility.
- Redaction is server-side and also applies to relationship detail, path names,
  claim/proposal context, duplicate candidates, media/source access, and
  JSON/GEDCOM/backup exports. Relationship structure remains visible, but dates and
  care context are removed unless both endpoints are `FULL`.

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

- `POST /claims` is idempotent for the same active `(spaceId, userId, personId)`
  claim. Repeated submission returns the existing `PENDING` or `VERIFIED` claim;
  the database enforces one active claim for that tuple.
- `POST /claims/verify` records one collective confirmation. A claim owner cannot
  confirm their own claim, the same OWNER/ADMIN cannot be counted twice, and status
  becomes `VERIFIED` only after two different confirmations. Responses expose
  `confirmationCount` and `required`; legacy verified claims remain valid.
- `GET /claims/me?spaceId=...` returns only the authenticated member's own latest
  claim (preferring `VERIFIED`) and its Person display name. It does not expose other
  members' claims and is the canonical account-to-Person link for the active space.

- `GET /changes?spaceId=...&limit=...` returns at most 10 recent collaborative
  activities and includes privacy-safe `actorDisplayName` in addition to the retained
  audit actor ID.
- `POST /changes/history-access-requests` lets a member request full-history access.
  OWNER/ADMIN review requests through
  `POST /changes/history-access-requests/:requestId/review`.
- `GET /changes/full?spaceId=...&limit=...&before=...` is available to OWNER/ADMIN or
  an approved requester. Results are cursor-paginated, with a maximum page size of
  50; clients must not render the entire history eagerly.

- `GET /persons/duplicates?spaceId=...` — lists duplicate candidates.
- `POST /persons/merge` — OWNER/ADMIN merges `sourcePersonId` into `targetPersonId` and audits the merge.
- `GET /relationships/path?spaceId=...&fromPersonId=...&toPersonId=...` — returns the shortest relationship path.
- `GET|POST /persons/:personId/sources` — reads or creates source/citation records for facts.
- `GET|POST /persons/:personId/media` — reads or creates media metadata/URI records.
- `POST /persons/:personId/media/upload?spaceId=...` — OWNER/ADMIN/EDITOR uploads one private
  JPEG, PNG, or WebP image. A VIEWER may use this endpoint only when a verified self
  claim matches the target Person. The backend enforces a 2 MB limit, validates magic
  bytes, re-encodes the image without EXIF metadata, and stores only an object
  reference in `media_items`. The response includes the new `mediaId`, a 15-minute
  signed URL, and expiry. A successful replacement removes older managed
  profile-photo metadata and storage objects only after the new photo is readable.
- `GET /spaces/:spaceId/profile-photos` returns the latest privacy-visible managed
  photo per Person with `mediaId`, signed URL, and expiry.
- `GET /spaces/:spaceId/profile-photos/me` returns only the verified self Person's
  photo (or `null`) so the global account avatar does not fetch every family photo.
- `GET /persons/:personId/media/:mediaId/access?spaceId=...` — a member with `FULL`
  privacy access to the matching Person receives a private read URL valid for 60
  seconds; restricted access is answered as not found.
- `GET|POST /proposals` — reads or creates edit proposals. A review item carries the
  value captured when the proposal was created (`beforeValue`), the current value,
  proposed value, contributor reason, reviewer, review time, and review reason.
- `POST /proposals/approve` and `POST /proposals/reject` — OWNER/ADMIN review proposal
  changes. Rejection from app version 3 or newer requires a trimmed
  `reviewReason`; requests without a version header retain the legacy optional
  contract during rollout. Proposal details and review actions require `FULL`
  privacy access to the target Person.
- `GET|POST /proposals/:proposalId/comments` — reads or appends an immutable
  discussion entry. Comment bodies are trimmed and limited to 1–1000 characters.
  The thread follows the target Person's `FULL` privacy access; responses expose a
  display name and `isMine`, never account email or author user ID. Comment creation
  is audited without copying the sensitive comment body into the activity record.

Safe person deletion contract:

- `GET /persons/:personId/deletion-impact?spaceId=...` — OWNER/ADMIN/EDITOR receives
  counts and blockers for relationships, claims, media, sources, and pending proposals.
- `DELETE /persons/:personId` — OWNER/ADMIN only; body contains `spaceId`. The server
  performs a soft-delete only when the impact check has no blockers. Linked records
  are never removed implicitly.
- `POST /persons/:personId/deletion-requests` — EDITOR only; body contains `spaceId`
  and a non-empty `reason`. It creates one pending `DELETE_PERSON` proposal.
- Approving a deletion proposal repeats the impact check and performs the soft-delete
  in the same transaction. A current deletion proposal does not block its own approval,
  but any other pending proposal does.
- A blocked direct deletion or approval returns `409 CONFLICT` with
  `details.impact`. Successful deletion retains a `PERSON/DELETE` audit record.
- Android removes the local card and profile-photo cache only after a successful
  server response. Pending local mutations are an additional client-side blocker.

Phase 4 concurrency contract (initial slice):

- `PATCH /persons/:personId/life` requires `spaceId`, `lifeStatus`, `expectedVersion`, and UUID `clientMutationId`; `deceasedAt` remains optional.
- `PATCH /persons/:personId/profile` uses the same contract for offline-safe edits to `birthPlace` and `notes`.
- A successful mutation increments `version`. Repeating the same request with the same `clientMutationId` returns the stored successful response without repeating side effects or audit entries.
- Reusing a mutation ID for different input, or sending a stale `expectedVersion`, returns `409 CONFLICT`.
- A stale-version response includes `details` with the current version and relevant server fields so clients can present an explicit resolution choice.
- `POST /persons/parent-child` and `POST /relationships/spouse` require UUID `clientMutationId`; replaying an identical relationship creation returns the originally stored relationship without duplicating graph edges or audit entries.
- `POST /persons` accepts optional UUID `clientMutationId`. New Android clients
  always send it so replay returns the same Person without a second audit entry;
  omission remains supported for legacy clients.
- `DELETE /relationships/:relationshipId?spaceId=...&clientMutationId=...` is
  idempotent when the mutation ID is supplied and returns the recorded deletion
  result on replay.
- `POST /persons/:personId/sources` accepts optional UUID `clientMutationId`.
  Replaying the same actor and normalized source payload returns the original
  source without a second row or audit entry; reuse for a different operation or
  payload returns `409 CONFLICT`. Legacy online clients may omit the value.
- `GET /relationships?spaceId=...` returns both `PARENT_CHILD` and `SPOUSE` edges, including `type`, dates, and metadata, so clients can maintain one offline graph cache.
- `FOSTER` and `GUARDIAN` are care-relationship metadata, not lineage. They may carry
  optional start/end dates and `careContext`, never affect generation/cycle/parent
  inference, and are returned separately as caregiver/care-recipient relations.
- Backup JSON retains care relationships. GEDCOM intentionally excludes them because
  there is no safe supported mapping in the current GEDCOM subset.
- Android queues create-person, source creation, and relationship deletion with
  optimistic Room state.
  Create-person atomically remaps its local ID through cached edges and dependent
  mutation payloads/source rows after server success. Source creation uses a
  privacy-aware Room cache, remains visible after process restart, and replaces its
  local row with the idempotent server result. Permanent errors roll back optimistic
  state; retry explicitly reapplies it. Relationship deletion retains a rollback
  snapshot and treats a server `404` as already converged.

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
