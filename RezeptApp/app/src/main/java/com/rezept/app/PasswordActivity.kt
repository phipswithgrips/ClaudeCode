package com.rezept.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest

class PasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password)

        val prefs = getSharedPreferences("rezept_prefs", Context.MODE_PRIVATE)
        val savedHash = prefs.getString("password_hash", null)

        val tvTitle = findViewById<TextView>(R.id.tvPasswordTitle)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etPasswordConfirm = findViewById<EditText>(R.id.etPasswordConfirm)
        val btnConfirm = findViewById<Button>(R.id.btnPasswordConfirm)

        if (savedHash == null) {
            // Erster Start – Passwort festlegen
            tvTitle.text = "Sicherheitspasswort festlegen"
            etPasswordConfirm.visibility = View.VISIBLE
            btnConfirm.text = "Passwort speichern"

            btnConfirm.setOnClickListener {
                val pw = etPassword.text.toString()
                val pwConfirm = etPasswordConfirm.text.toString()

                if (pw.length < 4) {
                    Toast.makeText(this, "Mindestens 4 Zeichen erforderlich", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (pw != pwConfirm) {
                    Toast.makeText(this, "Passworter stimmen nicht überein", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                prefs.edit().putString("password_hash", sha256(pw)).apply()
                openMain()
            }
        } else {
            // Folgestart – Passwort prüfen
            tvTitle.text = "Passwort eingeben"
            etPasswordConfirm.visibility = View.GONE
            btnConfirm.text = "Entsperren"

            btnConfirm.setOnClickListener {
                val pw = etPassword.text.toString()
                if (sha256(pw) == savedHash) {
                    openMain()
                } else {
                    Toast.makeText(this, "Falsches Passwort", Toast.LENGTH_SHORT).show()
                    etPassword.text.clear()
                    etPassword.requestFocus()
                }
            }

            // Enter-Taste bestätigt auch
            etPassword.setOnEditorActionListener { _, _, _ ->
                btnConfirm.performClick()
                true
            }
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
