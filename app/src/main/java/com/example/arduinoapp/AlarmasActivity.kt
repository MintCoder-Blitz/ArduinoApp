package com.example.arduinoapp

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import java.util.Locale

class AlarmasActivity : AppCompatActivity() {
    private lateinit var alarmasRef: DatabaseReference
    private lateinit var listaAlarmas: LinearLayout
    private lateinit var tvVacio: TextView
    private lateinit var btnAgregar: Button
    private var listenerAlarmas: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_alarmas)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        listaAlarmas = findViewById(R.id.listaAlarmas)
        tvVacio = findViewById(R.id.tvAlarmasVacio)
        btnAgregar = findViewById(R.id.btnAgregarAlarma)
        findViewById<Button>(R.id.btnVolverAlarmas).setOnClickListener { finish() }

        val configError = FirebaseServices.initialize(this)
        if (configError != null) {
            tvVacio.text = configError
            btnAgregar.isEnabled = false
            return
        }

        val uid = FirebaseServices.auth().currentUser?.uid
        if (uid == null) {
            tvVacio.text = "Inicia sesión para administrar tus horarios."
            btnAgregar.isEnabled = false
            return
        }

        alarmasRef = FirebaseServices.database().getReference("devices/$uid/alarms")
        btnAgregar.setOnClickListener { mostrarSelectorHora() }
        observarAlarmas()
    }

    private fun observarAlarmas() {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listaAlarmas.removeAllViews()
                val alarmas = snapshot.children.mapNotNull { child ->
                    val alarma = child.getValue(WateringAlarm::class.java)
                    if (child.key == null || alarma == null) null else child.key!! to alarma
                }.sortedWith(compareBy({ it.second.hour }, { it.second.minute }))

                tvVacio.visibility = if (alarmas.isEmpty()) View.VISIBLE else View.GONE
                btnAgregar.isEnabled = alarmas.size < MAX_ALARMAS
                alarmas.forEach { (key, alarma) -> agregarFila(key, alarma) }
            }

            override fun onCancelled(error: DatabaseError) {
                tvVacio.visibility = View.VISIBLE
                tvVacio.text = "No se pudieron cargar los horarios: ${error.message}"
            }
        }
        listenerAlarmas = listener
        alarmasRef.addValueEventListener(listener)
    }

    private fun agregarFila(key: String, alarma: WateringAlarm) {
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            background = getDrawable(R.drawable.alarm_row_background)
        }
        val detalle = TextView(this).apply {
            text = String.format(
                Locale.getDefault(),
                "%02d:%02d  ·  Todos los días  ·  %d s",
                alarma.hour,
                alarma.minute,
                alarma.durationSeconds
            )
            textSize = 16f
            setTextColor(getColor(android.R.color.white))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val interruptor = Switch(this).apply {
            isChecked = alarma.enabled
            setOnCheckedChangeListener { _, activada ->
                alarmasRef.child(key).child("enabled").setValue(activada)
                    .addOnFailureListener { error ->
                        Toast.makeText(
                            this@AlarmasActivity,
                            "No se pudo actualizar: ${error.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }
        fila.addView(detalle)
        fila.addView(interruptor)

        val eliminar = Button(this).apply {
            text = "Eliminar"
            setOnClickListener {
                alarmasRef.child(key).removeValue()
                    .addOnFailureListener { error ->
                        Toast.makeText(
                            this@AlarmasActivity,
                            "No se pudo eliminar: ${error.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }
        fila.addView(eliminar)
        listaAlarmas.addView(
            fila,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.alarm_row_spacing)
            }
        )
    }

    private fun mostrarSelectorHora() {
        TimePickerDialog(
            this,
            { _, hora, minuto -> guardarAlarma(hora, minuto) },
            7,
            0,
            true
        ).show()
    }

    private fun guardarAlarma(hora: Int, minuto: Int) {
        val nuevaAlarma = alarmasRef.push()
        nuevaAlarma.setValue(WateringAlarm(hour = hora, minute = minuto))
            .addOnSuccessListener {
                Toast.makeText(this, "Horario diario agregado", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "No se pudo guardar el horario: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    override fun onDestroy() {
        if (::alarmasRef.isInitialized) {
            listenerAlarmas?.let { alarmasRef.removeEventListener(it) }
        }
        super.onDestroy()
    }

    companion object {
        private const val MAX_ALARMAS = 8
    }
}
