package mia.chinese

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import mia.chinese.ui.MiaChineseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiaChineseApp(application as ChineseLearningApp)
        }
    }
}
