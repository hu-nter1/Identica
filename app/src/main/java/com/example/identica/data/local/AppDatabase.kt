package com.example.identica.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.identica.data.local.dao.AdminDao
import com.example.identica.data.local.dao.AuditLogDao
import com.example.identica.data.local.dao.PersonDao
import com.example.identica.data.local.entity.Admin
import com.example.identica.data.local.entity.AuditLog
import com.example.identica.data.local.entity.RegisteredPerson

@Database(
    entities = [Admin::class, RegisteredPerson::class, AuditLog::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun adminDao(): AdminDao

    abstract fun personDao(): PersonDao

    abstract fun auditLogDao(): AuditLogDao

    companion object {
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS registered_persons_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fullName TEXT NOT NULL, age INTEGER NOT NULL, sex TEXT NOT NULL, nationalIdHash TEXT NOT NULL, nationalIdMasked TEXT NOT NULL, documentDetails TEXT NOT NULL, photoPath TEXT NOT NULL, photoHash TEXT NOT NULL, fingerprintHash TEXT, registeredByAdminId INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                database.execSQL("INSERT INTO registered_persons_new (id, fullName, age, sex, nationalIdHash, nationalIdMasked, documentDetails, photoPath, photoHash, fingerprintHash, registeredByAdminId, createdAt) SELECT id, fullName, age, sex, nationalIdHash, nationalIdMasked, documentDetails, photoPath, photoHash, fingerprintHash, registeredByAdminId, createdAt FROM registered_persons")
                database.execSQL("DROP TABLE registered_persons")
                database.execSQL("ALTER TABLE registered_persons_new RENAME TO registered_persons")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_registered_persons_nationalIdHash ON registered_persons(nationalIdHash)")
            }
        }

        // Normalizes databases produced by earlier builds while preserving existing people.
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Some early builds created the table without fingerprintHash. Add it before copying.
                database.query("PRAGMA table_info(registered_persons)").use { cursor ->
                    var hasFingerprint = false
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0 && cursor.getString(nameIndex) == "fingerprintHash") {
                            hasFingerprint = true
                            break
                        }
                    }
                    if (!hasFingerprint) {
                        database.execSQL("ALTER TABLE registered_persons ADD COLUMN fingerprintHash TEXT")
                    }
                }
                database.execSQL("CREATE TABLE IF NOT EXISTS registered_persons_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fullName TEXT NOT NULL, age INTEGER NOT NULL, sex TEXT NOT NULL, nationalIdHash TEXT NOT NULL, nationalIdMasked TEXT NOT NULL, documentDetails TEXT NOT NULL, photoPath TEXT NOT NULL, photoHash TEXT NOT NULL, fingerprintHash TEXT, registeredByAdminId INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                database.execSQL("INSERT INTO registered_persons_new (id, fullName, age, sex, nationalIdHash, nationalIdMasked, documentDetails, photoPath, photoHash, fingerprintHash, registeredByAdminId, createdAt) SELECT id, fullName, age, sex, nationalIdHash, nationalIdMasked, documentDetails, photoPath, photoHash, fingerprintHash, registeredByAdminId, createdAt FROM registered_persons")
                database.execSQL("DROP TABLE registered_persons")
                database.execSQL("ALTER TABLE registered_persons_new RENAME TO registered_persons")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_registered_persons_nationalIdHash ON registered_persons(nationalIdHash)")
            }
        }

        // Upgrades version 3 databases to version 4.
        // Version 4 uses the same RegisteredPerson schema as version 3; this
        // migration intentionally preserves all existing data.
        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // No schema change is required. Room only needs an explicit
                // forward migration so existing v3 databases can open.
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "identica.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { instance = it }
            }
        }
    }
}
