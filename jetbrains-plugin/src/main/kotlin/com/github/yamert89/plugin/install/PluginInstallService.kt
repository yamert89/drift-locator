package com.github.yamert89.plugin.install

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level service for managing plugin installation state.
 * Tracks executed hooks and plugin version to enable one-time post-install/update actions.
 *
 * The state is persisted in `driftLocator-install.xml` in the IDE configuration directory.
 */
@Service
@State(
    name = "DriftLocatorInstallState",
    storages = [Storage("driftLocator-install.xml")],
)
class PluginInstallService : SimplePersistentStateComponent<PluginInstallState>(PluginInstallState()) {
    /**
     * Checks if a hook with the given ID has been executed.
     */
    fun isHookExecuted(hookId: String): Boolean = state.isHookExecuted(hookId)

    /**
     * Marks a hook as executed.
     */
    fun markHookExecuted(hookId: String) {
        state.markHookExecuted(hookId)
    }

    /**
     * Returns the plugin version at the time of last hook execution.
     */
    fun getLastVersion(): String? = state.version

    /**
     * Updates the stored plugin version.
     */
    fun updateVersion(version: String) {
        state.version = version
    }
}
