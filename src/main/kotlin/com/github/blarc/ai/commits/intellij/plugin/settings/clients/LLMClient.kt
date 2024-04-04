package com.github.blarc.ai.commits.intellij.plugin.settings.clients

import com.github.blarc.ai.commits.intellij.plugin.AICommitsUtils.getCredentialAttributes
import com.github.blarc.ai.commits.intellij.plugin.AICommitsUtils.retrieveToken
import com.github.blarc.ai.commits.intellij.plugin.notifications.Notification
import com.github.blarc.ai.commits.intellij.plugin.notifications.sendNotification
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Transient
import javax.swing.Icon

abstract class LLMClient(
    @Attribute var displayName: String,
    @Attribute var host: String,
    @Attribute var proxyUrl: String?,
    @Attribute var timeout: Int,
    @Attribute var modelId: String,
    @Attribute var temperature: String,
) : Cloneable {
    @get:Transient
    var token: String
        get() = retrieveToken(displayName) ?: ""
        set(token) = saveToken(token)

    abstract fun getIcon(): Icon

    abstract fun getHosts(): Set<String>

    abstract fun getModelIds(): Set<String>

    abstract suspend fun generateCommitMessage(prompt: String): String

    abstract suspend fun refreshModels()

    public abstract override fun clone(): LLMClient

    @Throws(Exception::class)
    abstract suspend fun verifyConfiguration(
        newHost: String,
        newProxy: String?,
        newTimeout: String,
        newToken: String
    )

    abstract fun panel(): LLMClientPanel

    private fun saveToken(token: String) {
        try {
            PasswordSafe.instance.setPassword(getCredentialAttributes(displayName), token)
        } catch (e: Exception) {
            sendNotification(Notification.unableToSaveToken(e.message))
        }
    }
}
