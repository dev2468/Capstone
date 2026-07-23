package com.devc010.mcpapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.gson.Gson
import kotlinx.coroutines.delay

// ── Color constants for markdown renderer ────────────────────────────────────
private val CodeBg = Color(0xFF1A1A2E)
private val CodeBorderBlue = Color(0xFF4285F4)
private val InlineCodeBg = Color(0xFF2A2A3E)
private val InlineCodeText = Color(0xFF80CBC4)
private val TableHeader = Color(0xFF1565C0)
private val TableRowEven = Color(0xFF1E1E1E)
private val TableRowOdd = Color(0xFF252525)
private val LinkColor = Color(0xFF4285F4)

// ── Chart JSON Data structures — ChartData is defined globally in GeminiApiModels.kt

sealed class ContentSegment {
    data class Text(val content: String) : ContentSegment()
    data class Chart(val data: ChartData) : ContentSegment()
}

// Splits full raw response string by [CHART:{...}] tags
fun parseSegments(text: String): List<ContentSegment> {
    val segments = mutableListOf<ContentSegment>()
    val regex = Regex("""\[CHART:(\{.*?\})\]""")
    val matches = regex.findAll(text).toList()

    if (matches.isEmpty()) {
        segments.add(ContentSegment.Text(text))
        return segments
    }

    var lastIndex = 0
    val gson = com.google.gson.GsonBuilder()
        .registerTypeAdapter(ChartData::class.java, ChartDataDeserializer())
        .create()
    for (match in matches) {
        if (match.range.first > lastIndex) {
            segments.add(ContentSegment.Text(text.substring(lastIndex, match.range.first)))
        }
        val jsonStr = match.groupValues[1]
        try {
            val chartData = gson.fromJson(jsonStr, ChartData::class.java)
            if (chartData != null && chartData.labels != null && chartData.values != null) {
                segments.add(ContentSegment.Chart(chartData))
            }
        } catch (e: Exception) {
            // skip silently (omit this chart block entirely)
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        segments.add(ContentSegment.Text(text.substring(lastIndex)))
    }
    return segments
}

// ── Sealed block types produced by the line parser ───────────────────────────
sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class CodeFence(val language: String, val code: String) : MdBlock()
    data class BulletList(val items: List<String>) : MdBlock()
    data class NumberedList(val items: List<String>) : MdBlock()
    data class MdTable(val headers: List<String>, val rows: List<List<String>>) : MdBlock()
    object Divider : MdBlock()
}

// ── Markdown parser (line-based) ─────────────────────────────────────────────
fun parseMarkdownBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // Code fence ```lang
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MdBlock.CodeFence(lang, codeLines.joinToString("\n")))
            i++ // skip closing ```
            continue
        }

        // Heading # / ## / ###
        val headingMatch = Regex("""^(#{1,3})\s+(.+)$""").find(trimmed)
        if (headingMatch != null) {
            blocks.add(MdBlock.Heading(headingMatch.groupValues[1].length, headingMatch.groupValues[2]))
            i++
            continue
        }

        // Horizontal divider --- or ***
        if (trimmed.matches(Regex("""^-{3,}$""")) || trimmed.matches(Regex("""^\*{3,}$"""))) {
            blocks.add(MdBlock.Divider)
            i++
            continue
        }

        // Table  | col | col |
        if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            val tableLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                tableLines.add(lines[i].trim())
                i++
            }
            if (tableLines.size >= 2) {
                val headers = tableLines[0].split("|").map { it.trim() }.filter { it.isNotBlank() }
                val rows = tableLines.drop(2).map { row ->
                    row.split("|").map { it.trim() }.filter { it.isNotBlank() }
                }
                if (headers.isNotEmpty()) blocks.add(MdBlock.MdTable(headers, rows))
            }
            continue
        }

        // Bullet list  - item / * item
        if (trimmed.matches(Regex("""^[-*]\s+.+"""))) {
            val items = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().matches(Regex("""^[-*]\s+.+"""))) {
                items.add(lines[i].trim().removePrefix("-").removePrefix("*").trim())
                i++
            }
            blocks.add(MdBlock.BulletList(items))
            continue
        }

        // Numbered list  1. item
        if (trimmed.matches(Regex("""^\d+\.\s+.+"""))) {
            val items = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().matches(Regex("""^\d+\.\s+.+"""))) {
                items.add(lines[i].trim().replace(Regex("""^\d+\.\s+"""), ""))
                i++
            }
            blocks.add(MdBlock.NumberedList(items))
            continue
        }

        // Paragraph — collect consecutive non-special lines
        if (trimmed.isNotBlank()) {
            val paraLines = mutableListOf<String>()
            while (i < lines.size) {
                val l = lines[i].trim()
                if (l.isBlank() || l.startsWith("#") || l.startsWith("```") ||
                    l.startsWith("|") || l.matches(Regex("""^[-*]\s+.+""")) ||
                    l.matches(Regex("""^\d+\.\s+.+""")) || l.matches(Regex("""^-{3,}$"""))
                ) break
                paraLines.add(lines[i])
                i++
            }
            if (paraLines.isNotEmpty()) blocks.add(MdBlock.Paragraph(paraLines.joinToString(" ")))
            continue
        }

        i++
    }

    return blocks
}

