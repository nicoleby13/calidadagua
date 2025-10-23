package com.example.calidadagua

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var tvError: TextView
    private lateinit var tvForgotPassword: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var sharedPrefs: SharedPreferences

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicializar Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        initializeViews()
        setupClickListeners()

        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Si ya está logueado en Firebase, ir directamente a MainActivity
        if (auth.currentUser != null) {
            navigateToMain()
            return
        }
    }

    private fun initializeViews() {
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvError = findViewById(R.id.tvError)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupClickListeners() {
        btnLogin.setOnClickListener {
            validateAndLogin()
        }

        // Enter key en password field
        etPassword.setOnEditorActionListener { _, _, _ ->
            validateAndLogin()
            true
        }

        // Forgot password
        tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    private fun validateAndLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        // Limpiar error anterior
        hideError()

        // Validaciones básicas
        when {
            email.isEmpty() -> {
                showError("Por favor ingresa tu correo electrónico")
                etEmail.requestFocus()
                return
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                showError("Por favor ingresa un correo válido")
                etEmail.requestFocus()
                return
            }
            password.isEmpty() -> {
                showError("Por favor ingresa tu contraseña")
                etPassword.requestFocus()
                return
            }
            password.length < 6 -> {
                showError("La contraseña debe tener al menos 6 caracteres")
                etPassword.requestFocus()
                return
            }
        }

        // Login con Firebase
        loginWithFirebase(email, password)
    }

    private fun loginWithFirebase(email: String, password: String) {
        showLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Login exitoso, verificar si usuario está en lista blanca
                    checkIfUserIsAuthorized(email)
                } else {
                    showLoading(false)
                    // Error en autenticación - con más detalles para debug
                    val exception = task.exception
                    val errorMessage = when {
                        exception?.message?.contains("badly formatted") == true ->
                            "Formato de correo inválido"
                        exception?.message?.contains("no user record") == true ->
                            "No existe una cuenta con este correo. Verifica en Firebase Authentication."
                        exception?.message?.contains("password is invalid") == true ->
                            "Contraseña incorrecta"
                        exception?.message?.contains("INVALID_LOGIN_CREDENTIALS") == true ->
                            "Credenciales inválidas. Verifica el email y contraseña en Firebase Authentication."
                        exception?.message?.contains("wrong-password") == true ->
                            "Contraseña incorrecta"
                        exception?.message?.contains("user-not-found") == true ->
                            "Usuario no encontrado en Firebase Authentication"
                        exception?.message?.contains("temporarily disabled") == true ->
                            "Cuenta temporalmente bloqueada por múltiples intentos fallidos"
                        else -> "Error: ${exception?.message ?: "Desconocido"}"
                    }
                    showError(errorMessage)

                    // Log para debug
                    android.util.Log.e("LoginActivity", "Error de autenticación: ${exception?.message}", exception)
                }
            }
    }

    private fun checkIfUserIsAuthorized(email: String) {
        // Convertir email a key válida para Firebase (reemplazar . por ,)
        val emailKey = email.replace(".", ",")

        database.child("authorized_users").child(emailKey)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    showLoading(false)

                    val isAuthorized = snapshot.getValue(Boolean::class.java) ?: false

                    if (isAuthorized) {
                        // Usuario autorizado, guardar sesión
                        saveLoginState(email)

                        // Suscribir a tópico de alertas FCM
                        subscribeToAlertsTopic()

                        Toast.makeText(
                            this@LoginActivity,
                            "Bienvenido",
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToMain()
                    } else {
                        // Usuario no está en lista blanca, cerrar sesión
                        auth.signOut()
                        showError("Tu cuenta no tiene autorización para acceder a este sistema. Contacta al administrador.")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    showLoading(false)
                    auth.signOut()
                    showError("Error al verificar permisos: ${error.message}")
                }
            })
    }

    private fun showForgotPasswordDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Recuperar contraseña")
        builder.setMessage("Ingresa tu correo electrónico y te enviaremos un enlace para restablecer tu contraseña.")

        val input = TextInputEditText(this)
        input.hint = "Correo electrónico"
        input.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        input.setPadding(50, 30, 50, 30)

        builder.setView(input)

        builder.setPositiveButton("Enviar") { dialog, _ ->
            val email = input.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(this, "Por favor ingresa tu correo", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Correo inválido", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            sendPasswordResetEmail(email)
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    private fun sendPasswordResetEmail(email: String) {
        showLoading(true)

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                showLoading(false)
                if (task.isSuccessful) {
                    AlertDialog.Builder(this)
                        .setTitle("Correo enviado")
                        .setMessage("Se ha enviado un enlace de recuperación a $email. Revisa tu bandeja de entrada.")
                        .setPositiveButton("Entendido", null)
                        .show()
                } else {
                    val errorMessage = when {
                        task.exception?.message?.contains("no user record") == true ->
                            "No existe una cuenta con este correo"
                        else -> "Error al enviar correo: ${task.exception?.message}"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !show
        etEmail.isEnabled = !show
        etPassword.isEnabled = !show
        tvForgotPassword.isEnabled = !show
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = TextView.VISIBLE
    }

    private fun hideError() {
        tvError.visibility = TextView.GONE
    }

    private fun saveLoginState(email: String) {
        sharedPrefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USER_EMAIL, email)
            .putLong(KEY_LOGIN_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    private fun subscribeToAlertsTopic() {
        FirebaseMessaging.getInstance().subscribeToTopic("water_quality_alerts")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    android.util.Log.d("LoginActivity", "Suscrito a alertas de calidad del agua")
                } else {
                    android.util.Log.e("LoginActivity", "Error al suscribirse a alertas", task.exception)
                }
            }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Evitar volver atrás a login
    }

    companion object {
        private const val PREFS_NAME = "CalidadAguaPrefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_LOGIN_TIMESTAMP = "login_timestamp"

        fun logout(context: Context) {
            // Cerrar sesión en Firebase
            FirebaseAuth.getInstance().signOut()

            // Limpiar SharedPreferences
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPrefs.edit()
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .remove(KEY_USER_EMAIL)
                .remove(KEY_LOGIN_TIMESTAMP)
                .apply()

            // Navegar a login
            val intent = Intent(context, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            context.startActivity(intent)
        }

        fun isUserLoggedIn(context: Context): Boolean {
            // Verificar tanto Firebase como SharedPreferences
            return FirebaseAuth.getInstance().currentUser != null &&
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(KEY_IS_LOGGED_IN, false)
        }
    }
}
