package com.codevalue.archnext

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.request.BaseRequest
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SendPoll
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.*
import org.jsoup.Jsoup
import java.io.IOException
import java.lang.RuntimeException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

object TriviaBot {
    private class UserContext(
            val processedQuestions: MutableSet<String> = hashSetOf(),
            val questionsPool: MutableList<TriviaQuestion> = mutableListOf()
    )

    private const val TRIVIA_API = "https://opentdb.com/api.php"
    private const val COMMAND_PREFIX = "/"
    private const val HELP_TEXT = "You can request next trivia question by sending /next or finish the session by sending /bye."

    private val httpClient = OkHttpClient()
    private val userContexts: ConcurrentMap<Int, UserContext> = ConcurrentHashMap()
    private val telegramBot: Lazy<TelegramBot> = lazy { TelegramBot(BotConfig.botToken) }
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val triviaQuestionsSetAdapter = moshi.adapter(TriviaQuestionsSet::class.java)

    private fun onUpdateReceived(update: Update) {
        if (update.message()?.text()?.startsWith(COMMAND_PREFIX) == true) {
            handleCommand(update)
        }
    }

    private fun buildSendMessage(messageText: String, update: Update): SendMessage =
            SendMessage(update.message().chat().id(), messageText)

    private fun handleCommand(update: Update) {
        val response = when (val commandText = update.message().text()) {
            "/start" -> handleSessionStart(update)
            "/bye" -> handleSessionEnd(update)
            "/next" -> handleNextRequest(update)
            else -> getDefaultResponse(update, commandText)
        }
        sendResponse(response)
    }

    private fun sendResponse(response: BaseRequest<*, *>?) {
        if (response == null) return

        try {
            telegramBot.value.execute(response)
        } catch (e: RuntimeException) {
            println("Failed to send the response: $e")
            e.printStackTrace()
        }
    }

    private fun handleSessionStart(update: Update): SendMessage {
        userContexts.putIfAbsent(update.message().from().id(), UserContext())
        return buildSendMessage(
                """
                    Hello ${update.message().from().firstName()}!
                    $HELP_TEXT
                    """.trimIndent(),
                update
        )
    }

    private fun handleNextRequest(update: Update): BaseRequest<*, *> {
        val userContext = userContexts[update.message().from().id()]
                ?: return buildSendMessage(
                        "I beg your pardon, but who are you? We haven't been introduced yet... (please /start me)",
                        update
                )

        if (userContext.questionsPool.isEmpty()) {
            updateQuestions(userContext) updateHandler@{ context, isSuccess ->
                if (isSuccess) {
                    val poll = getNextQuestion(context, update)
                    sendResponse(poll)
                    return@updateHandler
                }
                sendResponse(
                        buildSendMessage(
                                "Sorry, ${update.message().from().firstName()}, but I cannot find more question now, please try again later.",
                                update
                        )
                )
            }

            return buildSendMessage(
                    "${update.message().from().firstName()}, please wait till I find more questions for you...",
                    update
            )
        }

        return getNextQuestion(userContext, update)
    }

    private fun getNextQuestion(userContext: UserContext, update: Update): SendPoll {
        val nextQuestion = userContext.questionsPool.removeAt(userContext.questionsPool.lastIndex)
        userContext.processedQuestions += nextQuestion.question

        val correctAnswer = Jsoup.parse(nextQuestion.correctAnswer).text()
        val answers = nextQuestion.incorrectAnswers
                .map { Jsoup.parse(it).text() }
                .plus(correctAnswer)
                .shuffled()

        return SendPoll(
                update.message().chat().id(),
                Jsoup.parse(nextQuestion.question).text(),
                *answers.toTypedArray())
                .type("quiz")
                .correctOptionId(answers.indexOf(correctAnswer))
    }

    private fun updateQuestions(userContext: UserContext, updateHandler: (UserContext, Boolean) -> Unit) {
        val request = Request.Builder()
                .url("$TRIVIA_API?amount=10")
                .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("Couldn't get new questions, error occurred: $e")
                updateHandler(userContext, false)
            }

            override fun onResponse(call: Call, response: Response) {
                val triviaQuestionsSet = triviaQuestionsSetAdapter.fromJson(response.body()!!.source())
                if (triviaQuestionsSet!!.responseCode != 0) {
                    println("Couldn't get new questions, API response code: ${response.code()}")
                    updateHandler(userContext, false)
                    return
                }

                for (question in triviaQuestionsSet.results) {
                    if (!userContext.processedQuestions.contains(question.question)) {
                        userContext.questionsPool.add(question)
                    }
                }

                if (userContext.questionsPool.isEmpty()) {
                    updateHandler(userContext, false)
                    return
                }

                updateHandler(userContext, true)
            }
        })
    }

    private fun handleSessionEnd(update: Update): SendMessage? {
        if (userContexts.remove(update.message().from().id()) == null) return null

        return buildSendMessage(
                "Good bye ${update.message().from().firstName()}, see you next time!",
                update
        )
    }

    private fun getDefaultResponse(update: Update, commandText: String): SendMessage {
        return buildSendMessage(
                """
                    Sorry, I don't understand this command: '$commandText'
                    $HELP_TEXT
                    """.trimIndent(),
                update
        )
    }

    fun initialize() {
        telegramBot.value.setUpdatesListener { updates ->
            updates.filter { it.message() != null }.forEach { onUpdateReceived(it) }
            UpdatesListener.CONFIRMED_UPDATES_ALL
        }
    }
}