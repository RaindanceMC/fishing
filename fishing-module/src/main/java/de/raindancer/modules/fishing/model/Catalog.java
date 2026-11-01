package de.raindancer.modules.fishing.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Every fish and every dish there is, in the order the catalogue lists them. */
public record Catalog(List<FishKind> fish, List<Dish> dishes) {

    public Catalog {
        fish = List.copyOf(fish);
        dishes = List.copyOf(dishes);
    }

    public Optional<FishKind> fish(String id) {
        return fish.stream().filter(kind -> kind.id().equals(id)).findFirst();
    }

    public Optional<Dish> dish(String id) {
        return dishes.stream().filter(dish -> dish.id().equals(id)).findFirst();
    }

    /** What bites in this water at exactly this tier. */
    public List<FishKind> in(Water water, Tier tier) {
        return fish.stream().filter(kind -> kind.tier() == tier && kind.livesIn(water)).toList();
    }

    public List<FishKind> in(Water water) {
        return fish.stream().filter(kind -> kind.livesIn(water)).toList();
    }

    public List<FishKind> ofTier(Tier tier) {
        return fish.stream().filter(kind -> kind.tier() == tier).toList();
    }

    /** The tiers that have at least one fish in this water, lowest first. */
    public List<Tier> tiersIn(Water water) {
        List<Tier> found = new ArrayList<>();
        for (Tier tier : Tier.values()) {
            if (!in(water, tier).isEmpty()) {
                found.add(tier);
            }
        }
        return found;
    }

    /** Fish grouped by tier, best last — the order a collection page lists them in. */
    public Map<Tier, List<FishKind>> byTier() {
        Map<Tier, List<FishKind>> grouped = new LinkedHashMap<>();
        for (Tier tier : Tier.values()) {
            grouped.put(tier, ofTier(tier).stream().sorted(Comparator.comparing(FishKind::name)).toList());
        }
        return grouped;
    }
}
