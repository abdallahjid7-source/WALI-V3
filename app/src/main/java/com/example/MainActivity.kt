package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.theme.MyApplicationTheme
import com.example.wali.ads.WaliAdManager
import com.example.wali.ui.navigation.WaliApp

class MainActivity : ComponentActivity() {
  private var isFirstLaunch = true

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    if (savedInstanceState == null && isFirstLaunch) {
      isFirstLaunch = false
      WaliAdManager.showColdStartAppOpenAd(this) {
        // Callback déclenché dès que l'annonce est fermée ou si aucune pub n'est prête
      }
    }

    setContent {
      MyApplicationTheme {
        WaliApp()
      }
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  MyApplicationTheme { Greeting("Android") }
}
