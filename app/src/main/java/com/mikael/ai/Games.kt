package com.mikael.ai

import kotlin.random.Random

internal data class GameState(
    val type: String,
    val secretNumber: Int = 0,
    val board: List<Char> = List(9) { ' ' },
    val word: String = "",
    val guessedLetters: Set<Char> = emptySet(),
    val attemptsLeft: Int = 6,
    val quizIndex: Int = 0,
    val quizScore: Int = 0
)

internal data class GameReply(val state: GameState?, val text: String)

internal object Games {
    private data class QuizQuestion(val question: String, val options: List<String>, val answer: Int, val explanation: String)

    private val questions = listOf(
        QuizQuestion("Qual linguagem roda nativamente na JVM?", listOf("Python", "Java", "C", "JavaScript"), 1, "Java compila para bytecode executado pela JVM."),
        QuizQuestion("Qual estrutura segue o princípio FIFO?", listOf("Pilha", "Fila", "Árvore", "Grafo"), 1, "FIFO significa primeiro a entrar, primeiro a sair."),
        QuizQuestion("Qual palavra-chave declara uma constante em Kotlin?", listOf("var", "let", "val", "constante"), 2, "val cria uma referência somente para leitura."),
        QuizQuestion("Qual protocolo é usado normalmente para páginas seguras?", listOf("FTP", "HTTP", "HTTPS", "Telnet"), 2, "HTTPS protege a comunicação com TLS."),
        QuizQuestion("Qual destas opções é um sistema de controle de versão?", listOf("Git", "Gradle", "Kotlin", "SQLite"), 0, "Git controla o histórico de alterações do projeto.")
    )

    fun handle(current: GameState?, rawInput: String): GameReply? {
        val input = rawInput.trim()
        if (input.equals("/games", ignoreCase = true)) return GameReply(null, menu())
        if (input.equals("/games sair", ignoreCase = true) || input.equals("/games encerrar", ignoreCase = true)) {
            return if (current != null) GameReply(null, "Partida encerrada. Digite **/games** para escolher outro jogo.")
            else GameReply(null, "Nenhum jogo está em andamento. Digite **/games** para ver os jogos.")
        }
        if (input.startsWith("/games ", ignoreCase = true)) {
            val choice = input.removePrefix("/games").trim().lowercase()
            return start(choice)
        }
        if (current == null) return null
        return when (current.type) {
            "velha" -> playTicTacToe(current, input)
            "adivinhacao" -> playGuess(current, input)
            "forca" -> playHangman(current, input)
            "rps" -> playRps(current, input)
            "quiz" -> playQuiz(current, input)
            else -> GameReply(null, "Esse jogo não é reconhecido. Digite **/games** para escolher outro.")
        }
    }

    fun menu(): String = """
        **Central de jogos da Mikael AI**

        Escolha um jogo digitando um comando:
        • **/games velha** — Jogo da velha contra a IA
        • **/games adivinhacao** — Adivinhe um número de 1 a 100
        • **/games forca** — Descubra a palavra letra por letra
        • **/games rps** — Pedra, papel e tesoura
        • **/games quiz** — Quiz de programação com 5 perguntas

        Durante uma partida, digite **/games sair** para encerrar.
    """.trimIndent()

    private fun start(choice: String): GameReply = when (choice) {
        "velha", "jogo da velha", "tictactoe", "tic-tac-toe" -> {
            val state = GameState(type = "velha")
            GameReply(state, "Vamos jogar **Jogo da velha**! Você é **X** e eu sou **O**. Escolha uma casa de 1 a 9.\n\n" + boardText(state.board))
        }
        "adivinhacao", "adivinhação", "numero", "número" -> {
            val state = GameState(type = "adivinhacao", secretNumber = Random.nextInt(1, 101))
            GameReply(state, "Pensei em um número de **1 a 100**. Tente adivinhar! Digite um número.")
        }
        "forca" -> {
            val words = listOf("KOTLIN", "ANDROID", "COMPUTADOR", "ALGORITMO", "VARIAVEL", "PROGRAMA")
            val state = GameState(type = "forca", word = words.random())
            GameReply(state, "Vamos jogar **Forca**! Descubra a palavra. Você tem 6 tentativas. Digite uma letra.\n\n" + hangmanText(state))
        }
        "rps", "pedra", "pedra papel tesoura" -> GameReply(GameState(type = "rps"), "Vamos de **Pedra, papel e tesoura**! Digite pedra, papel ou tesoura.")
        "quiz", "perguntas" -> GameReply(GameState(type = "quiz"), quizText(0, 0))
        else -> GameReply(null, "Não reconheci esse jogo.\n\n" + menu())
    }

