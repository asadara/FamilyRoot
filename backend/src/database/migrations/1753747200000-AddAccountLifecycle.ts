import { MigrationInterface, QueryRunner } from 'typeorm';

export class AddAccountLifecycle1753747200000 implements MigrationInterface {
  name = 'AddAccountLifecycle1753747200000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      ALTER TABLE "users"
      ADD COLUMN "accountStatus" text NOT NULL DEFAULT 'ACTIVE',
      ADD COLUMN "deletedAt" timestamptz
    `);
    await queryRunner.query(
      'ALTER TABLE "users" DROP CONSTRAINT "CHK_users_login"',
    );
    await queryRunner.query(`
      ALTER TABLE "users"
      ADD CONSTRAINT "CHK_users_account_state" CHECK (
        (
          "accountStatus" = 'ACTIVE'
          AND ("email" IS NOT NULL OR "phone" IS NOT NULL)
          AND "deletedAt" IS NULL
        )
        OR
        (
          "accountStatus" = 'DELETED'
          AND "email" IS NULL
          AND "phone" IS NULL
          AND "passwordHash" IS NULL
          AND "deletedAt" IS NOT NULL
        )
      )
    `);
    await queryRunner.query(`
      CREATE TABLE "account_lifecycle_audit" (
        "auditId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "userId" uuid NOT NULL REFERENCES "users" ("userId") ON DELETE RESTRICT,
        "operation" text NOT NULL,
        "impactJson" text NOT NULL,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "CHK_account_lifecycle_operation"
          CHECK ("operation" IN ('DELETE'))
      )
    `);
    await queryRunner.query(
      'CREATE INDEX "IDX_users_account_status" ON "users" ("accountStatus")',
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query('DROP INDEX "IDX_users_account_status"');
    await queryRunner.query('DROP TABLE "account_lifecycle_audit"');
    await queryRunner.query(
      'ALTER TABLE "users" DROP CONSTRAINT "CHK_users_account_state"',
    );
    await queryRunner.query(
      'ALTER TABLE "users" DROP COLUMN "deletedAt", DROP COLUMN "accountStatus"',
    );
    await queryRunner.query(`
      ALTER TABLE "users"
      ADD CONSTRAINT "CHK_users_login"
      CHECK ("email" IS NOT NULL OR "phone" IS NOT NULL)
    `);
  }
}
