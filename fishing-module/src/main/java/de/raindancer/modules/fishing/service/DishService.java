package de.raindancer.modules.fishing.service;

import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.Dish;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.Tier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.Keyed;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.recipe.CookingBookCategory;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The dishes: crafted raw from the catch (a named fish, any fish of a tier, or any fish at all — vanilla cod and
 * salmon too) and the larder, then cooked; and what eating one does — the hunger the dish promises rather than the cod
 * or stew it is made of, and its effects. Every recipe is in the vanilla recipe book.
 */
public final class DishService implements IFishingService {

    private final Plugin plugin;
    private final Server server;
    private final LogChannel log;
    private final Messages messages;
    private final Catalog catalog;
    private final FishForge forge;
    private final List<NamespacedKey> registered = new ArrayList<>();

    public DishService(Plugin plugin, Server server, LogChannel log, Messages messages, Catalog catalog,
                       FishForge forge) {
        this.plugin = plugin;
        this.server = server;
        this.log = log;
        this.messages = messages;
        this.catalog = catalog;
        this.forge = forge;
    }

    @Override
    public void settings(FishingSettings settings) {
        // Nothing here reads the settings: a recipe is what the catalogue says it is.
    }

    /**
     * Adds every dish's recipes: crafted raw on the table, then cooked into the dish in a furnace, a smoker or over
     * a campfire. All of them sit in the recipe book's food pages; returns how many the server took.
     */
    public int registerRecipes() {
        int added = 0;
        for (Dish dish : catalog.dishes()) {
            ItemStack raw = forge.raw(dish, 1);
            ItemStack cooked = forge.dish(dish, 1);
            ShapelessRecipe crafted = new ShapelessRecipe(key("dish_" + dish.id()), raw);
            crafted.setCategory(CraftingBookCategory.MISC);
            crafted.setGroup("rainsfishing_dishes");
            boolean complete = true;
            for (String ingredient : dish.ingredients()) {
                RecipeChoice choice = choiceFor(ingredient);
                if (choice == null) {
                    log.warn("The {} recipe names '{}', which is no fish or item; it is left out.", dish.id(),
                            ingredient);
                    complete = false;
                    break;
                }
                crafted.addIngredient(choice);
            }
            if (!complete) {
                continue;
            }
            RecipeChoice uncooked = new RecipeChoice.ExactChoice(raw);
            float experience = 0.35f + 0.25f * dish.tier().ordinal();
            List<Recipe> recipes = List.of(crafted,
                    cooking(new FurnaceRecipe(key("cook_" + dish.id()), cooked, uncooked, experience, 200)),
                    cooking(new SmokingRecipe(key("smoke_" + dish.id()), cooked, uncooked, experience, 100)),
                    cooking(new CampfireRecipe(key("campfire_" + dish.id()), cooked, uncooked, experience, 600)));
            for (Recipe recipe : recipes) {
                if (server.addRecipe(recipe, false)) {
                    registered.add(((Keyed) recipe).getKey());
                    added++;
                }
            }
        }
        server.updateRecipes();
        return added;
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(plugin, name);
    }

    private static Recipe cooking(CookingRecipe<?> recipe) {
        recipe.setCategory(CookingBookCategory.FOOD);
        recipe.setGroup("rainsfishing_dishes");
        return recipe;
    }

    /** Puts every dish recipe in this player's recipe book. */
    public void discover(Player player) {
        if (!registered.isEmpty()) {
            player.discoverRecipes(registered);
        }
    }

    private RecipeChoice choiceFor(String ingredient) {
        if (ingredient.equals("any:fish")) {
            List<ItemStack> fish = new ArrayList<>();
            fish.add(new ItemStack(Material.COD));
            fish.add(new ItemStack(Material.SALMON));
            catalog.fish().forEach(kind -> fish.add(forge.fish(kind, 1)));
            return new RecipeChoice.ExactChoice(fish);
        }
        if (ingredient.startsWith("any:")) {
            Tier tier = Tier.parse(ingredient.substring(4)).orElse(null);
            if (tier == null) {
                return null;
            }
            List<ItemStack> fish = catalog.ofTier(tier).stream().map(kind -> forge.fish(kind, 1)).toList();
            return fish.isEmpty() ? null : new RecipeChoice.ExactChoice(fish);
        }
        if (ingredient.startsWith("minecraft:")) {
            Material material = Material.matchMaterial(ingredient);
            return material == null ? null : new RecipeChoice.MaterialChoice(material);
        }
        FishKind kind = catalog.fish(ingredient).orElse(null);
        return kind == null ? null : new RecipeChoice.ExactChoice(forge.fish(kind, 1));
    }

    public void unregisterRecipes() {
        registered.forEach(key -> server.removeRecipe(key, false));
        registered.clear();
        server.updateRecipes();
    }

    /**
     * Somebody ate a dish: top their hunger up to what the dish promises (vanilla has just given what its base item
     * gives) and put its effects on them, a tick later so vanilla's own eating has finished.
     */
    public void eaten(Player player, Dish dish, Material base) {
        int baseFood = base == Material.MUSHROOM_STEW ? 6 : 5;
        float baseSaturation = base == Material.MUSHROOM_STEW ? 7.2f : 6.0f;
        Scheduling.entityLater(plugin, player, 1L, () -> {
            player.setFoodLevel(Math.min(20, player.getFoodLevel() + Math.max(0, dish.food() - baseFood)));
            player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation()
                    + Math.max(0f, dish.saturation() - baseSaturation)));
            for (Dish.DishEffect effect : dish.effects()) {
                PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(
                        effect.effect().toLowerCase(Locale.ROOT)));
                if (type != null) {
                    player.addPotionEffect(new PotionEffect(type, effect.seconds() * 20, effect.amplifier()));
                }
            }
            if (dish.tier().atLeast(Tier.EPIC)) {
                messages.send(player, "fishing.dish.eaten", "dish", dish.name());
            }
        });
    }
}
