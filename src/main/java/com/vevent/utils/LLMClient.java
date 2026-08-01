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
    private String model;
    private String apiKey;
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

    private String maskKey(String key) {
        if (key == null || key.isEmpty()) return "VACIA";
        if (key.length() <= 8) return key.substring(0, 3) + "***";
        return key.substring(0, 6) + "..." + key.substring(key.length() - 4);
    }

    public void updateConfig(String newModel, String newApiKey) {
        this.model = newModel;
        this.apiKey = newApiKey;
        plugin.getLogger().info("[LLMClient] Config actualizada — modelo='" + model + "', api-key=" + maskKey(apiKey));
    }

    public CompletableFuture<LootProfile> fetchEventDataAsync(String tier, String tierRules) {
        String tierMobRules;
        String tierLootRules;
        if (tier.equalsIgnoreCase("easy")) {
            tierMobRules = "Genera entre 40 y 60 mobs, solo mobs comunes: ZOMBIE, SKELETON, SPIDER, CREEPER, SLIME, SILVERFISH. ";
            tierLootRules = "RECOMPENSAS NIVEL EASY: "
                    + "- rareItems: máximo 2 ítems. SOLO: IRON_SWORD, IRON_CHESTPLATE, BOW, CROSSBOW, FISHING_ROD, SHIELD. NUNCA diamantes, netherita, totems, elytras ni objetos del nether. "
                    + "- farmingItems: SOLO comida y materiales básicos: APPLE, BAKED_POTATO, CARROT, BREAD, COOKED_BEEF, COOKED_PORKCHOP, ARROW, IRON_INGOT, LEATHER, FEATHER, STRING. Cantidades entre 4 y 16. "
                    + "- enchantments: máximo 3 encantamientos de nivel 1 o 2. Solo en ítems de hierro o arco. ";
        } else if (tier.equalsIgnoreCase("medium")) {
            tierMobRules = "Genera entre 50 y 70 mobs. 60% mobs comunes (ZOMBIE, SKELETON, SPIDER, CREEPER) y 40% mobs de nivel medio (ENDERMAN, WITCH, GUARDIAN, HUSK, STRAY, DROWNED, PILLAGER, MAGMA_CUBE). ";
            tierLootRules = "RECOMPENSAS NIVEL MEDIUM: "
                    + "- rareItems: máximo 3 ítems. SOLO: DIAMOND_SWORD, DIAMOND_CHESTPLATE, DIAMOND_PICKAXE, GOLDEN_APPLE, ENDER_PEARL, ENCHANTED_BOOK, SADDLE, NAME_TAG. NUNCA netherita, totems, elytras. "
                    + "- farmingItems: DIAMOND, EMERALD, GOLD_INGOT, IRON_INGOT, LAPIS_LAZULI, EXPERIENCE_BOTTLE, BLAZE_ROD. Cantidades entre 2 y 10. "
                    + "- enchantments: máximo 5 encantamientos de nivel 2 o 3. ";
        } else if (tier.equalsIgnoreCase("hardcore")) {
            tierMobRules = "Genera entre 60 y 80 mobs. INCLUYE SIEMPRE mobs básicos (ZOMBIE, SKELETON, SPIDER, CREEPER) como al menos el 25% del total. "
                    + "El resto: mobs de alto nivel (WITHER_SKELETON, BLAZE, PIGLIN_BRUTE, ENDERMAN, EVOKER, VINDICATOR, RAVAGER, WITCH, GUARDIAN, HOGLIN, ZOMBIFIED_PIGLIN, MAGMA_CUBE). ";
            tierLootRules = "RECOMPENSAS NIVEL HARDCORE: "
                    + "- rareItems: máximo 3 ítems. NETHERITE_INGOT, NETHERITE_SWORD, TOTEM_OF_UNDYING, ENCHANTED_GOLDEN_APPLE, ELYTRA (máximo 1), TRIDENT, WITHER_SKELETON_SKULL. "
                    + "- farmingItems: DIAMOND, ANCIENT_DEBRIS, NETHERITE_SCRAP, BLAZE_ROD, GHAST_TEAR, NETHER_STAR (máximo 1). Cantidades entre 1 y 8. "
                    + "- enchantments: máximo 6 encantamientos de nivel 3, 4 o 5. ";
        } else {
            tierMobRules = "Genera entre 40 y 60 mobs variados. ";
            tierLootRules = "RECOMPENSAS: genera botín balanceado según el tier. ";
        }
        String systemPrompt = "Eres un generador de botín para Minecraft. Genera un botín para el tier: " + tier + ". " +
                "Reglas del tier: " + tierRules + ". " +
                tierLootRules +
                "- CANTIDAD DE MOBS: " + tierMobRules +
                "Devuelve la respuesta estrictamente con esta estructura JSON: " +
                "{ \"tier\": \"Nombre del tier\", \"environmentDescription\": \"Clima o ambiente\", " +
                "\"enchantments\": [\"SHARPNESS-3\"], \"rareItems\": [\"TRIDENT\"], " +
                "\"farmingItems\": [\"DIAMOND\"], \"mobs\": {\"ZOMBIE\": 15, \"SKELETON\": 10} }";

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

        plugin.getLogger().info("[LLMClient] Enviando petición a " + geminiUrl + " para tier '" + tier + "'" +
                " | key=" + maskKey(this.apiKey));

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