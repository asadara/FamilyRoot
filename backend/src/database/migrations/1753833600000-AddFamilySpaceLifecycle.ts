import { MigrationInterface, QueryRunner } from 'typeorm';

export class AddFamilySpaceLifecycle1753833600000 implements MigrationInterface {
  name = 'AddFamilySpaceLifecycle1753833600000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      ALTER TABLE "family_spaces"
      ADD COLUMN "status" text NOT NULL DEFAULT 'ACTIVE',
      ADD COLUMN "archivedAt" timestamptz,
      ADD COLUMN "deletedAt" timestamptz,
      ADD CONSTRAINT "CHK_family_spaces_lifecycle" CHECK (
        (
          "status" = 'ACTIVE'
          AND "archivedAt" IS NULL
          AND "deletedAt" IS NULL
        )
        OR
        (
          "status" = 'ARCHIVED'
          AND "archivedAt" IS NOT NULL
          AND "deletedAt" IS NULL
        )
        OR
        (
          "status" = 'DELETED'
          AND "archivedAt" IS NOT NULL
          AND "deletedAt" IS NOT NULL
        )
      )
    `);
    await queryRunner.query(
      'CREATE INDEX "IDX_family_spaces_status" ON "family_spaces" ("status")',
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query('DROP INDEX "IDX_family_spaces_status"');
    await queryRunner.query(
      'ALTER TABLE "family_spaces" DROP CONSTRAINT "CHK_family_spaces_lifecycle"',
    );
    await queryRunner.query(`
      ALTER TABLE "family_spaces"
      DROP COLUMN "deletedAt",
      DROP COLUMN "archivedAt",
      DROP COLUMN "status"
    `);
  }
}
