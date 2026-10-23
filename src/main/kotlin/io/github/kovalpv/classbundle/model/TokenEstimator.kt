package io.github.kovalpv.classbundle.service

/**
 * Грубая оценка количества токенов для контекстного окна LLM.
 *
 * Точные токенайзеры (JTokkit, tiktoken) тяжёлые для плагина и зависят от модели.
 * Для UI достаточно эвристики: ~1 токен на 3.5 символа кода.
 *
 * Значение используется только как индикатор (FR-14); при экспорте
 * soft-limit `maxTokens` применяется к этой же оценке.
 */
internal object TokenEstimator {

    private const val CHARS_PER_TOKEN = 3.5

    fun estimate(text: String): Int = (text.length / CHARS_PER_TOKEN).toInt()

    fun estimateOrZero(text: String?): Int = text?.let(::estimate) ?: 0
}