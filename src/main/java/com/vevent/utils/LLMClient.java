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
import java.util.logging.Level;

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

        if (apiKey == null || apiKey.isEmpty() || apiKey.equalsIgnoreCase("PON_TU_API_KEY_AQUI")) {
            plugin.getLogger().warning("[LLMClient] API key no configurada o usa el valor por defecto. Las peticiones a Gemini fallarán.");
        }
        if (model == null || model.isEmpty()) {
            plugin.getLogger().warning("[LLMClient] Modelo no configurado en llm-generation.model.");
        }
        plugin.getLogger().info("[LLMClient] Inicializado con modelo '" + model + "'. API key: " + (apiKey != null && !apiKey.isEmpty() ? "configurada" : "AUSENTE"));
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

        String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + this.model + ":generateContent";

        plugin.getLogger().info("[LLMClient] Enviando petición a " + geminiUrl + " para tier '" + tier + "'");

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(geminiUrl))
                .timeout(Duration.ofMinutes(1))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", this.apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()));

        long startTime = System.currentTimeMillis();

        return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    int status = response.statusCode();

                    if (status != 200) {
                        String bodyPreview = response.body();
                        if (bodyPreview != null && bodyPreview.length() > 1000) {
                            bodyPreview = bodyPreview.substring(0, 1000) + "...";
                        }
                        plugin.getLogger().warning("[LLMClient] HTTP " + status + " tras " + elapsed + "ms");
                        plugin.getLogger().warning("[LLMClient] Respuesta del servidor: " + bodyPreview);
                        return null;
                    }

                    plugin.getLogger().info("[LLMClient] HTTP 200 en " + elapsed + "ms. Parseando respuesta...");
                    try {
                        JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
                        String rawLootJson = jsonResponse.getAsJsonArray("candidates").get(0).getAsJsonObject()
                                .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                                .get("text").getAsString();
                        LootProfile profile = gson.fromJson(rawLootJson, LootProfile.class);
                        plugin.getLogger().info("[LLMClient] Botín generado correctamente: tier=" + profile.getTier()
                                + ", mobs=" + profile.getMobs() + ", enchantments=" + profile.getEnchantments());
                        return profile;
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "[LLMClient] Error al parsear respuesta JSON: " + e.getMessage(), e);
                        String bodyPreview = response.body();
                        if (bodyPreview != null && bodyPreview.length() > 1500) {
                            bodyPreview = bodyPreview.substring(0, 1500) + "...";
                        }
                        plugin.getLogger().warning("[LLMClient] Body recibido: " + bodyPreview);
                        return null;
                    }
                }).exceptionally(ex -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    plugin.getLogger().log(Level.SEVERE,
                            "[LLMClient] Excepción HTTP tras " + elapsed + "ms: " + ex.getClass().getSimpleName() + " - " + ex.getMessage(), ex);
                    return null;
                });
    }
}