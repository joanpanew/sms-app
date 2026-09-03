package com.joanpa.smsaviso.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Insert
    fun insertGroup(group: SmsGroup): Long

    @Query("DELETE FROM sms_group WHERE id = :groupId")
    fun deleteGroup(groupId: Long)

    @Query("UPDATE sms_group SET name = :name WHERE id = :groupId")
    fun renameGroup(groupId: Long, name: String)

    @Query(
        "SELECT g.*, (SELECT COUNT(*) FROM group_member m WHERE m.groupId = g.id) AS memberCount " +
            "FROM sms_group g ORDER BY g.name COLLATE NOCASE"
    )
    fun observeGroupsWithCount(): Flow<List<GroupWithCount>>

    @Insert
    fun insertMembers(members: List<GroupMember>)

    @Query("SELECT * FROM group_member WHERE groupId = :groupId ORDER BY name COLLATE NOCASE")
    fun membersOf(groupId: Long): List<GroupMember>

    @Query("SELECT phone FROM group_member WHERE groupId = :groupId")
    fun memberPhones(groupId: Long): List<String>

    @Query("SELECT COUNT(*) FROM group_member WHERE groupId = :groupId")
    fun memberCount(groupId: Long): Int

    @Query("DELETE FROM group_member WHERE groupId = :groupId")
    fun clearMembers(groupId: Long)
}
