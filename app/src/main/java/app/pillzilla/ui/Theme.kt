package app.pillzilla.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object PzColors {
    val Ground = Color(0xFFF3F5F7)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF111827)
    val Muted = Color(0xFF4B5563)
    val Line = Color(0xFFE5E7EB)
    val Teal = Color(0xFF0F766E)
    val TealSoft = Color(0xFFE6F4F1)
    val TealMid = Color(0xFF5EB8AA)
    val TealPale = Color(0xFFCCEBE5)
    val Night = Color(0xFF0B3B37)
    val Mint = Color(0xFF99F6E4)
    val Amber = Color(0xFFF59E0B)
    val AmberSoft = Color(0xFFFEF3C7)
    val AmberInk = Color(0xFF92400E)
    val Red = Color(0xFFB91C1C)
    val RedSoft = Color(0xFFFEE2E2)
    val Green = Color(0xFF166534)
    val GreenSoft = Color(0xFFDCFCE7)
}

@Composable
fun PillzillaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PzColors.Teal,
            onPrimary = Color.White,
            primaryContainer = PzColors.TealPale,
            onPrimaryContainer = Color(0xFF0F4F4A),
            secondaryContainer = PzColors.TealSoft,
            background = PzColors.Ground,
            surface = PzColors.Surface,
            onSurface = PzColors.Ink,
            onSurfaceVariant = PzColors.Muted,
            surfaceVariant = PzColors.Line,
            error = PzColors.Red,
        ),
        content = content,
    )
}
