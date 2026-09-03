package com.joanpa.smsaviso.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipientDao {

    @Insert
    fun insertAll(items: List<Recipient>)

    @Query("DELETE FROM recipient")
    fun clear()

    @Query("SELECT * FROM recipient ORDER BY id")
    fun observeAll(): Flow<List<Recipient>>

    @Query("SELECT * FROM recipient WHERE status = 'PENDING' ORDER BY id LIMIT 1")
    fun nextPending(): Recipient?

    @Query("SELECT phone FROM recipient")
    fun allPhones(): List<String>

    @Query("SELECT * FROM recipient ORDER BY id")
    fun getAll(): List<Recipient>

    @Query("UPDATE recipient SET status = :status, error = :error, attempts = :attempts, updatedAt = :ts WHERE id = :id")
    fun update(id: Long, status: String, error: String?, attempts: Int, ts: Long)

    @Query("SELECT COUNT(*) FROM recipient")
    fun count(): Int

    @Query("SELECT COUNT(*) FROM recipient")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM recipient WHERE status IN ('SENT','DELIVERED')")
    fun countDone(): Int

    @Query("SELECT COUNT(*) FROM recipient WHERE status = 'FAILED'")
    fun countFailed(): Int

    @Query("SELECT COUNT(*) FROM recipient WHERE status IN ('PENDING','SENDING')")
    fun countPending(): Int

    @Query("SELECT COUNT(*) FROM recipient WHERE status = 'SKIPPED_OPTOUT'")
    fun countSkipped(): Int

    @Query("UPDATE recipient SET status = 'PENDING' WHERE status = 'SENDING'")
    fun requeueStuck()
}

@Dao
interface SendLogDao {

    @Insert
    fun insert(log: SendLog): Long

    @Query("UPDATE send_log SET status = :status, error = :error WHERE id = :id")
    fun update(id: Long, status: String, error: String?)

    @Query("SELECT COUNT(*) FROM send_log WHERE status = 'ATTEMPT' AND timestamp > :since")
    fun countAttemptsSince(since: Long): Int

    @Query("SELECT * FROM send_log ORDER BY timestamp DESC LIMIT 1000")
    fun observeRecent(): Flow<List<SendLog>>

    @Query("SELECT * FROM send_log ORDER BY timestamp DESC")
    fun getAll(): List<SendLog>

    @Query("DELETE FROM send_log")
    fun clear()
}

@Dao
interface OptOutDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(o: OptOut)

    @Query("SELECT EXISTS(SELECT 1 FROM opt_out WHERE phoneKey = :phoneKey)")
    fun isOptedOut(phoneKey: String): Boolean

    @Query("SELECT * FROM opt_out ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<OptOut>>
}
