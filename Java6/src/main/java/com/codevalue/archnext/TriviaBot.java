package com.codevalue.archnext;

import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;
import org.jsoup.Jsoup;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.LongPollingBot;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.core.MediaType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TriviaBot extends TelegramLongPollingBot implements LongPollingBot {
    private static final String TRIVIA_API = "https://opentdb.com/api.php";

    private interface UpdatedQuestionsHandler {
        void onUpdatedQuestionsAvailable(UserContext context, boolean isSuccess);
    }

    private static class UserContext {
        final Set<String> processedQuestions = new HashSet<String>();
        final List<TriviaQuestion> questionsPool = new ArrayList<TriviaQuestion>();
    }

    private static final String COMMAND_PREFIX = "/";
    private static final String HELP_TEXT = "You can request next trivia question by sending /next or finish the session by sending /bye.";

    private final Random random = new Random();
    private final Client httpClient = ClientBuilder.newClient().register(JacksonJsonProvider.class);
    private final ConcurrentMap<Integer, UserContext> userContexts = new ConcurrentHashMap<Integer, UserContext>();

    @Override
    public String getBotUsername() {
        return BotConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return BotConfig.getBotToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) return;

        Message message = update.getMessage();
        if (!message.hasText()) return;

        if (message.getText().startsWith(COMMAND_PREFIX)) {
            handleCommand(update);
        }
    }

    private void handleCommand(Update update) {
        String commandText = update.getMessage().getText();
        BotApiMethod<?> response;
        if (commandText.equals("/start")) {
            response = handleSessionStart(update);
        } else if (commandText.equals("/bye")) {
            response = handleSessionEnd(update);
        } else if (commandText.equals("/next")) {
            response = handleNextRequest(update);
        } else {
            response = getDefaultResponse(update, commandText);
        }

        sendResponse(response);
    }

    private void sendResponse(BotApiMethod<?> response) {
        if (response != null) {
            try {
                execute(response);
            } catch (TelegramApiException e) {
                System.out.println("Failed to send the response: " + e);
                e.printStackTrace();
            }
        }
    }

    private SendMessage handleSessionStart(Update update) {
        userContexts.putIfAbsent(update.getMessage().getFrom().getId(), new UserContext());
        return buildSendMessage(
                "Hello " + update.getMessage().getFrom().getFirstName() + "!\n" + HELP_TEXT,
                update
        );
    }

    private BotApiMethod<?> handleNextRequest(final Update update) {
        UserContext userContext = userContexts.get(update.getMessage().getFrom().getId());
        if (userContext == null) {
            return buildSendMessage(
                    "I beg your pardon, but who are you? We haven't been introduced yet... (please /start me)",
                    update
            );
        }

        if (userContext.questionsPool.isEmpty()) {
            updateQuestions(
                    userContext,
                    new UpdatedQuestionsHandler() {
                        @Override
                        public void onUpdatedQuestionsAvailable(UserContext context, boolean isSuccess) {
                            if (isSuccess) {
                                SendPoll poll = getNextQuestion(context, update);
                                sendResponse(poll);
                                return;
                            }

                            sendResponse(
                                    buildSendMessage(
                                            "Sorry, " + update.getMessage().getFrom().getFirstName() + ", but I cannot find more questions now, please try again later.",
                                            update
                                    )
                            );
                        }
                    }
            );

            return buildSendMessage(
                    update.getMessage().getFrom().getFirstName() + ", please wait till I find more questions for you...",
                    update
            );
        }

        return getNextQuestion(userContext, update);
    }

    private SendPoll getNextQuestion(UserContext userContext, Update update) {
        SendPoll poll = new SendPoll();
        poll.setChatId(update.getMessage().getChatId());
        poll.setType("quiz");

        TriviaQuestion question = userContext.questionsPool.remove(userContext.questionsPool.size() - 1);
        userContext.processedQuestions.add(question.getQuestion());
        poll.setQuestion(Jsoup.parse(question.getQuestion()).text());

        List<String> answers = new ArrayList<String>();
        for (String answer : question.getIncorrectAnswers()) {
            answers.add(Jsoup.parse(answer).text());
        }
        int insertIndex = random.nextInt(answers.size());
        answers.add(insertIndex, Jsoup.parse(question.getCorrectAnswer()).text());
        poll.setOptions(answers);
        poll.setCorrectOptionId(insertIndex);

        return poll;
    }

    private void updateQuestions(final UserContext userContext, final UpdatedQuestionsHandler updateHandler) {
        httpClient
                .target(TRIVIA_API)
                .queryParam("amount", 10)
                .request(MediaType.APPLICATION_JSON)
                .async()
                .get(new InvocationCallback<TriviaQuestionsSet>() {
                    @Override
                    public void completed(TriviaQuestionsSet triviaQuestionsSet) {
                        if (triviaQuestionsSet.getResponseCode() != 0) {
                            System.out.println("Couldn't get new questions, response code: " + triviaQuestionsSet.getResponseCode());
                            updateHandler.onUpdatedQuestionsAvailable(userContext, false);
                            return;
                        }

                        for (TriviaQuestion question : triviaQuestionsSet.getResults()) {
                            if (!userContext.processedQuestions.contains(question.getQuestion())) {
                                userContext.questionsPool.add(question);
                            }
                        }

                        if (userContext.questionsPool.size() == 0) {
                            updateHandler.onUpdatedQuestionsAvailable(userContext, false);
                            return;
                        }

                        updateHandler.onUpdatedQuestionsAvailable(userContext, true);
                    }

                    @Override
                    public void failed(Throwable throwable) {
                        System.out.println("Couldn't get new questions, error: " + throwable);
                        updateHandler.onUpdatedQuestionsAvailable(userContext, false);
                    }
                });
    }

    private SendMessage handleSessionEnd(Update update) {
        if (userContexts.remove(update.getMessage().getFrom().getId()) == null) return null;

        return buildSendMessage(
                "Good bye " + update.getMessage().getFrom().getFirstName() + ", see you next time!",
                update
        );
    }

    private SendMessage getDefaultResponse(Update update, String commandText) {
        return buildSendMessage(
                "Sorry, I don't understand this command: '" + commandText + "'\n" + HELP_TEXT,
                update
        );
    }

    private static SendMessage buildSendMessage(String messageText, Update update) {
        SendMessage message = new SendMessage();
        message.setChatId(update.getMessage().getChatId());
        message.setText(messageText);
        return message;
    }
}
