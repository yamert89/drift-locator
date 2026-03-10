package com.github.yamert89.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger

@Service
class DriftLocatorApplicationService {
    private val log = Logger.getInstance(DriftLocatorApplicationService::class.java)

    init {
        log.info("Drift Locator plugin started")
    }

    companion object {
        fun getInstance(): DriftLocatorApplicationService =
            ApplicationManager.getApplication().getService(DriftLocatorApplicationService::class.java)
    }
}
