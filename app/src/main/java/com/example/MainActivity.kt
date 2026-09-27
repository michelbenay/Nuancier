package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.data.ColorDatabase
import com.example.data.ColorRepository
import com.example.ui.ColorApp
import com.example.ui.ColorViewModel
import com.example.ui.ColorViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = ColorDatabase.getDatabase(this)
        val repository = ColorRepository(database.colorDao())
        val viewModel: ColorViewModel by viewModels { ColorViewModelFactory(repository) }

        setContent {
            MyApplicationTheme {
                ColorApp(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}


