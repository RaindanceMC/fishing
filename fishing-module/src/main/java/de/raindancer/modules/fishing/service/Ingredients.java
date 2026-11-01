package de.raindancer.modules.fishing.service;

import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.Tier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

import java.util.ArrayList;
import java.util.List;

/**
 * What a recipe line names, as a recipe choice: {@code any:fish} (vanilla cod and salmon too), {@code any:<tier>},
 * one of our fish by id, or a vanilla item ({@code minecraft:string}). Null for anything else.
 */
final class Ingredients {

    private final Catalog catalog;
    private final FishForge forge;

    Ingredients(Catalog catalog, FishForge forge) {
        this.catalog = catalog;
        this.forge = forge;
    }

    RecipeChoice choiceFor(String ingredient) {
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
}
