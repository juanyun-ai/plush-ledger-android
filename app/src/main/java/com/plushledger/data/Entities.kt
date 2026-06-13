package com.plushledger.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val SYNC_DIRTY = "DIRTY"
const val SYNC_SYNCED = "SYNCED"

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "avatar_key") val avatarKey: String = "sunny",
    @ColumnInfo(name = "phone") val phone: String? = null,
    @ColumnInfo(name = "email") val email: String? = null,
    @ColumnInfo(name = "age") val age: Int? = null,
    @ColumnInfo(name = "birth_date") val birthDate: String? = null,
    @ColumnInfo(name = "gender") val gender: String? = null,
    @ColumnInfo(name = "role") val role: String = "user",
    @ColumnInfo(name = "membership_tier") val membershipTier: String = "free",
    @ColumnInfo(name = "wechat_bound") val wechatBound: Boolean = false,
    @ColumnInfo(name = "qq_bound") val qqBound: Boolean = false,
    @ColumnInfo(name = "agreement_version") val agreementVersion: String? = null,
    @ColumnInfo(name = "agreed_at") val agreedAt: Long? = null,
    @ColumnInfo(name = "currency") val currency: String = "CNY",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_state") val syncState: String = SYNC_DIRTY
)

@Entity(
    tableName = "books",
    indices = [Index("user_id")]
)
data class BookEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "currency") val currency: String = "CNY",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "sync_state") val syncState: String = SYNC_DIRTY
)

@Entity(
    tableName = "accounts",
    indices = [Index("user_id"), Index("book_id")]
)
data class AccountEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "color_hex") val colorHex: String,
    @ColumnInfo(name = "initial_balance_minor") val initialBalanceMinor: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "sync_state") val syncState: String = SYNC_DIRTY
)

@Entity(
    tableName = "categories",
    indices = [Index("user_id"), Index("book_id"), Index("kind")]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "color_hex") val colorHex: String,
    @ColumnInfo(name = "icon") val icon: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "sync_state") val syncState: String = SYNC_DIRTY
)

@Entity(
    tableName = "transactions",
    indices = [
        Index("user_id"),
        Index("book_id"),
        Index("category_id"),
        Index("account_id"),
        Index("occurred_at")
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency") val currency: String = "CNY",
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "to_account_id") val toAccountId: String? = null,
    @ColumnInfo(name = "note") val note: String = "",
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "sync_state") val syncState: String = SYNC_DIRTY
)

@Entity(
    tableName = "budgets",
    indices = [Index("user_id"), Index("book_id"), Index("month"), Index("category_id")]
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "month") val month: String,
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    @ColumnInfo(name = "limit_minor") val limitMinor: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "sync_state") val syncState: String = SYNC_DIRTY
)
