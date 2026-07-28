import { MigrationInterface, QueryRunner } from 'typeorm';

export class EnforceSingleSpaceOwner1753488000000 implements MigrationInterface {
  name = 'EnforceSingleSpaceOwner1753488000000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      CREATE UNIQUE INDEX "UQ_space_members_single_owner"
      ON "space_members" ("spaceId")
      WHERE "role" = 'OWNER'
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      'DROP INDEX IF EXISTS "UQ_space_members_single_owner"',
    );
  }
}
