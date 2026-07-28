# P7 Cloud Pilot — Security and Operational Closure

This checklist records evidence needed to close P7 without treating repository
readiness as proof of external cloud state.

Status date: 2026-07-28

## Security model

FamilyRoot uses Supabase PostgreSQL and private Storage only behind the NestJS
backend. Android must never query Supabase Data API tables directly. Consequently:

- application tables have RLS enabled as defense in depth;
- `anon` and `authenticated` receive no table privileges;
- no data-access RLS policies are created for those roles;
- the PostgreSQL backend connection remains the only application data path;
- the Supabase server secret remains server-only and is used for private object
  storage, never embedded in Android.

The Security Advisor suggestion **RLS Enabled No Policy** is therefore an intentional
deny-by-default condition, not an instruction to add permissive policies. Adding a
policy solely to silence the suggestion would expand the exposed data path and
conflict with the architecture.

## Repository remediation

Migration `1753920000000-HardenSupabaseBackendOnlyAccess`:

1. enables RLS on every table created by FamilyRoot migrations, including release
   policy tables;
2. revokes table access from `PUBLIC`, `anon`, and `authenticated`;
3. revokes access to the TypeORM migration sequence when it exists;
4. revokes public/API-role execution of `public.rls_auto_enable()` when that exact
   no-argument function exists;
5. preserves execution by the function owner, so an owner-controlled event trigger
   can continue operating;
6. does not create policies, drop the helper, change its body, or alter its
   `SECURITY DEFINER` search path without evidence of its external definition.

The migration is deliberately irreversible because a rollback cannot safely infer
which privileges existed before hardening. Restore grants only from a separately
reviewed privilege baseline.

Run the static source guard before deployment:

```powershell
Set-Location D:\FamilyRoot\backend
npm run security:database:check
```

The guard fails when a migration creates a table that is not included in the
backend-only hardening baseline. After this migration has been applied, do not edit
or move it: every later migration that creates a table must enable RLS and revoke
`PUBLIC`, `anon`, and `authenticated` privileges in that same transaction. The guard
also enforces that rule.

### Local validation checkpoint — 2026-07-28

The complete migration chain was executed transactionally against a temporary,
disposable PostgreSQL 15.15 cluster with fixture roles `anon` and `authenticated`
and a no-argument `SECURITY DEFINER` function named `public.rls_auto_enable()`.

- nine migrations applied successfully;
- all 18 backend-only tables had RLS enabled;
- `anon` and `authenticated` had DML access to zero application tables;
- both API roles lost `EXECUTE` on `public.rls_auto_enable()`;
- the table owner retained access;
- the temporary cluster was stopped and removed after validation.

This proves migration behavior only. It is not evidence that Supabase or Cloud Run
currently has the same state.

## Pre-migration evidence (read-only)

Run in Supabase SQL Editor and save redacted results. Do not paste credentials,
tokens, family data, or function bodies into tickets.

Confirm that the backend connection role can legitimately bypass the deny-by-default
RLS boundary because it owns the tables or has `BYPASSRLS`. If neither is true, stop:
do not apply the migration until a dedicated backend role and policies are designed.

```sql
select
  r.rolname as connection_role,
  r.rolsuper as is_superuser,
  r.rolbypassrls as can_bypass_rls,
  count(*) filter (where c.relowner = r.oid) as owned_public_tables,
  count(*) as public_tables
from pg_roles r
cross join pg_class c
join pg_namespace n on n.oid = c.relnamespace
where r.rolname = current_user
  and n.nspname = 'public'
  and c.relkind = 'r'
group by r.rolname, r.rolsuper, r.rolbypassrls;
```

```sql
select
  n.nspname as schema_name,
  c.relname as table_name,
  c.relrowsecurity as rls_enabled,
  has_table_privilege('anon', c.oid, 'SELECT,INSERT,UPDATE,DELETE') as anon_has_dml,
  has_table_privilege(
    'authenticated',
    c.oid,
    'SELECT,INSERT,UPDATE,DELETE'
  ) as authenticated_has_dml
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relkind = 'r'
order by c.relname;
```

