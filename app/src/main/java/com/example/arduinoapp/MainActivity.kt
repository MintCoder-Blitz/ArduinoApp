package com.example.arduinoapp

import DatabaseHelper
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
import kotlin.jvm.java

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
            hint = "Usuario"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val etContrasena = EditText(this).apply {
            hint = "Contraseña actual"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        contenido.addView(etUsuario)
        contenido.addView(etContrasena)

        AlertDialog.Builder(this)
            .setTitle("Eliminar usuario")
            .setMessage("Ingresa tus credenciales para continuar.")
            .setView(contenido)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar") { _, _ ->
                val usuario = etUsuario.text.toString().trim()
                val contrasena = etContrasena.text.toString()
                if (usuario.isEmpty() || contrasena.isEmpty()) {
                    Toast.makeText(this, "Completa ambos campos", Toast.LENGTH_SHORT).show()
                } else {
                    confirmarEliminacion(usuario, contrasena)
                }
            }
            .show()
    }

    private fun confirmarEliminacion(usuario: String, contrasena: String) {
        AlertDialog.Builder(this)
            .setTitle("¿Eliminar la cuenta?")
            .setMessage("Esta acción no se puede deshacer.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                val eliminado = DatabaseHelper(this).use { dbHelper ->
                    dbHelper.eliminarUsuario(usuario, contrasena)
                }
                val mensaje = if (eliminado) {
                    "La cuenta fue eliminada"
                } else {
                    "Usuario o contraseña incorrectos"
                }
                Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}