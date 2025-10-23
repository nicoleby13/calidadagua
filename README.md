  # Sistema de monitoreo de calidad del agua

  Aplicación Android para monitoreo en tiempo real de parámetros de calidad del agua, conectada con dispositivos IoT (ESP32) a través de
  Firebase Realtime Database.

  ## Características

  ### Monitoreo en tiempo real
  - **Parámetros monitoreados:**
    - Temperatura 
    - pH 
    - Conductividad eléctrica 
    - Sólidos disueltos totales 
    - Turbidez 
    - Potencial de oxidación-reducción 

  ### Sistema de alertas
  - Notificaciones push con Firebase Cloud Messaging
  - Alertas locales recurrentes cada 5 minutos
  - Diferenciación entre advertencias y alertas críticas
  - Visualización en tiempo real del estado del sistema

  ### Interfaz moderna
  - Cards de estado del sistema (Alertas y Dispositivo)
  - Barras de progreso visuales con código de colores
  - Pull-to-refresh para sincronización
  - Material Design 3

  ### Seguridad
  - Autenticación con Firebase Authentication
  - Sistema de lista blanca de usuarios autorizados
  - Sesiones persistentes con SharedPreferences

  ## Tecnologías

  - **Lenguaje:** Kotlin
  - **SDK mínimo:** Android 8.0 (API 26)
  - **SDK objetivo:** Android 15 (API 35)
  - **Base de datos:** Firebase Realtime Database
  - **Autenticación:** Firebase Authentication
  - **Notificaciones:** Firebase Cloud Messaging (FCM)
  - **UI:** Material Design Components, GridLayout, SwipeRefreshLayout

## Dependencias principales
```kotlin
// Firebase
com.google.firebase:firebase-auth
com.google.firebase:firebase-database
com.google.firebase:firebase-messaging

// UI
com.google.android.material:material
androidx.gridlayout:gridlayout
androidx.swiperefreshlayout:swiperefreshlayout
```

## Estructura del proyecto
```
app/
├── src/main/
│   ├── java/com/example/calidadagua/
│   │   ├── MainActivity.kt           # Pantalla principal
│   │   ├── LoginActivity.kt          # Autenticación
│   │   ├── NotificationHelper.kt     # Notificaciones locales
│   │   ├── FCMHelper.kt              # Firebase Cloud Messaging
│   │   └── MyFirebaseMessagingService.kt
│   └── res/
│       ├── layout/
│       │   ├── activity_main.xml
│       │   ├── activity_login.xml
│       │   ├── card_status_alerts.xml
│       │   └── card_status_device.xml
│       └── drawable/
```

## Sistema de notificaciones

- **Notificaciones locales:** Alertas recurrentes cada 5 minutos mientras haya parámetros fuera de rango
- **FCM Push:** Notificaciones a todos los dispositivos conectados
- **Prioridades:** Máxima para alertas críticas, alta para advertencias

## 👥 Autor

**Ing. Roxana Nicole Briones Yepez**  
Desarrollado para el sistema de monitoreo de calidad del agua en planta Q'Agua

