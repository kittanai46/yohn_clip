package th.yohnclip.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import th.yohnclip.R

/**
 * ชุดสีเดียวใช้ทั้งโหมดสว่างและมืด (แนวเดียวกับดีไซน์ Police Care)
 * สีแดงเลือดหมูใช้กับแถบหัว การ์ดสรุป ปุ่มหลัก และสิ่งที่เลือกอยู่เท่านั้น
 */
@Immutable
data class Palette(
    val bg: Color,        // พื้นหลังจอ
    val card: Color,      // พื้นการ์ด
    val line: Color,      // ขอบการ์ดและเส้นแบ่งแถว
    val line2: Color,     // ขอบปุ่มรองและช่องกรอก
    val ink: Color,       // ตัวหนังสือหลัก
    val muted: Color,     // ข้อความรอง
    val pri: Color,       // ลิงก์และตัวเลขเน้น
    val btn: Color,       // ปุ่มหลัก
    val hero: Color,      // แถบหัวและการ์ดสรุป
    val soft: Color,      // พื้นของสิ่งที่เลือกอยู่
    val sel: Color,       // ตัวหนังสือบนพื้น soft
    val icf: Color,       // พื้นช่องไอคอน
    val ok: Color,
    val okSoft: Color,
    val onOk: Color,
    val errSoft: Color,
    val onErr: Color,
    val nSoft: Color,     // พื้นป้ายกลางๆ และโครงตอนโหลด
    val scrim: Color,
    val dark: Boolean,
)

val LightPalette = Palette(
    bg = Color(0xFFF6F6F7), card = Color.White, line = Color(0xFFE4E4E8), line2 = Color(0xFFC9CBD1),
    ink = Color(0xFF1F2A36), muted = Color(0xFF5A6470),
    pri = Color(0xFFA3122F), btn = Color(0xFFA3122F), hero = Color(0xFFA3122F),
    soft = Color(0xFFF9ECEE), sel = Color(0xFF8B1A2E), icf = Color(0xFFF7E3E6),
    ok = Color(0xFF198754), okSoft = Color(0xFFE6F3EC), onOk = Color(0xFF0F5132),
    errSoft = Color(0xFFFBE7EA), onErr = Color(0xFF8B1A2E),
    nSoft = Color(0xFFEEEEF0), scrim = Color(0x8C000000), dark = false,
)

// โทนเทาอุ่นกับแดงเฉดเดียว ไม่ให้มีสีอมฟ้าปนน้ำตาล
val DarkPalette = Palette(
    bg = Color(0xFF121011), card = Color(0xFF1C191A), line = Color(0xFF443C3E), line2 = Color(0xFF5E5457),
    ink = Color(0xFFF4F1F1), muted = Color(0xFFBBB2B4),
    pri = Color(0xFFF47C8A), btn = Color(0xFFA3122F), hero = Color(0xFF7D1729),
    soft = Color(0xFF4A2730), sel = Color.White, icf = Color(0xFF503037),
    ok = Color(0xFF8FD3A8), okSoft = Color(0xFF20302A), onOk = Color(0xFF8FD3A8),
    errSoft = Color(0xFF44202A), onErr = Color(0xFFF9CBD2),
    nSoft = Color(0xFF2A2526), scrim = Color(0x8C000000), dark = true,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** สีของธีมปัจจุบัน ใช้ใน composable: `C.ink` */
val C: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

// ไฟล์เดียวเป็น variable font ต้องบอกแกนน้ำหนักเอง ไม่งั้นทุกระดับจะหนาเท่ากัน
// ฟอนต์เดียวกับดีไซน์ Police Care (Noto Sans Thai, OFL ดู licenses/NotoSansThai-OFL.txt)
@OptIn(ExperimentalTextApi::class)
private fun notoThai(weight: Int) = Font(
    R.font.noto_sans_thai, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val NotoSansThai = FontFamily(notoThai(400), notoThai(500), notoThai(600), notoThai(700))

/** สไตล์ตัวอักษรตามขนาดในดีไซน์ */
fun textStyle(size: Float, line: Float, weight: Int = 400) = TextStyle(
    fontFamily = NotoSansThai,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
)

private fun scheme(p: Palette) = (if (p.dark) darkColorScheme() else lightColorScheme()).copy(
    primary = p.btn,
    onPrimary = Color.White,
    primaryContainer = p.soft,
    onPrimaryContainer = p.sel,
    secondary = p.ink,
    onSecondary = p.card,
    secondaryContainer = p.soft,
    onSecondaryContainer = p.sel,
    tertiary = p.muted,
    error = p.pri,
    onError = Color.White,
    background = p.bg,
    onBackground = p.ink,
    surface = p.card,
    onSurface = p.ink,
    surfaceVariant = p.nSoft,
    onSurfaceVariant = p.muted,
    outline = p.line2,
    outlineVariant = p.line,
    inverseSurface = p.ink,
    inverseOnSurface = p.card,
    surfaceContainerLowest = p.card,
    surfaceContainerLow = p.card,
    surfaceContainer = p.card,
    surfaceContainerHigh = p.card,
    surfaceContainerHighest = p.nSoft,
)

// คอมโพเนนต์ของ Material ที่ไม่ได้กำหนดสไตล์เองจะใช้ NotoSansThai ด้วย
private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = NotoSansThai),
        displayMedium = displayMedium.copy(fontFamily = NotoSansThai),
        displaySmall = displaySmall.copy(fontFamily = NotoSansThai),
        headlineLarge = headlineLarge.copy(fontFamily = NotoSansThai),
        headlineMedium = headlineMedium.copy(fontFamily = NotoSansThai),
        headlineSmall = headlineSmall.copy(fontFamily = NotoSansThai),
        titleLarge = titleLarge.copy(fontFamily = NotoSansThai),
        titleMedium = titleMedium.copy(fontFamily = NotoSansThai),
        titleSmall = titleSmall.copy(fontFamily = NotoSansThai),
        bodyLarge = bodyLarge.copy(fontFamily = NotoSansThai),
        bodyMedium = bodyMedium.copy(fontFamily = NotoSansThai),
        bodySmall = bodySmall.copy(fontFamily = NotoSansThai),
        labelLarge = labelLarge.copy(fontFamily = NotoSansThai),
        labelMedium = labelMedium.copy(fontFamily = NotoSansThai),
        labelSmall = labelSmall.copy(fontFamily = NotoSansThai),
    )
}

/** ตามโหมดสว่าง/มืดของเครื่อง */
@Composable
fun YohnClipTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme(p), typography = AppTypography, content = content)
    }
}
