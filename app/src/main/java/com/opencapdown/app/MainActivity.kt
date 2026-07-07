package com.opencapdown.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.opencapdown.app.ui.OpenCapDownMainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as OpenCapDownApp
        val core = app.core

        setContent {
            OpenCapDownMainScreen(
                core = core,
                onCheckUpdate = {
                    // O controle de atualizações agora será feito de forma reativa e nativa
                }
            )
        }
    }
}
