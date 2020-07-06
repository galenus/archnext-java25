object BotConfig {
  lazy val botName: String =
    Option(System.getenv("BOT_NAME")).getOrElse("j25trivia_bot")
  lazy val botToken: String = System.getenv("BOT_TOKEN")
}
