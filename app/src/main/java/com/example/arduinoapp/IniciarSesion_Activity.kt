package com.example.arduinoapp

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class IniciarSesion_Activity : AppCompatActivity() {

    private val preferencias by lazy {
        getSharedPreferences(
            "sesion_local",
            Context.MODE_PRIVATE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main_iniciar)

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { view, insets ->
            val systemBars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        val etCorreo =
            findViewById<EditText>(
                R.id.ptIngresoUsuario
            )

        val etContrasena =
            findViewById<EditText>(
                R.id.ptIngresoContraseña
            )

        val btnIniciar =
            findViewById<Button>(
                R.id.btniniciarsesion
            )

        val btnRecuperar =
            findViewById<Button>(
                R.id.btnCambiarContrasena
            )

        btnIniciar.setOnClickListener {
            if (!hayInternet()) {
                iniciarSesionSinConexion()
                return@setOnClickListener
            }

            val correo =
                etCorreo.text
                    .toString()
                    .trim()

            val contrasena =
                etContrasena.text
                    .toString()

            if (
                correo.isBlank() ||
                contrasena.isBlank()
            ) {
                Toast.makeText(
                    this,
                    "Ingresa correo y contraseña",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                iniciarSesion(
                    correo,
                    contrasena,
                    btnIniciar
                )
            }
        }

        btnRecuperar.setOnClickListener {
            if (!hayInternet()) {
                Toast.makeText(
                    this,
                    "Necesitas conexión a Internet para restablecer la contraseña",
                    Toast.LENGTH_LONG
                ).show()

                return@setOnClickListener
            }

            val correo =
                etCorreo.text
                    .toString()
                    .trim()

            if (correo.isBlank()) {
                etCorreo.error =
                    "Ingresa tu correo"
            } else {
                enviarRecuperacion(
                    correo,
                    btnRecuperar
                )
            }
        }
    }

    private fun iniciarSesion(
        correo: String,
        contrasena: String,
        boton: Button
    ) {
        val configError =
            FirebaseServices.initialize(this)

        if (configError != null) {
            Toast.makeText(
                this,
                configError,
                Toast.LENGTH_LONG
            ).show()

            return
        }

        boton.isEnabled = false

        FirebaseServices
            .auth()
            .signInWithEmailAndPassword(
                correo,
                contrasena
            )
            .addOnSuccessListener { result ->
                val uid =
                    result.user?.uid

                if (uid == null) {
                    boton.isEnabled = true

                    Toast.makeText(
                        this,
                        "Firebase no devolvió el usuario",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                consultarPerfil(
                    uid,
                    correo,
                    boton
                )
            }
            .addOnFailureListener { error ->
                boton.isEnabled = true

                Toast.makeText(
                    this,
                    "No se pudo iniciar sesión: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun consultarPerfil(
        uid: String,
        correo: String,
        boton: Button
    ) {
        FirebaseServices
            .database()
            .getReference("users")
            .child(uid)
            .child("role")
            .get()
            .addOnSuccessListener { snapshot ->
                val role =
                    snapshot.getValue(
                        String::class.java
                    ) ?: "usuario"

                guardarSesionLocal(
                    uid,
                    correo,
                    role
                )

                abrirPanel(
                    uid,
                    role,
                    false
                )
            }
            .addOnFailureListener { error ->
                boton.isEnabled = true

                val roleGuardado =
                    preferencias.getString(
                        "rol",
                        null
                    )

                val uidGuardado =
                    preferencias.getString(
                        "uid",
                        null
                    )

                if (
                    roleGuardado != null &&
                    uidGuardado == uid
                ) {
                    abrirPanel(
                        uid,
                        roleGuardado,
                        true
                    )
                } else {
                    FirebaseServices
                        .auth()
                        .signOut()

                    Toast.makeText(
                        this,
                        "No se pudo consultar el perfil: ${error.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun iniciarSesionSinConexion() {
        val configError =
            FirebaseServices.initialize(this)

        if (configError != null) {
            Toast.makeText(
                this,
                configError,
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val usuarioActual =
            FirebaseServices
                .auth()
                .currentUser

        val uidGuardado =
            preferencias.getString(
                "uid",
                null
            )

        val rolGuardado =
            preferencias.getString(
                "rol",
                null
            )

        if (
            usuarioActual == null ||
            uidGuardado == null ||
            rolGuardado == null ||
            usuarioActual.uid != uidGuardado
        ) {
            Toast.makeText(
                this,
                "Sin conexión. Conéctate a Internet para iniciar sesión por primera vez.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        abrirPanel(
            uidGuardado,
            rolGuardado,
            true
        )
    }

    private fun guardarSesionLocal(
        uid: String,
        correo: String,
        rol: String
    ) {
        preferencias
            .edit()
            .putString(
                "uid",
                uid
            )
            .putString(
                "correo",
                correo
            )
            .putString(
                "rol",
                rol
            )
            .apply()
    }

    private fun abrirPanel(
        uid: String,
        rol: String,
        modoSinConexion: Boolean
    ) {
        if (modoSinConexion) {
            Toast.makeText(
                this,
                "Acceso sin conexión. Puedes utilizar Bluetooth.",
                Toast.LENGTH_LONG
            ).show()
        }

        startActivity(
            Intent(
                this,
                Arduino_Activity::class.java
            )
                .putExtra(
                    "ROL_USUARIO",
                    rol
                )
                .putExtra(
                    "USUARIO_UID",
                    uid
                )
                .putExtra(
                    "MODO_SIN_CONEXION",
                    modoSinConexion
                )
        )

        finish()
    }

    private fun hayInternet(): Boolean {
        val connectivityManager =
            getSystemService(
                Context.CONNECTIVITY_SERVICE
            ) as ConnectivityManager

        val network =
            connectivityManager.activeNetwork
                ?: return false

        val capabilities =
            connectivityManager
                .getNetworkCapabilities(network)
                ?: return false

        return capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) &&
                capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                )
    }

    private fun enviarRecuperacion(
        correo: String,
        boton: Button
    ) {
        val configError =
            FirebaseServices.initialize(this)

        if (configError != null) {
            Toast.makeText(
                this,
                configError,
                Toast.LENGTH_LONG
            ).show()

            return
        }

        boton.isEnabled = false

        FirebaseServices
            .auth()
            .sendPasswordResetEmail(correo)
            .addOnCompleteListener { task ->
                boton.isEnabled = true

                val mensaje =
                    if (task.isSuccessful) {
                        "Revisa tu correo para restablecer la contraseña"
                    } else {
                        "No se pudo enviar el correo: ${task.exception?.localizedMessage}"
                    }

                Toast.makeText(
                    this,
                    mensaje,
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}