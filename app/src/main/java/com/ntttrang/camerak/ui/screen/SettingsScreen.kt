package com.ntttrang.camerak.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ntttrang.camerak.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBackClick: () -> Unit = {}) {
	val rememberCameraMode by viewModel.rememberCameraMode.collectAsState()
	val rememberAspectRatio by viewModel.rememberAspectRatio.collectAsState()
	
	// Handle system back button
	BackHandler {
		onBackClick()
	}

	Scaffold { innerPadding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding)
		) {
			// Top bar with back button and title
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(16.dp),
				verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
			) {
				IconButton(onClick = onBackClick) {
					Icon(Icons.Default.ArrowBack, contentDescription = "Back")
				}
				Text(
					text = "Settings",
					style = MaterialTheme.typography.headlineSmall,
					modifier = Modifier.padding(start = 8.dp)
				)
			}
			
			// Settings content
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(horizontal = 16.dp),
				verticalArrangement = Arrangement.spacedBy(24.dp)
			) {
				RowItem(
					title = "Remember camera mode",
					subtitle = "Keep last used Photo/Video when reopening the app",
					checked = rememberCameraMode,
					onCheckedChange = { viewModel.setRememberCameraMode(it) }
				)

				RowItem(
					title = "Remember aspect ratio",
					subtitle = "Keep last used aspect ratio when reopening the app",
					checked = rememberAspectRatio,
					onCheckedChange = { viewModel.setRememberAspectRatio(it) }
				)
			}
		}
	}
}

@Composable
private fun RowItem(
	title: String,
	subtitle: String,
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit
) {
	Column(modifier = Modifier.fillMaxWidth()) {
		Text(text = title, style = MaterialTheme.typography.titleMedium)
		Text(text = subtitle, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
		Switch(checked = checked, onCheckedChange = onCheckedChange)
	}
}