// ── Inline markdown → AnnotatedString ────────────────────────────────────────
private fun AnnotatedString.Builder.appendInlineSegment(text: String): String? {
    val link       = Regex("""\[([^\]]+)\]\(([^)]+)\)""").find(text)
    val code       = Regex("""`([^`]+)`""").find(text)
    val boldItalic = Regex("""\*\*\*(.+?)\*\*\*""").find(text)
    val bold       = Regex("""\*\*(.+?)\*\*""").find(text)
    val italic     = Regex("""\*(.+?)\*""").find(text)

    val first = listOfNotNull(
        link?.let { "link" to it },
        code?.let { "code" to it },
        boldItalic?.let { "bolditalic" to it },
        bold?.let { "bold" to it },
        italic?.let { "italic" to it }
    ).minByOrNull { it.second.range.first } ?: return null

    val (kind, match) = first
    if (match.range.first > 0) append(text.substring(0, match.range.first))

    when (kind) {
        "link" -> {
            pushStringAnnotation("URL", match.groupValues[2])
            withStyle(SpanStyle(color = LinkColor, textDecoration = TextDecoration.Underline)) { append(match.groupValues[1]) }
            pop()
        }
        "code"       -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = InlineCodeBg, color = InlineCodeText)) { append(match.groupValues[1]) }
        "bolditalic" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) { append(match.groupValues[1]) }
        "bold"       -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
        "italic"     -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[1]) }
    }

    return text.substring(match.range.last + 1)
}

fun buildInlineAnnotated(raw: String): AnnotatedString = buildAnnotatedString {
    var text = raw
    while (text.isNotEmpty()) {
        val remaining = appendInlineSegment(text)
        if (remaining == null) {
            append(text)
            break
        }
        text = remaining
    }
}

// ── Root composable ───────────────────────────────────────────────────────────
@Composable
fun MarkdownContent(text: String, modifier: Modifier = Modifier) {
    val segments = remember(text) { parseSegments(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (segment in segments) {
            when (segment) {
                is ContentSegment.Text -> {
                    val blocks = remember(segment.content) { parseMarkdownBlocks(segment.content) }
                    for (block in blocks) {
                        when (block) {
                            is MdBlock.Heading      -> MdHeading(block.level, block.text)
                            is MdBlock.Paragraph    -> MdParagraph(block.text)
                            is MdBlock.CodeFence    -> MdCodeBlock(block.language, block.code)
                            is MdBlock.BulletList   -> MdBulletList(block.items)
                            is MdBlock.NumberedList -> MdNumberedList(block.items)
                            is MdBlock.MdTable      -> MdTable(block.headers, block.rows)
                            MdBlock.Divider         -> HorizontalDivider(color = CodeBorderBlue.copy(alpha = 0.45f), thickness = 1.dp)
                        }
                    }
                }
                is ContentSegment.Chart -> {
                    MdChart(segment.data)
                }
            }
        }
    }
}

// ── Block composables ─────────────────────────────────────────────────────────

@Composable
private fun MdHeading(level: Int, text: String) {
    val (size, weight) = when (level) {
        1    -> 22.sp to FontWeight.Bold
        2    -> 18.sp to FontWeight.Bold
        else -> 15.sp to FontWeight.SemiBold
    }
    Text(
        text = text,
        color = Color.White,
        fontSize = size,
        fontWeight = weight,
        modifier = Modifier.padding(top = if (level == 1) 4.dp else 2.dp)
    )
}

@Composable
private fun MdParagraph(text: String) {
    val context = LocalContext.current
    val annotated = remember(text) { buildInlineAnnotated(text) }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
            }
        }
    )
}

