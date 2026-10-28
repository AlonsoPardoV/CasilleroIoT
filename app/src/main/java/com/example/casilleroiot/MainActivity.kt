package com.example.casilleroiot

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val TAG = "CasilleroFirebase"

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private lateinit var editTextCorreo: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var textViewEstado: TextView
    private lateinit var textViewDato: TextView
    private lateinit var btnAbrir: Button
    private lateinit var btnGuardarDato: Button
    private lateinit var btnCerrarSesion: Button
    private lateinit var tvEstado: TextView
    private lateinit var tvLog: TextView

    private var estadoCasillero = 0 // 0 = Cerrado, 1 = Abierto
    private val logs = StringBuilder("• Sistema iniciado correctamente\n")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "onCreate() llamado")

        auth = Firebase.auth
        database = Firebase.database.reference

        editTextCorreo = findViewById(R.id.editTextCorreo)
        editTextPassword = findViewById(R.id.editTextPassword)
        textViewEstado = findViewById(R.id.textViewEstado)
        textViewDato = findViewById(R.id.textViewDato)
        tvEstado = findViewById(R.id.tvEstado)
        btnAbrir = findViewById(R.id.btnAbrir)
        btnGuardarDato = findViewById(R.id.btnGuardarDato)
        btnCerrarSesion = findViewById(R.id.btnCerrarSesion)
        tvLog = findViewById(R.id.tvLog)

        // Validar estado inicial de sesión (bloquea controles si no hay usuario)
        verificarSesionActiva()

        findViewById<Button>(R.id.btnRegistrar).setOnClickListener { registrarUsuario() }
        findViewById<Button>(R.id.btnIniciarSesion).setOnClickListener { iniciarSesion() }

        // Botón de Cerrar Sesión
        btnCerrarSesion.setOnClickListener {
            auth.signOut()
            actualizarControlesUI(false)
            textViewEstado.text = "Estado de sesión: Sin autenticar"
            textViewDato.text = "Nube: Sin datos sincronizados"
            Toast.makeText(this, "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()
        }

        // Botón Toggle para Abrir / Cerrar el Casillero
        btnAbrir.setOnClickListener {
            if (auth.currentUser == null) {
                Toast.makeText(this, "Acceso denegado: Inicia sesión primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val horaActual = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

            if (estadoCasillero == 0) {
                // Abrir casillero
                estadoCasillero = 1
                tvEstado.text = "ESTADO: ABIERTO 🔓"
                tvEstado.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                btnAbrir.text = "Cerrar Casillero"
                btnAbrir.setBackgroundColor(resources.getColor(android.R.color.holo_green_dark))
                Toast.makeText(this, "Casillero abierto", Toast.LENGTH_SHORT).show()
                logs.insert(0, "[$horaActual] - Casillero Abierto\n")
            } else {
                // Cerrar casillero
                estadoCasillero = 0
                tvEstado.text = "ESTADO: CERRADO (Seguro) 🔒"
                tvEstado.setTextColor(resources.getColor(android.R.color.holo_green_light))
                btnAbrir.text = "Abrir Casillero"
                btnAbrir.setBackgroundColor(resources.getColor(android.R.color.holo_red_dark))
                Toast.makeText(this, "Casillero cerrado y asegurado", Toast.LENGTH_SHORT).show()
                logs.insert(0, "[$horaActual] - Casillero Cerrado\n")
            }
            tvLog.text = logs.toString()
        }

        // Botón Sincronizar en la Nube
        btnGuardarDato.setOnClickListener {
            guardarDatoNube(estadoCasillero)
        }
    }

    private fun verificarSesionActiva() {
        if (auth.currentUser != null) {
            textViewEstado.text = "Sesión activa: ${auth.currentUser?.email}"
            actualizarControlesUI(true)
            leerDatosNube()
        } else {
            actualizarControlesUI(false)
        }
    }

    private fun actualizarControlesUI(autenticado: Boolean) {
        // Si no está autenticado, se bloquean los botones de control del casillero
        btnAbrir.isEnabled = autenticado
        btnGuardarDato.isEnabled = autenticado
        btnCerrarSesion.isEnabled = autenticado

        if (!autenticado) {
            btnAbrir.alpha = 0.5f
            btnGuardarDato.alpha = 0.5f
            btnCerrarSesion.alpha = 0.5f
        } else {
            btnAbrir.alpha = 1.0f
            btnGuardarDato.alpha = 1.0f
            btnCerrarSesion.alpha = 1.0f
        }
    }

    private fun registrarUsuario() {
        val correo = editTextCorreo.text.toString().trim()
        val password = editTextPassword.text.toString().trim()

        if (correo.isBlank() || password.isBlank()) {
            Toast.makeText(this, "Completa correo y contraseña", Toast.LENGTH_SHORT).show()
            return
        }

        auth.createUserWithEmailAndPassword(correo, password)
            .addOnCompleteListener(this) { tarea ->
                if (tarea.isSuccessful) {
                    Toast.makeText(this, "¡Usuario registrado con éxito!", Toast.LENGTH_SHORT).show()
                    textViewEstado.text = "Sesión iniciada como: ${auth.currentUser?.email}"
                    actualizarControlesUI(true)
                    leerDatosNube()
                } else {
                    Toast.makeText(this, "Error: ${tarea.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun iniciarSesion() {
        val correo = editTextCorreo.text.toString().trim()
        val password = editTextPassword.text.toString().trim()

        if (correo.isBlank() || password.isBlank()) {
            Toast.makeText(this, "Completa correo y contraseña", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(correo, password)
            .addOnCompleteListener(this) { tarea ->
                if (tarea.isSuccessful) {
                    Toast.makeText(this, "¡Bienvenido de vuelta!", Toast.LENGTH_SHORT).show()
                    textViewEstado.text = "Sesión iniciada como: ${auth.currentUser?.email}"
                    actualizarControlesUI(true)
                    leerDatosNube()
                } else {
                    textViewEstado.text = "Error al iniciar sesión"
                    Toast.makeText(this, "Credenciales incorrectas", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun guardarDatoNube(valorEstado: Int) {
        val uid = auth.currentUser?.uid ?: return
        database.child("usuarios").child(uid).child("estadoCasillero").setValue(valorEstado)
            .addOnSuccessListener {
                Toast.makeText(this, "Estado sincronizado en la nube", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun leerDatosNube() {
        val uid = auth.currentUser?.uid ?: return

        database.child("usuarios").child(uid).child("estadoCasillero")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val valor = snapshot.getValue(Int::class.java) ?: 0
                    val textoEstado = if (valor == 1) "Abierto" else "Cerrado (Seguro)"
                    textViewDato.text = "Nube - Estado actual: $textoEstado"
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@MainActivity, "Error al leer la nube: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}