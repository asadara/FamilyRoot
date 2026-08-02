import { MigrationInterface, QueryRunner } from 'typeorm';

export class HardenActiveClaims1754611200000 implements MigrationInterface {
  name = 'HardenActiveClaims1754611200000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      CREATE TEMP TABLE "claim_dedup_map" ON COMMIT DROP AS
      WITH confirmation_counts AS (
        SELECT "claimId", COUNT(*)::int AS confirmation_count
        FROM "claim_confirmations"
        GROUP BY "claimId"
      ), ranked AS (
        SELECT
          claim."claimId",
          FIRST_VALUE(claim."claimId") OVER (
            PARTITION BY claim."spaceId", claim."userId", claim."personId"
            ORDER BY
              CASE WHEN claim."status" = 'VERIFIED' THEN 0 ELSE 1 END,
              COALESCE(counts.confirmation_count, 0) DESC,
              claim."requestedAt" ASC,
              claim."claimId" ASC
          ) AS survivor_id,
          ROW_NUMBER() OVER (
            PARTITION BY claim."spaceId", claim."userId", claim."personId"
            ORDER BY
              CASE WHEN claim."status" = 'VERIFIED' THEN 0 ELSE 1 END,
              COALESCE(counts.confirmation_count, 0) DESC,
              claim."requestedAt" ASC,
              claim."claimId" ASC
          ) AS duplicate_rank
        FROM "user_person_claims" claim
        LEFT JOIN confirmation_counts counts
          ON counts."claimId" = claim."claimId"
        WHERE claim."status" IN ('PENDING', 'VERIFIED')
      )
      SELECT
        "claimId" AS duplicate_id,
        survivor_id
      FROM ranked
      WHERE duplicate_rank > 1
    `);

    await queryRunner.query(`
      INSERT INTO "claim_confirmations" (
        "confirmationId",
        "claimId",
        "confirmedBy",
        "confirmedAt"
      )
      SELECT
        gen_random_uuid(),
        dedup.survivor_id,
        confirmation."confirmedBy",
        confirmation."confirmedAt"
      FROM "claim_dedup_map" dedup
      INNER JOIN "claim_confirmations" confirmation
        ON confirmation."claimId" = dedup.duplicate_id
      ON CONFLICT ("claimId", "confirmedBy") DO NOTHING
    `);

    await queryRunner.query(`
      UPDATE "user_person_claims" claim
      SET "status" = 'REJECTED'
      FROM "claim_dedup_map" dedup
      WHERE claim."claimId" = dedup.duplicate_id
    `);

    await queryRunner.query(`
      UPDATE "user_person_claims" claim
      SET "status" = 'VERIFIED'
      WHERE claim."status" = 'PENDING'
        AND (
          SELECT COUNT(*)
          FROM "claim_confirmations" confirmation
          WHERE confirmation."claimId" = claim."claimId"
        ) >= 2
    `);

    await queryRunner.query(`
      CREATE UNIQUE INDEX "UQ_claims_active_space_user_person"
      ON "user_person_claims" ("spaceId", "userId", "personId")
      WHERE "status" IN ('PENDING', 'VERIFIED')
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query('DROP INDEX "UQ_claims_active_space_user_person"');
  }
}
