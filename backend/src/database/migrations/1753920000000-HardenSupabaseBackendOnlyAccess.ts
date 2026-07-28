import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * FamilyRoot deliberately keeps its application tables behind the NestJS backend.
 * The Android client does not use Supabase Data API roles.
 *
 * Keep this list in sync with every application table created by a migration.
 * `scripts/check-database-security.mjs` enforces that relationship in CI.
 */
const backendOnlyTables = [
  'familyroot_migrations',
  'users',
  'family_spaces',
  'space_members',
  'persons',
  'relationships',
  'user_person_claims',
  'change_log',
  'refresh_sessions',
  'space_invitations',
  'fact_sources',
  'media_items',
  'edit_proposals',
  'client_mutations',
  'user_google_identities',
  'app_release_policies',
  'app_release_policy_audit',
  'account_lifecycle_audit',
] as const;

export class HardenSupabaseBackendOnlyAccess1753920000000 implements MigrationInterface {
  name = 'HardenSupabaseBackendOnlyAccess1753920000000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    for (const table of backendOnlyTables) {
      await queryRunner.query(
        `ALTER TABLE public."${table}" ENABLE ROW LEVEL SECURITY`,
      );
      await queryRunner.query(
        `REVOKE ALL PRIVILEGES ON TABLE public."${table}" FROM PUBLIC`,
      );
      await queryRunner.query(`
        DO $familyroot$
        BEGIN
          IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
            EXECUTE 'REVOKE ALL PRIVILEGES ON TABLE public."${table}" FROM anon';
          END IF;
          IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
            EXECUTE 'REVOKE ALL PRIVILEGES ON TABLE public."${table}" FROM authenticated';
          END IF;
        END
        $familyroot$
      `);
    }

    // TypeORM creates this sequence before application migrations are executed.
    // The guard keeps the migration portable if its implementation changes.
    await queryRunner.query(`
      DO $familyroot$
      BEGIN
        IF to_regclass('public.familyroot_migrations_id_seq') IS NOT NULL THEN
          REVOKE ALL PRIVILEGES
            ON SEQUENCE public.familyroot_migrations_id_seq
            FROM PUBLIC;
          IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
            REVOKE ALL PRIVILEGES
              ON SEQUENCE public.familyroot_migrations_id_seq
              FROM anon;
          END IF;
          IF EXISTS (
            SELECT 1 FROM pg_roles WHERE rolname = 'authenticated'
          ) THEN
            REVOKE ALL PRIVILEGES
              ON SEQUENCE public.familyroot_migrations_id_seq
              FROM authenticated;
          END IF;
        END IF;
      END
      $familyroot$
    `);

    // Supabase Security Advisor reported this no-argument SECURITY DEFINER
    // helper as executable by public API roles. Its owner retains EXECUTE, so
    // an event trigger owned by that role can continue to call it.
    await queryRunner.query(`
      DO $familyroot$
      BEGIN
        IF to_regprocedure('public.rls_auto_enable()') IS NOT NULL THEN
          REVOKE EXECUTE ON FUNCTION public.rls_auto_enable() FROM PUBLIC;
          IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
            REVOKE EXECUTE ON FUNCTION public.rls_auto_enable() FROM anon;
          END IF;
          IF EXISTS (
            SELECT 1 FROM pg_roles WHERE rolname = 'authenticated'
          ) THEN
            REVOKE EXECUTE
              ON FUNCTION public.rls_auto_enable()
              FROM authenticated;
          END IF;
        END IF;
      END
      $familyroot$
    `);
  }

  public down(): Promise<void> {
    return Promise.reject(
      new Error(
        'Backend-only privilege hardening is intentionally irreversible; restore grants only from an approved privilege baseline.',
      ),
    );
  }
}
