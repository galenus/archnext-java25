package com.codevalue.archnext;

import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class App
{
    public static void main( String[] args ) {
        try {
            ApiContextInitializer.init();
            var botsApi = new TelegramBotsApi();
            try {
                botsApi.registerBot(new TriviaBot());
            } catch (TelegramApiException e) {
                System.out.println("Failed to register the trivia bot");
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
