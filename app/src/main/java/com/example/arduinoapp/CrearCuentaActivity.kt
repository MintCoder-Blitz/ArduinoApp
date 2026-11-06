package com.example.arduinoapp

import DatabaseHelper
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText

class CrearCuentaActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main_crear)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val dbHelper = DatabaseHelper(this)

        //pantalla de crear usuario

        val etUsuario = findViewById<TextInputEditText>(R.id.ptUsuario)
        val etContraseña = findViewById<TextInputEditText>(R.id.ptContraseña)
        val btnRegistrarse = findViewById<Button>(R.id.btnRegitrarse)

        btnRegistrarse.setOnClickListener {
            val nombreTxt = etUsuario.text.toString().trim()
            val passTxt = etContraseña.text.toString().trim()

            if (nombreTxt.isEmpty() || passTxt.isEmpty()) {
                Toast.makeText(this, "Por favor llena todos los campos", Toast.LENGTH_SHORT).show()
            } else {
                val guardadoExitoso = dbHelper.insertarUsuario(nombreTxt, passTxt)
                if (guardadoExitoso) {
                    Toast.makeText(this, "¡Usuario registrado con éxito!", Toast.LENGTH_SHORT).show()
                    finish() // Cierra el registro y regresa
                } else {
                    Toast.makeText(this, "Error al registrar usuario", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}