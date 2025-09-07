package com.ntttrang.camerak.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.ntttrang.camerak.data.PreferencesRepository
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

	private val preferencesRepository = PreferencesRepository(application.applicationContext)

	val rememberCameraMode: StateFlow<Boolean> = preferencesRepository.rememberCameraMode
	val rememberAspectRatio: StateFlow<Boolean> = preferencesRepository.rememberAspectRatio

	fun setRememberCameraMode(remember: Boolean) {
		preferencesRepository.setRememberCameraMode(remember)
	}

	fun setRememberAspectRatio(remember: Boolean) {
		preferencesRepository.setRememberAspectRatio(remember)
	}
}


