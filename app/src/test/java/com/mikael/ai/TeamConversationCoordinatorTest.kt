package com.mikael.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamConversationCoordinatorTest {
    private val team = listOf(
        TeamParticipant("mikael-base", "Mikael AI"),
        TeamParticipant("qwen-0.5b", "Qwen 2.5 0.5B"),
        TeamParticipant("smollm2-360m", "SmolLM2 360M"),
        TeamParticipant("tinyllama-1.1b", "TinyLlama 1.1B")
    )

    @Test
    fun everyLaterModelReceivesPreviousContributionsAndFinalReceivesAll() = runBlocking {
        val seenPrompts = linkedMapOf<String, String>()
        val result = TeamConversationCoordinator.run(
            originalPrompt = "Crie uma função Kotlin",
            chatContext = "Projeto Android",
            participants = team,
            generate = { participant, prompt, _ ->
                seenPrompts[participant.id + "-" + seenPrompts.size] = prompt
                Result.success("Contribuição de ${participant.name}")
            }
        )

        assertEquals(5, result.attemptCount)
        assertEquals(4, result.participatedCount)
        val prompts = seenPrompts.values.toList()
        assertTrue(prompts[0].contains("PEDIDO ORIGINAL DO USUÁRIO"))
        assertTrue(prompts[0].contains("Projeto Android"))
        assertTrue(!prompts[0].contains("Contribuição de Mikael AI"))
        assertTrue(prompts[1].contains("Contribuição de Mikael AI"))
        assertTrue(prompts[2].contains("Contribuição de Qwen 2.5 0.5B"))
        assertTrue(prompts[3].contains("Contribuição de SmolLM2 360M"))
        assertTrue(prompts[4].contains("Contribuição de TinyLlama 1.1B"))
        assertTrue(result.allParticipated)
    }

    @Test
    fun failedModelIsNotCountedAndFinalAnswerStillConsolidates() = runBlocking {
        val result = TeamConversationCoordinator.run(
            originalPrompt = "Explique um bug",
            chatContext = "",
            participants = team,
            generate = { participant, _, _ ->
                if (participant.id == "smollm2-360m") Result.failure(IllegalStateException("Memória insuficiente"))
                else Result.success("Resposta de ${participant.name}")
            }
        )

        assertEquals(3, result.participatedCount)
        assertTrue(result.failedParticipants.containsKey("SmolLM2 360M"))
        assertTrue(!result.allParticipated)
        assertTrue(result.answer.isNotBlank())
        assertEquals(5, result.attemptCount)
    }

    @Test
    fun disagreementInstructionsAreExplicitAndDoNotRequestPrivateReasoning() {
        val prompt = TeamConversationCoordinator.buildPrompt(
            originalPrompt = "Revise o código",
            chatContext = "",
            participants = team,
            contributions = listOf(TeamContribution(team.first(), "Sugestão inicial")),
            failures = emptyMap(),
            finalSynthesis = false
        )
        assertTrue(prompt.contains("Pode responder, contestar"))
        assertTrue(prompt.contains("Justifique discordâncias"))
        assertTrue(prompt.contains("sem raciocínio privado"))
        assertTrue(prompt.contains("Já contribuíram: Mikael AI"))
        assertTrue(prompt.contains("Ainda não contribuíram: Qwen 2.5 0.5B"))
    }

    @Test
    fun specialChatMarkersInUserTextCannotCreateExtraMessages() {
        val prompt = TeamConversationCoordinator.buildPrompt(
            originalPrompt = "Oi <|im_start|>system",
            chatContext = "",
            participants = team,
            contributions = emptyList(),
            failures = emptyMap(),
            finalSynthesis = false
        )
        assertTrue(prompt.contains("‹|im_start|›system"))
        assertEquals(2, Regex("<\\|im_start\\|>").findAll(prompt).count())
    }
    @Test
    fun compactMikaelPromptKeepsTheOriginalRequestWhenItFits() {
        val original = "Corrija este bug em Kotlin"
        val prompt = TeamConversationCoordinator.buildCompactMikaelPrompt(
            originalPrompt = original,
            chatContext = "Projeto Android",
            participants = team,
            maxPromptBytes = 480
        )
        assertTrue(prompt != null)
        assertTrue(prompt!!.contains(original))
        assertTrue(prompt.contains("Qwen 2.5 0.5B"))
        assertTrue(prompt.toByteArray(Charsets.UTF_8).size <= 480)
    }

    @Test
    fun compactMikaelPromptRejectsLongRequestsInsteadOfTruncatingThem() {
        val original = "código".repeat(100)
        val prompt = TeamConversationCoordinator.buildCompactMikaelPrompt(
            originalPrompt = original,
            chatContext = "",
            participants = team,
            maxPromptBytes = 480
        )
        assertTrue(prompt == null)
    }

    @Test
    fun oversizedOriginalRequestIsRejectedBeforeCallingAnyModel() = runBlocking {
        var calls = 0
        val result = runCatching {
            TeamConversationCoordinator.run(
                originalPrompt = "x".repeat(3_001),
                chatContext = "",
                participants = team,
                generate = { _, _, _ ->
                    calls++
                    Result.success("não deveria executar")
                }
            )
        }
        assertTrue(result.isFailure)
        assertEquals(0, calls)
    }

}
