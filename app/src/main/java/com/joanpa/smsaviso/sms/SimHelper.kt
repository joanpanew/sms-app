package com.joanpa.smsaviso.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

object SimHelper {

    data class Sim(val subId: Int, val label: String)

    fun activeSims(context: Context): List<Sim> {
        val list = mutableListOf(Sim(-1, "SIM por defecto del sistema"))
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return list
        return try {
            val sm = context.getSystemService(SubscriptionManager::class.java) ?: return list
            sm.activeSubscriptionInfoList?.forEach { info ->
                val name = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                    ?: info.displayName?.toString()?.takeIf { it.isNotBlank() }
                    ?: "SIM ${info.simSlotIndex + 1}"
                list.add(Sim(info.subscriptionId, "$name (ranura ${info.simSlotIndex + 1})"))
            }
            list
        } catch (e: SecurityException) {
            list
        }
    }

    @Suppress("DEPRECATION")
    fun smsManager(context: Context, subId: Int): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val base = context.getSystemService(SmsManager::class.java)
            if (subId >= 0) base.createForSubscriptionId(subId) else base
        } else {
            if (subId >= 0) SmsManager.getSmsManagerForSubscriptionId(subId)
            else SmsManager.getDefault()
        }
    }
}
