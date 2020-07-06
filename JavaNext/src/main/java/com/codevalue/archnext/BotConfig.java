package com.codevalue.archnext;

import java.util.Optional;

public class BotConfig {
    private static final String BOT_NAME_VAR_NAME = "BOT_NAME";
    private static final String BOT_TOKEN_VAR_NAME = "BOT_TOKEN";

    public static String getBotName() {
        return Optional.ofNullable(System.getenv(BOT_NAME_VAR_NAME)).orElse("j25trivia_bot");
    }

    public static String getBotToken() {
        return System.getenv(BOT_TOKEN_VAR_NAME);
    }
}
