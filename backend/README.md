# Family Tree Platform Backend

NestJS API and source of truth for Family Tree Platform. Product direction and roadmap live in [`../PROJECT_BLUEPRINT.md`](../PROJECT_BLUEPRINT.md); the Phase 1 API and role contract are documented in [`API_CONTRACT.md`](API_CONTRACT.md).

## Setup

```bash
npm install
copy .env.example .env
npm run start:dev
```

The server listens on `http://localhost:3001`. Interactive OpenAPI documentation is available at `http://localhost:3001/api/docs`.

Set a strong, private `JWT_SECRET`. The application refuses to start in production without it. SQLite is currently used for development; `DB_DATABASE` controls its path.

## Verification

```bash
npm run lint
npm run build
npm test -- --runInBand
npm run test:e2e
npm audit --omit=dev --audit-level=high
```

E2E tests should use a disposable database through `DB_DATABASE`, never `dev.sqlite`.

## Authentication

Register or sign in through `/auth/register` and `/auth/login`, then send:

```http
Authorization: Bearer <accessToken>
```

The legacy `x-user-id` mechanism is no longer accepted.
