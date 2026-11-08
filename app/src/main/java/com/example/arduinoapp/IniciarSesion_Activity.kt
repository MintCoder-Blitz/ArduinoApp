package com.example.arduinoapp

import DatabaseHelper
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class IniciarSesion_Activity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main_iniciar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val dbHelper = DatabaseHelper(this)

        //pantalla iniciar sesion

        val ptingresarusuario = findViewById<EditText>(R.id.ptIngresoUsuario)
        val ptingresocontraseña = findViewById<EditText>(R.id.ptIngresoContraseña)
        val btniniciarsesion = findViewById<Button>(R.id.btniniciarsesion)
        val btnCambiarContrasena = findViewById<Button>(R.id.btnCambiarContrasena)

        btniniciarsesion.setOnClickListener {
            val textoUsuario = ptingresarusuario.text.toString().trim()
            val textocontraseña = ptingresocontraseña.text.toString().trim()

            if (textoUsuario.isEmpty() || textocontraseña.isEmpty()) {
                Toast.makeText(this, "Rellene todos los campos", Toast.LENGTH_SHORT).show()
            } else {
                val esValido = dbHelper.verificarUsuario(textoUsuario, textocontraseña)
                if (esValido) {
                    Toast.makeText(this, "Bienvenido", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, Arduino_Activity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "Nombre o Contraseña incorrecto", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnCambiarContrasena.setOnClickListener {
            mostrarDialogoCambiarContrasena()
        }
    }

    private fun mostrarDialogoCambiarContrasena() {
        val contenido = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 0, 48, 0)
        }
        val etUsuario = EditText(this).apply {
            hint = "Nombre de usuario"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val etNuevaContrasena = EditText(this).apply {
            hint = "Nueva contraseña"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val etConfirmarContrasena = EditText(this).apply {
            hint = "Confirmar nueva contraseña"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        contenido.addView(etUsuario)
        contenido.addView(etNuevaContrasena)
        contenido.addView(etConfirmarContrasena)

        AlertDialog.Builder(this)
            .setTitle("Cambiar contraseña")
            .setMessage("La cuenta se identifica solo por su nombre de usuario.")
            .setView(contenido)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar") { _, _ ->
                val usuario = etUsuario.text.toString().trim()
                val nuevaContrasena = etNuevaContrasena.text.toString()
                val confirmarContrasena = etConfirmarContrasena.text.toString()

                when {
                    usuario.isEmpty() || nuevaContrasena.isEmpty() || confirmarContrasena.isEmpty() ->
                        Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                    nuevaContrasena != confirmarContrasena ->
                        Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                    else -> {
                        val actualizada = DatabaseHelper(this).use { dbHelper ->
                            dbHelper.actualizarContrasena(usuario, nuevaContrasena)
                        }
                        val mensaje = if (actualizada) {
                            "Contraseña actualizada"
                        } else {
                            "No se encontró ese usuario"
                        }
                        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }
}