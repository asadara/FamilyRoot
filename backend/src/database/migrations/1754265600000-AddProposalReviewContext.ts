import { MigrationInterface, QueryRunner } from 'typeorm';

export class AddProposalReviewContext1754265600000 implements MigrationInterface {
  name = 'AddProposalReviewContext1754265600000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      ALTER TABLE "edit_proposals"
      ADD COLUMN "beforeValue" text,
      ADD COLUMN "reviewReason" text,
      ADD CONSTRAINT "CHK_edit_proposals_review_reason" CHECK (
        "reviewReason" IS NULL
        OR (
          char_length(btrim("reviewReason")) BETWEEN 1 AND 1000
          AND "reviewReason" = btrim("reviewReason")
        )
      )
    `);
    await queryRunner.query(`
      UPDATE "edit_proposals" AS proposal
      SET "beforeValue" = CASE proposal."field"
        WHEN 'notes' THEN person."notes"
        WHEN 'birthPlace' THEN person."birthPlace"
        WHEN 'deathPlace' THEN person."deathPlace"
        ELSE NULL
      END
      FROM "persons" AS person
      WHERE proposal."status" = 'PENDING'
        AND proposal."spaceId" = person."spaceId"
        AND proposal."personId" = person."personId"
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      ALTER TABLE "edit_proposals"
      DROP CONSTRAINT "CHK_edit_proposals_review_reason",
      DROP COLUMN "reviewReason",
      DROP COLUMN "beforeValue"
    `);
  }
}
