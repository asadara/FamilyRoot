import { MigrationInterface, QueryRunner } from 'typeorm';

export class AddGoogleIdentity1753056000000 implements MigrationInterface {
  name = 'AddGoogleIdentity1753056000000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      CREATE TABLE "user_google_identities" (
        "googleSubject" text PRIMARY KEY,
        "userId" uuid NOT NULL UNIQUE REFERENCES "users" ("userId") ON DELETE CASCADE,
        "emailAtLink" text NOT NULL,
        "createdAt" timestamptz NOT NULL DEFAULT now()
      )
    `);
    await queryRunner.query(
      'ALTER TABLE "user_google_identities" ENABLE ROW LEVEL SECURITY',
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query('DROP TABLE IF EXISTS "user_google_identities"');
  }
}
