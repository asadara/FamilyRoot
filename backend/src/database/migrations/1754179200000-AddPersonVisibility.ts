import { MigrationInterface, QueryRunner } from 'typeorm';

export class AddPersonVisibility1754179200000 implements MigrationInterface {
  name = 'AddPersonVisibility1754179200000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      ALTER TABLE "persons"
      ADD COLUMN "visibility" text NOT NULL DEFAULT 'FAMILY',
      ADD CONSTRAINT "CHK_persons_visibility"
        CHECK ("visibility" IN ('FAMILY', 'LIMITED', 'PRIVATE'))
    `);
    await queryRunner.query(
      'CREATE INDEX "IDX_persons_space_visibility" ON "persons" ("spaceId", "visibility")',
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query('DROP INDEX "IDX_persons_space_visibility"');
    await queryRunner.query(
      'ALTER TABLE "persons" DROP CONSTRAINT "CHK_persons_visibility"',
    );
    await queryRunner.query('ALTER TABLE "persons" DROP COLUMN "visibility"');
  }
}
