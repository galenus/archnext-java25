package com.codevalue.archnext

object BotConfig {
    private const val BOT_NAME_VAR_NAME = "BOT_NAME"
    private const val BOT_TOKEN_VAR_NAME = "BOT_TOKEN"

    val botToken: String
        get() = System.getenv(BOT_TOKEN_VAR_NAME)

    val botName: String
        get() = System.getenv(BOT_NAME_VAR_NAME) ?: "j25trivia_bot"
}