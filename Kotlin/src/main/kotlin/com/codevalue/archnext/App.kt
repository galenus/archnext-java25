package com.codevalue.archnext

fun main() {
    try {
        TriviaBot.initialize();
    } catch (e: Exception) {
        println("Failed to register the trivia bot")
        e.printStackTrace()
    }
}
