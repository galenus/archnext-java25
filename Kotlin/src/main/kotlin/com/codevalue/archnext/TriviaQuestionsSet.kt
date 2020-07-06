package com.codevalue.archnext

import com.squareup.moshi.Json

data class TriviaQuestionsSet(
    @Json(name = "response_code") val responseCode: Int,
    val results: List<TriviaQuestion>
)
