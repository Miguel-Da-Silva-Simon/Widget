package com.example.widget_android.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.widget_android.data.ProfilePhotoStorage
import com.example.widget_android.data.SessionRepository
import kotlinx.coroutines.launch

class WidgetProfilePhotoPickerActivity : ComponentActivity() {

    private val picker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            lifecycleScope.launch {
                if (uri != null) {
                    ProfilePhotoStorage.importToAppStorage(applicationContext, uri)?.let { storedPhoto ->
                        SessionRepository(applicationContext).saveProfilePhotoUri(storedPhoto)
                    }
                }
                FichajeWidgetUpdater.updateAll(applicationContext)
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }
}
