package dmagdaleno.customcamera2.ui.activity

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import dmagdaleno.customcamera2.R
import dmagdaleno.customcamera2.ui.fragment.CustomCameraFragment

class CustomCameraActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_camera)
        savedInstanceState ?: supportFragmentManager.beginTransaction()
                .replace(R.id.container, CustomCameraFragment.newInstance())
                .commit()
    }
}
