package com.codevalue.archnext

import com.squareup.moshi.Json

data class TriviaQuestion (
    val question: String,
    @Json(name = "correct_answer")val correctAnswer: String,
    @Json(name = "incorrect_answers")val incorrectAnswers: List<String>,
    val category: String,
    val type: String,
    val difficulty: String
)