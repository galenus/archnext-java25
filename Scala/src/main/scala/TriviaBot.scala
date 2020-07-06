import akka.actor.typed.scaladsl._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import javax.ws.rs.client.{ClientBuilder, InvocationCallback}
import javax.ws.rs.core
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider
import org.jsoup.Jsoup
import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

import scala.collection.immutable.HashSet
import scala.jdk.CollectionConverters._
import scala.util.Random

object QuestionsFetcher {
  private val TRIVIA_API = "https://opentdb.com/api.php"

  case class GetQuestions(count: Int, replyTo: ActorRef[UserInteractor.Request])

  private lazy val httpClient =
    ClientBuilder.newClient.register(classOf[JacksonJsonProvider])

  def apply(): Behavior[GetQuestions] = Behaviors.receiveMessage {
    case GetQuestions(count, replyTo) =>
      httpClient
        .target(TRIVIA_API)
        .queryParam("amount", count)
        .request(core.MediaType.APPLICATION_JSON)
        .async()
        .get(new InvocationCallback[TriviaQuestionsSet] {
          override def completed(response: TriviaQuestionsSet): Unit =
            replyTo ! UserInteractor.UpdateQuestions(response)

          override def failed(throwable: Throwable): Unit =
            replyTo ! UserInteractor.FetchQuestionsError(throwable)
        })
      Behaviors.same
  }
}

object UserInteractor {

  sealed trait Request

  final case object Stop extends Request

  final case class GetQuestion(sender: String,
                               chatId: Long,
                               replyTo: ActorRef[TriviaBot.Action])
      extends Request

  final case class UpdateQuestions(questions: TriviaQuestionsSet)
      extends Request

  final case class FetchQuestionsError(error: Throwable) extends Request

  sealed trait State

  final case class Data(
    processedQuestions: HashSet[String] = HashSet.empty[String],
    questionsPool: List[TriviaQuestion] = List.empty[TriviaQuestion]
  ) extends State

  private val random = new Random()

  private def constructPoll(question: TriviaQuestion,
                            getQuestion: GetQuestion): SendPoll = {
    val poll = new SendPoll()
    poll.setChatId(getQuestion.chatId)
    poll.setType("quiz")
    poll.setQuestion(Jsoup.parse(question.question).text())

    val answers = for (q <- question.incorrectAnswers.asScala)
      yield Jsoup.parse(q).text()
    val insertIndex = random.nextInt(answers.size)
    answers.insert(insertIndex, Jsoup.parse(question.correctAnswer).text())

    poll.setOptions(answers.asJava)
    poll.setCorrectOptionId(insertIndex)

    poll
  }

  private def handleUpdatedQuestions(
    data: Data,
    getQuestion: GetQuestion,
    questionsFetcher: ActorRef[QuestionsFetcher.GetQuestions]
  ): Behavior[Request] = Behaviors.receive { (context, message) =>
    message match {
      case FetchQuestionsError(error) =>
        println(s"Failed to fetch questions due to an error: $error")
        reportNoQuestions(getQuestion)
        setState(data, questionsFetcher)

      case UpdateQuestions(questions) if questions.responseCode != 0 =>
        reportNoQuestions(getQuestion)
        setState(data, questionsFetcher)

      case UpdateQuestions(questions) =>
        val updatedData = Data(
          data.processedQuestions,
          data.questionsPool ++ (for (q <- questions.results.asScala
                                      if !data.processedQuestions
                                        .contains(q.question)) yield q)
        )
        if (updatedData.questionsPool.isEmpty) {
          reportNoQuestions(getQuestion)
        } else {
          context.self ! getQuestion
        }
        setState(updatedData, questionsFetcher)
    }
  }

  private def reportNoQuestions(getQuestion: GetQuestion): Unit = {
    getQuestion.replyTo ! TriviaBot.SendText(
      s"Sorry, ${getQuestion.sender}, but I cannot find more questions now, please try again later.",
      getQuestion.chatId
    )
  }

  private def setState(
    data: State,
    questionsFetcher: ActorRef[QuestionsFetcher.GetQuestions]
  ): Behavior[Request] = Behaviors.receive { (context, message) =>
    message match {
      case Stop => Behaviors.stopped
      case getQuestion @ GetQuestion(sender, chatId, replyTo) =>
        data match {
          case Data(processedQuestions, q :: qs) =>
            val poll = constructPoll(q, getQuestion)
            replyTo ! TriviaBot.Send(poll)
            setState(
              Data(processedQuestions.incl(q.question), qs),
              questionsFetcher
            )
          case d: Data =>
            questionsFetcher ! QuestionsFetcher.GetQuestions(10, context.self)
            replyTo ! TriviaBot.SendText(
              s"$sender, please wait till I find more questions for you...",
              chatId
            )
            handleUpdatedQuestions(d, getQuestion, questionsFetcher)
          case _ =>
            Behaviors.unhandled
        }
      case _ =>
        Behaviors.unhandled
    }
  }

  def apply(
    questionsFetcher: ActorRef[QuestionsFetcher.GetQuestions]
  ): Behavior[Request] = setState(Data(), questionsFetcher)
}

object CommandsProcessor {

