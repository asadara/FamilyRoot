package com.example.familytreeplatform.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE persons ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS offline_mutations (
                mutationId TEXT NOT NULL PRIMARY KEY,
                spaceId TEXT NOT NULL,
                personId TEXT NOT NULL,
                mutationType TEXT NOT NULL,
                payloadJson TEXT NOT NULL,
                baseVersion INTEGER NOT NULL,
                status TEXT NOT NULL,
                attemptCount INTEGER NOT NULL,
                lastError TEXT,
                conflictVersion INTEGER,
                conflictPayloadJson TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_mutations_spaceId ON offline_mutations(spaceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_mutations_personId ON offline_mutations(personId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_mutations_status ON offline_mutations(status)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE persons ADD COLUMN birthPlace TEXT")
        db.execSQL("ALTER TABLE persons ADD COLUMN notes TEXT")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS relationships (
                relationshipId TEXT NOT NULL PRIMARY KEY,
                spaceId TEXT NOT NULL,
                type TEXT NOT NULL,
                fromPersonId TEXT NOT NULL,
                toPersonId TEXT NOT NULL,
                meta TEXT,
                startDate TEXT,
                endDate TEXT,
                createdAt TEXT NOT NULL,
                pendingMutationId TEXT
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_relationships_spaceId ON relationships(spaceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_relationships_fromPersonId ON relationships(fromPersonId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_relationships_toPersonId ON relationships(toPersonId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_relationships_pendingMutationId ON relationships(pendingMutationId)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE persons ADD COLUMN nickName TEXT")
        db.execSQL("ALTER TABLE persons ADD COLUMN deathPlace TEXT")
    }
}
