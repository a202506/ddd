package com.buzzingmountain.dingclock.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.buzzingmountain.dingclock.data.PunchType
import com.buzzingmountain.dingclock.service.PunchForegroundService
import timber.log.Timber

/**
 * Triggered by AlarmManager. Either fires a punch (morning / evening) or asks the
 * scheduler to recompute tomorrow's alarms (when type=RESCHEDULE).
 */
class PunchAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val typeName = intent.getStringExtra(EXTRA_TYPE) ?: return
        Timber.i("Alarm fired: %s", typeName)
        when (typeName) {
            ACTION_RESCHEDULE -> PunchScheduler(context).rescheduleAll()
            else -> {
                val type = runCatching { PunchType.valueOf(typeName) }.getOrNull() ?: return
                PunchForegroundService.startWith(context, type)
                // Re-arm tomorrow's alarms after this one fires.
                PunchScheduler(context).rescheduleAll()
            }
        }
    }

    companion object {
        const val EXTRA_TYPE = "punch_type"
        const val ACTION_RESCHEDULE = "RESCHEDULE"
    }
}
