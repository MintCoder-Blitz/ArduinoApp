package com.example.arduinoapp

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

object FirebaseServices {
    private const val APP_NAME = "arduapp"

    @Volatile
    private var firebaseApp: FirebaseApp? = null

    fun initialize(context: Context): String? {
        if (firebaseApp != null) return null

        val missing = buildList {
            if (BuildConfig.FIREBASE_API_KEY.isBlank()) add("API key")
            if (BuildConfig.FIREBASE_APP_ID.isBlank()) add("App ID")
            if (BuildConfig.FIREBASE_PROJECT_ID.isBlank()) add("Project ID")
            if (BuildConfig.FIREBASE_DATABASE_URL.isBlank()) add("Database URL")
        }
        if (missing.isNotEmpty()) {
            return "Falta configurar Firebase en local.properties: ${missing.joinToString()}"
        }

        return try {
            synchronized(this) {
                firebaseApp = firebaseApp
                    ?: FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }
                    ?: FirebaseApp.initializeApp(
                        context,
                        FirebaseOptions.Builder()
                            .setApiKey(BuildConfig.FIREBASE_API_KEY)
                            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                            .setDatabaseUrl(BuildConfig.FIREBASE_DATABASE_URL)
                            .build(),
                        APP_NAME
                    )
            }
            null
        } catch (e: IllegalStateException) {
            "No se pudo inicializar Firebase: ${e.localizedMessage ?: "configuración inválida"}"
        }
    }

    fun auth(): FirebaseAuth =
        FirebaseAuth.getInstance(requireNotNull(firebaseApp) { "Firebase no está inicializado" })

    fun database(): FirebaseDatabase =
        FirebaseDatabase.getInstance(
            requireNotNull(firebaseApp) { "Firebase no está inicializado" },
            BuildConfig.FIREBASE_DATABASE_URL
        )
}
