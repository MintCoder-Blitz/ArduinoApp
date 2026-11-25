package com.example.arduinoapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class IniciarSesion_Activity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main_iniciar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val etCorreo = findViewById<EditText>(R.id.ptIngresoUsuario)
        val etContrasena = findViewById<EditText>(R.id.ptIngresoContraseña)
        val btnIniciar = findViewById<Button>(R.id.btniniciarsesion)
        val btnRecuperar = findViewById<Button>(R.id.btnCambiarContrasena)

        btnIniciar.setOnClickListener {
            val correo = etCorreo.text.toString().trim()
            val contrasena = etContrasena.text.toString()
            if (correo.isBlank() || contrasena.isBlank()) {
                Toast.makeText(this, "Ingresa correo y contraseña", Toast.LENGTH_SHORT).show()
            } else {
                iniciarSesion(correo, contrasena, btnIniciar)
            }
        }

        btnRecuperar.setOnClickListener {
            val correo = etCorreo.text.toString().trim()
            if (correo.isBlank()) {
                etCorreo.error = "Ingresa tu correo"
            } else {
                enviarRecuperacion(correo, btnRecuperar)
            }
        }
    }

    private fun iniciarSesion(correo: String, contrasena: String, boton: Button) {
        val configError = FirebaseServices.initialize(this)
        if (configError != null) {
            Toast.makeText(this, configError, Toast.LENGTH_LONG).show()
            return
        }

        boton.isEnabled = false
        FirebaseServices.auth().signInWithEmailAndPassword(correo, contrasena)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid == null) {
                    boton.isEnabled = true
                    Toast.makeText(this, "Firebase no devolvió el usuario", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                FirebaseServices.database().getReference("users")
                    .child(uid)
                    .child("role")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val role = snapshot.getValue(String::class.java) ?: "usuario"
                        startActivity(
                            Intent(this, Arduino_Activity::class.java)
                                .putExtra("ROL_USUARIO", role)
                                .putExtra("USUARIO_UID", uid)
                        )
                        finish()
                    }
                    .addOnFailureListener { error ->
                        FirebaseServices.auth().signOut()
                        boton.isEnabled = true
                        Toast.makeText(
                            this,
                            "No se pudo consultar el perfil: ${error.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
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

    private fun enviarRecuperacion(correo: String, boton: Button) {
        val configError = FirebaseServices.initialize(this)
        if (configError != null) {
            Toast.makeText(this, configError, Toast.LENGTH_LONG).show()
            return
        }

        boton.isEnabled = false
        FirebaseServices.auth().sendPasswordResetEmail(correo)
            .addOnCompleteListener { task ->
                boton.isEnabled = true
                val mensaje = if (task.isSuccessful) {
                    "Revisa tu correo para restablecer la contraseña"
                } else {
                    "No se pudo enviar el correo: ${task.exception?.localizedMessage}"
                }
                Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
            }
    }
}
