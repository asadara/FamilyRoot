import { MigrationInterface, QueryRunner } from 'typeorm';

export class AddInvitationTargetEmail1754092800000 implements MigrationInterface {
  name = 'AddInvitationTargetEmail1754092800000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      ALTER TABLE "space_invitations"
      ADD COLUMN "targetEmail" text,
      ADD CONSTRAINT "CHK_space_invitations_target_email" CHECK (
        "targetEmail" IS NULL
        OR (
          "targetEmail" = lower(btrim("targetEmail"))
          AND char_length("targetEmail") <= 254
          AND position('@' in "targetEmail") > 1
        )
      )
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      ALTER TABLE "space_invitations"
      DROP CONSTRAINT "CHK_space_invitations_target_email",
      DROP COLUMN "targetEmail"
    `);
  }
}
