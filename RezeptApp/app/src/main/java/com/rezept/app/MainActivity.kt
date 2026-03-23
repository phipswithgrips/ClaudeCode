package com.rezept.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    data class Medication(
        val name: String,
        val cbId: Int,
        val cardId: Int
    )

    private val medications = listOf(
        Medication("Ramipril 5mg",       R.id.cbMed1, R.id.cardMed1),
        Medication("Torasemid 2,5mg",    R.id.cbMed2, R.id.cardMed2),
        Medication("Lercanidipin 10mg",  R.id.cbMed3, R.id.cardMed3),
        Medication("Doxazosin 1mg",      R.id.cbMed4, R.id.cardMed4),
        Medication("Rosuvastatin 10mg",  R.id.cbMed5, R.id.cardMed5),
        Medication("Bisoprolol 2,5mg",   R.id.cbMed6, R.id.cardMed6)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvSelected = findViewById<TextView>(R.id.tvSelected)
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)

        // Karte anklicken aktiviert Checkbox
        medications.forEach { med ->
            val card = findViewById<LinearLayout>(med.cardId)
            val cb = findViewById<CheckBox>(med.cbId)

            card.setOnClickListener {
                cb.isChecked = !cb.isChecked
                updateSelectedLabel(tvSelected)
            }
            cb.setOnCheckedChangeListener { _, _ ->
                updateSelectedLabel(tvSelected)
            }
        }

        btnSubmit.setOnClickListener {
            val selected = medications
                .filter { findViewById<CheckBox>(it.cbId).isChecked }
                .map { it.name }

            if (selected.isEmpty()) {
                Toast.makeText(this, "Bitte mindestens ein Medikament auswählen", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, WebFormActivity::class.java)
            intent.putStringArrayListExtra("medications", ArrayList(selected))
            startActivity(intent)
        }
    }

    private fun updateSelectedLabel(tv: TextView) {
        val count = medications.count { findViewById<CheckBox>(it.cbId).isChecked }
        tv.text = when (count) {
            0 -> "Kein Medikament ausgewählt"
            1 -> "1 Medikament ausgewählt"
            else -> "$count Medikamente ausgewählt"
        }
    }
}
