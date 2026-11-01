package de.raindancer.modules.fishing.service;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.RodTier;
import de.raindancer.modules.fishing.rules.RodTierRule;
import de.raindancer.modules.fishing.store.ProfileStore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The rods as things a fisher earns: unlocked with fishing experience once their level is reached, then crafted — the
 * recipe appears in the recipe book on unlocking, and the table refuses it to anybody who has not — and cast only by
 * whoever unlocked them.
 */
public final class RodWorkshopService implements IFishingService {

    private final Plugin plugin;
    private final Server server;
    private final Messages messages;
    private final Effects effects;
    private final ProfileService profiles;
    private final ProfileStore store;
    private final FishForge forge;
    private final Catalog catalog;
    private final Map<RodTier, NamespacedKey> recipes = new EnumMap<>(RodTier.class);

    public RodWorkshopService(Plugin plugin, Server server, Messages messages, Effects effects, ProfileService profiles,
                              ProfileStore store, FishForge forge, Catalog catalog) {
        this.plugin = plugin;
        this.server = server;
        this.messages = messages;
        this.effects = effects;
        this.profiles = profiles;
        this.store = store;
        this.forge = forge;
        this.catalog = catalog;
    }

    @Override
    public void settings(FishingSettings settings) {
        // What a rod needs is the rod's own; nothing here reads the settings.
    }

    public RodTierRule rule() {
        return new RodTierRule(profiles.levels());
    }

    /**
     * A rod in the shape of the vanilla one: its shaft running up the diagonal, its line down the right, and a fish of
     * its tier where the hand holds it.
     */
    public int registerRecipes() {
        for (RodTier rod : RodTier.values()) {
            Material shaft = Material.matchMaterial(rod.shaft());
            Material line = Material.matchMaterial(rod.line());
            List<ItemStack> fish = new ArrayList<>(catalog.ofTier(rod.fishIngredient()).stream()
                    .map(kind -> forge.fish(kind, 1)).toList());
            if (shaft == null || line == null || fish.isEmpty()) {
                continue;
            }
            NamespacedKey key = new NamespacedKey(plugin, "rod_" + rod.id());
            ShapedRecipe recipe = new ShapedRecipe(key, forge.rod(rod));
            recipe.shape("  S", " SL", "F L");
            recipe.setIngredient('S', new RecipeChoice.MaterialChoice(shaft));
            recipe.setIngredient('L', new RecipeChoice.MaterialChoice(line));
            recipe.setIngredient('F', new RecipeChoice.ExactChoice(fish));
            recipe.setCategory(CraftingBookCategory.EQUIPMENT);
            recipe.setGroup("rainsfishing_rods");
            if (server.addRecipe(recipe, false)) {
                recipes.put(rod, key);
            }
        }
        server.updateRecipes();
        return recipes.size();
    }

    public void unregisterRecipes() {
        recipes.values().forEach(key -> server.removeRecipe(key, false));
        recipes.clear();
        server.updateRecipes();
    }

    public Optional<RodTier> rodMadeBy(NamespacedKey recipe) {
        return recipes.entrySet().stream().filter(entry -> entry.getValue().equals(recipe)).map(Map.Entry::getKey)
                .findFirst();
    }

    /** The recipes of every rod this player has unlocked, into their recipe book. */
    public void discover(Player player) {
        FisherProfile profile = profiles.of(player.getUniqueId());
        List<NamespacedKey> theirs = new ArrayList<>();
        recipes.forEach((rod, key) -> {
            if (profile.hasUnlocked(rod)) {
                theirs.add(key);
            }
        });
        if (!theirs.isEmpty()) {
            player.discoverRecipes(theirs);
        }
    }

    public boolean mayUse(Player player, Rod rod) {
        return rule().mayCast(profiles.of(player.getUniqueId()), rod);
    }

    public boolean hasUnlocked(UUID player, RodTier rod) {
        return profiles.of(player).hasUnlocked(rod);
    }

    /** Unlocks a rod for its fishing experience; {@code then} runs on the player's thread, either way. */
    public void unlock(Player player, RodTier rod, Runnable then) {
        UUID id = player.getUniqueId();
        RodTierRule rule = rule();
        Optional<RodTierRule.Refusal> refusal = rule.refusal(profiles.of(id), rod);
        if (refusal.isPresent()) {
            refuse(player, rod, refusal.get(), then);
            return;
        }
        Scheduling.async(plugin, () -> {
            AtomicReference<RodTierRule.Refusal> refused = new AtomicReference<>();
            boolean written = store.change(id, profile -> {
                Optional<RodTierRule.Refusal> now = rule.refusal(profile, rod);
                if (now.isPresent()) {
                    refused.set(now.get());
                    return profile;
                }
                return profile.withSpent(profile.spent() + rod.cost()).withRod(rod);
            });
            Scheduling.entity(plugin, player, () -> {
                if (!written || refused.get() != null) {
                    refuse(player, rod, refused.get() == null ? RodTierRule.Refusal.NOT_ENOUGH_EXPERIENCE
                            : refused.get(), then);
                    return;
                }
                NamespacedKey key = recipes.get(rod);
                if (key != null) {
                    player.discoverRecipe(key);
                }
                effects.play(id, Cues.EARNED);
                messages.send(player, "fishing.rods.unlocked", "rod", rod.title(), "cost",
                        String.valueOf(rod.cost()));
                then.run();
            });
        });
    }

    private void refuse(Player player, RodTier rod, RodTierRule.Refusal why, Runnable then) {
        effects.play(player.getUniqueId(), Cues.NO);
        switch (why) {
            case ALREADY -> messages.send(player, "fishing.rods.already", "rod", rod.title());
            case PREVIOUS_FIRST -> messages.send(player, "fishing.rods.previous-first", "rod", rod.title(),
                    "previous", RodTier.values()[Math.max(0, rod.ordinal() - 1)].title());
            case LEVEL_TOO_LOW -> messages.send(player, "fishing.rods.level-too-low", "rod", rod.title(),
                    "level", String.valueOf(rod.level()));
            case NOT_ENOUGH_EXPERIENCE -> messages.send(player, "fishing.rods.not-enough", "rod", rod.title(),
                    "cost", String.valueOf(rod.cost()),
                    "have", String.valueOf(profiles.of(player.getUniqueId()).available()));
        }
        then.run();
    }
}