    private fun playTicTacToe(state: GameState, input: String): GameReply {
        val cell = input.toIntOrNull() ?: return GameReply(state, "Digite o número de uma casa livre (1 a 9).\n\n" + boardText(state.board))
        if (cell !in 1..9) return GameReply(state, "Escolha um número entre 1 e 9.\n\n" + boardText(state.board))
        val index = cell - 1
        if (state.board[index] != ' ') return GameReply(state, "Essa casa já está ocupada. Escolha outra.\n\n" + boardText(state.board))
        val board = state.board.toMutableList()
        board[index] = 'X'
        if (winner(board) == 'X') return GameReply(null, "Você venceu! **X ganhou.**\n\n" + boardText(board) + "\nDigite **/games velha** para jogar novamente.")
        if (board.none { it == ' ' }) return GameReply(null, "Deu velha! Empatamos.\n\n" + boardText(board) + "\nDigite **/games velha** para outra partida.")
        val aiMove = bestMove(board)
        if (aiMove >= 0) board[aiMove] = 'O'
        val result = winner(board)
        val response = when (result) {
            'O' -> "Eu ganhei desta vez! **O venceu.**"
            'X' -> "Você venceu! **X ganhou.**"
            else -> if (board.none { it == ' ' }) "Empatamos!" else "Minha jogada foi na casa **\${aiMove + 1}**. Sua vez!"
        }
        val next = if (result == null && board.any { it == ' ' }) state.copy(board = board) else null
        return GameReply(next, response + "\n\n" + boardText(board) + if (next == null) "\nDigite **/games velha** para jogar novamente." else "")
    }

    private fun bestMove(board: List<Char>): Int {
        var bestScore = Int.MIN_VALUE
        var best = -1
        for (i in board.indices) {
            if (board[i] != ' ') continue
            val candidate = board.toMutableList()
            candidate[i] = 'O'
            val score = minimax(candidate, false, 0)
            if (score > bestScore) { bestScore = score; best = i }
        }
        return best
    }

    private fun minimax(board: MutableList<Char>, aiTurn: Boolean, depth: Int): Int {
        when (winner(board)) {
            'O' -> return 10 - depth
            'X' -> return depth - 10
        }
        if (board.none { it == ' ' }) return 0
        if (aiTurn) {
            var best = Int.MIN_VALUE
            for (i in board.indices) if (board[i] == ' ') {
                board[i] = 'O'; best = maxOf(best, minimax(board, false, depth + 1)); board[i] = ' '
            }
            return best
        }
        var best = Int.MAX_VALUE
        for (i in board.indices) if (board[i] == ' ') {
            board[i] = 'X'; best = minOf(best, minimax(board, true, depth + 1)); board[i] = ' '
        }
        return best
    }

