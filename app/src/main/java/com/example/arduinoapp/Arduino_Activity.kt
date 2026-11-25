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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.Executors

class Arduino_Activity : AppCompatActivity() {

    private val uuidHc06 =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val bluetoothAdapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private val connectionExecutor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var bluetoothSocket: BluetoothSocket? = null

    private var inputStream: InputStream? = null

    @Volatile
    private var escuchandoBluetooth = false

    private lateinit var btnConectar: Button
    private lateinit var tvRespuestaArduino: TextView

    private var referenciaEstado: DatabaseReference? = null
    private var listenerEstado: ValueEventListener? = null

    private val solicitudPermisoBluetooth =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { concedido ->
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

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { v, insets ->

            val systemBars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        btnConectar = findViewById(R.id.btnConectar)

        val btnRegar =
            findViewById<Button>(R.id.btnRegar)

        val btnNoRegar =
            findViewById<Button>(R.id.btnNoRegar)

        val btnHorarios =
            findViewById<Button>(R.id.btnHorarios)

        val btnCerrarSesion =
            findViewById<Button>(R.id.btnCerrar)

        tvRespuestaArduino =
            findViewById(R.id.tvRespuestaArduino)

        val errorConfiguracionFirebase =
            FirebaseServices.initialize(this)

        if (errorConfiguracionFirebase != null) {
            mostrarToast(errorConfiguracionFirebase)
        }

        val rol =
            intent.getStringExtra("ROL_USUARIO")

        val esAdministrador =
            rol == "admin"

        btnRegar.isEnabled =
            esAdministrador

        btnNoRegar.isEnabled =
            esAdministrador

        btnHorarios.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    AlarmasActivity::class.java
                )
                    .putExtra(
                        "ROL_USUARIO",
                        rol
                    )
                    .putExtra(
                        "USUARIO_UID",
                        intent.getStringExtra("USUARIO_UID")
                    )
            )
        }

        observarEstadoFirebase()

        btnConectar.setOnClickListener {
            conectarBluetooth()
        }

        btnRegar.setOnClickListener {
            enviarComando(
                "SI",
                "Enviando confirmación de riego (SI)"
            )
        }

        btnNoRegar.setOnClickListener {
            enviarComando(
                "NO",
                "Enviando confirmación de no regar (NO)"
            )
        }

        btnCerrarSesion.setOnClickListener {
            desconectarBluetooth()

            FirebaseServices.auth().signOut()

            startActivity(
                Intent(
                    this,
                    MainActivity::class.java
                )
            )

            finish()
        }
    }

    private fun observarEstadoFirebase() {
        val configError =
            FirebaseServices.initialize(this)

        if (configError != null) {
            mostrarToast(configError)
            return
        }

        val uid =
            FirebaseServices
                .auth()
                .currentUser
                ?.uid

        if (uid == null) {
            mostrarToast(
                "Inicia sesión para consultar el estado de tu placa"
            )
            return
        }

        val reference =
            FirebaseServices
                .database()
                .getReference("devices/$uid/status")

        val listener =
            object : ValueEventListener {

                override fun onDataChange(
                    snapshot: DataSnapshot
                ) {
                    if (!snapshot.exists()) {
                        return
                    }

                    val humedad =
                        snapshot
                            .child("humedad_porcentaje")
                            .getValue(Int::class.java)

                    val seca =
                        snapshot
                            .child("necesita_riego")
                            .getValue(Boolean::class.java)
                            ?: false

                    val seRego =
                        snapshot
                            .child("se_rego")
                            .getValue(Boolean::class.java)
                            ?: false

                    val motivo =
                        snapshot
                            .child("motivo")
                            .getValue(String::class.java)
                            ?: "Sin motivo"

                    handler.post {
                        if (!isFinishing) {
                            tvRespuestaArduino.text =
                                "Estado: ${if (seca) "SECA" else "HÚMEDA"}\n" +
                                        "Humedad: ${humedad?.let { "$it%" } ?: "sin dato"}\n" +
                                        "Se regó: ${if (seRego) "Sí" else "No"}\n" +
                                        "Motivo: $motivo"
                        }
                    }
                }

                override fun onCancelled(
                    error: DatabaseError
                ) {
                    mostrarToast(
                        "No se pudo leer el estado de Firebase: ${error.message}"
                    )
                }
            }

        referenciaEstado = reference
        listenerEstado = listener

        reference.addValueEventListener(listener)
    }

    private fun enviarComando(
        comando: String,
        mensajeEnviado: String
    ) {
        val errorConfiguracion =
            FirebaseServices.initialize(this)

        if (errorConfiguracion != null) {
            mostrarToast(errorConfiguracion)

            enviarComandoBluetooth(
                comando,
                mensajeEnviado
            )

            return
        }

        val uid =
            FirebaseServices
                .auth()
                .currentUser
                ?.uid

        if (uid == null) {
            mostrarToast("No hay una sesión iniciada")

            enviarComandoBluetooth(
                comando,
                mensajeEnviado
            )

            return
        }

        val referenciaComando =
            FirebaseServices
                .database()
                .getReference("devices/$uid/command")

        val datosComando =
            mapOf(
                "action" to comando.uppercase(),
                "commandId" to UUID.randomUUID().toString(),
                "pending" to true,
                "createdAt" to ServerValue.TIMESTAMP
            )

        referenciaComando
            .setValue(datosComando)
            .addOnSuccessListener {
                mostrarToast(
                    "$mensajeEnviado mediante WiFi/Firebase"
                )
            }
            .addOnFailureListener { error ->
                mostrarToast(
                    "Firebase no disponible: ${error.message}. Intentando Bluetooth."
                )

                enviarComandoBluetooth(
                    comando,
                    mensajeEnviado
                )
            }
    }

    private fun conectarBluetooth() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            solicitudPermisoBluetooth.launch(
                Manifest.permission.BLUETOOTH_CONNECT
            )
            return
        }

        val adapter =
            bluetoothAdapter

        if (adapter == null) {
            mostrarToast("Bluetooth no disponible")
            return
        }

        btnConectar.isEnabled = false

        connectionExecutor.execute {
            var socket: BluetoothSocket? = null

            try {
                if (!adapter.isEnabled) {
                    mostrarErrorConexion(
                        "Activa Bluetooth e inténtalo de nuevo"
                    )
                    return@execute
                }

                val dispositivo: BluetoothDevice? =
                    adapter
                        .bondedDevices
                        .firstOrNull {
                            it.name == "HC-06"
                        }

                if (dispositivo == null) {
                    mostrarErrorConexion(
                        "No se encontró el HC-06 vinculado"
                    )
                    return@execute
                }

                socket =
                    dispositivo
                        .createRfcommSocketToServiceRecord(
                            uuidHc06
                        )

                socket.connect()

                val stream =
                    socket.inputStream

                bluetoothSocket = socket
                inputStream = stream
                escuchandoBluetooth = true

                handler.post {
                    if (!isFinishing) {
                        btnConectar.isEnabled = true

                        Toast.makeText(
                            this,
                            "¡Conectado al HC-06!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                iniciarLecturaBluetooth(stream)

            } catch (_: IOException) {
                cerrarSocket(socket)

                mostrarErrorConexion(
                    "No se pudo conectar con el Arduino"
                )

            } catch (_: SecurityException) {
                cerrarSocket(socket)

                mostrarErrorConexion(
                    "No hay permiso para acceder a Bluetooth"
                )
            }
        }
    }

    private fun iniciarLecturaBluetooth(
        stream: InputStream
    ) {
        Thread(
            {
                val buffer =
                    ByteArray(1024)

                try {
                    while (escuchandoBluetooth) {
                        val bytes =
                            stream.read(buffer)

                        if (bytes < 0) {
                            break
                        }

                        val mensaje =
                            String(
                                buffer,
                                0,
                                bytes,
                                Charsets.UTF_8
                            ).trim()

                        if (mensaje.isNotEmpty()) {
                            handler.post {
                                if (!isFinishing) {
                                    tvRespuestaArduino.text =
                                        mensaje
                                }
                            }
                        }
                    }

                } catch (_: IOException) {
                    if (escuchandoBluetooth) {
                        mostrarToast(
                            "Se perdió la conexión con el Arduino"
                        )
                    }

                } finally {
                    escuchandoBluetooth = false

                    handler.post {
                        if (!isFinishing) {
                            btnConectar.isEnabled =
                                true
                        }
                    }
                }
            },
            "hc06-reader"
        ).start()
    }

    private fun enviarComandoBluetooth(
        comando: String,
        mensajeEnviado: String
    ) {
        connectionExecutor.execute {
            try {
                val socket =
                    bluetoothSocket

                if (
                    socket == null ||
                    !socket.isConnected
                ) {
                    mostrarToast(
                        "No hay conexión mediante Firebase ni Bluetooth"
                    )
                    return@execute
                }

                socket.outputStream.apply {
                    write(
                        "$comando\n"
                            .toByteArray(
                                Charsets.UTF_8
                            )
                    )
                    flush()
                }

                mostrarToast(
                    "$mensajeEnviado mediante Bluetooth"
                )

            } catch (_: IOException) {
                mostrarToast(
                    "Error al enviar el comando por Bluetooth"
                )

            } catch (_: SecurityException) {
                mostrarToast(
                    "No hay permiso para acceder a Bluetooth"
                )
            }
        }
    }

    private fun mostrarErrorConexion(
        mensaje: String
    ) {
        handler.post {
            if (!isFinishing) {
                btnConectar.isEnabled =
                    true

                Toast.makeText(
                    this,
                    mensaje,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun mostrarToast(
        mensaje: String
    ) {
        handler.post {
            if (!isFinishing) {
                Toast.makeText(
                    this,
                    mensaje,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun cerrarSocket(
        socket: BluetoothSocket?
    ) {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
    }

    private fun desconectarBluetooth() {
        escuchandoBluetooth =
            false

        val stream =
            inputStream

        val socket =
            bluetoothSocket

        inputStream =
            null

        bluetoothSocket =
            null

        try {
            stream?.close()
        } catch (_: IOException) {
        }

        cerrarSocket(socket)
    }

    override fun onDestroy() {
        listenerEstado?.let {
            referenciaEstado
                ?.removeEventListener(it)
        }

        listenerEstado =
            null

        referenciaEstado =
            null

        desconectarBluetooth()

        connectionExecutor.shutdownNow()

        super.onDestroy()
    }
}