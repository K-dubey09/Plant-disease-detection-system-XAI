package com.leafguard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leafguard.app.BuildConfig
import com.leafguard.app.data.inference.ModelIntegrity
import com.leafguard.app.ui.components.LeafScaffold

/**
 * About (v2.6.0.0; v2.7.0.0 reads version + digest from code instead of hardcoded literals). A
 * read-only transparency screen: application version and build, the shipped model's SHA-256 digest
 * prefix (so the on-device asset can be verified against the repository), a model-and-code
 * availability note linking the public GitHub repository and the Kaggle notebook collection, and the
 * AI-assisted-development disclosure. The screen is static: it adds no data-layer or domain-layer
 * types and does not touch the inference path, so the model, the integrity guard, and the occlusion
 * heatmap behave exactly as in 2.6.0.0.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    // Read from code so the screen never drifts from a version bump or a model swap.
    val versionLine = "LeafGuard ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})\n" +
        "Explainable on-device plant disease detection."
    val shaPrefix = ModelIntegrity.EXPECTED_SHA256.take(8)

    LeafScaffold(title = "About LeafGuard", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AboutSection(title = "Version") {
                Text(versionLine, style = MaterialTheme.typography.bodyMedium)
            }
            AboutSection(title = "On-device model") {
                Text(
                    "DINOv2 ViT-S/14 with a domain-adversarial head, converted to an 86.8 MB " +
                        "TensorFlow Lite graph that runs entirely on-device. SHA-256 digest " +
                        "prefix: $shaPrefix... Verify this against the pinned value in " +
                        "ModelIntegrity.kt before trusting a downloaded copy.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            AboutSection(title = "Model and code availability") {
                Text(
                    "Source code, the trained TFLite model, the conversion pipeline, and the " +
                        "seven training notebooks (including the failed iterations) are public.\n\n" +
                        "GitHub: github.com/K-dubey09/Plant-disease-detection-system-XAI\n" +
                        "Kaggle notebooks: kaggle.com/code/kushagradubey09",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            AboutSection(title = "AI-assisted development") {
                Text(
                    "AI coding assistants were used for routine scaffolding (boilerplate UI, " +
                        "Gradle configuration, and first-draft prose) and all generated material " +
                        "was reviewed and revised by the author. The model architecture, the " +
                        "methodology, and all engineering and validation decisions are the " +
                        "author's own.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AboutSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
