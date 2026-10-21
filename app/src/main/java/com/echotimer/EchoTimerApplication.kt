package com.echotimer

import android.app.Application

/** 应用入口：只负责创建依赖容器。 */
class EchoTimerApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
