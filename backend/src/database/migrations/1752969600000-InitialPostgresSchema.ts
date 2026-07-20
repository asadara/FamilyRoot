import { MigrationInterface, QueryRunner } from 'typeorm';

export class InitialPostgresSchema1752969600000 implements MigrationInterface {
  name = 'InitialPostgresSchema1752969600000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      CREATE TABLE "users" (
        "userId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "email" text UNIQUE,
        "phone" text UNIQUE,
        "displayName" text NOT NULL,
        "passwordHash" text,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        "updatedAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "CHK_users_login" CHECK ("email" IS NOT NULL OR "phone" IS NOT NULL)
      )
    `);

    await queryRunner.query(`
      CREATE TABLE "family_spaces" (
        "spaceId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "name" text NOT NULL,
        "createdBy" uuid NOT NULL REFERENCES "users" ("userId") ON DELETE RESTRICT,
        "createdAt" timestamptz NOT NULL DEFAULT now()
      )
    `);

    await queryRunner.query(`
      CREATE TABLE "space_members" (
        "memberId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "spaceId" uuid NOT NULL REFERENCES "family_spaces" ("spaceId") ON DELETE CASCADE,
        "userId" uuid NOT NULL REFERENCES "users" ("userId") ON DELETE RESTRICT,
        "role" text NOT NULL,
        "joinedAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "UQ_space_members_space_user" UNIQUE ("spaceId", "userId"),
        CONSTRAINT "CHK_space_members_role" CHECK ("role" IN ('OWNER', 'ADMIN', 'EDITOR', 'VIEWER'))
      )
    `);

    await queryRunner.query(`
      CREATE TABLE "persons" (
        "personId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "spaceId" uuid NOT NULL REFERENCES "family_spaces" ("spaceId") ON DELETE CASCADE,
        "fullName" text NOT NULL,
        "title" text,
        "firstName" text,
        "lastName" text,
        "suffix" text,
        "nickName" text,
        "gender" text,
        "birthDate" date,
        "birthPlace" text,
        "deathDate" date,
        "deathPlace" text,
        "idNumber" text,
        "notes" text,
        "lifeStatus" text NOT NULL DEFAULT 'ALIVE',
        "deceasedAt" date,
        "version" integer NOT NULL DEFAULT 1,
        "isDeleted" boolean NOT NULL DEFAULT false,
        "deleted_at" timestamptz,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        "updatedAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "UQ_persons_space_person" UNIQUE ("spaceId", "personId"),
        CONSTRAINT "CHK_persons_life_status" CHECK ("lifeStatus" IN ('ALIVE', 'DECEASED', 'UNKNOWN')),
        CONSTRAINT "CHK_persons_soft_delete" CHECK (
          ("isDeleted" = false AND "deleted_at" IS NULL) OR
          ("isDeleted" = true AND "deleted_at" IS NOT NULL)
        )
      )
    `);

    await queryRunner.query(`
      CREATE TABLE "relationships" (
        "relationshipId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "spaceId" uuid NOT NULL REFERENCES "family_spaces" ("spaceId") ON DELETE CASCADE,
        "type" text NOT NULL,
        "fromPersonId" uuid NOT NULL,
        "toPersonId" uuid NOT NULL,
        "meta" text,
        "startDate" text,
        "endDate" text,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "FK_relationships_from_person" FOREIGN KEY ("spaceId", "fromPersonId")
          REFERENCES "persons" ("spaceId", "personId") ON DELETE CASCADE,
        CONSTRAINT "FK_relationships_to_person" FOREIGN KEY ("spaceId", "toPersonId")
          REFERENCES "persons" ("spaceId", "personId") ON DELETE CASCADE,
        CONSTRAINT "UQ_relationships_identity" UNIQUE ("spaceId", "type", "fromPersonId", "toPersonId"),
        CONSTRAINT "CHK_relationships_type" CHECK ("type" IN ('PARENT_CHILD', 'SPOUSE')),
        CONSTRAINT "CHK_relationships_meta" CHECK (
          "meta" IS NULL OR "meta" IN ('BIOLOGICAL', 'ADOPTIVE', 'STEP', 'MARRIED', 'DIVORCED', 'WIDOWED')
        ),
        CONSTRAINT "CHK_relationships_not_self" CHECK ("fromPersonId" <> "toPersonId")
      )
    `);

    await queryRunner.query(`
      CREATE TABLE "user_person_claims" (
        "claimId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "spaceId" uuid NOT NULL REFERENCES "family_spaces" ("spaceId") ON DELETE CASCADE,
        "userId" uuid NOT NULL REFERENCES "users" ("userId") ON DELETE RESTRICT,
        "personId" uuid NOT NULL,
        "status" text NOT NULL DEFAULT 'PENDING',
        "requestedAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "FK_claims_person" FOREIGN KEY ("spaceId", "personId")
          REFERENCES "persons" ("spaceId", "personId") ON DELETE CASCADE,
        CONSTRAINT "CHK_claims_status" CHECK ("status" IN ('PENDING', 'VERIFIED', 'REJECTED'))
      )
    `);

    await queryRunner.query(`
      CREATE TABLE "change_log" (
        "changeId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "spaceId" uuid NOT NULL REFERENCES "family_spaces" ("spaceId") ON DELETE CASCADE,
        "actorUserId" uuid NOT NULL REFERENCES "users" ("userId") ON DELETE RESTRICT,
        "entityType" text NOT NULL,
        "entityId" uuid NOT NULL,
        "operation" text NOT NULL,
        "note" text,
        "beforeJson" text,
        "afterJson" text,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "CHK_change_log_entity_type" CHECK (
          "entityType" IN ('SPACE', 'MEMBERSHIP', 'INVITATION', 'PERSON', 'RELATIONSHIP', 'CLAIM', 'SOURCE', 'MEDIA', 'PROPOSAL')
        ),
        CONSTRAINT "CHK_change_log_operation" CHECK ("operation" IN ('CREATE', 'UPDATE', 'DELETE', 'VERIFY'))
      )
    `);

    await queryRunner.query(`
      CREATE TABLE "refresh_sessions" (
        "sessionId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "userId" uuid NOT NULL REFERENCES "users" ("userId") ON DELETE CASCADE,
        "familyId" uuid NOT NULL,
        "tokenHash" text NOT NULL UNIQUE,
        "expiresAt" timestamptz NOT NULL,
        "revokedAt" timestamptz,
        "replacedBySessionId" uuid REFERENCES "refresh_sessions" ("sessionId") ON DELETE SET NULL,
        "createdAt" timestamptz NOT NULL DEFAULT now()
      )
    `);

    await queryRunner.query(`
      CREATE TABLE "space_invitations" (
        "inviteId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "spaceId" uuid NOT NULL REFERENCES "family_spaces" ("spaceId") ON DELETE CASCADE,
        "token" text NOT NULL UNIQUE,
        "role" text NOT NULL,
        "createdBy" uuid NOT NULL REFERENCES "users" ("userId") ON DELETE RESTRICT,
        "expiresAt" timestamptz NOT NULL,
        "acceptedBy" uuid REFERENCES "users" ("userId") ON DELETE RESTRICT,
        "acceptedAt" timestamptz,
        "revokedAt" timestamptz,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "CHK_space_invitations_role" CHECK ("role" IN ('ADMIN', 'EDITOR', 'VIEWER'))
      )
    `);

    await queryRunner.query(`
      CREATE TABLE "fact_sources" (
        "sourceId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "spaceId" uuid NOT NULL REFERENCES "family_spaces" ("spaceId") ON DELETE CASCADE,
        "personId" uuid NOT NULL,
        "title" text NOT NULL,
        "type" text NOT NULL,
        "url" text,
        "note" text,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "UQ_fact_sources_space_source" UNIQUE ("spaceId", "sourceId"),
        CONSTRAINT "FK_fact_sources_person" FOREIGN KEY ("spaceId", "personId")
          REFERENCES "persons" ("spaceId", "personId") ON DELETE CASCADE,
        CONSTRAINT "CHK_fact_sources_type" CHECK ("type" IN ('DOCUMENT', 'STORY', 'PHOTO', 'OTHER'))
      )
    `);

    await queryRunner.query(`
      CREATE TABLE "media_items" (
        "mediaId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "spaceId" uuid NOT NULL REFERENCES "family_spaces" ("spaceId") ON DELETE CASCADE,
        "personId" uuid NOT NULL,
        "label" text NOT NULL,
        "kind" text NOT NULL,
        "uri" text NOT NULL,
        "sourceId" uuid,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "FK_media_items_person" FOREIGN KEY ("spaceId", "personId")
          REFERENCES "persons" ("spaceId", "personId") ON DELETE CASCADE,
        CONSTRAINT "FK_media_items_source" FOREIGN KEY ("spaceId", "sourceId")
          REFERENCES "fact_sources" ("spaceId", "sourceId") ON DELETE RESTRICT,
        CONSTRAINT "CHK_media_items_kind" CHECK ("kind" IN ('PHOTO', 'DOCUMENT', 'AUDIO', 'OTHER'))
      )
    `);

    await queryRunner.query(`
      CREATE TABLE "edit_proposals" (
        "proposalId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "spaceId" uuid NOT NULL REFERENCES "family_spaces" ("spaceId") ON DELETE CASCADE,
        "personId" uuid NOT NULL,
        "field" text NOT NULL,
        "proposedValue" text NOT NULL,
        "reason" text,
        "status" text NOT NULL DEFAULT 'PENDING',
        "reviewedByUserId" uuid REFERENCES "users" ("userId") ON DELETE RESTRICT,
        "reviewedAt" timestamptz,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "FK_edit_proposals_person" FOREIGN KEY ("spaceId", "personId")
          REFERENCES "persons" ("spaceId", "personId") ON DELETE CASCADE,
        CONSTRAINT "CHK_edit_proposals_field" CHECK ("field" IN ('notes', 'birthPlace', 'deathPlace')),
        CONSTRAINT "CHK_edit_proposals_status" CHECK ("status" IN ('PENDING', 'APPROVED', 'REJECTED'))
      )
    `);

    await queryRunner.query(`
      CREATE TABLE "client_mutations" (
        "clientMutationId" uuid PRIMARY KEY,
        "actorUserId" uuid NOT NULL REFERENCES "users" ("userId") ON DELETE RESTRICT,
        "spaceId" uuid NOT NULL REFERENCES "family_spaces" ("spaceId") ON DELETE CASCADE,
        "operation" text NOT NULL,
        "requestFingerprint" text NOT NULL,
        "responseJson" text NOT NULL,
        "createdAt" timestamptz NOT NULL DEFAULT now()
      )
    `);

    await queryRunner.query(
      'CREATE INDEX "IDX_persons_space_active" ON "persons" ("spaceId", "isDeleted")',
    );
    await queryRunner.query(
      'CREATE INDEX "IDX_claims_space_person" ON "user_person_claims" ("spaceId", "personId")',
    );
    await queryRunner.query(
      'CREATE INDEX "IDX_change_log_space_created" ON "change_log" ("spaceId", "createdAt")',
    );
    await queryRunner.query(
      'CREATE INDEX "IDX_refresh_sessions_family" ON "refresh_sessions" ("familyId")',
    );
    await queryRunner.query(
      'CREATE INDEX "IDX_refresh_sessions_user" ON "refresh_sessions" ("userId")',
    );
    await queryRunner.query(
      'CREATE INDEX "IDX_fact_sources_space_person" ON "fact_sources" ("spaceId", "personId")',
    );
    await queryRunner.query(
      'CREATE INDEX "IDX_media_items_space_person" ON "media_items" ("spaceId", "personId")',
    );
    await queryRunner.query(
      'CREATE INDEX "IDX_edit_proposals_space_person" ON "edit_proposals" ("spaceId", "personId")',
    );

    const applicationTables = [
      'users',
      'family_spaces',
      'space_members',
      'persons',
      'relationships',
      'user_person_claims',
      'change_log',
      'refresh_sessions',
      'space_invitations',
      'fact_sources',
      'media_items',
      'edit_proposals',
      'client_mutations',
    ];
    for (const table of applicationTables) {
      await queryRunner.query(
        `ALTER TABLE "${table}" ENABLE ROW LEVEL SECURITY`,
      );
    }
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query('DROP TABLE IF EXISTS "client_mutations"');
    await queryRunner.query('DROP TABLE IF EXISTS "edit_proposals"');
    await queryRunner.query('DROP TABLE IF EXISTS "media_items"');
    await queryRunner.query('DROP TABLE IF EXISTS "fact_sources"');
    await queryRunner.query('DROP TABLE IF EXISTS "space_invitations"');
    await queryRunner.query('DROP TABLE IF EXISTS "refresh_sessions"');
    await queryRunner.query('DROP TABLE IF EXISTS "change_log"');
    await queryRunner.query('DROP TABLE IF EXISTS "user_person_claims"');
    await queryRunner.query('DROP TABLE IF EXISTS "relationships"');
    await queryRunner.query('DROP TABLE IF EXISTS "persons"');
    await queryRunner.query('DROP TABLE IF EXISTS "space_members"');
    await queryRunner.query('DROP TABLE IF EXISTS "family_spaces"');
    await queryRunner.query('DROP TABLE IF EXISTS "users"');
  }
}
