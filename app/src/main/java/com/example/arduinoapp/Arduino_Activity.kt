package com.example.arduinoapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.Executors

class Arduino_Activity : AppCompatActivity() {

    private val uuidHc06 = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val connectionExecutor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null

    @Volatile
    private var escuchandoBluetooth = false

    private lateinit var btnConectar: Button
    private lateinit var tvRespuestaArduino: TextView

    private val solicitudPermisoBluetooth =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) {
                conectarBluetooth()
            } else {
                mostrarToast("Se necesita permiso de Bluetooth para conectar")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_arduino)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        btnConectar = findViewById(R.id.btnConectar)
        val btnEstado = findViewById<Button>(R.id.btnEstado)
        val btnRegar = findViewById<Button>(R.id.btnRegar)
        val btnCerrarSesion = findViewById<Button>(R.id.btnCerrar)
        tvRespuestaArduino = findViewById(R.id.tvRespuestaArduino)

        btnConectar.setOnClickListener {
            conectarBluetooth()
        }

        btnEstado.setOnClickListener {
            enviarComando("ESTADO\n", "Consultando estado...")
        }

        btnRegar.setOnClickListener {
            enviarComando("SI\n", "Enviando confirmación de riego (SI)")
        }

        btnCerrarSesion.setOnClickListener {
            desconectarBluetooth()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun conectarBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            solicitudPermisoBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null) {
            mostrarToast("Bluetooth no disponible")
            return
        }

        btnConectar.isEnabled = false
        connectionExecutor.execute {
            var socket: BluetoothSocket? = null
            try {
                if (!adapter.isEnabled) {
                    mostrarErrorConexion("Activa Bluetooth e inténtalo de nuevo")
                    return@execute
                }

                val dispositivo: BluetoothDevice? =
                    adapter.bondedDevices.firstOrNull { it.name == "HC-06" }
                if (dispositivo == null) {
                    mostrarErrorConexion("No se encontró el HC-06 vinculado")
                    return@execute
                }

                socket = dispositivo.createRfcommSocketToServiceRecord(uuidHc06)
                socket.connect()
                val stream = socket.inputStream
                bluetoothSocket = socket
                inputStream = stream
                escuchandoBluetooth = true

                handler.post {
                    if (!isFinishing) {
                        btnConectar.isEnabled = true
                        Toast.makeText(this, "¡Conectado al HC-06!", Toast.LENGTH_SHORT).show()
                    }
                }
                iniciarLecturaBluetooth(stream)
            } catch (e: IOException) {
                cerrarSocket(socket)
                mostrarErrorConexion("No se pudo conectar con el Arduino")
            } catch (e: SecurityException) {
                cerrarSocket(socket)
                mostrarErrorConexion("No hay permiso para acceder a Bluetooth")
            }
        }
    }

    private fun iniciarLecturaBluetooth(stream: InputStream) {
        Thread({
            val buffer = ByteArray(1024)
            try {
                while (escuchandoBluetooth) {
                    val bytes = stream.read(buffer)
                    if (bytes < 0) break

                    val mensaje = String(buffer, 0, bytes, Charsets.UTF_8).trim()
                    if (mensaje.isNotEmpty()) {
                        handler.post {
                            if (!isFinishing) {
                                tvRespuestaArduino.text = mensaje
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                if (escuchandoBluetooth) {
                    mostrarToast("Se perdió la conexión con el Arduino")
                }
            } finally {
                escuchandoBluetooth = false
                handler.post {
                    if (!isFinishing) {
                        btnConectar.isEnabled = true
                    }
                }
            }
        }, "hc06-reader").start()
    }

    private fun enviarComando(comando: String, mensajeEnviado: String) {
        connectionExecutor.execute {
            try {
                val socket = bluetoothSocket
                if (socket == null || !socket.isConnected) {
                    mostrarToast("Primero conéctate al Arduino")
                    return@execute
                }

                socket.outputStream.apply {
                    write(comando.toByteArray(Charsets.UTF_8))
                    flush()
                }
                mostrarToast(mensajeEnviado)
            } catch (e: IOException) {
                mostrarToast("Error al enviar el comando")
            } catch (e: SecurityException) {
                mostrarToast("No hay permiso para acceder a Bluetooth")
            }
        }
    }

    private fun mostrarErrorConexion(mensaje: String) {
        handler.post {
            if (!isFinishing) {
                btnConectar.isEnabled = true
                Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun mostrarToast(mensaje: String) {
        handler.post {
            if (!isFinishing) {
                Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cerrarSocket(socket: BluetoothSocket?) {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
    }

    private fun desconectarBluetooth() {
        escuchandoBluetooth = false
        val stream = inputStream
        val socket = bluetoothSocket
        inputStream = null
        bluetoothSocket = null

        try {
            stream?.close()
        } catch (_: IOException) {
        }
        cerrarSocket(socket)
    }

    override fun onDestroy() {
        desconectarBluetooth()
        connectionExecutor.shutdownNow()
        super.onDestroy()
    }
}
