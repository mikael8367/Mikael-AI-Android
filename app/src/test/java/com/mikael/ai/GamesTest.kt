package com.mikael.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GamesTest {
    @Test
    fun menuCommandWorksWithoutModelOrSession() {
        val result = Games.handle(null, "/games")
        assertNotNull(result)
        assertNull(result?.state)
        assertTrue(result!!.text.contains("/games velha"))
    }

    @Test
    fun ticTacToeStartsAndMakesAnAiMove() {
        val start = Games.handle(null, "/GAMES   velha")!!
        assertEquals("velha", start.state?.type)
        val turn = Games.handle(start.state, "1")!!
        assertNotNull(turn.state)
        assertEquals('X', turn.state!!.board[0])
        assertEquals(1, turn.state!!.board.count { it == 'O' })
    }

    @Test
    fun ticTacToeRejectsOccupiedCellWithoutChangingBoard() {
        val start = Games.handle(null, "/games velha")!!
        val turn = Games.handle(start.state, "1")!!
        val before = turn.state!!.board
        val invalid = Games.handle(turn.state, "1")!!
        assertEquals(before, invalid.state!!.board)
        assertTrue(invalid.text.contains("ocupada"))
    }

    @Test
    fun numberGameGivesCorrectHintAndEndsWhenGuessed() {
        val state = GameState(type = "adivinhacao", secretNumber = 50)
        assertTrue(Games.handle(state, "40")!!.text.contains("maior"))
        val won = Games.handle(state, "50")!!
        assertNull(won.state)
        assertTrue(won.text.contains("Acertou"))
    }

    @Test
    fun hangmanTracksLettersAndDoesNotChargeForCorrectGuess() {
        val state = GameState(type = "forca", word = "KOTLIN")
        val result = Games.handle(state, "k")!!
        assertEquals(6, result.state!!.attemptsLeft)
        assertTrue('K' in result.state!!.guessedLetters)
        val duplicate = Games.handle(result.state, "K")!!
        assertEquals(result.state, duplicate.state)
    }

    @Test
    fun quizRejectsInvalidAnswerAndAdvancesOnValidAnswer() {
        val state = GameState(type = "quiz")
        val invalid = Games.handle(state, "Z")!!
        assertEquals(0, invalid.state!!.quizIndex)
        val valid = Games.handle(state, "B")!!
        assertEquals(1, valid.state!!.quizIndex)
        assertEquals(1, valid.state!!.quizScore)
    }

    @Test
    fun endingGameClearsSession() {
        val state = GameState(type = "rps")
        val result = Games.handle(state, "pedra")!!
        assertNull(result.state)
        assertTrue(result.text.contains("Você:"))
    }
}
