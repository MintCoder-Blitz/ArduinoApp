package com.example.arduinoapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class CrearCuentaActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main_crear)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val etCorreo = findViewById<EditText>(R.id.ptUsuario)
        val etContrasena = findViewById<EditText>(R.id.ptContraseña)
        val etConfirmar = findViewById<EditText>(R.id.ptConfirmar)
        val btnRegistrarse = findViewById<Button>(R.id.btnRegitrarse)

        btnRegistrarse.setOnClickListener {
            val correo = etCorreo.text.toString().trim()
            val contrasena = etContrasena.text.toString()
            val confirmar = etConfirmar.text.toString()

            when {
                correo.isBlank() || contrasena.isBlank() || confirmar.isBlank() ->
                    Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                contrasena != confirmar ->
                    etConfirmar.error = "Las contraseñas no coinciden"
                else -> crearCuenta(correo, contrasena, btnRegistrarse)
            }
        }
    }

    private fun crearCuenta(correo: String, contrasena: String, boton: Button) {
        val configError = FirebaseServices.initialize(this)
        if (configError != null) {
            Toast.makeText(this, configError, Toast.LENGTH_LONG).show()
            return
        }

        boton.isEnabled = false
        FirebaseServices.auth().createUserWithEmailAndPassword(correo, contrasena)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid == null) {
                    boton.isEnabled = true
                    Toast.makeText(this, "Firebase no devolvió el usuario creado", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                FirebaseServices.database().getReference("users")
                    .child(uid)
                    .setValue(mapOf("role" to "usuario"))
                    .addOnSuccessListener {
                        FirebaseServices.auth().signOut()
                        Toast.makeText(
                            this,
                            "Cuenta creada. El rol de administrador se asigna desde Firebase.",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                    .addOnFailureListener { error ->
                        boton.isEnabled = true
                        Toast.makeText(
                            this,
                            "Cuenta creada, pero no se pudo guardar el perfil: ${error.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { error ->
                boton.isEnabled = true
                Toast.makeText(
                    this,
                    "No se pudo crear la cuenta: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}
