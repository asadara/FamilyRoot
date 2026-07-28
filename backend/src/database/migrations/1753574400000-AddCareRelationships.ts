import { MigrationInterface, QueryRunner } from 'typeorm';

export class AddCareRelationships1753574400000 implements MigrationInterface {
  name = 'AddCareRelationships1753574400000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      'ALTER TABLE "relationships" ADD COLUMN "careContext" text',
    );
    await queryRunner.query(
      'ALTER TABLE "relationships" DROP CONSTRAINT "UQ_relationships_identity"',
    );
    await queryRunner.query(
      'ALTER TABLE "relationships" DROP CONSTRAINT "CHK_relationships_meta"',
    );
    await queryRunner.query(`
      UPDATE "relationships"
      SET "meta" = CASE
        WHEN "type" = 'PARENT_CHILD' THEN 'BIOLOGICAL'
        WHEN "type" = 'SPOUSE' THEN 'MARRIED'
        ELSE "meta"
      END
      WHERE "meta" IS NULL
    `);
    await queryRunner.query(`
      ALTER TABLE "relationships"
      ADD CONSTRAINT "CHK_relationships_meta" CHECK (
        (
          "type" = 'PARENT_CHILD' AND
          (
            (
              "meta" IN ('BIOLOGICAL', 'ADOPTIVE', 'STEP') AND
              "careContext" IS NULL
            ) OR
            "meta" IN ('FOSTER', 'GUARDIAN')
          )
        ) OR (
          "type" = 'SPOUSE' AND
          "meta" IN ('MARRIED', 'DIVORCED', 'WIDOWED') AND
          "careContext" IS NULL
        )
      )
    `);
    await queryRunner.query(`
      CREATE UNIQUE INDEX "UQ_relationships_spouse_identity"
      ON "relationships" ("spaceId", "fromPersonId", "toPersonId")
      WHERE "type" = 'SPOUSE'
    `);
    await queryRunner.query(`
      CREATE UNIQUE INDEX "UQ_relationships_parentage_identity"
      ON "relationships" ("spaceId", "fromPersonId", "toPersonId", "meta")
      WHERE "type" = 'PARENT_CHILD'
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query('DROP INDEX "UQ_relationships_parentage_identity"');
    await queryRunner.query('DROP INDEX "UQ_relationships_spouse_identity"');
    await queryRunner.query(
      'ALTER TABLE "relationships" DROP CONSTRAINT "CHK_relationships_meta"',
    );
    await queryRunner.query(`
      ALTER TABLE "relationships"
      ADD CONSTRAINT "CHK_relationships_meta" CHECK (
        "meta" IS NULL OR
        "meta" IN ('BIOLOGICAL', 'ADOPTIVE', 'STEP', 'MARRIED', 'DIVORCED', 'WIDOWED')
      )
    `);
    await queryRunner.query(`
      ALTER TABLE "relationships"
      ADD CONSTRAINT "UQ_relationships_identity"
      UNIQUE ("spaceId", "type", "fromPersonId", "toPersonId")
    `);
    await queryRunner.query(
      'ALTER TABLE "relationships" DROP COLUMN "careContext"',
    );
  }
}
