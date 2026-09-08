package com.konstantyp.gymcal.ui.quote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.konstantyp.gymcal.data.MotivationQuote
import com.konstantyp.gymcal.data.QuoteRepository

/** Hard palette — BINDING quote-bar-A (same five blues as widget D). */
private val Navy = Color(0xFF003366)
private val Steel = Color(0xFF336699)
private val Ice = Color(0xFFCCFFFF)

/**
 * Motivation quote bar Variant A. Hidden until a successful fetch;
 * offline/fail → stays hidden (no error UI). CalendarScreen only.
 */
@Composable
fun MotivationQuoteBar(modifier: Modifier = Modifier) {
    var quote by remember { mutableStateOf<MotivationQuote?>(null) }

    LaunchedEffect(Unit) {
        quote = QuoteRepository.fetchRandom()
    }

    val q = quote ?: return

    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 96.dp, max = 220.dp)
            .clip(shape)
            .background(Ice),
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(Steel),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        ) {
            Text(
                text = "“${q.text}”",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = Navy,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "— ${q.author} · zenquotes.io",
                style = MaterialTheme.typography.labelSmall,
                color = Steel,
                modifier = Modifier.padding(top = 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
