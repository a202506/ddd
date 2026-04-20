package com.buzzingmountain.dingclock

import android.app.Application
import com.buzzingmountain.dingclock.log.FileLogTree
import timber.log.Timber

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(FileLogTree(FileLogTree.resolveLogsDir(this)))
        Timber.i("App start: pkg=%s ver=%s (%d)", packageName, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    }
}
