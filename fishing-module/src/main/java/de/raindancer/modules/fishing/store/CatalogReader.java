package de.raindancer.modules.fishing.store;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.Dish;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.model.Water;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the catalogue the art was built from ({@code catalog.json}, shipped in the jar), so the fish the plugin
 * knows and the models the pack has come from one list.
 */
public final class CatalogReader {

    private static final String RESOURCE = "/de/raindancer/modules/fishing/catalog.json";

    private CatalogReader() {
    }

    public static Catalog shipped() {
        try (InputStream in = CatalogReader.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("catalog.json is missing from the jar");
            }
            return read(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            throw new IllegalStateException("catalog.json could not be read", unreadable);
        }
    }

    public static Catalog read(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        List<FishKind> fish = new ArrayList<>();
        for (JsonElement each : root.getAsJsonArray("fish")) {
            JsonObject entry = each.getAsJsonObject();
            Set<Water> waters = EnumSet.noneOf(Water.class);
            for (JsonElement water : entry.getAsJsonArray("waters")) {
                waters.add(Water.parse(water.getAsString()).orElseThrow(() ->
                        new IllegalArgumentException(entry.get("id").getAsString() + ": no water "
                                + water.getAsString())));
            }
            Set<String> biomes = new java.util.HashSet<>();
            if (entry.has("biomes")) {
                for (JsonElement biome : entry.getAsJsonArray("biomes")) {
                    biomes.add(biome.getAsString());
                }
            }
            fish.add(new FishKind(entry.get("id").getAsString(), entry.get("name").getAsString(),
                    tier(entry), waters, entry.has("glow") && entry.get("glow").getAsBoolean(), biomes));
        }
        List<Dish> dishes = new ArrayList<>();
        for (JsonElement each : root.getAsJsonArray("dishes")) {
            JsonObject entry = each.getAsJsonObject();
            List<String> ingredients = new ArrayList<>();
            entry.getAsJsonArray("ingredients").forEach(item -> ingredients.add(item.getAsString()));
            JsonArray food = entry.getAsJsonArray("food");
            List<Dish.DishEffect> effects = new ArrayList<>();
            for (JsonElement effect : entry.getAsJsonArray("effects")) {
                JsonArray parts = effect.getAsJsonArray();
                effects.add(new Dish.DishEffect(parts.get(0).getAsString(), parts.get(1).getAsInt(),
                        parts.get(2).getAsInt()));
            }
            dishes.add(new Dish(entry.get("id").getAsString(), entry.get("name").getAsString(), tier(entry),
                    ingredients, food.get(0).getAsInt(), food.get(1).getAsFloat(), effects));
        }
        return new Catalog(fish, dishes);
    }

    private static Tier tier(JsonObject entry) {
        return Tier.parse(entry.get("tier").getAsString()).orElseThrow(() ->
                new IllegalArgumentException(entry.get("id").getAsString() + ": no tier "
                        + entry.get("tier").getAsString()));
    }
}
