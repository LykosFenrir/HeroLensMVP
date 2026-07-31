package com.herolens.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class IntroPage(
    val symbol: String,
    val eyebrow: String,
    val title: String,
    val body: String
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = remember {
        listOf(
            IntroPage(
                symbol = "⌗",
                eyebrow = "THREE SCAN MODES",
                title = "Fast, stable or manual",
                body = "Use Auto Scan for quick live-match picks, Picture Scan for a clear photo or screenshot, or Manual Selection when you need a guaranteed fallback."
            ),
            IntroPage(
                symbol = "AI",
                eyebrow = "ON-DEVICE RECOGNITION",
                title = "Neural AI with a safe fallback",
                body = "When the trained model is installed, portrait crops are classified locally on your phone. Uncertain slots remain editable and the signature matcher stays available as a fallback."
            ),
            IntroPage(
                symbol = "◈",
                eyebrow = "EXPLAINABLE & PRIVATE",
                title = "Know why the pick works",
                body = "Recommendations explain counters and synergy. Nothing is uploaded automatically; optional reviewed training crops remain under your control."
            )
        )
    }
    var page by remember { mutableIntStateOf(0) }

    Surface(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .safeDrawingPadding()
                .padding(24.dp)
        ) {
            Text(
                "HEROLENS V8",
                modifier = Modifier.align(Alignment.TopStart),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black
            )

            AnimatedContent(
                targetState = page,
                transitionSpec = { (fadeIn() togetherWith fadeOut()).using(SizeTransform(clip = false)) },
                modifier = Modifier.align(Alignment.Center)
            ) { index ->
                val item = pages[index]
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier
                            .size(108.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(item.symbol, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(item.eyebrow, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                    Text(item.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Text(item.body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    pages.indices.forEach { index ->
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (index == page) 12.dp else 8.dp)
                                .background(
                                    if (index == page) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.35f),
                                    CircleShape
                                )
                        )
                    }
                }
                Button(
                    onClick = {
                        if (page == pages.lastIndex) onFinish() else page++
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(if (page == pages.lastIndex) "START SCANNING" else "NEXT", fontWeight = FontWeight.Black)
                }
                if (page < pages.lastIndex) {
                    OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Text("SKIP")
                    }
                }
            }
        }
    }
}
