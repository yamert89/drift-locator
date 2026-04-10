package com.github.yamert89.plugin.install

import com.intellij.openapi.components.BaseState

/**
 * State class for tracking plugin installation/migration progress.
 * Stores the plugin version at the time of last execution and the set of completed hook IDs.
 */
class PluginInstallState : BaseState() {
    /**
     * Plugin version at the time of last hook execution.
     * Used to determine if new hooks need to be run after plugin update.
     */
    var version by string()

    /**
     * Set of hook IDs that have been successfully executed.
     * Each hook is executed only once across all plugin installations.
     */
    var executedHooks by list<String>()

    /**
     * Checks if a hook with the given ID has been executed.
     */
    fun isHookExecuted(hookId: String): Boolean = hookId in executedHooks

    /**
     * Marks a hook as executed by adding its ID to the set.
     */
    fun markHookExecuted(hookId: String) {
        if (hookId !in executedHooks) {
            executedHooks += hookId
        }
    }
}
