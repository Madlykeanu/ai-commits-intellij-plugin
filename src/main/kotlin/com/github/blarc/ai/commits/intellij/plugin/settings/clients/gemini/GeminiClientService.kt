package com.github.blarc.ai.commits.intellij.plugin.settings.clients.gemini

import com.github.blarc.ai.commits.intellij.plugin.AICommitsUtils.getCredentialAttributes
import com.github.blarc.ai.commits.intellij.plugin.AICommitsUtils.retrieveToken
import com.github.blarc.ai.commits.intellij.plugin.notifications.Notification
import com.github.blarc.ai.commits.intellij.plugin.notifications.sendNotification
import com.github.blarc.ai.commits.intellij.plugin.settings.clients.LLMClientService
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.text.nullize
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.output.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.Color
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.swing.JLabel

@Service(Service.Level.APP)
class GeminiClientService(private val cs: CoroutineScope) : LLMClientService<GeminiClientConfiguration>(cs) {

    companion object {
        @JvmStatic
        fun getInstance(): GeminiClientService = service()
        private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
    }

    override suspend fun buildChatModel(client: GeminiClientConfiguration): ChatLanguageModel {
        val token = client.token.nullize(true) ?: retrieveToken(client.id)?.toString(true)
        return GeminiChatModel(token ?: "", client.modelId, client.temperature.toFloat())
    }

    fun saveToken(client: GeminiClientConfiguration, token: String) {
        cs.launch(Dispatchers.Default) {
            try {
                PasswordSafe.instance.setPassword(getCredentialAttributes(client.id), token)
                client.tokenIsStored = true
            } catch (e: Exception) {
                sendNotification(Notification.unableToSaveToken(e.message))
                println("Failed to save token: ${e.message}")
            }
        }
    }

    fun verifyConfiguration(client: GeminiClientConfiguration, verifyLabel: JLabel) {
        cs.launch(Dispatchers.IO) {
            try {
                println("Verifying Gemini configuration")
                val chatModel = buildChatModel(client)
                val response = chatModel.generate(mutableListOf<dev.langchain4j.data.message.ChatMessage>(dev.langchain4j.data.message.UserMessage("Hello, can you hear me?")))
                if (response.content().text().isNotBlank()) {
                    println("Gemini configuration verified successfully")
                    withContext(Dispatchers.Main) {
                        verifyLabel.text = "Configuration verified successfully!"
                        verifyLabel.foreground = Color.GREEN
                    }
                } else {
                    throw RuntimeException("Empty response from API")
                }
            } catch (e: Exception) {
                println("Error verifying Gemini configuration: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    verifyLabel.text = "Invalid configuration: ${e.message}"
                    verifyLabel.foreground = Color.RED
                }
            }
        }
    }

    private inner class GeminiChatModel(private val apiKey: String, private val modelId: String, private val temperature: Float) : ChatLanguageModel {
        override fun generate(messages: MutableList<dev.langchain4j.data.message.ChatMessage>): Response<AiMessage> {
            val httpClient = HttpClient.newBuilder().build()
            val requestBody = Json.encodeToString(GeminiRequest.serializer(), GeminiRequest(
                contents = listOf(Content(parts = listOf(Part(text = messages.last().text())))),
                generationConfig = GenerationConfig(temperature = temperature)
            ))

            println("Sending request to Gemini API")
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$API_URL$modelId:generateContent?key=$apiKey"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            println("Received response from Gemini API. Status code: ${response.statusCode()}")
            println("Gemini API Response Body: ${response.body()}")

            if (response.statusCode() != 200) {
                throw RuntimeException("API request failed with status code: ${response.statusCode()}")
            }

            val json = Json { ignoreUnknownKeys = true }
            val geminiResponse = json.decodeFromString(GeminiResponse.serializer(), response.body())
            val content = geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw RuntimeException("No content in response")

            return Response.from(AiMessage(content))
        }
    }

    @Serializable
    private data class GeminiRequest(
        val contents: List<Content>,
        val generationConfig: GenerationConfig
    )

    @Serializable
    private data class Content(val parts: List<Part>)

    @Serializable
    private data class Part(val text: String)

    @Serializable
    private data class GenerationConfig(val temperature: Float)

    @Serializable
    private data class GeminiResponse(
        val candidates: List<Candidate>,
        val promptFeedback: PromptFeedback? = null
    )

    @Serializable
    private data class Candidate(val content: Content)

    @Serializable
    private data class PromptFeedback(val safetyRatings: List<SafetyRating>)

    @Serializable
    private data class SafetyRating(val category: String, val probability: String)
}