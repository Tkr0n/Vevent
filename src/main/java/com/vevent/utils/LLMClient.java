package com.vevent.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vevent.VEventPlugin;
import com.vevent.models.LootProfile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class LLMClient {

    private final VEventPlugin plugin;
    private final HttpClient httpClient;
    private final String model;
    private final String apiKey;
    private final Gson gson;

    public LLMClient(VEventPlugin plugin, String endpoint, String model, String apiKey) {
        this.plugin = plugin;
        this.model = model;
        this.apiKey = apiKey;
        this.gson = new Gson();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public CompletableFuture<LootProfile> fetchEventDataAsync(String tier, String tierRules) {
        String systemPrompt = "Eres un generador de botín para Minecraft. Genera un botín para el tier: " + tier + ". " +
                "Reglas: " + tierRules + ". " +
                "Devuelve la respuesta estrictamente con esta estructura JSON: " +
                "{ \"tier\": \"Nombre\", \"environmentDescription\": \"Clima\", \"enchantments\": [\"SHARPNESS-3\"], " +
                "\"rareItems\": [\"ELYTRA\"], \"farmingItems\": [\"DIAMOND\"], \"mobs\": {\"ZOMBIE\": 10, \"SKELETON\": 5} }";

        JsonObject requestBody = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject part = new JsonObject();
        JsonObject textObj = new JsonObject();
        textObj.addProperty("text", systemPrompt);
        JsonArray partsArray = new JsonArray();
        partsArray.add(textObj);
        part.add("parts", partsArray);
        contents.add(part);
        requestBody.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("responseMimeType", "application/json");
        requestBody.add("generationConfig", generationConfig);

        String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + this.model + ":generateContent?key=" + this.apiKey;

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(geminiUrl))
                .timeout(Duration.ofMinutes(1))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()));

        return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) return null;
                    try {
                        JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
                        String rawLootJson = jsonResponse.getAsJsonArray("candidates").get(0).getAsJsonObject()
                                .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                                .get("text").getAsString();
                        return gson.fromJson(rawLootJson, LootProfile.class);
                    } catch (Exception e) { return null; }
                }).exceptionally(ex -> null);
    }
}