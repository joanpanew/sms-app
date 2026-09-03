package com.joanpa.smsaviso.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Recipient::class,
        SendLog::class,
        OptOut::class,
        SmsGroup::class,
        GroupMember::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recipientDao(): RecipientDao
    abstract fun sendLogDao(): SendLogDao
    abstract fun optOutDao(): OptOutDao
    abstract fun groupDao(): GroupDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "smsaviso.db"
            )
                // El servicio y los BroadcastReceiver de estado hacen escrituras
                // muy puntuales (1 SMS cada 15 s o más); permitir el hilo principal
                // simplifica el código sin impacto real de rendimiento.
                .allowMainThreadQueries()
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }
}
