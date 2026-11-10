package ai.pleos.challenge.arrival

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One look for every screen. The display is large and read from the driver's seat at 160 dpi, so the type
 * is bigger than phone defaults, and colour is kept for the one action or state that matters on a screen.
 */
object Ui {
    val Bg = Color(0xFF121416)
    val Panel = Color(0xFF1C1F22)
    val Raised = Color(0xFF272B2F)
    val Line = Color(0xFF353A40)
    val Ink = Color(0xFFF1F2F0)
    val Muted = Color(0xFFA3A9AE)
    val Accent = Color(0xFF5EC8B8)
    val OnAccent = Color(0xFF0E1B19)
    val Warn = Color(0xFFF0B45C)
    val Star = Color(0xFFF1CD84)

    val Title = 34.sp
    val Heading = 28.sp
    val Value = 48.sp
    val Body = 24.sp
    val Small = 20.sp

    val Shape = RoundedCornerShape(12.dp)
    val ControlShape = RoundedCornerShape(10.dp)

    /** Width of the phone hand-off column, the same on every tab so the QR is always in the same place. */
    val ShareWidth = 440.dp
    val Gap = 20.dp
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(Ui.Panel, Ui.Shape).padding(28.dp), content = content)
}

@Composable
fun Heading(text: String, modifier: Modifier = Modifier) =
    Text(text, fontSize = Ui.Heading, fontWeight = FontWeight.Bold, color = Ui.Ink, modifier = modifier)

@Composable
fun Note(text: String, color: Color = Ui.Muted, modifier: Modifier = Modifier) =
    Text(text, fontSize = Ui.Small, color = color, lineHeight = 28.sp, modifier = modifier)

@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, shape = Ui.ControlShape, modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = Ui.Accent, contentColor = Ui.OnAccent),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 14.dp),
    ) { Text(label, fontSize = Ui.Body, fontWeight = FontWeight.Bold) }
}

@Composable
fun QuietButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, shape = Ui.ControlShape, modifier = modifier,
        border = BorderStroke(1.dp, Ui.Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ui.Ink),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
    ) { Text(label, fontSize = Ui.Body) }
}

/** One choice out of a few, as a single joined control. Wraps when the labels do not fit one line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Segmented(labels: List<String>, selected: Int, modifier: Modifier = Modifier, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    FlowRow(modifier.background(Ui.Raised, Ui.ControlShape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index, label ->
            val on = index == selected
            Text(
                label, fontSize = Ui.Small, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                color = if (on) Ui.OnAccent else if (enabled) Ui.Ink else Ui.Muted,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Ui.Accent else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(index) }.padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * The right-hand column of every tab: what goes to the phone, always in the same place and always as a QR on
 * the screen itself, never behind a dialog.
 */
@Composable
fun SharePanel(
    options: List<String>, selected: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.width(Ui.ShareWidth).fillMaxHeight().background(Ui.Panel, Ui.Shape).padding(28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Heading("휴대폰으로 열기")
        // With a single thing to send there is nothing to choose, only to name.
        if (options.size > 1) Segmented(options, selected, onSelect = onSelect)
        else Text(options.first(), fontSize = Ui.Body, color = Ui.Muted)
        content()
    }
}

/** A QR on a white plate, the same size wherever it appears. [text] null shows [empty] in its place. */
@Composable
fun QrPlate(text: String?, description: String, empty: String, size: Dp = 320.dp) {
    val bitmap = remember(text) { text?.let { runCatching { qrBitmap(it, 800).asImageBitmap() }.getOrNull() } }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = description, modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)))
        } else {
            Box(Modifier.size(size).border(1.dp, Ui.Line, RoundedCornerShape(8.dp)).padding(24.dp), contentAlignment = Alignment.Center) {
                Note(empty)
            }
        }
    }
}

/**
 * The mic button used on every tab. Drawn, so it looks the same on any system font. While [listening] the same
 * button turns into a stop button, so the thumb that started the mic is already on the control that cancels it.
 */
@Composable
fun MicButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, large: Boolean = false, listening: Boolean = false, onClick: () -> Unit) {
    val size = if (large) 52.dp else 32.dp
    Button(
        onClick = onClick, enabled = enabled || listening, shape = Ui.ControlShape, modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (listening) Ui.Raised else Ui.Accent, contentColor = if (listening) Ui.Ink else Ui.OnAccent,
        ),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 18.dp),
    ) {
        if (listening) StopIcon(Ui.Warn, size) else MicIcon(if (enabled) Ui.OnAccent else Ui.Muted, size)
        Spacer(Modifier.width(if (large) 20.dp else 14.dp))
        Text(if (listening) "취소" else label, fontSize = if (large) 44.sp else 28.sp, fontWeight = FontWeight.Bold)
    }
}

/** A rounded square: stop listening. */
@Composable
fun StopIcon(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        drawRoundRect(color, Offset(w * 0.2f, w * 0.2f), Size(w * 0.6f, w * 0.6f), CornerRadius(w * 0.12f))
    }
}

@Composable
fun MicIcon(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.09f
        // Capsule, the cradle around it, the stem and the foot.
        drawRoundRect(color, Offset(w * 0.36f, h * 0.06f), Size(w * 0.28f, h * 0.52f), CornerRadius(w * 0.14f))
        drawArc(color, 0f, 180f, false, Offset(w * 0.22f, h * 0.18f), Size(w * 0.56f, h * 0.54f), style = Stroke(stroke, cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.9f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.34f, h * 0.92f), Offset(w * 0.66f, h * 0.92f), stroke, StrokeCap.Round)
    }
}

/** A thin rule between rows of one panel. */
@Composable
fun Rule(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(Ui.Line))
