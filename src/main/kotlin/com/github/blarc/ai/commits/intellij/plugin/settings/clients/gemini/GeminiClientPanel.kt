package com.github.blarc.ai.commits.intellij.plugin.settings.clients.gemini

import com.github.blarc.ai.commits.intellij.plugin.AICommitsBundle.message
import com.github.blarc.ai.commits.intellij.plugin.emptyText
import com.github.blarc.ai.commits.intellij.plugin.notBlank
import com.github.blarc.ai.commits.intellij.plugin.settings.clients.LLMClientPanel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

class GeminiClientPanel private constructor(
    private val clientConfiguration: GeminiClientConfiguration,
    val service: GeminiClientService
) : LLMClientPanel(clientConfiguration) {

    private val apiKeyPasswordField = JBPasswordField()

    constructor(configuration: GeminiClientConfiguration): this(configuration, GeminiClientService.getInstance())

    override fun create() = panel {
        nameRow()
        apiKeyRow()
        modelIdRow()
        temperatureRow()
        verifyRow()
    }

    private fun Panel.apiKeyRow() {
        row {
            label(message("settings.llmClient.token"))
                .widthGroup("label")
            cell(apiKeyPasswordField)
                .bindText(getter = { "" }, setter = {
                    GeminiClientService.getInstance().saveToken(clientConfiguration, it)
                })
                .emptyText(if (clientConfiguration.tokenIsStored) message("settings.llmClient.token.stored") else message("settings.gemini.token.example"))
                .resizableColumn()
                .align(Align.FILL)
                .comment(message("settings.gemini.token.comment"), 50)
        }
    }

    override fun verifyConfiguration() {
        clientConfiguration.modelId = modelComboBox.item
        clientConfiguration.temperature = temperatureTextField.text
        clientConfiguration.token = String(apiKeyPasswordField.password)

        service.verifyConfiguration(clientConfiguration, verifyLabel)
    }
}
