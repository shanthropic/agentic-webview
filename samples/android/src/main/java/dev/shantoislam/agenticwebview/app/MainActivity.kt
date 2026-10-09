package dev.shantoislam.agenticwebview.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import dev.shantoislam.agenticwebview.app.data.AppDatabase
import dev.shantoislam.agenticwebview.app.ui.AgenticWebViewModel
import dev.shantoislam.agenticwebview.app.ui.MainScreen
import dev.shantoislam.agenticwebview.app.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getInstance(applicationContext)
        val dao = database.agentSettingsDao()
        val viewModel = ViewModelProvider(
            this,
            AgenticWebViewModel.Factory(dao),
        )[AgenticWebViewModel::class.java]

        setContent {
            AppTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
