package com.family.puzzlepop.kid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.puzzlepop.R
import com.family.puzzlepop.kid.PlayTimeKeeper.Status

/**
 * A small friendly badge for the play screen: a timer and the time left (Warning), or a gentle
 * "Finish your puzzle!" (FinishThenBreak). Draws nothing for other statuses. No sound, no flashing.
 */
@Composable
fun CountdownBadge(status: Status, modifier: Modifier = Modifier) {
    val text: String
    val a11y: String
    when (status) {
        is Status.Warning -> {
            text = formatCountdown(status.remainingMs)
            a11y = stringResource(R.string.time_left_a11y, text)
        }
        Status.FinishThenBreak -> {
            text = stringResource(R.string.time_finish_note)
            a11y = text
        }
        else -> return
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colorResource(R.color.icon_sun_light),
        border = BorderStroke(3.dp, colorResource(R.color.icon_sun)),
        modifier = modifier.semantics { contentDescription = a11y },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_timer), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(36.dp))
            Text(text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = colorResource(R.color.icon_ink))
        }
    }
}
