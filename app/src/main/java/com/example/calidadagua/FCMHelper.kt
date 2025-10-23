package com.example.calidadagua

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import org.json.JSONObject

/**
 * Helper para enviar notificaciones FCM de forma gratuita
 * usando Firebase Realtime Database como intermediario
 */
object FCMHelper {

    private const val TAG = "FCMHelper"
    private val database = FirebaseDatabase.getInstance().reference

    /**
     * Envía una notificación push a todos los dispositivos suscritos
     * al tópico "water_quality_alerts" escribiendo en Firebase Database.
     *
     * IMPORTANTE: Esta es la versión GRATUITA. Cualquier dispositivo que esté
     * escuchando /fcm_triggers/alerts recibirá la notificación y la enviará
     * vía FCM a todos los demás dispositivos.
     */
    fun sendAlert(
        title: String,
        message: String,
        severity: String,  // "critical" o "warning"
        parameter: String,
        value: String
    ) {
        val alertData = mapOf(
            "title" to title,
            "message" to message,
            "severity" to severity,
            "parameter" to parameter,
            "value" to value,
            "timestamp" to System.currentTimeMillis(),
            "topic" to "water_quality_alerts"
        )

        // Escribir en Firebase Database
        // Cualquier app abierta detectará este cambio y enviará la notificación FCM
        database.child("fcm_triggers").child("alerts")
            .push()
            .setValue(alertData)
            .addOnSuccessListener {
                Log.d(TAG, "Alert trigger creado exitosamente")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al crear alert trigger", e)
            }
    }

    /**
     * Limpia triggers antiguos (más de 1 hora)
     * para evitar acumulación de datos
     */
    fun cleanOldTriggers() {
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)

        database.child("fcm_triggers").child("alerts")
            .orderByChild("timestamp")
            .endAt(oneHourAgo.toDouble())
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.children.forEach { it.ref.removeValue() }
                Log.d(TAG, "Triggers antiguos eliminados")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al limpiar triggers", e)
            }
    }
}
