import { MigrationInterface, QueryRunner } from 'typeorm';

export class AddProposalComments1754352000000 implements MigrationInterface {
  name = 'AddProposalComments1754352000000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      CREATE TABLE "proposal_comments" (
        "commentId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "spaceId" uuid NOT NULL,
        "proposalId" uuid NOT NULL,
        "authorUserId" uuid NOT NULL,
        "body" text NOT NULL,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "FK_proposal_comments_proposal"
          FOREIGN KEY ("proposalId")
          REFERENCES "edit_proposals" ("proposalId")
          ON DELETE CASCADE,
        CONSTRAINT "FK_proposal_comments_author"
          FOREIGN KEY ("authorUserId")
          REFERENCES "users" ("userId")
          ON DELETE RESTRICT,
        CONSTRAINT "CHK_proposal_comments_body" CHECK (
          char_length(btrim("body")) BETWEEN 1 AND 1000
          AND "body" = btrim("body")
        )
      )
    `);
    await queryRunner.query(`
      CREATE INDEX "IDX_proposal_comments_proposal_created"
      ON "proposal_comments" ("proposalId", "createdAt")
    `);
    await queryRunner.query(
      'ALTER TABLE public."proposal_comments" ENABLE ROW LEVEL SECURITY',
    );
    await queryRunner.query(
      'REVOKE ALL PRIVILEGES ON TABLE public."proposal_comments" FROM PUBLIC',
    );
    await queryRunner.query(`
      DO $familyroot$
      BEGIN
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
          REVOKE ALL PRIVILEGES
            ON TABLE public."proposal_comments"
            FROM anon;
        END IF;
        IF EXISTS (
          SELECT 1 FROM pg_roles WHERE rolname = 'authenticated'
        ) THEN
          REVOKE ALL PRIVILEGES
            ON TABLE public."proposal_comments"
            FROM authenticated;
        END IF;
      END
      $familyroot$
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      'DROP INDEX "IDX_proposal_comments_proposal_created"',
    );
    await queryRunner.query('DROP TABLE "proposal_comments"');
  }
}
