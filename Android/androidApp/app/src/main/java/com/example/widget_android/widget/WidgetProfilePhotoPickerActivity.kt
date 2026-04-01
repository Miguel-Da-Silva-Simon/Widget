package com.example.widget_android.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.widget_android.data.SessionRepository
import kotlinx.coroutines.launch

class WidgetProfilePhotoPickerActivity : ComponentActivity() {

    private val picker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            lifecycleScope.launch {
                if (uri != null) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    SessionRepository(applicationContext).saveProfilePhotoUri(uri.toString())
                }
                FichajeWidgetUpdater.updateAll(applicationContext)
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            picker.launch(arrayOf("image/*"))
        }
    }
}