  val HELP_TEXT =
    "You can request next trivia question by sending /next or finish the session by sending /bye."

  object CommandType extends Enumeration {
    type CommandType = Value
    val start: CommandsProcessor.CommandType.Value = Value("/start")
    val finish: CommandsProcessor.CommandType.Value = Value("/bye")
    val next: CommandsProcessor.CommandType.Value = Value("/next")

    def from(str: String): Option[CommandsProcessor.CommandType.Value] =
      values.find(v => v.toString == str)
  }

  import CommandsProcessor.CommandType.CommandType

  sealed trait Command

  final case class KnownCommand(commandType: CommandType,
                                sender: String,
                                senderId: Int,
                                chatId: Long,
                                replyTo: ActorRef[TriviaBot.Action])
      extends Command

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    val questionsFetcher = context.spawnAnonymous(QuestionsFetcher())

    Behaviors.receive { (context, message) =>
      message match {
        case KnownCommand(
            CommandType.start,
            sender,
            senderId,
            chatId,
            replyTo
            ) =>
          context.spawn(
            UserInteractor(questionsFetcher),
            s"userInteractor-$senderId"
          )
          replyTo ! TriviaBot.SendText(s"Hello $sender!\n$HELP_TEXT", chatId)
          Behaviors.same

        case KnownCommand(
            CommandType.finish,
            sender,
            senderId,
            chatId,
            replyTo
            ) =>
          context.child(s"userInteractor-$senderId") match {
            case Some(userInteractor: ActorRef[UserInteractor.Request]) =>
              userInteractor ! UserInteractor.Stop
              replyTo ! TriviaBot
                .SendText(s"Good bye $sender, see you next time!", chatId)
            case _ =>
          }
          Behaviors.same

        case KnownCommand(
            CommandType.next,
            sender,
            senderId,
            chatId,
            replyTo
            ) =>
          context.child(s"userInteractor-$senderId") match {
            case Some(userInteractor: ActorRef[UserInteractor.Request]) =>
              userInteractor ! UserInteractor
                .GetQuestion(sender, chatId, replyTo)
            case _ =>
              replyTo ! TriviaBot.SendText(
                "I beg your pardon, but who are you? We haven't been introduced yet... (please /start me)",
                chatId
              )
          }
          Behaviors.same

        case _ =>
          Behaviors.unhandled
      }
    }
  }
}

object TriviaBot {

  sealed trait Action

  final case object Initialize extends Action

  final case class SendText(text: String, chatId: Long) extends Action

  final case class Send(poll: SendPoll) extends Action

  private final case class ReportUnrecognized(text: String, chatId: Long)
      extends Action

  import CommandsProcessor._

  class BotBridge(context: ActorContext[Action],
                  commandsProcessor: ActorRef[CommandsProcessor.Command])
      extends TelegramLongPollingBot {
    override def getBotUsername: String = BotConfig.botName

    override def getBotToken: String = BotConfig.botToken

    override def onUpdateReceived(update: Update): Unit = {
      if (!update.hasMessage || !update.getMessage.hasText) return

      val message = update.getMessage
      val text = message.getText
      val sender = message.getFrom.getFirstName
      val senderId = message.getFrom.getId
      val chatId = message.getChatId

      CommandType.from(text) match {
        case Some(commandType) =>
          commandsProcessor ! KnownCommand(
            commandType,
            sender,
            senderId,
            chatId,
            context.self
          )
        case _ => context.self ! ReportUnrecognized(text, chatId)
      }
    }

    def send(msg: Any): Unit = {
      try {
        execute[java.io.Serializable, BotApiMethod[java.io.Serializable]](
          msg.asInstanceOf[BotApiMethod[java.io.Serializable]]
        )
      } catch {
        case e: TelegramApiException =>
          println(s"Failed to send message due to an error: $e")
          e.printStackTrace()
      }
    }
  }

  def apply(): Behavior[Action] = Behaviors.setup { context =>
    val commandsProcessor = context.spawnAnonymous(CommandsProcessor())
    val botBridge = new BotBridge(context, commandsProcessor)

    def send(text: String, chatId: Long): Unit = {
      botBridge.send(new SendMessage(chatId, text))
    }

    Behaviors.receiveMessage {
      case Initialize =>
        initialize(botBridge)
        Behaviors.same
      case ReportUnrecognized(text, chatId) =>
        send(
          s"Sorry, I don't understand this command: '$text'\n$HELP_TEXT",
          chatId
        )
        Behaviors.same
      case SendText(text, chatId) =>
        send(text, chatId)
        Behaviors.same
      case Send(poll) =>
        botBridge.send(poll)
        Behaviors.same
    }
  }

  private def initialize(botBridge: BotBridge): Unit = {
    try {
      lazy val botsApi = new TelegramBotsApi()
      botsApi.registerBot(botBridge)
    } catch {
      case e: TelegramApiException =>
        println(s"Failed to register the trivia bot: $e")
      case e: Throwable =>
        println(s"Unknown exception was thrown: $e")
        e.printStackTrace()
    }
  }

}

object Main extends App {
  ApiContextInitializer.init()

  val system = ActorSystem(TriviaBot(), "TriviaBot")

  system ! TriviaBot.Initialize
}
