package de.raindancer.modules.fishing.model;

import de.raindancer.core.content.enchant.RainsEnchants;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Everything about one fishing rod that fishing asks: the parts fitted to it, which of the module's rods it is (none
 * for a plain vanilla rod), and the fishing enchantments on it.
 *
 * @param tier     the rod tier, or null for a vanilla rod
 * @param enchants fishing enchantment levels by key ({@code rainsenchants:deep_bite})
 */
public record Rod(Map<RodPart, Integer> ranks, RodTier tier, Map<String, Integer> enchants) {

    public static final String DEEP_BITE = RainsEnchants.DEEP_BITE;
    public static final String TWIN_CATCH = RainsEnchants.TWIN_CATCH;
    public static final String TREASURE_HUNTER = RainsEnchants.TREASURE_HUNTER;
    public static final String MYTHIC_LURE = RainsEnchants.MYTHIC_LURE;

    public static final Rod PLAIN = new Rod(Map.of(), null, Map.of());

    public Rod {
        EnumMap<RodPart, Integer> clamped = new EnumMap<>(RodPart.class);
        ranks.forEach((part, rank) -> {
            int value = Math.max(0, Math.min(part.most(), rank == null ? 0 : rank));
            if (value > 0) {
                clamped.put(part, value);
            }
        });
        ranks = Map.copyOf(clamped);
        Map<String, Integer> levels = new HashMap<>();
        enchants.forEach((key, level) -> {
            if (level != null && level > 0) {
                levels.put(key, Math.min(10, level));
            }
        });
        enchants = Map.copyOf(levels);
    }

    public Rod(Map<RodPart, Integer> ranks) {
        this(ranks, null, Map.of());
    }

    public int rank(RodPart part) {
        return ranks.getOrDefault(part, 0);
    }

    public int enchant(String key) {
        return enchants.getOrDefault(key, 0);
    }

    public Rod with(RodPart part, int rank) {
        EnumMap<RodPart, Integer> changed = new EnumMap<>(RodPart.class);
        changed.putAll(ranks);
        changed.put(part, rank);
        return new Rod(changed, tier, enchants);
    }

    public Rod withTier(RodTier newTier) {
        return new Rod(ranks, newTier, enchants);
    }

    public Rod withEnchant(String key, int level) {
        Map<String, Integer> changed = new HashMap<>(enchants);
        changed.put(key, level);
        return new Rod(ranks, tier, changed);
    }

    /** Lava with an Ember Line or an Ember or Leviathan Rod; the void with a Void Line or the Void Rod. */
    public boolean fishes(Water water) {
        boolean byLine = switch (water) {
            case LAVA -> rank(RodPart.LINE) >= 1;
            case VOID -> rank(RodPart.LINE) >= 2;
            default -> true;
        };
        return byLine || tier != null && tier.fishes(water);
    }

    /** How much sooner fish bite from the rod itself and Deep Bite, as a factor on the wait (1 = no sooner). */
    public double ownWaitFactor() {
        double fromTier = tier == null ? 1.0 : 1.0 - tier.waitPercent() / 100.0;
        return fromTier * (1.0 - 0.07 * enchant(DEEP_BITE));
    }

    /** Extra sea chest chance out of a thousand, from the rod itself and Treasure Hunter. */
    public int ownChestPermille() {
        return (tier == null ? 0 : tier.chestPermille()) + 10 * enchant(TREASURE_HUNTER);
    }

    /** Extra weight on rare and better, as a fraction, from the rod itself and Mythic Lure. */
    public double ownRareBonus() {
        return (tier == null ? 0 : tier.rarePercent() / 100.0) + 0.08 * enchant(MYTHIC_LURE);
    }

    /** Extra double-catch chance out of a thousand, from the rod itself and Twin Catch. */
    public int ownDoublePermille() {
        return (tier == null ? 0 : tier.doublePermille()) + 50 * enchant(TWIN_CATCH);
    }

    public boolean isPlain() {
        return ranks.isEmpty() && tier == null && enchants.isEmpty();
    }
}
