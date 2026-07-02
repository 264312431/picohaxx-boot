package pico.haxx

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan

class AnsiColorParser {
    // Keep track of terminal state across multiple lines
    private var currentFg: Int? = null
    private var currentBg: Int? = null
    private var isBold = false

    // Standard Terminal Colors (Black, Red, Green, Yellow, Blue, Magenta, Cyan, Light Gray)
    private val standardColors = intArrayOf(
        Color.parseColor("#000000"), Color.parseColor("#CD3131"),
        Color.parseColor("#0DBC79"), Color.parseColor("#E5E510"),
        Color.parseColor("#2472C8"), Color.parseColor("#BC3FBC"),
        Color.parseColor("#11A8CD"), Color.parseColor("#E5E5E5")
    )

    // Bright Terminal Colors
    private val brightColors = intArrayOf(
        Color.parseColor("#666666"), Color.parseColor("#F14C4C"),
        Color.parseColor("#23D18B"), Color.parseColor("#F5F543"),
        Color.parseColor("#3B8EEA"), Color.parseColor("#D670D6"),
        Color.parseColor("#29B8DB"), Color.parseColor("#FFFFFF")
    )

    fun parse(input: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        // Matches standard ANSI escape sequences ending in 'm'
        val matcher = "\u001B\\[([0-9;]*)m".toRegex()

        var lastIndex = 0
        for (match in matcher.findAll(input)) {
            // 1. Append the text *before* the escape code with current styles
            val textChunk = input.substring(lastIndex, match.range.first)
            appendWithSpans(builder, textChunk)

            // 2. Update the state machine with the new ANSI codes
            val codeStr = match.groupValues[1]
            val codes = if (codeStr.isEmpty()) listOf(0) else codeStr.split(";").mapNotNull { it.toIntOrNull() }

            for (code in codes) {
                applyCode(code)
            }
            lastIndex = match.range.last + 1
        }

        // 3. Append any remaining text after the last escape code
        appendWithSpans(builder, input.substring(lastIndex))
        return builder
    }

    private fun appendWithSpans(builder: SpannableStringBuilder, text: String) {
        if (text.isEmpty()) return
        val start = builder.length
        builder.append(text)

        currentFg?.let {
            builder.setSpan(ForegroundColorSpan(it), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        currentBg?.let {
            builder.setSpan(BackgroundColorSpan(it), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (isBold) {
            builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun applyCode(code: Int) {
        when (code) {
            0 -> { // Reset
                currentFg = null
                currentBg = null
                isBold = false
            }
            1 -> isBold = true
            21, 22 -> isBold = false // Reset bold
            39 -> currentFg = null // Default FG
            49 -> currentBg = null // Default BG
            in 30..37 -> currentFg = standardColors[code - 30]
            in 40..47 -> currentBg = standardColors[code - 40]
            in 90..97 -> currentFg = brightColors[code - 90]
            in 100..107 -> currentBg = brightColors[code - 100]
        }
    }
}