```sql
select
  p.oid::regprocedure::text as function_name,
  p.prosecdef as security_definer,
  has_function_privilege('anon', p.oid, 'EXECUTE') as anon_can_execute,
  has_function_privilege(
    'authenticated',
    p.oid,
    'EXECUTE'
  ) as authenticated_can_execute
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname = 'rls_auto_enable';
```

Also capture the event trigger dependency without copying the function body:

```sql
select
  e.evtname as event_trigger,
  e.evtenabled as enabled,
  e.evtfoid::regprocedure::text as function_name
from pg_event_trigger e
where e.evtfoid = to_regprocedure('public.rls_auto_enable()');
```

## Migration gate

- [ ] Export a fresh dummy-only `familyroot-backup` and store it in an
      owner-controlled location.
- [ ] Record current migration list with `npm run migration:show`.
- [ ] Confirm the database contains dummy/test data only.
- [ ] Apply pending migrations exactly once from an approved operator environment.
- [ ] Record the applied migration name and UTC timestamp without recording the
      database URL.
- [ ] Confirm Cloud Run health and an authenticated read/write smoke after migration.
- [ ] Do not enable APK compatibility enforcement as part of this database change.

## Post-migration evidence (read-only)

Repeat both privilege queries above. Acceptance:

- every FamilyRoot table reports `rls_enabled = true`;
- `anon_has_dml = false`;
- `authenticated_has_dml = false`;
- `anon_can_execute = false` for `public.rls_auto_enable()`;
- `authenticated_can_execute = false` for that function;
- backend health, authenticated reads, and authenticated writes still work;
- bucket `family-media` remains private and signed URLs expire as designed.

Re-run Supabase Security Advisor and save:

- UTC timestamp;
- counts by `WARN` and `INFO`;
- the two prior `SECURITY DEFINER` executable warnings are absent;
- remaining `RLS Enabled No Policy` entries are documented as accepted
  deny-by-default findings and are not “fixed” with permissive policies.

## Cloud Run and account-owner evidence

The following cannot be proven from source and must be captured from the consoles:

- [ ] active revision uses `min instances = 0`, `max instances = 1`,
      concurrency `20`, timeout `60s`, and database pool maximum `5`;
- [ ] database URL, JWT secret, and Supabase secret key are Secret Manager
      references, not plaintext environment variables;
- [ ] runtime service account has Secret Accessor only on the required secrets;
- [ ] repository, Cloud Build output, runtime logs, and downloadable APK/AAB contain
      no secret;
- [ ] Google Cloud budget alerts and current billing report are reviewed;
- [ ] Supabase database and storage usage are reviewed;
- [ ] data survives scale-to-zero or a revision restart;
- [ ] the dummy backup is recoverable into an empty Family Space;
- [ ] retention, PITR/backup tier, restore drill frequency, RPO, and RTO are approved
      before real family data is allowed.

## Device evidence

- [ ] Samsung reference tablet completes cloud login, cold start, offline mutation,
      reconnect/sync, and conflict recovery over the deployed HTTPS endpoint.
- [ ] A second physical device completes login, shared-space refresh, mutation
      visibility, session recovery, and access-revocation behavior.
- [ ] USB debugging is used for test tooling; Wireless ADB remains disabled.

A two-session backend smoke test supports protocol evidence but does not replace the
second-device UI and lifecycle test.

## Closure rule

P7 may be marked complete only when all repository checks pass and every external
item above has a dated, redacted artifact. Security Advisor INFO findings may remain
accepted only while the backend-only architecture remains unchanged.

## Primary references

- [Supabase — Securing your API](https://supabase.com/docs/guides/api/securing-your-api)
- [Supabase — Database function privileges](https://supabase.com/docs/guides/database/functions#function-privileges)
- [PostgreSQL — Row security policies](https://www.postgresql.org/docs/current/ddl-rowsecurity.html)
- [PostgreSQL — REVOKE](https://www.postgresql.org/docs/current/sql-revoke.html)
