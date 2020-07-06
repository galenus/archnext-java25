package com.codevalue.archnext;

public class BotConfig {
    private static final String BOT_NAME_VAR_NAME = "BOT_NAME";
    private static final String BOT_TOKEN_VAR_NAME = "BOT_TOKEN";

    public static String getBotName() {
        String envValue = System.getenv(BOT_NAME_VAR_NAME);
        return envValue  == null ? "j25trivia_bot" : envValue;
    }

    public static String getBotToken() {
        return System.getenv(BOT_TOKEN_VAR_NAME);
    }
}
