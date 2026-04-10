package com.github.yamert89.plugin.install.hooks

import com.github.yamert89.plugin.PasswordStorage
import com.github.yamert89.plugin.install.ProjectInstallHook
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.*
import java.io.File
import java.io.IOException

/**
 * Post-install hook that clears plaintext passwords from connections.json files.
 *
 * In previous versions, passwords were stored in plaintext in the connections.json file.
 * This hook ensures that any remaining plaintext passwords are:
 * 1. Migrated to PasswordSafe (if not already there)
 * 2. Removed from the JSON file
 */
class ClearPasswordsHook(override val project: Project) : ProjectInstallHook {
    companion object {
        private val LOG = Logger.getInstance(ClearPasswordsHook::class.java)
        private const val CONNECTIONS_FILE = "connections.json"
        private const val PASSWORD_FIELD = "password"
    }

    override val id: String = "clear-plaintext-passwords"
    override val description: String = "Remove plaintext passwords from connections.json files"

    override fun execute(project: Project) {
        val systemDir = File(project.basePath, ".driftLocator/system")
        if (!systemDir.exists()) {
            LOG.debug("No .driftLocator/system directory found for project: ${project.name}")
            return
        }

        val connectionsFile = File(systemDir, CONNECTIONS_FILE)
        if (!connectionsFile.exists()) {
            LOG.debug("No connections.json file found for project: ${project.name}")
            return
        }

        try {
            processConnectionsFile(connectionsFile)
        } catch (e: Exception) {
            LOG.warn("Failed to process connections file: ${e.message}", e)
        }
    }

    private fun processConnectionsFile(file: File) {
        val jsonString = file.readText()
        if (jsonString.isBlank()) {
            return
        }

        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            isLenient = true
        }

        val jsonElement = json.parseToJsonElement(jsonString)
        if (jsonElement !is JsonArray) {
            LOG.debug("connections.json is not a JSON array, skipping")
            return
        }

        var hasPasswords = false
        val passwordsToMigrate = mutableListOf<Pair<String, String>>()

        // Check for passwords in the JSON
        val cleanedArray = JsonArray(
            jsonElement.map { element ->
                if (element is JsonObject) {
                    val passwordPrimitive = element[PASSWORD_FIELD]
                    if (passwordPrimitive != null && passwordPrimitive is JsonPrimitive) {
                        val password = passwordPrimitive.content
                        if (password.isNotBlank()) {
                            hasPasswords = true
                            val id = element["id"]?.jsonPrimitive?.content
                            if (id != null) {
                                passwordsToMigrate.add(id to password)
                            }
                        }
                        // Remove password field from the object
                        JsonObject(element.filterKeys { it != PASSWORD_FIELD })
                    } else {
                        element
                    }
                } else {
                    element
                }
            }
        )

        if (!hasPasswords) {
            LOG.debug("No plaintext passwords found in ${file.absolutePath}")
            return
        }

        // Migrate passwords to secure storage in background
        if (passwordsToMigrate.isNotEmpty()) {
            ApplicationManager.getApplication().executeOnPooledThread {
                passwordsToMigrate.forEach { (id, password) ->
                    try {
                        // Only save if not already present
                        if (!PasswordStorage.hasPassword(id)) {
                            PasswordStorage.savePassword(id, password)
                            LOG.info("Migrated password for connection: $id")
                        }
                    } catch (e: Exception) {
                        LOG.warn("Failed to migrate password for connection $id: ${e.message}")
                    }
                }
            }
        }

        // Write cleaned JSON back to file
        try {
            val cleanedJsonString = json.encodeToString(JsonArray.serializer(), cleanedArray)
            file.writeText(cleanedJsonString)
            LOG.info("Cleared ${passwordsToMigrate.size} plaintext password(s) from ${file.absolutePath}")
        } catch (e: IOException) {
            LOG.error("Failed to write cleaned connections file: ${e.message}", e)
        }
    }
}
