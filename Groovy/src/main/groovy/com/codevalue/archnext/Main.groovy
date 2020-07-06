package com.codevalue.archnext

import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

try {
    ApiContextInitializer.init()
    def botsApi = new TelegramBotsApi()
    try {
        botsApi.registerBot(new TriviaBot())
    } catch (TelegramApiException e) {
        println "Failed to register the trivia bot"
        e.printStackTrace();
    }
} catch(Exception e) {
    e.printStackTrace()
}