@Composable
private fun MdBulletList(items: List<String>) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (item in items) {
            val annotated = remember(item) { buildInlineAnnotated(item) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", color = CodeBorderBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                ClickableText(
                    text = annotated,
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    onClick = { offset ->
                        annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MdNumberedList(items: List<String>) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((index, item) in items.withIndex()) {
            val annotated = remember(item) { buildInlineAnnotated(item) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${index + 1}.", color = CodeBorderBlue, fontWeight = FontWeight.Bold)
                ClickableText(
                    text = annotated,
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    onClick = { offset ->
                        annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MdCodeBlock(language: String, code: String) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CodeBg)
            .border(1.dp, CodeBorderBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight()
                .width(3.dp)
                .background(CodeBorderBlue)
        )
        Column(
            modifier = Modifier
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" },
                    color = CodeBorderBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(
                    onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("code", code))
                        copied = true
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = if (copied) "Copied!" else "Copy code",
                        tint = if (copied) Color(0xFF81C784) else Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Text(
                    text = code,
                    color = Color(0xFFE0E0E0),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun MdTable(headers: List<String>, rows: List<List<String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, CodeBorderBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TableHeader)
                .padding(vertical = 8.dp, horizontal = 12.dp)
        ) {
            for (header in headers) {
                Text(
                    text = buildInlineAnnotated(header),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp
                )
            }
        }
        // Data rows
        for ((rowIndex, row) in rows.withIndex()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (rowIndex % 2 == 0) TableRowEven else TableRowOdd)
                    .padding(vertical = 7.dp, horizontal = 12.dp)
            ) {
                for (col in headers.indices) {
                    Text(
                        text = buildInlineAnnotated(row.getOrElse(col) { "" }),
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MdChart(chartData: ChartData) {
    val labels = chartData.labels
    val values = chartData.values
    if (values.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = chartData.title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0A0A0A))
        ) {
            val colors = listOf(
                0xFF4285F4.toInt(), // primary blue
                0xFF1A73E8.toInt(), // google blue
                0xFF8AB4F8.toInt(), // light blue
                0xFF1967D2.toInt(), // dark blue
                0xFFADCCFF.toInt()  // pale blue
            )

            when (chartData.type.lowercase()) {
                "line" -> AndroidView(
                    factory = { ctx ->
                        LineChart(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(0xFF0A0A0A.toInt())
                            description.isEnabled = false
                            legend.apply {
                                textColor = 0xFFFFFFFF.toInt()
                                isEnabled = true
                                textSize = 9f
                            }
                            setDrawGridBackground(false)
                            axisLeft.apply {
                                textColor = 0xFFFFFFFF.toInt()
                                gridColor = 0xFF1E1E1E.toInt()
                                axisLineColor = 0xFF4285F4.toInt()
                            }
                            axisRight.isEnabled = false
                            xAxis.apply {
                                position = XAxis.XAxisPosition.BOTTOM
                                textColor = 0xFFFFFFFF.toInt()
                                gridColor = 0xFF1E1E1E.toInt()
                                valueFormatter = IndexAxisValueFormatter(labels)
                                granularity = 1f
                            }
                            
                            val dataSets = values.mapIndexed { i, series ->
                                val entries = series.mapIndexed { idx, v -> Entry(idx.toFloat(), v) }
                                val seriesName = chartData.seriesLabels?.getOrNull(i) ?: "Series ${i + 1}"
                                LineDataSet(entries, seriesName).apply {
                                    color = colors[i % colors.size]
                                    setCircleColor(colors[i % colors.size])
                                    valueTextColor = 0xFFFFFFFF.toInt()
                                    lineWidth = 2f
                                    circleRadius = 4f
                                    setDrawFilled(true)
                                    fillColor = colors[i % colors.size]
                                    fillAlpha = 30
                                }
                            }
                            
                            this.data = LineData(dataSets)
                            notifyDataSetChanged()
                            invalidate()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                "pie" -> AndroidView(
                    factory = { ctx ->
                        PieChart(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(0xFF0A0A0A.toInt())
                            description.isEnabled = false
                            legend.apply {
                                textColor = 0xFFFFFFFF.toInt()
                                isEnabled = true
                                textSize = 9f
                            }
                            isDrawHoleEnabled = true
                            setHoleColor(0xFF0A0A0A.toInt())
                            setTransparentCircleColor(0xFF0A0A0A.toInt())
                            setEntryLabelColor(0xFFFFFFFF.toInt())
                            setEntryLabelTextSize(11f)
                            
                            val firstSeries = values.firstOrNull() ?: emptyList()
                            val entries = firstSeries.mapIndexed { idx, v ->
                                PieEntry(v, labels.getOrElse(idx) { "" })
                            }
                            val ds = PieDataSet(entries, chartData.title).apply {
                                this.colors = colors
                                valueTextColor = 0xFFFFFFFF.toInt()
                                valueTextSize = 11f
                            }
                            this.data = PieData(ds)
                            notifyDataSetChanged()
                            invalidate()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                else -> AndroidView( // bar (default)
                    factory = { ctx ->
                        BarChart(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(0xFF0A0A0A.toInt())
                            description.isEnabled = false
                            legend.apply {
                                textColor = 0xFFFFFFFF.toInt()
                                isEnabled = true
                                textSize = 9f
                            }
                            setDrawGridBackground(false)
                            axisLeft.apply {
                                textColor = 0xFFFFFFFF.toInt()
                                gridColor = 0xFF1E1E1E.toInt()
                                axisLineColor = 0xFF4285F4.toInt()
                            }
                            axisRight.isEnabled = false
                            xAxis.apply {
                                position = XAxis.XAxisPosition.BOTTOM
                                textColor = 0xFFFFFFFF.toInt()
                                gridColor = 0xFF1E1E1E.toInt()
                                valueFormatter = IndexAxisValueFormatter(labels)
                                granularity = 1f
                            }
                            
                            val dataSets = values.mapIndexed { i, series ->
                                val entries = series.mapIndexed { idx, v -> BarEntry(idx.toFloat(), v) }
                                val seriesName = chartData.seriesLabels?.getOrNull(i) ?: "Series ${i + 1}"
                                BarDataSet(entries, seriesName).apply {
                                    color = colors[i % colors.size]
                                    valueTextColor = 0xFFFFFFFF.toInt()
                                    valueTextSize = 11f
                                }
                            }
                            
                            this.data = BarData(dataSets)
                            notifyDataSetChanged()
                            invalidate()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
