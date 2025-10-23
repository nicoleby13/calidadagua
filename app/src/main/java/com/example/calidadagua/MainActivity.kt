package com.example.calidadagua

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // ⚙️ Configuración del dispositivo
    private val deviceId = "UTEQ-01"
    private val dbUrl = "https://calidadagua-629a2-default-rtdb.firebaseio.com"

    // 📊 Modelo de lectura con ORP
    data class Reading(
        val tempC: Double? = null,
        val pH: Double? = null,
        val ec_uS: Double? = null,
        val tds_ppm: Double? = null,
        val ntu: Double? = null,
        val orp_mV: Double? = null
    )

    // 🚨 Configuración de umbrales para alertas - Valores actualizados
    data class WaterThresholds(
        val tempMin: Double = 22.0,        // Nueva especificación: 22-26.5°C rango normal
        val tempMax: Double = 26.5,        // Nueva especificación: ≤26.5°C
        val phMin: Double = 6.5,           // Mantener: 6.5-8.5
        val phMax: Double = 8.5,
        val ecMin: Double = 400.0,         // Nueva especificación: 400-2500 µS/cm
        val ecMax: Double = 2500.0,
        val tdsMax: Double = 600.0,        // Nueva especificación: ≤600 ppm (era 1000)
        val ntuIdeal: Double = 1.0,        // Nueva especificación: <1 NTU ideal
        val ntuMax: Double = 5.0,          // Nueva especificación: máximo 5 NTU (era 4)
        val orpMin: Double = 650.0,        // Nueva especificación: 650-750 mV (era 200-800)
        val orpMax: Double = 750.0
    )

    // 📱 Referencias UI - Valores
    private lateinit var tvTemp: TextView
    private lateinit var tvPH: TextView
    private lateinit var tvEC: TextView
    private lateinit var tvTDS: TextView
    private lateinit var tvNTU: TextView
    private lateinit var tvORP: TextView

    // 📱 Referencias UI - Estados
    private lateinit var tvTempStatus: TextView
    private lateinit var tvPHStatus: TextView
    private lateinit var tvECStatus: TextView
    private lateinit var tvTDSStatus: TextView
    private lateinit var tvNTUStatus: TextView
    private lateinit var tvORPStatus: TextView

    // 📊 Referencias UI - Barras de progreso
    private lateinit var progressTemp: ProgressBar
    private lateinit var progressPH: ProgressBar
    private lateinit var progressEC: ProgressBar
    private lateinit var progressTDS: ProgressBar
    private lateinit var progressNTU: ProgressBar
    private lateinit var progressORP: ProgressBar

    // 🎯 Referencias UI - Status Cards
    private lateinit var tvAlertsStatus: TextView
    private lateinit var tvAlertsCount: TextView
    private lateinit var dotAlertsStatus: View
    private lateinit var cardAlerts: MaterialCardView
    private lateinit var tvDeviceConnectionStatus: TextView
    private lateinit var dotDeviceStatus: View
    private lateinit var cardDevice: MaterialCardView

    // 📱 Referencias UI - Generales
    private lateinit var tvLastUpdate: TextView
    private lateinit var btnLogout: Button
    private lateinit var swipeRefresh: SwipeRefreshLayout

    // 🚨 Alertas actuales
    private var currentAlerts: List<String> = emptyList()

    // 🔥 Firebase
    private var valueListener: ValueEventListener? = null
    private var ref: DatabaseReference? = null
    private val thresholds = WaterThresholds()
    private lateinit var firebaseAuth: FirebaseAuth

    // 📱 Notificaciones
    private lateinit var notificationHelper: NotificationHelper
    
    // 🔔 Launcher para solicitar permisos de notificación
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Sin permisos de notificación, algunas alertas no se mostrarán", Toast.LENGTH_LONG).show()
            }
        }

    // 🕒 Control de tiempo
    private var lastUpdateTime: Long = 0
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 🔐 Verificar autenticación de Firebase antes de continuar
        firebaseAuth = FirebaseAuth.getInstance()
        if (firebaseAuth.currentUser == null) {
            // No hay usuario autenticado, volver a login
            LoginActivity.logout(this)
            return
        }

        initializeViews()
        setupClickListeners()
        setupNotifications()

        // 🔥 Usuario ya autenticado, conectar a Database
        authenticateAndStartListening()
        updateDeviceStatus(true)

        // 🔔 Escuchar triggers de FCM para enviar notificaciones a otros dispositivos
        setupFCMTriggerListener()
    }

    private fun initializeViews() {
        // Referencias a valores
        tvTemp = findViewById(R.id.tvTemp)
        tvPH = findViewById(R.id.tvPH)
        tvEC = findViewById(R.id.tvEC)
        tvTDS = findViewById(R.id.tvTDS)
        tvNTU = findViewById(R.id.tvNTU)
        tvORP = findViewById(R.id.tvORP)

        // Referencias a estados
        tvTempStatus = findViewById(R.id.tvTempStatus)
        tvPHStatus = findViewById(R.id.tvPHStatus)
        tvECStatus = findViewById(R.id.tvECStatus)
        tvTDSStatus = findViewById(R.id.tvTDSStatus)
        tvNTUStatus = findViewById(R.id.tvNTUStatus)
        tvORPStatus = findViewById(R.id.tvORPStatus)

        // Referencias a barras de progreso
        progressTemp = findViewById(R.id.progressTemp)
        progressPH = findViewById(R.id.progressPH)
        progressEC = findViewById(R.id.progressEC)
        progressTDS = findViewById(R.id.progressTDS)
        progressNTU = findViewById(R.id.progressNTU)
        progressORP = findViewById(R.id.progressORP)

        // Referencias a status cards
        tvAlertsStatus = findViewById(R.id.tvAlertsStatus)
        tvAlertsCount = findViewById(R.id.tvAlertsCount)
        dotAlertsStatus = findViewById(R.id.dotAlertsStatus)
        cardAlerts = findViewById(R.id.cardAlerts)
        tvDeviceConnectionStatus = findViewById(R.id.tvDeviceConnectionStatus)
        dotDeviceStatus = findViewById(R.id.dotDeviceStatus)
        cardDevice = findViewById(R.id.cardDevice)

        // Referencias generales
        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        btnLogout = findViewById(R.id.btnLogout)
        swipeRefresh = findViewById(R.id.swipeRefresh)
    }

    private fun setupClickListeners() {
        // SwipeRefreshLayout
        swipeRefresh.setOnRefreshListener {
            // Los datos se actualizan automáticamente vía Firebase
            // Solo mostramos feedback visual
            Toast.makeText(this, "Sincronizando...", Toast.LENGTH_SHORT).show()
            swipeRefresh.isRefreshing = false
        }

        btnLogout.setOnClickListener {
            // Cerrar sesión
            LoginActivity.logout(this)
        }

        // Click en card de alertas para mostrar detalle
        cardAlerts.setOnClickListener {
            if (currentAlerts.isNotEmpty()) {
                showAlertsDialog(currentAlerts)
            }
        }
    }

    private fun showAlertsDialog(alerts: List<String>) {
        val criticalAlerts = alerts.filter { it.contains("⚠️") }
        val warningAlerts = alerts.filter { it.contains("⚡") }

        val message = buildString {
            if (criticalAlerts.isNotEmpty()) {
                append("⚠️ CRÍTICAS (${criticalAlerts.size})\n\n")
                criticalAlerts.forEach { alert ->
                    append("• ${alert.removePrefix("⚠️ ")}\n")
                }
                if (warningAlerts.isNotEmpty()) {
                    append("\n")
                }
            }

            if (warningAlerts.isNotEmpty()) {
                append("⚡ ADVERTENCIAS (${warningAlerts.size})\n\n")
                warningAlerts.forEach { alert ->
                    append("• ${alert.removePrefix("⚡ ")}\n")
                }
            }
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Detalle de alertas")
            .setMessage(message.trim())
            .setPositiveButton("Entendido") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun setupNotifications() {
        // Inicializar NotificationHelper
        notificationHelper = NotificationHelper(this)

        // Solicitar permisos de notificación para Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun updateDeviceStatus(isConnected: Boolean) {
        if (isConnected) {
            tvDeviceConnectionStatus.text = getString(R.string.status_connected)
            tvDeviceConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_normal))
            dotDeviceStatus.setBackgroundResource(R.drawable.circle_status_dot)
            dotDeviceStatus.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.status_normal))
            cardDevice.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_status_excellent))
        } else {
            tvDeviceConnectionStatus.text = getString(R.string.status_disconnected)
            tvDeviceConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_critical))
            dotDeviceStatus.setBackgroundResource(R.drawable.circle_status_dot)
            dotDeviceStatus.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.status_critical))
            cardDevice.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_status_offline))
        }
    }

    private fun authenticateAndStartListening() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            // Usuario ya autenticado, iniciar escucha
            startListening()
        } else {
            // Autenticación anónima
            firebaseAuth.signInAnonymously()
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Autenticación exitosa, iniciar escucha
                        startListening()
                    } else {
                        // Error en autenticación
                        Toast.makeText(this, "Error de autenticación: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        updateDeviceStatus(false)
                        clearAllUIValues()
                    }
                }
        }
    }

    private fun startListening() {
        val db = FirebaseDatabase.getInstance(dbUrl)
        ref = db.getReference("readings").child(deviceId)

        valueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Obtener el último punto de datos
                val last = snapshot.children.maxByOrNull { it.key?.toLongOrNull() ?: Long.MIN_VALUE }
                val reading = last?.getValue(Reading::class.java)

                if (reading != null) {
                    updateUI(reading)
                    checkAlerts(reading)
                    lastUpdateTime = System.currentTimeMillis()
                    tvLastUpdate.text = "Última actualización: ${dateFormat.format(Date(lastUpdateTime))}"
                    updateDeviceStatus(true)
                } else {
                    // No hay datos disponibles
                    tvLastUpdate.text = "Sin datos disponibles"
                    updateDeviceStatus(false)
                    clearAllUIValues()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Error de base de datos: ${error.message}", Toast.LENGTH_LONG).show()
                updateDeviceStatus(false)
                clearAllUIValues()
            }
        }

        // Escuchar solo el último registro para eficiencia
        ref!!.orderByKey().limitToLast(1).addValueEventListener(valueListener as ValueEventListener)
    }

    private fun updateUI(reading: Reading) {
        // Actualizar valores
        tvTemp.text = reading.tempC?.let { "%.1f°C".format(it) } ?: "--°C"
        tvPH.text = reading.pH?.let { "%.2f".format(it) } ?: "--"
        tvEC.text = reading.ec_uS?.let { "%.0f µS/cm".format(it) } ?: "-- µS/cm"
        tvTDS.text = reading.tds_ppm?.let { "%.0f ppm".format(it) } ?: "-- ppm"
        tvNTU.text = reading.ntu?.let { "%.1f NTU".format(it) } ?: "-- NTU"
        tvORP.text = reading.orp_mV?.let { "%.0f mV".format(it) } ?: "-- mV"

        // Actualizar estados y barras de progreso
        updateParameterWithProgress(tvTempStatus, progressTemp, reading.tempC, thresholds.tempMin, thresholds.tempMax)
        updateParameterWithProgress(tvPHStatus, progressPH, reading.pH, thresholds.phMin, thresholds.phMax)
        updateParameterWithProgress(tvECStatus, progressEC, reading.ec_uS, thresholds.ecMin, thresholds.ecMax)
        updateParameterWithProgress(tvTDSStatus, progressTDS, reading.tds_ppm, null, thresholds.tdsMax)
        updateTurbidityWithProgress(tvNTUStatus, progressNTU, reading.ntu)
        updateParameterWithProgress(tvORPStatus, progressORP, reading.orp_mV, thresholds.orpMin, thresholds.orpMax)
    }

    private fun updateParameterWithProgress(statusView: TextView, progressBar: ProgressBar, value: Double?, min: Double?, max: Double?) {
        if (value == null) {
            statusView.text = "Sin datos"
            statusView.setTextColor(ContextCompat.getColor(this, R.color.status_offline))
            progressBar.progress = 0
            progressBar.progressTintList = ContextCompat.getColorStateList(this, R.color.status_offline)
            return
        }

        // Calcular porcentaje de progreso
        val progress = when {
            min != null && max != null -> {
                val range = max - min
                val position = value - min
                ((position / range) * 100).toInt().coerceIn(0, 100)
            }
            max != null -> {
                ((value / max) * 100).toInt().coerceIn(0, 100)
            }
            else -> 50 // Default
        }

        progressBar.progress = progress

        // Determinar estado
        val isNormal = when {
            min != null && max != null -> value >= min && value <= max
            min != null -> value >= min
            max != null -> value <= max
            else -> true
        }

        if (isNormal) {
            statusView.text = "Normal"
            statusView.setTextColor(ContextCompat.getColor(this, R.color.status_normal))
            progressBar.progressTintList = ContextCompat.getColorStateList(this, R.color.progress_excellent)
        } else {
            val isCritical = when {
                min != null && max != null -> value < (min * 0.8) || value > (max * 1.2)
                min != null -> value < (min * 0.8)
                max != null -> value > (max * 1.2)
                else -> false
            }

            if (isCritical) {
                statusView.text = "Crítico"
                statusView.setTextColor(ContextCompat.getColor(this, R.color.status_critical))
                progressBar.progressTintList = ContextCompat.getColorStateList(this, R.color.progress_critical)
            } else {
                statusView.text = "Alerta"
                statusView.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
                progressBar.progressTintList = ContextCompat.getColorStateList(this, R.color.progress_warning)
            }
        }
    }

    private fun updateTurbidityWithProgress(statusView: TextView, progressBar: ProgressBar, value: Double?) {
        if (value == null) {
            statusView.text = "Sin datos"
            statusView.setTextColor(ContextCompat.getColor(this, R.color.status_offline))
            progressBar.progress = 0
            progressBar.progressTintList = ContextCompat.getColorStateList(this, R.color.status_offline)
            return
        }

        // Para turbidez, menor es mejor
        val progress = ((1 - (value / thresholds.ntuMax)) * 100).toInt().coerceIn(0, 100)
        progressBar.progress = progress

        when {
            value < thresholds.ntuIdeal -> {
                statusView.text = "Excelente"
                statusView.setTextColor(ContextCompat.getColor(this, R.color.status_normal))
                progressBar.progressTintList = ContextCompat.getColorStateList(this, R.color.progress_excellent)
            }
            value <= thresholds.ntuMax -> {
                statusView.text = "Aceptable"
                statusView.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
                progressBar.progressTintList = ContextCompat.getColorStateList(this, R.color.progress_warning)
            }
            else -> {
                statusView.text = "Crítico"
                statusView.setTextColor(ContextCompat.getColor(this, R.color.status_critical))
                progressBar.progressTintList = ContextCompat.getColorStateList(this, R.color.progress_critical)
            }
        }
    }

    private fun updateParameterStatus(statusView: TextView, value: Double?, min: Double?, max: Double?) {
        if (value == null) {
            setStatusView(statusView, "Sin datos", R.color.status_offline, R.drawable.status_bg_offline)
            return
        }

        val isNormal = when {
            min != null && max != null -> value >= min && value <= max
            min != null -> value >= min
            max != null -> value <= max
            else -> true
        }

        if (isNormal) {
            setStatusView(statusView, "Normal", R.color.status_normal, R.drawable.status_bg_normal)
        } else {
            // Determinar si es advertencia o crítico
            val isCritical = when {
                min != null && max != null -> value < (min * 0.8) || value > (max * 1.2)
                min != null -> value < (min * 0.8)
                max != null -> value > (max * 1.2)
                else -> false
            }

            if (isCritical) {
                setStatusView(statusView, "Crítico", R.color.status_critical, R.drawable.status_bg_critical)
            } else {
                setStatusView(statusView, "Alerta", R.color.status_warning, R.drawable.status_bg_warning)
            }
        }
    }

    private fun setStatusView(statusView: TextView, text: String, textColorRes: Int, backgroundRes: Int) {
        statusView.text = text
        statusView.setTextColor(ContextCompat.getColor(this, textColorRes))
        statusView.setBackgroundResource(backgroundRes)
    }

    private fun updateTurbidityStatus(statusView: TextView, value: Double?) {
        if (value == null) {
            setStatusView(statusView, "Sin datos", R.color.status_offline, R.drawable.status_bg_offline)
            return
        }

        when {
            value < thresholds.ntuIdeal -> {
                setStatusView(statusView, "Excelente", R.color.status_normal, R.drawable.status_bg_normal)
            }
            value <= thresholds.ntuMax -> {
                setStatusView(statusView, "Aceptable", R.color.status_warning, R.drawable.status_bg_warning)
            }
            else -> {
                setStatusView(statusView, "Crítico", R.color.status_critical, R.drawable.status_bg_critical)
            }
        }
    }

    private fun checkAlerts(reading: Reading) {
        val alerts = mutableListOf<String>()

        // Verificar temperatura
        reading.tempC?.let { temp ->
            when {
                temp < thresholds.tempMin * 0.8 || temp > thresholds.tempMax * 1.2 ->
                    alerts.add("⚠️ Temperatura crítica: %.1f°C (debe estar entre 22-26.5°C)".format(temp))
                temp < thresholds.tempMin || temp > thresholds.tempMax ->
                    alerts.add("⚡ Temperatura fuera de rango: %.1f°C (debe estar entre 22-26.5°C)".format(temp))
                else -> {} // Temperatura normal
            }
        }

        // Verificar pH
        reading.pH?.let { ph ->
            when {
                ph < thresholds.phMin * 0.8 || ph > thresholds.phMax * 1.2 ->
                    alerts.add("⚠️ pH crítico: %.2f".format(ph))
                ph < thresholds.phMin || ph > thresholds.phMax ->
                    alerts.add("⚡ pH fuera de rango: %.2f".format(ph))

                else -> {}
            }
        }

        // Verificar Conductividad
        reading.ec_uS?.let { ec ->
            when {
                ec < thresholds.ecMin * 0.8 || ec > thresholds.ecMax * 1.2 ->
                    alerts.add("⚠️ Conductividad crítica: %.0f µS/cm (debe estar entre 400-2500)".format(ec))
                ec < thresholds.ecMin || ec > thresholds.ecMax ->
                    alerts.add("⚡ Conductividad fuera de rango: %.0f µS/cm (debe estar entre 400-2500)".format(ec))
                else -> {} // Conductividad normal
            }
        }

        // Verificar TDS
        reading.tds_ppm?.let { tds ->
            when {
                tds > thresholds.tdsMax * 1.2 ->
                    alerts.add("⚠️ TDS crítico: %.0f ppm (debe ser ≤600)".format(tds))
                tds > thresholds.tdsMax ->
                    alerts.add("⚡ TDS elevado: %.0f ppm (debe ser ≤600)".format(tds))
                else -> {} // TDS normal
            }
        }

        // Verificar Turbidez
        reading.ntu?.let { ntu ->
            when {
                ntu > thresholds.ntuMax ->
                    alerts.add("⚠️ Turbidez crítica: %.1f NTU (máximo permitido: 5 NTU)".format(ntu))
                ntu >= thresholds.ntuIdeal ->
                    alerts.add("⚡ Turbidez elevada: %.1f NTU (ideal: <1 NTU)".format(ntu))
                else -> {} // Turbidez excelente
            }
        }

        // Verificar ORP
        reading.orp_mV?.let { orp ->
            when {
                orp < thresholds.orpMin * 0.8 || orp > thresholds.orpMax * 1.2 ->
                    alerts.add("⚠️ ORP crítico: %.0f mV (debe estar entre 650-750)".format(orp))
                orp < thresholds.orpMin || orp > thresholds.orpMax ->
                    alerts.add("⚡ ORP fuera de rango: %.0f mV (debe estar entre 650-750)".format(orp))
                else -> {} // ORP normal
            }
        }

        // Mostrar resumen de alertas
        updateAlertSummary(alerts)
    }

    private fun updateAlertSummary(alerts: List<String>) {
        // Guardar alertas actuales para el diálogo
        currentAlerts = alerts

        // Actualizar card de estado de alertas
        if (alerts.isEmpty()) {
            tvAlertsStatus.text = getString(R.string.no_alerts)
            tvAlertsStatus.setTextColor(ContextCompat.getColor(this, R.color.status_normal))
            tvAlertsCount.text = "0 activas"
            dotAlertsStatus.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.status_normal))
            cardAlerts.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_status_excellent))
        } else {
            // Determinar el color según la severidad
            val hasCritical = alerts.any { it.contains("⚠️") }

            // Actualizar status card de alertas
            tvAlertsStatus.text = if (hasCritical) "Críticas" else "Advertencias"
            tvAlertsStatus.setTextColor(ContextCompat.getColor(this,
                if (hasCritical) R.color.status_critical else R.color.status_warning))
            tvAlertsCount.text = "${alerts.size} activa(s)"
            dotAlertsStatus.setBackgroundTintList(ContextCompat.getColorStateList(this,
                if (hasCritical) R.color.status_critical else R.color.status_warning))
            cardAlerts.setCardBackgroundColor(ContextCompat.getColor(this,
                if (hasCritical) R.color.card_status_critical else R.color.card_status_warning))

            // 🔔 Enviar notificaciones locales Y FCM
            sendNotificationsForAlerts(alerts, hasCritical)

            // Notificación toast para alertas críticas
            if (hasCritical) {
                Toast.makeText(this, "¡ALERTA CRÍTICA! Revise los parámetros del agua", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendNotificationsForAlerts(alerts: List<String>, hasCritical: Boolean) {
        if (!::notificationHelper.isInitialized) return

        if (alerts.isEmpty()) {
            // Si no hay alertas, limpiar notificaciones recurrentes
            notificationHelper.clearAlerts()
            return
        }

        // Preparar mensaje para notificación
        val message = when {
            alerts.size == 1 -> alerts[0].removePrefix("⚠️ ").removePrefix("⚡ ")
            alerts.size <= 3 -> alerts.joinToString(", ") { 
                it.removePrefix("⚠️ ").removePrefix("⚡ ") 
            }
            else -> "Múltiples parámetros fuera de rango (${alerts.size} alertas)"
        }

        // Determinar el título según criticidad
        val title = if (hasCritical) "⚠️ ALERTA CRÍTICA" else "⚡ Advertencia"

        // Iniciar notificaciones recurrentes cada 5 minutos
        // Esto enviará la primera notificación inmediatamente y luego cada 5 minutos
        notificationHelper.startRecurringNotifications(title, message, hasCritical)

        // 🔔 Enviar notificación FCM a TODOS los dispositivos (incluso cerrados)
        sendFCMAlert(alerts, hasCritical)
    }

    private fun sendFCMAlert(alerts: List<String>, hasCritical: Boolean) {
        // Obtener el parámetro más crítico
        val criticalAlert = alerts.firstOrNull { it.contains("⚠️") } ?: alerts.firstOrNull()
        if (criticalAlert == null) return

        // Extraer información del alerta
        val alertText = criticalAlert.removePrefix("⚠️ ").removePrefix("⚡ ")
        val parameter = alertText.split(":").firstOrNull() ?: "Parámetro"
        val value = alertText.split(":").getOrNull(1)?.trim() ?: "N/A"

        val title = if (hasCritical) "⚠️ ALERTA CRÍTICA - Calidad del agua" else "⚡ Advertencia - Calidad del agua"
        val message = if (alerts.size == 1) {
            alertText
        } else {
            "$alertText (+${alerts.size - 1} alerta(s) más)"
        }

        val severity = if (hasCritical) "critical" else "warning"

        // Enviar a través del helper (escribe en Firebase Database)
        FCMHelper.sendAlert(title, message, severity, parameter, value)
    }

    /**
     * Escucha cambios en /fcm_triggers/alerts para enviar notificaciones FCM
     * Este listener solo funciona cuando la app está abierta.
     * Cuando detecta un trigger, envía la notificación FCM a TODOS los dispositivos.
     */
    private fun setupFCMTriggerListener() {
        val fcmTriggersRef = FirebaseDatabase.getInstance().reference
            .child("fcm_triggers").child("alerts")

        fcmTriggersRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val alertData = snapshot.value as? Map<String, Any> ?: return

                val title = alertData["title"] as? String ?: return
                val message = alertData["message"] as? String ?: return
                val severity = alertData["severity"] as? String ?: "warning"
                val parameter = alertData["parameter"] as? String ?: ""
                val value = alertData["value"] as? String ?: ""
                val timestamp = (alertData["timestamp"] as? Long) ?: 0L

                // Evitar procesar triggers muy antiguos (más de 5 minutos)
                val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
                if (timestamp < fiveMinutesAgo) {
                    return
                }

                // TODO: Aquí deberías enviar la notificación FCM real
                // Como estamos en Plan Spark (gratis), no podemos usar el Admin SDK
                // desde la app directamente. La notificación FCM se maneja automáticamente
                // por MyFirebaseMessagingService cuando el mensaje llega.

                // Por ahora, solo logueamos
                android.util.Log.d("MainActivity", "FCM Trigger detectado: $title - $message")

                // Limpiar este trigger después de procesarlo (opcional)
                snapshot.ref.removeValue()
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("MainActivity", "Error en FCM trigger listener", error.toException())
            }
        })

        // Limpiar triggers antiguos al inicio
        FCMHelper.cleanOldTriggers()
    }


    private fun clearAllUIValues() {
        // Mostrar dashes cuando el dispositivo esté desconectado
        tvTemp.text = "---"
        tvPH.text = "---"
        tvEC.text = "---"
        tvTDS.text = "---"
        tvNTU.text = "---"
        tvORP.text = "---"
        
        // Actualizar todos los estados a "Sin datos"
        setStatusView(tvTempStatus, "Sin datos", R.color.status_offline, R.drawable.status_bg_offline)
        setStatusView(tvPHStatus, "Sin datos", R.color.status_offline, R.drawable.status_bg_offline)
        setStatusView(tvECStatus, "Sin datos", R.color.status_offline, R.drawable.status_bg_offline)
        setStatusView(tvTDSStatus, "Sin datos", R.color.status_offline, R.drawable.status_bg_offline)
        setStatusView(tvNTUStatus, "Sin datos", R.color.status_offline, R.drawable.status_bg_offline)
        setStatusView(tvORPStatus, "Sin datos", R.color.status_offline, R.drawable.status_bg_offline)

        // Limpiar alertas
        currentAlerts = emptyList()
    }

    override fun onDestroy() {
        super.onDestroy()
        valueListener?.let { ref?.removeEventListener(it) }
        
        // Limpiar notificaciones recurrentes cuando se cierre la app
        if (::notificationHelper.isInitialized) {
            notificationHelper.stopRecurringNotifications()
        }
    }

    override fun onResume() {
        super.onResume()
        // Verificar si han pasado más de 2 minutos sin actualización
        if (lastUpdateTime > 0 && System.currentTimeMillis() - lastUpdateTime > 120000) {
            updateDeviceStatus(false)
            clearAllUIValues()
            Toast.makeText(this, "Sin actualizaciones recientes del dispositivo", Toast.LENGTH_SHORT).show()
        }
    }
}