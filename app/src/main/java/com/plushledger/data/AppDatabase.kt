package com.plushledger.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProfileEntity::class,
        BookEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN avatar_key TEXT NOT NULL DEFAULT 'sunny'")
                db.execSQL("ALTER TABLE profiles ADD COLUMN role TEXT NOT NULL DEFAULT 'user'")
                db.execSQL("ALTER TABLE profiles ADD COLUMN membership_tier TEXT NOT NULL DEFAULT 'free'")
                db.execSQL("ALTER TABLE profiles ADD COLUMN wechat_bound INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE profiles ADD COLUMN qq_bound INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE profiles ADD COLUMN agreement_version TEXT")
                db.execSQL("ALTER TABLE profiles ADD COLUMN agreed_at INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN age INTEGER")
                db.execSQL("ALTER TABLE profiles ADD COLUMN birth_date TEXT")
                db.execSQL("ALTER TABLE profiles ADD COLUMN gender TEXT")
            }
        }
    }
}
