package com.ntttrang.camerak.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PreferencesRepository(context: Context) {

	private val prefs = context.getSharedPreferences("camera_prefs", Context.MODE_PRIVATE)

	private val _rememberCameraMode = MutableStateFlow(prefs.getBoolean(KEY_REMEMBER_CAMERA_MODE, true))
	val rememberCameraMode: StateFlow<Boolean> = _rememberCameraMode

	private val _rememberAspectRatio = MutableStateFlow(prefs.getBoolean(KEY_REMEMBER_ASPECT_RATIO, true))
	val rememberAspectRatio: StateFlow<Boolean> = _rememberAspectRatio

	fun setRememberCameraMode(remember: Boolean) {
		pPrefsEdit { putBoolean(KEY_REMEMBER_CAMERA_MODE, remember) }
		_rememberCameraMode.value = remember
	}

	fun setRememberAspectRatio(remember: Boolean) {
		pPrefsEdit { putBoolean(KEY_REMEMBER_ASPECT_RATIO, remember) }
		_rememberAspectRatio.value = remember
	}

	fun getLastCameraMode(): String? = prefs.getString(KEY_LAST_CAMERA_MODE, null)

	fun setLastCameraMode(mode: String) {
		pPrefsEdit { putString(KEY_LAST_CAMERA_MODE, mode) }
	}

	fun getLastAspectRatio(): String? = prefs.getString(KEY_LAST_ASPECT_RATIO, null)

	fun setLastAspectRatio(ratio: String) {
		pPrefsEdit { putString(KEY_LAST_ASPECT_RATIO, ratio) }
	}

	private inline fun pPrefsEdit(block: android.content.SharedPreferences.Editor.() -> Unit) {
		val editor = prefs.edit()
		block(editor)
		editor.apply()
	}

	companion object {
		private const val KEY_REMEMBER_CAMERA_MODE = "remember_camera_mode"
		private const val KEY_REMEMBER_ASPECT_RATIO = "remember_aspect_ratio"
		private const val KEY_LAST_CAMERA_MODE = "last_camera_mode"
		private const val KEY_LAST_ASPECT_RATIO = "last_aspect_ratio"
	}
}


