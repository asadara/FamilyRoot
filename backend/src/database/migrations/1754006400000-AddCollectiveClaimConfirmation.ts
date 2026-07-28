import { MigrationInterface, QueryRunner } from 'typeorm';

export class AddCollectiveClaimConfirmation1754006400000 implements MigrationInterface {
  name = 'AddCollectiveClaimConfirmation1754006400000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      CREATE TABLE "claim_confirmations" (
        "confirmationId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "claimId" uuid NOT NULL,
        "confirmedBy" uuid NOT NULL,
        "confirmedAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "FK_claim_confirmations_claim"
          FOREIGN KEY ("claimId")
          REFERENCES "user_person_claims" ("claimId")
          ON DELETE CASCADE,
        CONSTRAINT "FK_claim_confirmations_user"
          FOREIGN KEY ("confirmedBy")
          REFERENCES "users" ("userId")
          ON DELETE RESTRICT,
        CONSTRAINT "UQ_claim_confirmations_claim_actor"
          UNIQUE ("claimId", "confirmedBy")
      )
    `);
    await queryRunner.query(`
      CREATE INDEX "IDX_claim_confirmations_claim"
      ON "claim_confirmations" ("claimId")
    `);
    // This table is created after the backend-only hardening baseline.
    // Keep the NestJS service as its only data-access boundary.
    await queryRunner.query(
      'ALTER TABLE public."claim_confirmations" ENABLE ROW LEVEL SECURITY',
    );
    await queryRunner.query(
      'REVOKE ALL PRIVILEGES ON TABLE public."claim_confirmations" FROM PUBLIC',
    );
    await queryRunner.query(`
      DO $familyroot$
      BEGIN
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
          REVOKE ALL PRIVILEGES
            ON TABLE public."claim_confirmations"
            FROM anon;
        END IF;
        IF EXISTS (
          SELECT 1 FROM pg_roles WHERE rolname = 'authenticated'
        ) THEN
          REVOKE ALL PRIVILEGES
            ON TABLE public."claim_confirmations"
            FROM authenticated;
        END IF;
      END
      $familyroot$
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query('DROP INDEX "IDX_claim_confirmations_claim"');
    await queryRunner.query('DROP TABLE "claim_confirmations"');
  }
}
