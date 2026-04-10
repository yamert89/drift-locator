package com.github.yamert89.plugin

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Secure storage for database passwords using IntelliJ Platform's PasswordSafe.
 * Passwords are encrypted using the OS-native keychain (macOS Keychain, Windows Credential Manager, Linux Secret Service).
 */
object PasswordStorage {
    private const val SERVICE_NAME = "DriftLocator.DatabaseConnection"

    /**
     * Saves a password for the given connection ID.
     * If password is null or empty, any existing password is removed.
     */
    fun savePassword(connectionId: String, password: String?) {
        val attributes = createCredentialAttributes(connectionId)
        if (password.isNullOrBlank()) {
            PasswordSafe.instance.set(attributes, null)
        } else {
            PasswordSafe.instance.set(attributes, Credentials(connectionId, password))
        }
    }

    /**
     * Retrieves the password for the given connection ID.
     * Returns null if no password is stored.
     */
    fun getPassword(connectionId: String): String? {
        val attributes = createCredentialAttributes(connectionId)
        return PasswordSafe.instance.get(attributes)?.getPasswordAsString()
    }

    /**
     * Removes the password for the given connection ID.
     */
    fun removePassword(connectionId: String) {
        val attributes = createCredentialAttributes(connectionId)
        PasswordSafe.instance.set(attributes, null)
    }

    /**
     * Checks if a password exists for the given connection ID.
     */
    fun hasPassword(connectionId: String): Boolean = !getPassword(connectionId).isNullOrBlank()

    private fun createCredentialAttributes(connectionId: String): CredentialAttributes =
        CredentialAttributes(
            "$SERVICE_NAME.$connectionId",
            connectionId,
        )
}
