package com.family.puzzlepop

import android.app.Application

class PuzzlePopApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
