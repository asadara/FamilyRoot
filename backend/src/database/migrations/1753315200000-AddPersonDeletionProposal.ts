import { MigrationInterface, QueryRunner } from 'typeorm';

export class AddPersonDeletionProposal1753315200000 implements MigrationInterface {
  name = 'AddPersonDeletionProposal1753315200000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      'ALTER TABLE "edit_proposals" DROP CONSTRAINT "CHK_edit_proposals_field"',
    );
    await queryRunner.query(`
      ALTER TABLE "edit_proposals"
      ADD CONSTRAINT "CHK_edit_proposals_field"
      CHECK ("field" IN ('notes', 'birthPlace', 'deathPlace', 'DELETE_PERSON'))
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      'ALTER TABLE "edit_proposals" DROP CONSTRAINT "CHK_edit_proposals_field"',
    );
    await queryRunner.query(`
      ALTER TABLE "edit_proposals"
      ADD CONSTRAINT "CHK_edit_proposals_field"
      CHECK ("field" IN ('notes', 'birthPlace', 'deathPlace'))
    `);
  }
}