    private fun winner(board: List<Char>): Char? {
        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )
        return lines.firstNotNullOfOrNull { line ->
            val first = board[line[0]]
            if (first != ' ' && line.all { board[it] == first }) first else null
        }
    }

    private fun boardText(board: List<Char>): String = buildString {
        for (row in 0..2) {
            val cells = (0..2).map { col ->
                val index = row * 3 + col
                board[index].takeIf { it != ' ' }?.toString() ?: (index + 1).toString()
            }
            append(cells.joinToString(" | "))
            if (row < 2) append("\n---------\n")
        }
    }

    private fun playGuess(state: GameState, input: String): GameReply {
        val guess = input.toIntOrNull() ?: return GameReply(state, "Digite apenas um número inteiro entre 1 e 100.")
        if (guess !in 1..100) return GameReply(state, "O número precisa estar entre 1 e 100.")
        val reply = when {
            guess < state.secretNumber -> "É maior! Tente novamente."
            guess > state.secretNumber -> "É menor! Tente novamente."
            else -> "Acertou! O número era **$guess**.\nDigite **/games adivinhacao** para uma nova rodada."
        }
        return GameReply(if (guess == state.secretNumber) null else state, reply)
    }

    private fun playHangman(state: GameState, input: String): GameReply {
        val guess = input.trim().uppercase()
        if (guess.length != 1 || guess[0] !in 'A'..'Z') return GameReply(state, "Digite apenas uma letra de A a Z.\n\n" + hangmanText(state))
        val letter = guess[0]
        if (letter in state.guessedLetters) return GameReply(state, "Você já tentou essa letra. Escolha outra.\n\n" + hangmanText(state))
        val updated = state.copy(guessedLetters = state.guessedLetters + letter, attemptsLeft = state.attemptsLeft - if (letter in state.word) 0 else 1)
        val won = updated.word.all { it in updated.guessedLetters }
        val lost = updated.attemptsLeft <= 0
        val ending = when {
            won -> "\nVocê descobriu a palavra! A palavra era **\${updated.word}**."
            lost -> "\nAs tentativas acabaram. A palavra era **\${updated.word}**."
            letter in state.word -> "\nBoa! A letra existe na palavra."
            else -> "\nEssa letra não aparece na palavra."
        }
        return GameReply(if (won || lost) null else updated, ending + "\n\n" + hangmanText(updated) + if (won || lost) "\nDigite **/games forca** para outra rodada." else "")
    }

    private fun hangmanText(state: GameState): String {
        val shown = state.word.map { if (it in state.guessedLetters) it else '_' }.joinToString(" ")
        val tried = state.guessedLetters.sorted().joinToString(", ").ifBlank { "nenhuma" }
        return "Palavra: **$shown**\nTentativas restantes: **\${state.attemptsLeft}**\nLetras usadas: $tried"
    }

    private fun playRps(state: GameState, input: String): GameReply {
        val player = input.trim().lowercase()
        if (player !in setOf("pedra", "papel", "tesoura")) return GameReply(state, "Escolha pedra, papel ou tesoura.")
        val ai = listOf("pedra", "papel", "tesoura").random()
        val result = when {
            player == ai -> "Empate!"
            (player == "pedra" && ai == "tesoura") || (player == "papel" && ai == "pedra") || (player == "tesoura" && ai == "papel") -> "Você venceu!"
            else -> "Eu venci!"
        }
        return GameReply(null, "Você: **$player**\nEu: **$ai**\n\n$result\nDigite **/games rps** para jogar novamente.")
    }

    private fun playQuiz(state: GameState, input: String): GameReply {
        val answer = input.trim().uppercase().removeSuffix(")")
        val question = questions.getOrNull(state.quizIndex) ?: return GameReply(null, "Quiz finalizado! Sua pontuação: **\${state.quizScore}/\${questions.size}**.")
        val selected = when (answer) { "A", "1" -> 0; "B", "2" -> 1; "C", "3" -> 2; "D", "4" -> 3; else -> -1 }
        if (selected !in 0..3) return GameReply(state, "Responda com A, B, C ou D.\n\n" + quizText(state.quizIndex, state.quizScore))
        val correct = selected == question.answer
        val score = state.quizScore + if (correct) 1 else 0
        val explanation = if (correct) "Correto!" else "Não foi dessa vez. A resposta era **\${'A' + question.answer}) \${question.options[question.answer]}**."
        val nextIndex = state.quizIndex + 1
        if (nextIndex >= questions.size) return GameReply(null, "$explanation\n\${question.explanation}\n\nQuiz concluído! Você fez **$score/\${questions.size}** pontos.\nDigite **/games quiz** para jogar novamente.")
        val next = state.copy(quizIndex = nextIndex, quizScore = score)
        return GameReply(next, "$explanation\n\${question.explanation}\n\n" + quizText(nextIndex, score))
    }

    private fun quizText(index: Int, score: Int): String {
        val question = questions[index]
        val choices = question.options.mapIndexed { i, option -> "\${'A' + i}) $option" }.joinToString("\n")
        return "**Quiz de programação — Pergunta \${index + 1}/\${questions.size}**\n$score acertos até agora.\n\n\${question.question}\n$choices\n\nResponda com A, B, C ou D."
    }
}
