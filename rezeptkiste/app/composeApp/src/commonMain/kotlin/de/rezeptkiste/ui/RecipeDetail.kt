package de.rezeptkiste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.rezeptkiste.AppController
import de.rezeptkiste.db.Label
import de.rezeptkiste.db.Photo
import de.rezeptkiste.db.Recipe
import de.rezeptkiste.sync.AppJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

private val nutritionLabels = listOf(
    "serving_size" to "Portionsgröße",
    "kcal" to "Kalorien (kcal)",
    "fat" to "Fett (g)",
    "saturated_fat" to "davon gesättigt (g)",
    "carbohydrates" to "Kohlenhydrate (g)",
    "sugar" to "davon Zucker (g)",
    "fiber" to "Ballaststoffe (g)",
    "protein" to "Eiweiß (g)",
    "sodium" to "Natrium (mg)",
    "cholesterol" to "Cholesterin (mg)",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeDetail(
    recipe: Recipe,
    photos: List<Photo>,
    labelsById: Map<String, Label>,
    controller: AppController,
    onBack: (() -> Unit)?,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    TextButton(onClick = onBack) { Text("← Zurück", color = RkColors.Text) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { controller.toggleFavourite(recipe) }) {
                    Text(
                        if (recipe.is_favourite == 1L) "♥ Favorit" else "♡ Favorit",
                        color = if (recipe.is_favourite == 1L) RkColors.Accent else RkColors.TextSecondary,
                    )
                }
            }

            photos.firstOrNull()?.let { cover ->
                RemoteImage(
                    cover.sha256, "medium", controller,
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(if (twoColumns) 320.dp else 240.dp).clip(RoundedCornerShape(12.dp)),
                )
            }

            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(recipe.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                if (recipe.rating > 0) Text(stars(recipe.rating), color = RkColors.Accent)

                val facts = listOfNotNull(
                    recipe.servings_text?.let { "Portionen" to it },
                    formatMinutes(recipe.prep_min)?.let { "Arbeitszeit" to it },
                    formatMinutes(recipe.cook_min)?.let { "Kochzeit" to it },
                    formatMinutes(recipe.total_min)?.let { "Gesamt" to it },
                    (recipe.source_name ?: recipe.source_url)?.let { "Quelle" to it },
                )
                if (facts.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        facts.forEach { (k, v) ->
                            Column {
                                Text(k, style = MaterialTheme.typography.labelMedium, color = RkColors.TextSecondary)
                                Text(v, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                val chips = recipe.labelIds().mapNotNull { labelsById[it]?.name }
                if (chips.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        chips.forEach { name ->
                            Surface(shape = RoundedCornerShape(16.dp), color = RkColors.SurfaceHigh) {
                                Text(name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
                recipe.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }

            if (twoColumns) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.widthIn(max = 360.dp).weight(0.4f)) { Ingredients(recipe) }
                    Column(Modifier.weight(0.6f)) { Directions(recipe) }
                }
            } else {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Ingredients(recipe)
                    Spacer(Modifier.height(16.dp))
                    Directions(recipe)
                }
            }

            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recipe.notes?.takeIf { it.isNotBlank() }?.let {
                    SectionTitle("Notizen")
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
                Nutrition(recipe.nutrition)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = RkColors.Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
private fun Ingredients(recipe: Recipe) {
    val lines = recipe.ingredients_text?.lines().orEmpty()
    if (lines.isEmpty()) return
    SectionTitle("Zutaten")
    lines.forEach { line ->
        when {
            line.isBlank() -> Spacer(Modifier.height(8.dp))
            isHeading(line) -> Text(line.trim(), fontWeight = FontWeight.SemiBold, color = RkColors.Accent, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
            else -> Row(Modifier.padding(vertical = 3.dp)) {
                Text("-", color = RkColors.TextSecondary, modifier = Modifier.width(16.dp))
                Text(line.trim(), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun Directions(recipe: Recipe) {
    val lines = recipe.directions_text?.lines()?.filter { it.isNotBlank() }.orEmpty()
    if (lines.isEmpty()) return
    SectionTitle("Zubereitung")
    var step = 0
    lines.forEach { line ->
        if (isHeading(line)) {
            Text(line.trim(), fontWeight = FontWeight.SemiBold, color = RkColors.Accent, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        } else {
            step++
            Row(Modifier.padding(vertical = 6.dp)) {
                Text("$step", color = RkColors.Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(28.dp))
                Text(line.trim(), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun Nutrition(raw: String?) {
    val obj: JsonObject = raw?.let { runCatching { AppJson.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return
    val rows = nutritionLabels.mapNotNull { (key, label) ->
        (obj[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let { label to it }
    }
    if (rows.isEmpty()) return
    SectionTitle("Nährwerte")
    Surface(shape = RoundedCornerShape(8.dp), color = RkColors.Surface, modifier = Modifier.widthIn(max = 420.dp)) {
        Column(Modifier.padding(12.dp)) {
            rows.forEachIndexed { i, (label, value) ->
                Row(
                    Modifier.fillMaxWidth().background(if (i % 2 == 1) RkColors.SurfaceHigh else RkColors.Surface).padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(label, modifier = Modifier.weight(1f), color = RkColors.TextSecondary)
                    Text(value)
                }
            }
        }
    }
}
