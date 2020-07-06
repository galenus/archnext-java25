package com.codevalue.archnext;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class TriviaQuestionsSet {
    @JsonProperty("response_code")
    private int responseCode;

    @JsonProperty("results")
    private List<TriviaQuestion> results;

    public int getResponseCode() {
        return responseCode;
    }

    public List<TriviaQuestion> getResults() {
        return results;
    }
}
