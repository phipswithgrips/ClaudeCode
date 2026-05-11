package com.rezept.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rezept.app.db.AppDatabase
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private data class MedicationCard(
        val position: Int,
        val cbId: Int,
        val cardId: Int,
        val tvId: Int
    )

    private val cards = listOf(
        MedicationCard(1, R.id.cbMed1, R.id.cardMed1, R.id.tvMed1),
        MedicationCard(2, R.id.cbMed2, R.id.cardMed2, R.id.tvMed2),
        MedicationCard(3, R.id.cbMed3, R.id.cardMed3, R.id.tvMed3),
        MedicationCard(4, R.id.cbMed4, R.id.cardMed4, R.id.tvMed4),
        MedicationCard(5, R.id.cbMed5, R.id.cardMed5, R.id.tvMed5),
        MedicationCard(6, R.id.cbMed6, R.id.cardMed6, R.id.tvMed6)
    )

    private var medicationNames: Map<Int, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvSelected = findViewById<TextView>(R.id.tvSelected)
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)

        cards.forEach { card ->
            val cardView = findViewById<LinearLayout>(card.cardId)
            val cb = findViewById<CheckBox>(card.cbId)

            cardView.setOnClickListener {
                cb.isChecked = !cb.isChecked
                updateSelectedLabel(tvSelected)
            }
            cb.setOnCheckedChangeListener { _, _ ->
                updateSelectedLabel(tvSelected)
            }
        }

        btnSubmit.setOnClickListener {
            val checkedCards = cards.filter { findViewById<CheckBox>(it.cbId).isChecked }

            if (checkedCards.isEmpty()) {
                Toast.makeText(this, "Bitte mindestens ein Medikament auswählen", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selected = checkedCards.mapNotNull { medicationNames[it.position] }
            if (selected.isEmpty()) return@setOnClickListener

            val intent = Intent(this, WebFormActivity::class.java)
            intent.putStringArrayListExtra("medications", ArrayList(selected))
            startActivity(intent)
        }

        loadMedicationsFromDb()
    }

    private fun loadMedicationsFromDb() {
        val db = AppDatabase.getInstance(this)
        Executors.newSingleThreadExecutor().execute {
            val medications = db.medicationDao().getAll()
            val names = medications.associate { it.position to it.name }
            runOnUiThread {
                medicationNames = names
                cards.forEach { card ->
                    val name = names[card.position] ?: return@forEach
                    findViewById<TextView>(card.tvId).text = name
                }
            }
        }
    }

    private fun updateSelectedLabel(tv: TextView) {
        val count = cards.count { findViewById<CheckBox>(it.cbId).isChecked }
        tv.text = when (count) {
            0 -> "Kein Medikament ausgewählt"
            1 -> "1 Medikament ausgewählt"
            else -> "$count Medikamente ausgewählt"
        }
    }
}
