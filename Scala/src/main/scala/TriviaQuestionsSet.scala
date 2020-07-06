import java.util

import com.fasterxml.jackson.annotation.JsonProperty

class TriviaQuestionsSet {
  @JsonProperty("response_code") val responseCode: Int = 0
  @JsonProperty("results") val results: util.List[TriviaQuestion] = null
}
