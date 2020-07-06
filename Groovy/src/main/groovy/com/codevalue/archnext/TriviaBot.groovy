package com.codevalue.archnext

import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider
import org.jsoup.Jsoup
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.meta.generics.LongPollingBot

import javax.ws.rs.client.Client
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.InvocationCallback
import javax.ws.rs.core.MediaType
import java.util.concurrent.ConcurrentHashMap

class TriviaBot extends TelegramLongPollingBot implements LongPollingBot {
    private static final String TRIVIA_API = "https://opentdb.com/api.php"

    private static class UserContext {
        Set<String> processedQuestions = []
        List<TriviaQuestion> questionsPool = []
    }

    private static final String COMMAND_PREFIX = "/"
    private static final String HELP_TEXT = "You can request next trivia question by sending /next or finish the session by sending /bye."

    private final Random random = new Random()
    private final Client httpClient = ClientBuilder.newClient().register(JacksonJsonProvider.class)
    private final ConcurrentHashMap<Integer, UserContext> userContexts = new ConcurrentHashMap<Integer, UserContext>()

    String getBotUsername() { BotConfig.getBotName() }

    String getBotToken() { BotConfig.getBotToken() }

    void onUpdateReceived(Update update) {
        if (!update.hasMessage()) return

        Message message = update.getMessage()
        if (!message.hasText()) return

        if (message.getText().startsWith(COMMAND_PREFIX)) {
            handleCommand(update)
        }
    }

    private def handleCommand(Update update) {
        String commandText = update.getMessage().getText()

        def response
        switch (commandText) {
            case "/start":
                response = handleSessionStart(update)
                break
            case "/bye":
                response = handleSessionEnd(update)
                break
            case "/next":
                response = handleNextRequest(update)
                break
            default:
                response = getDefaultResponse(update, commandText)
        }

        sendResponse(response)
    }

    private def sendResponse(response) {
        if (response) {
            try {
                execute(response)
            } catch (TelegramApiException e) {
                System.out.println("Failed to send the response: ${e}")
                e.printStackTrace()
            }
        }
    }

    private def handleSessionStart(Update update) {
        userContexts.putIfAbsent(update.getMessage().getFrom().getId(), new UserContext())
        buildSendMessage(
                "Hello ${update.getMessage().getFrom().getFirstName()}!\n${HELP_TEXT}",
                update
        )
    }

    private def handleNextRequest(final Update update) {
        UserContext userContext = userContexts.get(update.getMessage().getFrom().getId())
        if (!userContext) {
            return buildSendMessage(
                    "I beg your pardon, but who are you? We haven't been introduced yet... (please /start me)",
                    update
            )
        }

        if (userContext.questionsPool.isEmpty()) {
            updateQuestions(
                    userContext,
                    { UserContext context, boolean isSuccess ->
                        if (isSuccess) {
                            SendPoll poll = getNextQuestion(context, update)
                            sendResponse(poll)
                            return
                        }

                        sendResponse(
                                buildSendMessage(
                                        "Sorry, ${update.getMessage().getFrom().getFirstName()}, but I cannot find more questions now, please try again later.",
                                        update
                                )
                        )
                    }
            )

            return buildSendMessage(
                    "${update.getMessage().getFrom().getFirstName()}, please wait till I find more questions for you...",
                    update
            )
        }

        getNextQuestion(userContext, update)
    }

    private SendPoll getNextQuestion(UserContext userContext, Update update) {
        SendPoll poll = new SendPoll()
        poll.setChatId(update.getMessage().getChatId())
        poll.setType("quiz")

        TriviaQuestion question = userContext.questionsPool.remove(userContext.questionsPool.size() - 1)
        userContext.processedQuestions += question.getQuestion()
        poll.setQuestion(Jsoup.parse(question.getQuestion()).text())

        def answers = question.getIncorrectAnswers()
            .collect { it -> Jsoup.parse(it).text()}
        def insertIndex = random.nextInt(answers.size())
        answers.add(insertIndex, Jsoup.parse(question.getCorrectAnswer()).text())
        poll.setOptions(answers)
        poll.setCorrectOptionId(insertIndex)

        return poll
    }

    private void updateQuestions(final UserContext userContext, updateHandler) {
        httpClient
                .target(TRIVIA_API)
                .queryParam("amount", 10)
                .request(MediaType.APPLICATION_JSON)
                .async()
                .get(new InvocationCallback<TriviaQuestionsSet>() {
                    void completed(TriviaQuestionsSet triviaQuestionsSet) {
                        if (triviaQuestionsSet.getResponseCode() != 0) {
                            println("Couldn't get new questions, response code: ${triviaQuestionsSet.getResponseCode()}")
                            updateHandler(userContext, false)
                            return
                        }

                        userContext.questionsPool += triviaQuestionsSet.getResults()
                                .findAll({!(it.getQuestion() in userContext.processedQuestions)})

                        if (userContext.questionsPool.size() == 0) {
                            updateHandler(userContext, false)
                            return
                        }

                        updateHandler(userContext, true)
                    }

                    void failed(Throwable throwable) {
                        println("Couldn't get new questions, error: ${throwable}")
                        updateHandler(userContext, false)
                    }
                })
    }

    private SendMessage handleSessionEnd(Update update) {
        if (!userContexts.remove(update.getMessage().getFrom().getId())) return null

        return buildSendMessage(
                "Good bye ${update.getMessage().getFrom().getFirstName()}, see you next time!",
                update
        )
    }

    private static SendMessage getDefaultResponse(Update update, String commandText) {
        return buildSendMessage(
                "Sorry, I don't understand this command: '${commandText}'\n${HELP_TEXT}",
                update
        )
    }

    private static SendMessage buildSendMessage(String messageText, Update update) {
        SendMessage message = new SendMessage()
        message.setChatId(update.getMessage().getChatId())
        message.setText(messageText)
        return message
    }
}
