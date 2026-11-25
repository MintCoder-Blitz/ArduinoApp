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
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import org.json.JSONObject
import java.nio.ByteBuffer
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener

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
    private var mqttClient: Mqtt3AsyncClient? = null
    private var mqttConectado = false
    private var mqttEstadoTopic = ""
    private var mqttComandoTopic = ""
    private var referenciaEstado: DatabaseReference? = null
    private var listenerEstado: ValueEventListener? = null

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
        val btnRegar = findViewById<Button>(R.id.btnRegar)
        val btnNoRegar = findViewById<Button>(R.id.btnNoRegar)
        val btnHorarios = findViewById<Button>(R.id.btnHorarios)
        val btnCerrarSesion = findViewById<Button>(R.id.btnCerrar)
        tvRespuestaArduino = findViewById(R.id.tvRespuestaArduino)

        val errorConfiguracionFirebase = FirebaseServices.initialize(this)
        val uidUsuario = if (errorConfiguracionFirebase == null) {
            FirebaseServices.auth().currentUser?.uid
        } else {
            null
        }
        if (uidUsuario != null) {
            mqttEstadoTopic = "riego/$uidUsuario/estado"
            mqttComandoTopic = "riego/$uidUsuario/comando"
            conectarMQTT()
        }

        val rol = intent.getStringExtra("ROL_USUARIO")
        val esAdministrador = rol == "admin"
        btnRegar.isEnabled = esAdministrador
        btnNoRegar.isEnabled = esAdministrador
        btnHorarios.setOnClickListener {
            startActivity(
                Intent(this, AlarmasActivity::class.java)
                    .putExtra("ROL_USUARIO", rol)
                    .putExtra("USUARIO_UID", intent.getStringExtra("USUARIO_UID"))
            )
        }
        observarEstadoFirebase()

        btnConectar.setOnClickListener {
            conectarBluetooth()
        }

        btnRegar.setOnClickListener {
            enviarComando("SI", "Enviando confirmación de riego (SI)")
        }

        btnNoRegar.setOnClickListener {
            enviarComando("NO", "Enviando confirmación de no regar (NO)")
        }

        btnCerrarSesion.setOnClickListener {
            desconectarBluetooth()
            desconectarMQTT()
            FirebaseServices.auth().signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun conectarMQTT() {
        mqttClient?.disconnect()

        mqttClient = MqttClient.builder()
            .useMqttVersion3()
            .identifier("arduino-app-${System.currentTimeMillis()}")
            .serverHost("10.46.111.101")
            .serverPort(1883)
            .buildAsync()

        mqttClient?.connect()
            ?.whenComplete { _, error ->
                if (error == null) {
                    mqttConectado = true
                    suscribirEstadoMQTT()
                } else {
                    mqttConectado = false
                    handler.post {
                        if (!isFinishing) {
                            tvRespuestaArduino.text =
                                "Wi-Fi/MQTT no disponible. Puedes usar Bluetooth."
                        }
                    }
                }
            }
    }

    private fun suscribirEstadoMQTT() {
        mqttClient?.subscribeWith()
            ?.topicFilter(mqttEstadoTopic)
            ?.callback { publish ->
                val texto = publish.payloadAsBytes.toString(Charsets.UTF_8)
                mostrarEstadoMQTT(texto)
            }
            ?.send()
    }

    private fun observarEstadoFirebase() {
        val configError = FirebaseServices.initialize(this)
        if (configError != null) {
            mostrarToast(configError)
            return
        }

        val uid = FirebaseServices.auth().currentUser?.uid
        if (uid == null) {
            mostrarToast("Inicia sesión para consultar el estado de tu placa")
            return
        }

        val reference = FirebaseServices.database().getReference("devices/$uid/status")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val humedad = snapshot.child("humedad_porcentaje").getValue(Int::class.java)
                val seca = snapshot.child("necesita_riego").getValue(Boolean::class.java) ?: false
                val motivo = snapshot.child("motivo").getValue(String::class.java) ?: "Sin motivo"
                handler.post {
                    if (!isFinishing) {
                        tvRespuestaArduino.text =
                            "Estado: ${if (seca) "SECA" else "HÚMEDA"}\n" +
                                "Humedad: ${humedad?.let { "$it%" } ?: "sin dato"}\n" +
                                "Motivo: $motivo"
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                mostrarToast("No se pudo leer el estado de Firebase: ${error.message}")
            }
        }
        referenciaEstado = reference
        listenerEstado = listener
        reference.addValueEventListener(listener)
    }

    private fun mostrarEstadoMQTT(texto: String) {
        try {
            val estado = JSONObject(texto)
            val humedad = estado.optInt("humedad_porcentaje")
            val seca = estado.optBoolean("necesita_riego")
            val regada = estado.optBoolean("se_rego")
            val motivo = estado.optString("motivo", "Sin motivo")

            handler.post {
                if (!isFinishing) {
                    tvRespuestaArduino.text =
                        "Estado: ${if (seca) "SECA" else "HUMEDA"}\n" +
                        "Humedad: $humedad%\n" +
                        "Se regó: ${if (regada) "Sí" else "No"}\n" +
                        "Motivo: $motivo"
                }
            }
        } catch (_: Exception) {
            handler.post {
                if (!isFinishing) tvRespuestaArduino.text = texto
            }
        }
    }

    private fun desconectarMQTT() {
        mqttConectado = false
        mqttClient?.disconnect()
        mqttClient = null
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
        if (mqttConectado) {
            mqttClient?.publishWith()
                ?.topic(mqttComandoTopic)
                ?.payload(ByteBuffer.wrap(comando.toByteArray(Charsets.UTF_8)))
                ?.send()
            mostrarToast(mensajeEnviado)
            return
        }

        connectionExecutor.execute {
            try {
                val socket = bluetoothSocket
                if (socket == null || !socket.isConnected) {
                    mostrarToast("Primero conéctate al Arduino")
                    return@execute
                }

                socket.outputStream.apply {
                    write("$comando\n".toByteArray(Charsets.UTF_8))
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
        listenerEstado?.let { referenciaEstado?.removeEventListener(it) }
        desconectarBluetooth()
        desconectarMQTT()
        connectionExecutor.shutdownNow()
        super.onDestroy()
    }
}
