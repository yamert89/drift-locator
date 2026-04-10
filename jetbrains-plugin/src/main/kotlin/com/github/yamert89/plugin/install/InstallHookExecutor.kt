package com.github.yamert89.plugin.install

import com.github.yamert89.plugin.install.hooks.ClearPasswordsHook
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Executes one-time post-install/update hooks when a project is opened.
 *
 * This class registers and runs all [InstallHook] implementations that haven't been
 * executed yet. Hooks are tracked by their unique IDs and executed only once
 * across all plugin installations.
 *
 * Hooks are registered in the [registerHooks] method. To add a new hook:
 * 1. Implement the [InstallHook] interface
 * 2. Add it to the [registerHooks] method
 * 3. The hook will be automatically executed on the next project open
 */
class InstallHookExecutor : ProjectActivity {
    companion object {
        private val LOG = Logger.getInstance(InstallHookExecutor::class.java)
        private const val PLUGIN_ID = "com.github.yamert89.drift-locator"
    }

    private val hooks = mutableListOf<InstallHook>()

    override suspend fun execute(project: Project) {
        val installService = ApplicationManager.getApplication().service<PluginInstallService>()
        val currentVersion = getCurrentPluginVersion()

        LOG.info("Checking install hooks. Current version: $currentVersion, Last version: ${installService.getLastVersion()}")

        // Register all available hooks
        registerHooks(project)

        // Execute hooks that haven't been run yet
        var executedCount = 0
        hooks.forEach { hook ->
            if (!installService.isHookExecuted(hook.id)) {
                try {
                    LOG.info("Executing install hook: ${hook.id} - ${hook.description}")
                    hook.execute()
                    installService.markHookExecuted(hook.id)
                    executedCount++
                    LOG.info("Successfully executed hook: ${hook.id}")
                } catch (e: Exception) {
                    LOG.error("Failed to execute hook ${hook.id}: ${e.message}", e)
                    // Continue with other hooks even if one fails
                }
            } else {
                LOG.debug("Hook already executed: ${hook.id}")
            }
        }

        // Update the stored version after all hooks are executed
        if (currentVersion != null) {
            installService.updateVersion(currentVersion)
        }

        if (executedCount > 0) {
            LOG.info("Completed $executedCount install hook(s)")
        } else {
            LOG.debug("No new hooks to execute")
        }
    }

    /**
     * Registers all available install hooks.
     * Add new hooks here to include them in the execution pipeline.
     */
    private fun registerHooks(project: Project) {
        hooks.clear()

        // Register password cleanup hook
        hooks.add(ClearPasswordsHook(project))

        // Add new hooks here as needed:
        // hooks.add(NewHook(project))
        // hooks.add(AnotherHook())

        LOG.debug("Registered ${hooks.size} install hook(s)")
    }

    /**
     * Gets the current version of the Drift Locator plugin.
     * Uses reflection for compatibility with older IDE versions (241-251)
     * where PluginId.Companion may not be resolved correctly.
     */
    private fun getCurrentPluginVersion(): String? {
        return try {
            // Use reflection to avoid NoSuchFieldError for PluginId.Companion in older IDE versions
            val pluginIdClass = Class.forName("com.intellij.openapi.extensions.PluginId")
            val getIdMethod = pluginIdClass.getMethod("getId", String::class.java)
            val pluginId = getIdMethod.invoke(null, PLUGIN_ID)
            PluginManagerCore.getPlugin(pluginId as PluginId)?.version
        } catch (e: Exception) {
            LOG.warn("Failed to get plugin version: ${e.message}")
            null
        }
    }
}
