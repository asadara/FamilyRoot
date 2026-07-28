import { MigrationInterface, QueryRunner } from 'typeorm';

export class AddUserNotifications1754438400000 implements MigrationInterface {
  name = 'AddUserNotifications1754438400000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      CREATE TABLE "user_notifications" (
        "notificationId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "userId" uuid NOT NULL,
        "spaceId" uuid,
        "kind" text NOT NULL,
        "code" text NOT NULL,
        "title" text NOT NULL,
        "message" text NOT NULL,
        "readAt" timestamptz,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "FK_user_notifications_user"
          FOREIGN KEY ("userId")
          REFERENCES "users" ("userId")
          ON DELETE CASCADE,
        CONSTRAINT "CHK_user_notifications_kind"
          CHECK ("kind" IN ('SUCCESS', 'WARNING', 'ERROR', 'INFO')),
        CONSTRAINT "CHK_user_notifications_copy"
          CHECK (
            char_length("code") BETWEEN 1 AND 80
            AND char_length("title") BETWEEN 1 AND 160
            AND char_length("message") BETWEEN 1 AND 500
          )
      )
    `);
    await queryRunner.query(`
      CREATE INDEX "IDX_user_notifications_user_created"
      ON "user_notifications" ("userId", "createdAt" DESC)
    `);
    await queryRunner.query(`
      CREATE INDEX "IDX_user_notifications_user_unread"
      ON "user_notifications" ("userId")
      WHERE "readAt" IS NULL
    `);
    await queryRunner.query(
      'ALTER TABLE public."user_notifications" ENABLE ROW LEVEL SECURITY',
    );
    await queryRunner.query(
      'REVOKE ALL PRIVILEGES ON TABLE public."user_notifications" FROM PUBLIC',
    );
    await queryRunner.query(`
      DO $familyroot$
      BEGIN
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
          REVOKE ALL PRIVILEGES
            ON TABLE public."user_notifications"
            FROM anon;
        END IF;
        IF EXISTS (
          SELECT 1 FROM pg_roles WHERE rolname = 'authenticated'
        ) THEN
          REVOKE ALL PRIVILEGES
            ON TABLE public."user_notifications"
            FROM authenticated;
        END IF;
      END
      $familyroot$
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query('DROP INDEX "IDX_user_notifications_user_unread"');
    await queryRunner.query('DROP INDEX "IDX_user_notifications_user_created"');
    await queryRunner.query('DROP TABLE "user_notifications"');
  }
}
