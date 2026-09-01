package com.example.litcompose

import android.app.Application
import com.example.litcompose.core.AppContainer

class LitComposeApp : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer.create(this)
    }
}

