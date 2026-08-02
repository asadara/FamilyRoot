import { MigrationInterface, QueryRunner } from 'typeorm';

export class AddHistoryAccessRequests1754524800000 implements MigrationInterface {
  name = 'AddHistoryAccessRequests1754524800000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      CREATE TABLE "history_access_requests" (
        "requestId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "spaceId" uuid NOT NULL REFERENCES "family_spaces" ("spaceId") ON DELETE CASCADE,
        "userId" uuid NOT NULL REFERENCES "users" ("userId") ON DELETE CASCADE,
        "status" text NOT NULL DEFAULT 'PENDING',
        "reviewedByUserId" uuid REFERENCES "users" ("userId") ON DELETE SET NULL,
        "reviewedAt" timestamptz,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        "updatedAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "UQ_history_access_space_user" UNIQUE ("spaceId", "userId"),
        CONSTRAINT "CHK_history_access_status"
          CHECK ("status" IN ('PENDING', 'APPROVED', 'REJECTED'))
      )
    `);
    await queryRunner.query(`
      CREATE INDEX "IDX_history_access_space_status"
      ON "history_access_requests" ("spaceId", "status", "createdAt" DESC)
    `);
    await queryRunner.query(
      'ALTER TABLE public."history_access_requests" ENABLE ROW LEVEL SECURITY',
    );
    await queryRunner.query(
      'REVOKE ALL PRIVILEGES ON TABLE public."history_access_requests" FROM PUBLIC',
    );
    await queryRunner.query(`
      DO $familyroot$
      BEGIN
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
          REVOKE ALL PRIVILEGES ON TABLE public."history_access_requests" FROM anon;
        END IF;
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
          REVOKE ALL PRIVILEGES ON TABLE public."history_access_requests" FROM authenticated;
        END IF;
      END
      $familyroot$
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query('DROP INDEX "IDX_history_access_space_status"');
    await queryRunner.query('DROP TABLE "history_access_requests"');
  }
}
