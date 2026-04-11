package com.github.yamert89.plugin.ui

import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

fun Project.notifyInfo(content: String) {
    com.intellij.notification.NotificationGroupManager
        .getInstance()
        .getNotificationGroup("DriftLocatorNotifications")
        .createNotification(
            title = "Info",
            content = content,
            type = NotificationType.INFORMATION,
        ).notify(this)
}

fun Project.notifyError(content: String) {
    com.intellij.notification.NotificationGroupManager
        .getInstance()
        .getNotificationGroup("DriftLocatorNotifications")
        .createNotification(
            title = "Error",
            content = content,
            type = NotificationType.ERROR,
        ).notify(this)
}
