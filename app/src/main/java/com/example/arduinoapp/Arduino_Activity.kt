package com.example.arduinoapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.InputStream
import java.util.UUID

class Arduino_Activity : AppCompatActivity() {

    private val UUID_HC06 = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var bluetoothSocket: BluetoothSocket? = null
    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    // Variables para la lectura continua del Arduino
    private var inputStream: InputStream? = null
    @Volatile private var escuchandoBluetooth = false
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvRespuestaArduino: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_arduino)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnConectar = findViewById<Button>(R.id.btnConectar)
        val btnEstado = findViewById<Button>(R.id.btnEstado)
        val btnRegar = findViewById<Button>(R.id.btnRegar)
        val btnCerrarSesion = findViewById<Button>(R.id.btnCerrar)
        tvRespuestaArduino = findViewById(R.id.tvRespuestaArduino)

        // 1. Conectar al HC-06
        btnConectar.setOnClickListener {
            conectarBluetooth()
        }

        // 2. Botón Estado -> Envía "ESTADO\n" para activar la lectura del sensor en el Arduino
        btnEstado.setOnClickListener {
            enviarComando("ESTADO\n")
            Toast.makeText(this, "Consultando estado...", Toast.LENGTH_SHORT).show()
        }

        // 3. Botón Regar -> Envía "SI\n" para confirmar el riego cuando el Arduino lo pida
        btnRegar.setOnClickListener {
            enviarComando("SI\n")
            Toast.makeText(this, "Enviando confirmación de riego (SI)", Toast.LENGTH_SHORT).show()
        }

        // 4. Botón Cerrar sesión
        btnCerrarSesion.setOnClickListener {
            desconectarBluetooth()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun conectarBluetooth() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        val dispositivosEmparejados = bluetoothAdapter?.bondedDevices
        var dispositivoHC06: BluetoothDevice? = null

        dispositivosEmparejados?.forEach { dispositivo ->
            if (dispositivo.name == "HC-06") {
                dispositivoHC06 = dispositivo
                return@forEach
            }
        }

        if (dispositivoHC06 == null) {
            Toast.makeText(this, "No se encontró el HC-06 vinculado.", Toast.LENGTH_LONG).show()
            return
        }

        try {
            bluetoothSocket = dispositivoHC06?.createRfcommSocketToServiceRecord(UUID_HC06)
            bluetoothSocket?.connect()
            inputStream = bluetoothSocket?.inputStream
            escuchandoBluetooth = true

            // Arrancamos el hilo para escuchar lo que responde el Arduino
            iniciarLecturaBluetooth()

            Toast.makeText(this, "¡Conectado al HC-06!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al conectar con el Arduino", Toast.LENGTH_LONG).show()
            desconectarBluetooth()
        }
    }

    // --- HILO SECUNDARIO PARA LEER RESPUESTAS DEL ARDUINO ---
    private fun iniciarLecturaBluetooth() {
        Thread {
            val buffer = ByteArray(1024)
            var bytes: Int

            while (escuchandoBluetooth) {
                try {
                    if (inputStream != null && inputStream!!.available() > 0) {
                        bytes = inputStream!!.read(buffer)
                        val mensajeRecibido = String(buffer, 0, bytes).trim()

                        if (mensajeRecibido.isNotEmpty()) {
                            // Actualizamos la interfaz gráfica de Android de forma segura
                            handler.post {
                                tvRespuestaArduino.text = mensajeRecibido
                            }
                        }
                    }
                } catch (e: Exception) {
                    escuchandoBluetooth = false
                }
            }
        }.start()
    }

    private fun enviarComando(comando: String) {
        if (bluetoothSocket != null && bluetoothSocket!!.isConnected) {
            try {
                bluetoothSocket!!.outputStream.write(comando.toByteArray())
            } catch (e: Exception) {
                Toast.makeText(this, "Error al enviar comando", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Primero conéctate al Arduino", Toast.LENGTH_SHORT).show()
        }
    }

    private fun desconectarBluetooth() {
        escuchandoBluetooth = false
        try {
            inputStream?.close()
            bluetoothSocket?.close()
        } catch (e: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        desconectarBluetooth()
    }
}
