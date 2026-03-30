package com.demo.butler_voice_app.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.demo.butler_voice_app.MainActivity
import com.demo.butler_voice_app.api.SmartReorderManager
import com.demo.butler_voice_app.api.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ProactiveButlerWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val userId = UserSessionManager.currentUserId() ?: return@withContext Result.success()
            val name   = UserSessionManager.currentProfile?.full_name
                ?.split(" ")?.first() ?: "there"

            val suggestions = SmartReorderManager.getSuggestions(userId)
            if (suggestions.isEmpty()) return@withContext Result.success()

            // Build the proactive message
            val topItem  = suggestions.first()
            val message  = buildProactiveMessage(name, topItem.productName, suggestions.size)

            // Store the message so MainActivity can speak it on launch
            context.getSharedPreferences("butler_proactive", Context.MODE_PRIVATE)
                .edit()
                .putString("pending_message", message)
                .putString("pending_product", topItem.productName)
                .putInt("pending_qty", topItem.avgQty)
                .putLong("message_time", System.currentTimeMillis())
                .apply()

            fireNotification(context, name, topItem.productName)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun buildProactiveMessage(name: String, product: String, totalCount: Int): String {
        val extras = if (totalCount > 1) " aur ${totalCount - 1} aur items" else ""
        return listOf(
            "$name bhai, $product khatam hone wala hai. mangwaoon?",
            "Good morning $name! $product order karna hai aaj?$extras",
            "$name, aapka usual $product — order kar doon?",
            "Haan $name, $product low hai. abhi mangwaoon?"
        ).random()
    }

    private fun fireNotification(context: Context, name: String, product: String) {
        val channelId = "butler_proactive"
        val manager   = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Butler Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Proactive order reminders from Butler"
                    enableVibration(true)
                }
            )
        }

        // Intent that opens MainActivity with proactive flag
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("proactive_launch", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Butler — $name bhai!")
            .setContentText("$product order karna hai aaj?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(1001, notification)
    }

    companion object {
        // Call this once from MainActivity.onCreate to schedule the daily worker
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Run once a day, starting at next 8am
            val request = PeriodicWorkRequestBuilder<ProactiveButlerWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(computeDelayUntil8am(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "proactive_butler",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        private fun computeDelayUntil8am(): Long {
            val now     = java.util.Calendar.getInstance()
            val target  = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 8)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                if (before(now)) add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }
}