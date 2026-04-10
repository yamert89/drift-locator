package com.github.yamert89.plugin.install

import com.intellij.openapi.project.Project

/**
 * Interface for one-time post-install/update hooks.
 * Each hook is executed only once across all plugin installations.
 */
interface InstallHook {
    /**
     * Unique identifier for this hook.
     * Used to track whether the hook has been executed.
     */
    val id: String

    /**
     * Human-readable description of what this hook does.
     * Used for logging purposes.
     */
    val description: String

    /**
     * Executes the hook's logic.
     * Implementations should handle their own errors and not throw exceptions.
     */
    fun execute()
}

/**
 * Base interface for hooks that operate on a specific project.
 */
interface ProjectInstallHook : InstallHook {
    /**
     * The project this hook operates on.
     */
    val project: Project

    override fun execute() {
        execute(project)
    }

    /**
     * Executes the hook's logic for the given project.
     */
    fun execute(project: Project)
}
