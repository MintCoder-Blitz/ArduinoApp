package com.example.arduinoapp

import DatabaseHelper
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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

        btniniciarsesion.setOnClickListener {
            val textoUsuario = ptingresarusuario.text.toString()
            val textocontraseña = ptingresocontraseña.text.toString()

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

    }
}