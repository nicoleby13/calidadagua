package com.example.calidadagua

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "WATER_QUALITY_ALERTS"
        private const val CHANNEL_NAME = "Alertas de calidad del agua"
        private const val CHANNEL_DESCRIPTION = "Notificaciones para alertas de calidad del agua"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS_NAME = "notification_prefs"
        private const val KEY_LAST_ALERT_HASH = "last_alert_hash"
        private const val KEY_LAST_NOTIFICATION_TIME = "last_notification_time"
        private const val REPEAT_INTERVAL_MS = 5 * 60 * 1000L // 5 minutos
    }

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendWaterQualityAlert(title: String, message: String, isCritical: Boolean = false, forceNotify: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        val alertHash = "${title}_${message}".hashCode()
        
        // Verificar si debemos enviar la notificación
        if (!forceNotify && !shouldSendNotification(alertHash, currentTime)) {
            return
        }

        // Intent para abrir la app cuando se toque la notificación
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Configurar el ícono y prioridad según la criticidad
        val iconRes = if (isCritical) {
            android.R.drawable.stat_notify_error
        } else {
            android.R.drawable.stat_notify_more
        }

        val priority = if (isCritical) {
            NotificationCompat.PRIORITY_MAX
        } else {
            NotificationCompat.PRIORITY_HIGH
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(if (isCritical) longArrayOf(0, 500, 250, 500) else longArrayOf(0, 250))

        // Color para alertas críticas
        if (isCritical) {
            builder.setColor(0xFFFF0000.toInt()) // Rojo
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            
            // Guardar información de la última notificación
            saveLastNotification(alertHash, currentTime)
            
        } catch (e: SecurityException) {
            // Si no hay permisos, no hacer nada
            android.util.Log.w("NotificationHelper", "No permission to send notifications", e)
        }
    }

    private fun shouldSendNotification(alertHash: Int, currentTime: Long): Boolean {
        val lastAlertHash = sharedPrefs.getInt(KEY_LAST_ALERT_HASH, -1)
        val lastNotificationTime = sharedPrefs.getLong(KEY_LAST_NOTIFICATION_TIME, 0)
        
        // Si es una nueva alerta, enviar inmediatamente
        if (lastAlertHash != alertHash) {
            return true
        }
        
        // Si es la misma alerta, verificar si han pasado 5 minutos
        return (currentTime - lastNotificationTime) >= REPEAT_INTERVAL_MS
    }

    private fun saveLastNotification(alertHash: Int, time: Long) {
        sharedPrefs.edit()
            .putInt(KEY_LAST_ALERT_HASH, alertHash)
            .putLong(KEY_LAST_NOTIFICATION_TIME, time)
            .apply()
    }

    fun sendCriticalAlert(message: String) {
        sendWaterQualityAlert("⚠️ ALERTA CRÍTICA", message, true)
    }

    fun sendWarningAlert(message: String) {
        sendWaterQualityAlert("⚡ Advertencia", message, false)
    }

    fun startRecurringNotifications(title: String, message: String, isCritical: Boolean) {
        stopRecurringNotifications() // Detener cualquier notificación recurrente anterior
        
        repeatRunnable = object : Runnable {
            override fun run() {
                // Enviar notificación cada 5 minutos mientras haya alertas activas
                sendWaterQualityAlert(title, message, isCritical, forceNotify = true)
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
        
        // Iniciar inmediatamente y luego repetir
        sendWaterQualityAlert(title, message, isCritical, forceNotify = true)
        handler.postDelayed(repeatRunnable!!, REPEAT_INTERVAL_MS)
    }

    fun stopRecurringNotifications() {
        repeatRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
            repeatRunnable = null
        }
    }

    fun clearAlerts() {
        // Limpiar las alertas guardadas para permitir nuevas notificaciones inmediatas
        sharedPrefs.edit()
            .remove(KEY_LAST_ALERT_HASH)
            .remove(KEY_LAST_NOTIFICATION_TIME)
            .apply()
        
        // Detener notificaciones recurrentes
        stopRecurringNotifications()
        
        // Cancelar notificación activa
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (e: SecurityException) {
            android.util.Log.w("NotificationHelper", "No permission to cancel notifications", e)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
}