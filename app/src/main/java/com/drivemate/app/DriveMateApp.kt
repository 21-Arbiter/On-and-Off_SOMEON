package com.drivemate.app

import android.app.Application
import com.drivemate.app.drive.DriveSession

class DriveMateApp : Application() {
    val drive by lazy { DriveSession(this) }

    override fun onCreate() {
        super.onCreate()
        drive.start()
    }
}
