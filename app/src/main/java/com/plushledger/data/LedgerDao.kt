package com.plushledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBook(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccounts(accounts: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(transactions: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudgets(budgets: List<BudgetEntity>)

    @Query("SELECT * FROM profiles WHERE id = :userId")
    suspend fun getProfile(userId: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE id = :userId")
    fun observeProfile(userId: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM books WHERE user_id = :userId AND deleted_at IS NULL ORDER BY created_at LIMIT 1")
    suspend fun getDefaultBook(userId: String): BookEntity?

    @Query("SELECT COUNT(*) FROM categories WHERE user_id = :userId AND deleted_at IS NULL")
    suspend fun categoryCount(userId: String): Int

    @Query("SELECT COUNT(*) FROM accounts WHERE user_id = :userId AND deleted_at IS NULL")
    suspend fun accountCount(userId: String): Int

    @Query("SELECT * FROM books WHERE user_id = :userId AND deleted_at IS NULL ORDER BY created_at")
    fun observeBooks(userId: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM accounts WHERE user_id = :userId AND deleted_at IS NULL ORDER BY created_at")
    fun observeAccounts(userId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE user_id = :userId AND deleted_at IS NULL ORDER BY created_at")
    suspend fun accountsSnapshot(userId: String): List<AccountEntity>

    @Query("SELECT * FROM categories WHERE user_id = :userId AND deleted_at IS NULL ORDER BY kind, sort_order")
    fun observeCategories(userId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND deleted_at IS NULL ORDER BY occurred_at DESC, created_at DESC")
    fun observeTransactions(userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND month = :month AND deleted_at IS NULL ORDER BY created_at")
    fun observeBudgets(userId: String, month: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND deleted_at IS NULL ORDER BY occurred_at DESC, created_at DESC")
    suspend fun transactionsSnapshot(userId: String): List<TransactionEntity>

    @Query("UPDATE transactions SET deleted_at = :now, updated_at = :now, sync_state = :syncState WHERE id = :id AND user_id = :userId")
    suspend fun softDeleteTransaction(id: String, userId: String, now: Long, syncState: String = SYNC_DIRTY)

    @Query("UPDATE accounts SET deleted_at = :now, updated_at = :now, sync_state = :syncState WHERE id = :id AND user_id = :userId")
    suspend fun softDeleteAccount(id: String, userId: String, now: Long, syncState: String = SYNC_DIRTY)

    @Query("UPDATE categories SET deleted_at = :now, updated_at = :now, sync_state = :syncState WHERE id = :id AND user_id = :userId")
    suspend fun softDeleteCategory(id: String, userId: String, now: Long, syncState: String = SYNC_DIRTY)

    @Query("DELETE FROM profiles WHERE id = :userId")
    suspend fun deleteLocalProfile(userId: String)

    @Query("DELETE FROM books WHERE user_id = :userId")
    suspend fun deleteLocalBooks(userId: String)

    @Query("DELETE FROM accounts WHERE user_id = :userId")
    suspend fun deleteLocalAccounts(userId: String)

    @Query("DELETE FROM categories WHERE user_id = :userId")
    suspend fun deleteLocalCategories(userId: String)

    @Query("DELETE FROM transactions WHERE user_id = :userId")
    suspend fun deleteLocalTransactions(userId: String)

    @Query("DELETE FROM budgets WHERE user_id = :userId")
    suspend fun deleteLocalBudgets(userId: String)

    @Query("SELECT * FROM profiles WHERE id = :userId AND sync_state != :synced")
    suspend fun dirtyProfiles(userId: String, synced: String = SYNC_SYNCED): List<ProfileEntity>

    @Query("SELECT * FROM books WHERE user_id = :userId AND sync_state != :synced")
    suspend fun dirtyBooks(userId: String, synced: String = SYNC_SYNCED): List<BookEntity>

    @Query("SELECT * FROM accounts WHERE user_id = :userId AND sync_state != :synced")
    suspend fun dirtyAccounts(userId: String, synced: String = SYNC_SYNCED): List<AccountEntity>

    @Query("SELECT * FROM categories WHERE user_id = :userId AND sync_state != :synced")
    suspend fun dirtyCategories(userId: String, synced: String = SYNC_SYNCED): List<CategoryEntity>

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND sync_state != :synced")
    suspend fun dirtyTransactions(userId: String, synced: String = SYNC_SYNCED): List<TransactionEntity>

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND sync_state != :synced")
    suspend fun dirtyBudgets(userId: String, synced: String = SYNC_SYNCED): List<BudgetEntity>

    @Query("UPDATE profiles SET sync_state = :synced WHERE id IN (:ids)")
    suspend fun markProfilesSynced(ids: List<String>, synced: String = SYNC_SYNCED)

    @Query("UPDATE books SET sync_state = :synced WHERE id IN (:ids)")
    suspend fun markBooksSynced(ids: List<String>, synced: String = SYNC_SYNCED)

    @Query("UPDATE accounts SET sync_state = :synced WHERE id IN (:ids)")
    suspend fun markAccountsSynced(ids: List<String>, synced: String = SYNC_SYNCED)

    @Query("UPDATE categories SET sync_state = :synced WHERE id IN (:ids)")
    suspend fun markCategoriesSynced(ids: List<String>, synced: String = SYNC_SYNCED)

    @Query("UPDATE transactions SET sync_state = :synced WHERE id IN (:ids)")
    suspend fun markTransactionsSynced(ids: List<String>, synced: String = SYNC_SYNCED)

    @Query("UPDATE budgets SET sync_state = :synced WHERE id IN (:ids)")
    suspend fun markBudgetsSynced(ids: List<String>, synced: String = SYNC_SYNCED)
}
