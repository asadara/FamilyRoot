# Family Tree Platform Backend

NestJS API and source of truth for Family Tree Platform. Product direction and roadmap live in [`../PROJECT_BLUEPRINT.md`](../PROJECT_BLUEPRINT.md); the Phase 1 API and role contract are documented in [`API_CONTRACT.md`](API_CONTRACT.md).

## Setup

```powershell
npm ci
Copy-Item .env.example .env
npm run start:dev
```

Untuk menyiapkan akun dan Family Space demo yang sama pada PC baru, lihat
[`DEMO_DATA.md`](DEMO_DATA.md) lalu jalankan `npm run seed:dev`.

The server listens on `http://localhost:3001`. Interactive OpenAPI documentation is available at `http://localhost:3001/api/docs`.

Set a strong, private `JWT_SECRET`. SQLite remains the local development database;
`DB_DATABASE` controls its path. Production requires a PostgreSQL `DATABASE_URL`,
keeps schema synchronization disabled, and uses explicit TypeORM migrations:

```powershell
npm run migration:show
npm run migration:run
```

The production container and the complete Cloud Run + Supabase Free pilot procedure
are documented in [`../docs/STEP7_CLOUD_PILOT_RUNBOOK.md`](../docs/STEP7_CLOUD_PILOT_RUNBOOK.md).

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
