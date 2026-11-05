package com.eyeguard.app.feature.focus

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eyeguard.app.R

@Composable
fun FocusMusicToggle(enabled: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (enabled) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
            contentDescription = stringResource(
                if (enabled) R.string.focus_music_disable else R.string.focus_music_enable
            ),
            tint = if (enabled) CultivationPalette.Jade else CultivationPalette.InkSoft
        )
    }
}

@Composable
fun FocusMusicCreditsButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = CultivationPalette.Ink),
        border = BorderStroke(1.dp, CultivationPalette.Gold.copy(alpha = 0.40f))
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = CultivationPalette.Jade)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.focus_music_entry), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun FocusMusicCreditsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CultivationPalette.Panel,
        titleContentColor = CultivationPalette.Ink,
        textContentColor = CultivationPalette.Ink,
        title = {
            Text(
                stringResource(R.string.focus_music_dialog_title),
                color = CultivationPalette.Ink,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.focus_music_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CultivationPalette.InkSoft
                )
                Text(
                    stringResource(R.string.focus_music_plan_credit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CultivationPalette.Ink,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = {
                    open("https://soundimage.org/fantasywonder/")
                }) {
                    Text(stringResource(R.string.focus_music_plan_source), color = CultivationPalette.Jade)
                }
                Text(
                    stringResource(R.string.focus_music_focus_credit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CultivationPalette.Ink,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = {
                    open("https://commons.wikimedia.org/wiki/File:Guqin-Yangguan_Sandie.ogg")
                }) {
                    Text(stringResource(R.string.focus_music_focus_source), color = CultivationPalette.Jade)
                }
                Text(
                    stringResource(R.string.focus_music_license),
                    style = MaterialTheme.typography.bodySmall,
                    color = CultivationPalette.InkSoft,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                TextButton(onClick = {
                    open("https://creativecommons.org/licenses/by-sa/3.0/")
                }) {
                    Text(stringResource(R.string.focus_music_license_link), color = CultivationPalette.Jade)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.focus_music_close),
                    color = CultivationPalette.Ink,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}
