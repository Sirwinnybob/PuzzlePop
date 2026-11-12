package com.family.puzzlepop

import android.content.Context
import com.family.puzzlepop.data.AppDatabase
import com.family.puzzlepop.data.PhotoRepository
import com.family.puzzlepop.data.PuzzleRepository
import com.family.puzzlepop.settings.BestsStore
import com.family.puzzlepop.settings.SettingsRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.create(appContext)

    val puzzleRepository = PuzzleRepository(appContext, db)
    val photoRepository = PhotoRepository(appContext, db, puzzleRepository)
    val settings = SettingsRepository(appContext)
    val bests = BestsStore(appContext)
}
