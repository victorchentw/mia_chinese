package mia.chinese.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val MiaColors = darkColors(
    primary = Color(0xFF61D9D0),
    onPrimary = Color(0xFF06201F),
    secondary = Color(0xFFFFC857),
    onSecondary = Color(0xFF261900),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFF5F7FA),
    surface = Color(0xFF162236),
    onSurface = Color(0xFFF5F7FA),
    error = Color(0xFFFF8A80)
)

private val BaseTypography = Typography()
private val MiaTypography = BaseTypography.copy(
    h4 = BaseTypography.h4.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    h5 = BaseTypography.h5.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    h6 = BaseTypography.h6.copy(fontSize = 23.sp, fontWeight = FontWeight.Bold),
    body1 = BaseTypography.body1.copy(fontSize = 22.sp, lineHeight = 30.sp),
    body2 = BaseTypography.body2.copy(fontSize = 18.sp, lineHeight = 26.sp),
    button = BaseTypography.button.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold)
)

@Composable
fun MiaChineseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = MiaColors,
        typography = MiaTypography,
        content = content
    )
}
