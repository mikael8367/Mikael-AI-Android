package com.mikael.ai

import java.math.BigDecimal
import java.math.MathContext

/** Avaliador aritmético local: aceita apenas números, operadores e parênteses. */
internal object SafeCalculator {
    fun evaluate(expression: String): String {
        require(expression.length in 1..120) { "Expressão muito longa." }
        val parser = Parser(expression)
        val value = parser.parse()
        require(parser.finished()) { "Expressão contém caracteres ou operações não permitidas." }
        return value.stripTrailingZeros().toPlainString()
    }

    private class Parser(private val source: String) {
        private var index = 0
        private val math = MathContext.DECIMAL64

        fun finished(): Boolean {
            skipSpaces()
            return index == source.length
        }

        fun parse(): BigDecimal {
            val value = expression()
            skipSpaces()
            return value
        }

        private fun expression(): BigDecimal {
            var value = term()
            while (true) {
                skipSpaces()
                value = when {
                    consume('+') -> value.add(term(), math)
                    consume('-') -> value.subtract(term(), math)
                    else -> return value
                }
            }
        }

        private fun term(): BigDecimal {
            var value = factor()
            while (true) {
                skipSpaces()
                value = when {
                    consume('*') -> value.multiply(factor(), math)
                    consume('/') -> {
                        val divisor = factor()
                        require(divisor.compareTo(BigDecimal.ZERO) != 0) { "Divisão por zero." }
                        value.divide(divisor, math)
                    }
                    else -> return value
                }
            }
        }

        private fun factor(): BigDecimal {
            skipSpaces()
            if (consume('+')) return factor()
            if (consume('-')) return factor().negate()
            if (consume('(')) {
                val value = expression()
                skipSpaces()
                require(consume(')')) { "Parêntese não foi fechado." }
                return value
            }
            val start = index
            var dots = 0
            while (index < source.length && (source[index].isDigit() || source[index] == '.' || source[index] == ',')) {
                if (source[index] == '.' || source[index] == ',') dots++
                index++
            }
            require(index > start && dots <= 1) { "Número inválido na expressão." }
            return BigDecimal(source.substring(start, index).replace(',', '.'))
        }

        private fun consume(char: Char): Boolean {
            if (index < source.length && source[index] == char) {
                index++
                return true
            }
            return false
        }

        private fun skipSpaces() {
            while (index < source.length && source[index].isWhitespace()) index++
        }
    }
}
