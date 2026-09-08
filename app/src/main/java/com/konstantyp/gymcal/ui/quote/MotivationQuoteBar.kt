package com.konstantyp.gymcal.ui.quote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.konstantyp.gymcal.data.MotivationQuote
import com.konstantyp.gymcal.data.QuoteRepository

/**
 * Motivation quote bar Variant A (tonal-fill delta).
 * Fill matches FilledTonalButton secondaryContainer. Hidden until success.
 */
@Composable
fun MotivationQuoteBar(modifier: Modifier = Modifier) {
    var quote by remember { mutableStateOf<MotivationQuote?>(null) }

    LaunchedEffect(Unit) {
        quote = QuoteRepository.fetchRandom()
    }

    val q = quote ?: return
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 96.dp, max = 220.dp)
            .clip(shape)
            .background(scheme.secondaryContainer),
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(scheme.secondary),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        ) {
            Text(
                text = "“${q.text}”",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = scheme.onSecondaryContainer,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "— ${q.author} · zenquotes.io",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSecondaryContainer.copy(alpha = 0.70f),
                modifier = Modifier.padding(top = 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
