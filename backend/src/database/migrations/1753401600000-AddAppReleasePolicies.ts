import { MigrationInterface, QueryRunner } from 'typeorm';

export class AddAppReleasePolicies1753401600000 implements MigrationInterface {
  name = 'AddAppReleasePolicies1753401600000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      CREATE TABLE "app_release_policies" (
        "channel" text NOT NULL,
        "minimumSupportedVersionCode" integer NOT NULL,
        "latestVersionCode" integer NOT NULL,
        "apiContractVersion" integer NOT NULL,
        "enforcementEnabled" boolean NOT NULL DEFAULT false,
        "updateUrl" text,
        "message" text,
        "updatedByUserId" uuid,
        "updatedAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "PK_app_release_policies" PRIMARY KEY ("channel"),
        CONSTRAINT "CHK_app_release_policies_channel"
          CHECK ("channel" IN ('DEBUG', 'PILOT', 'PRODUCTION')),
        CONSTRAINT "CHK_app_release_policies_versions"
          CHECK ("minimumSupportedVersionCode" > 0
            AND "latestVersionCode" >= "minimumSupportedVersionCode"
            AND "apiContractVersion" > 0),
        CONSTRAINT "FK_app_release_policies_updated_by"
          FOREIGN KEY ("updatedByUserId") REFERENCES "users" ("userId")
          ON DELETE SET NULL
      )
    `);
    await queryRunner.query(`
      CREATE TABLE "app_release_policy_audit" (
        "auditId" uuid NOT NULL DEFAULT gen_random_uuid(),
        "channel" text NOT NULL,
        "actorUserId" uuid NOT NULL,
        "beforeJson" text,
        "afterJson" text NOT NULL,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "PK_app_release_policy_audit" PRIMARY KEY ("auditId"),
        CONSTRAINT "CHK_app_release_policy_audit_channel"
          CHECK ("channel" IN ('DEBUG', 'PILOT', 'PRODUCTION')),
        CONSTRAINT "FK_app_release_policy_audit_actor"
          FOREIGN KEY ("actorUserId") REFERENCES "users" ("userId")
          ON DELETE RESTRICT
      )
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query('DROP TABLE "app_release_policy_audit"');
    await queryRunner.query('DROP TABLE "app_release_policies"');
  }
}
