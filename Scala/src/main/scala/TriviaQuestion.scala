import java.util

import com.fasterxml.jackson.annotation.JsonProperty

class TriviaQuestion {
  @JsonProperty("question") val question: String = null
  @JsonProperty("correct_answer") val correctAnswer: String = null
  @JsonProperty("incorrect_answers") val incorrectAnswers: util.List[String] = null
  @JsonProperty("category") private val category: String = null
  @JsonProperty("type") private val `type`: String = null
  @JsonProperty("difficulty") private val difficulty: String = null
}

