package com.family.puzzlepop.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.puzzlepop.BuildConfig
import com.family.puzzlepop.R

/** Hosted copy of docs/PRIVACY.md; the same URL goes in the Play Console's privacy policy field. */
const val PRIVACY_POLICY_URL = "https://github.com/Sirwinnybob/PuzzlePop/blob/main/docs/PRIVACY.md"

/** The open-source libraries in the app, all Apache 2.0 (see the release runtime classpath). */
private val LIBRARIES = listOf(
    "AndroidX: Jetpack Compose, Activity, Navigation, Lifecycle, Room, DataStore, Biometric, Fragment, ExifInterface, Metrics and their AndroidX dependencies" to "The Android Open Source Project",
    "Kotlin standard library, kotlinx.coroutines, kotlinx.serialization" to "JetBrains s.r.o. and Kotlin contributors",
    "JetBrains Java Annotations" to "JetBrains s.r.o.",
    "Guava ListenableFuture" to "The Guava Authors",
    "Okio" to "Square, Inc.",
    "JSpecify" to "The JSpecify Authors",
)

/** About, privacy summary and open-source licenses. Reached from grown-up settings, behind the gate. */
@Composable
fun AboutScreen(onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showLicense by rememberSaveable { mutableStateOf(false) }
    val licenseText = remember {
        context.resources.openRawResource(R.raw.apache_license_2_0).bufferedReader().use { it.readText() }
    }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onDone, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.settings_done), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card {
                Text(stringResource(R.string.about_privacy_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.about_privacy_body), fontSize = 17.sp)
                FilledTonalButton(onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.about_privacy_link), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card {
                Text(stringResource(R.string.about_licenses_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.about_licenses_body), fontSize = 17.sp)
                for ((name, holder) in LIBRARIES) {
                    Column {
                        Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("© $holder", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FilledTonalButton(onClick = { showLicense = !showLicense }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(
                        stringResource(if (showLicense) R.string.about_license_hide else R.string.about_license_show),
                        fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    )
                }
                if (showLicense) {
                    Text(licenseText, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
