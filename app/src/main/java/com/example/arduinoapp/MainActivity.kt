package com.example.arduinoapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        //pantalla de inicio

        val btnCrearCuenta = findViewById<Button>(R.id.btncrear)
        val btnIniciarSesion = findViewById<Button>(R.id.btningresar)
        val btnEliminarUsuario = findViewById<Button>(R.id.btnEliminarUsuario)

        btnCrearCuenta.setOnClickListener {
            val intent = Intent(this, CrearCuentaActivity::class.java)
            startActivity(intent)
        }

        btnIniciarSesion.setOnClickListener {
            val intent = Intent(this, IniciarSesion_Activity::class.java)
            startActivity(intent)
        }

        btnEliminarUsuario.setOnClickListener {
            mostrarDialogoEliminarUsuario()
        }
    }

    private fun mostrarDialogoEliminarUsuario() {
        val contenido = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 0, 48, 0)
        }
        val etUsuario = EditText(this).apply {
            hint = "Correo electrónico"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val etContrasena = EditText(this).apply {
            hint = "Contraseña actual"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        contenido.addView(etUsuario)
        contenido.addView(etContrasena)

        AlertDialog.Builder(this)
            .setTitle("Eliminar usuario")
            .setMessage("Se eliminará la cuenta. Los horarios de la planta no se borran.")
            .setView(contenido)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar") { _, _ ->
                val correo = etUsuario.text.toString().trim()
                val contrasena = etContrasena.text.toString()
                if (correo.isEmpty() || contrasena.isEmpty()) {
                    Toast.makeText(this, "Completa ambos campos", Toast.LENGTH_SHORT).show()
                } else {
                    eliminarCuenta(correo, contrasena)
                }
            }
            .show()
    }

    private fun eliminarCuenta(correo: String, contrasena: String) {
        val configError = FirebaseServices.initialize(this)
        if (configError != null) {
            Toast.makeText(this, configError, Toast.LENGTH_LONG).show()
            return
        }

        val auth = FirebaseServices.auth()
        auth.signInWithEmailAndPassword(correo, contrasena)
            .addOnSuccessListener { result ->
                val usuario = result.user
                if (usuario == null) {
                    Toast.makeText(this, "Firebase no devolvió el usuario", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val perfil = FirebaseServices.database().getReference("users").child(usuario.uid)
                perfil.child("role").get()
                    .addOnSuccessListener { role ->
                        if (role.getValue(String::class.java) == "admin") {
                            auth.signOut()
                            Toast.makeText(
                                this,
                                "No se puede eliminar la cuenta administradora desde la app",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            perfil.removeValue()
                                .addOnSuccessListener {
                                    usuario.delete()
                                        .addOnSuccessListener {
                                            auth.signOut()
                                            Toast.makeText(this, "Cuenta eliminada", Toast.LENGTH_SHORT).show()
                                        }
                                        .addOnFailureListener { error ->
                                            Toast.makeText(
                                                this,
                                                "No se pudo eliminar la cuenta: ${error.localizedMessage}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                }
                                .addOnFailureListener { error ->
                                    Toast.makeText(
                                        this,
                                        "No se pudo eliminar el perfil: ${error.localizedMessage}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                    }
                    .addOnFailureListener { error ->
                        auth.signOut()
                        Toast.makeText(
                            this,
                            "No se pudo consultar el perfil: ${error.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "No se pudo validar la cuenta: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}