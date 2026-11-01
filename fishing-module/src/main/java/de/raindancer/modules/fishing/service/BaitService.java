package de.raindancer.modules.fishing.service;

import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.Bait;
import de.raindancer.modules.fishing.model.Catalog;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bait: crafted from a fish and a few things from the larder (all in the recipe book), held in the off hand, one taken
 * by every cast — and remembered on that hook until what bites is decided.
 */
public final class BaitService implements IFishingService {

    /** What one craft gives, and what goes in. */
    private record Recipe(int makes, List<String> ingredients) {
    }

    private static final Map<Bait, Recipe> RECIPES = new EnumMap<>(Map.of(
            Bait.WRIGGLER, new Recipe(4, List.of("any:fish", "minecraft:wheat_seeds", "minecraft:rotten_flesh")),
            Bait.SHIMMER, new Recipe(4, List.of("any:uncommon", "minecraft:glow_ink_sac", "minecraft:glowstone_dust")),
            Bait.GILDED, new Recipe(4, List.of("any:rare", "minecraft:gold_nugget", "minecraft:gold_nugget",
                    "minecraft:sweet_berries")),
            Bait.ABYSS, new Recipe(4, List.of("any:epic", "minecraft:prismarine_crystals", "minecraft:ink_sac",
                    "minecraft:ender_pearl")),
            Bait.STARLURE, new Recipe(2, List.of("any:legendary", "minecraft:amethyst_shard",
                    "minecraft:chorus_fruit", "minecraft:phantom_membrane"))));

    private final Plugin plugin;
    private final Server server;
    private final LogChannel log;
    private final Catalog catalog;
    private final FishForge forge;
    private final List<NamespacedKey> registered = new ArrayList<>();
    private final Map<UUID, Bait> onHooks = new ConcurrentHashMap<>();

    public BaitService(Plugin plugin, Server server, LogChannel log, Catalog catalog, FishForge forge) {
        this.plugin = plugin;
        this.server = server;
        this.log = log;
        this.catalog = catalog;
        this.forge = forge;
    }

    @Override
    public void settings(FishingSettings settings) {
        // Bait is what it is.
    }

    public int registerRecipes() {
        Ingredients ingredients = new Ingredients(catalog, forge);
        int added = 0;
        for (Map.Entry<Bait, Recipe> each : RECIPES.entrySet()) {
            ShapelessRecipe recipe = new ShapelessRecipe(new NamespacedKey(plugin, "bait_" + each.getKey().id()),
                    forge.bait(each.getKey(), each.getValue().makes()));
            recipe.setCategory(CraftingBookCategory.MISC);
            recipe.setGroup("rainsfishing_bait");
            boolean complete = true;
            for (String ingredient : each.getValue().ingredients()) {
                RecipeChoice choice = ingredients.choiceFor(ingredient);
                if (choice == null) {
                    log.warn("The {} recipe names '{}', which is nothing here; it is left out.", each.getKey().id(),
                            ingredient);
                    complete = false;
                    break;
                }
                recipe.addIngredient(choice);
            }
            if (complete && server.addRecipe(recipe, false)) {
                registered.add(((Keyed) recipe).getKey());
                added++;
            }
        }
        server.updateRecipes();
        return added;
    }

    public void discover(Player player) {
        if (!registered.isEmpty()) {
            player.discoverRecipes(registered);
        }
    }

    public void unregisterRecipes() {
        registered.forEach(key -> server.removeRecipe(key, false));
        registered.clear();
        server.updateRecipes();
    }

    /** A cast: one bait comes off the off hand onto this hook, if there is any. Call on the player's thread. */
    public Optional<Bait> castWith(Player player, FishHook hook) {
        ItemStack held = player.getInventory().getItemInOffHand();
        Optional<Bait> bait = forge.baitOf(held);
        bait.ifPresent(on -> {
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.setAmount(held.getAmount() - 1);
            }
            onHooks.put(hook.getUniqueId(), on);
        });
        return bait;
    }

    /** The bait on a hook still out; null for none. */
    public Bait on(UUID hook) {
        return onHooks.get(hook);
    }

    /** The bait on a hook that came in (with a catch or without); null for none. It is gone from the hook. */
    public Bait take(UUID hook) {
        return onHooks.remove(hook);
    }
}
