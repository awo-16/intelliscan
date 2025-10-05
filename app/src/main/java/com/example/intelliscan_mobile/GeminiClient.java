package com.example.intelliscan_mobile;

import okhttp3.*;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

public class GeminiClient {

    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta3/models/gemini-2.5-flash:generateContent";
    private static final String API_KEY = "YOUR_API_KEY"; // Replace with your actual API key

    private final OkHttpClient client;

    public GeminiClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void callLLMSummarize(String inputText, Callback callback) {
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("model", "gemini-2.5-flash");
            requestBody.put("contents", inputText);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.get("application/json")
        );

        Request request = new Request.Builder()
                .url(API_URL + "?key=" + API_KEY)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(callback);
    }
}