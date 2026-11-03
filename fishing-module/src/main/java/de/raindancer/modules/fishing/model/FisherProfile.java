package de.raindancer.modules.fishing.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * One player's fishing: experience, the skill ranks they bought with it, and what they have caught and opened.
 *
 * @param experience fishing experience ever earned; the level follows from it and never goes down
 * @param spent      of that, what has gone on rod upgrades; the rest is {@link #available()}
 * @param nodes      skill tree ranks taken, by node id
 * @param freeUnlearn the next unlearning costs nothing — given once to everybody whose old skills became the tree
 * @param caught     how many of each fish id they have landed
 * @param chests     how many sea chests of each tier they have opened
 * @param rods       the rod tiers they have unlocked, which they may craft and cast
 * @param heaviest   the heaviest of each fish id they have landed, in kilograms
 * @param skins      the rod skins they got from sea chest tokens (the ones earned by fishing follow from the rest)
 * @param voidDry    fish caught over the void since their last mythic one: the void's debt to them
 */
public record FisherProfile(long experience, long spent, Map<String, Integer> nodes, Map<String, Integer> caught,
                            Map<Tier, Integer> chests, Set<RodTier> rods,
                            Map<String, Double> heaviest, Set<RodSkin> skins, boolean freeUnlearn, int voidDry) {

    public static final FisherProfile NEW = new FisherProfile(0, 0, Map.of(), Map.of(), Map.of(), Set.of(), Map.of(),
            Set.of(), false, 0);

    public FisherProfile(long experience, long spent, Map<String, Integer> nodes, Map<String, Integer> caught,
                         Map<Tier, Integer> chests, Set<RodTier> rods, Map<String, Double> heaviest,
                         Set<RodSkin> skins, boolean freeUnlearn) {
        this(experience, spent, nodes, caught, chests, rods, heaviest, skins, freeUnlearn, 0);
    }

    public FisherProfile(long experience, long spent, Map<String, Integer> nodes, Map<String, Integer> caught,
                         Map<Tier, Integer> chests, Set<RodTier> rods, Map<String, Double> heaviest) {
        this(experience, spent, nodes, caught, chests, rods, heaviest, Set.of(), false);
    }

    public FisherProfile(long experience, long spent, Map<String, Integer> nodes, Map<String, Integer> caught,
                         Map<Tier, Integer> chests, Set<RodTier> rods, Map<String, Double> heaviest,
                         Set<RodSkin> skins) {
        this(experience, spent, nodes, caught, chests, rods, heaviest, skins, false, 0);
    }

    public FisherProfile {
        experience = Math.max(0, experience);
        spent = Math.max(0, Math.min(experience, spent));
        Map<String, Integer> ranks = new HashMap<>();
        nodes.forEach((node, rank) -> {
            if (node != null && rank != null && rank > 0) {
                ranks.put(node, rank);
            }
        });
        nodes = Map.copyOf(ranks);
        Map<String, Integer> counted = new HashMap<>();
        caught.forEach((id, count) -> {
            if (count != null && count > 0) {
                counted.put(id, count);
            }
        });
        caught = Map.copyOf(counted);
        EnumMap<Tier, Integer> opened = new EnumMap<>(Tier.class);
        chests.forEach((tier, count) -> {
            if (count != null && count > 0) {
                opened.put(tier, count);
            }
        });
        chests = Map.copyOf(opened);
        rods = rods == null || rods.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(rods));
        heaviest = heaviest == null ? Map.of() : Map.copyOf(heaviest);
        skins = skins == null || skins.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(skins));
        voidDry = Math.max(0, voidDry);
    }

    public FisherProfile withVoidDry(int dry) {
        return new FisherProfile(experience, spent, nodes, caught, chests, rods, heaviest, skins, freeUnlearn, dry);
    }

    /** A catch of {@code amount} fish of {@code tier} over the void: a mythic pays the debt off, anything else adds. */
    public FisherProfile withVoidCatch(Tier tier, int amount) {
        return withVoidDry(tier == Tier.MYTHIC ? 0 : voidDry + Math.max(0, amount));
    }

    /** Fishing experience that can still be spent on rod upgrades. */
    public long available() {
        return experience - spent;
    }

    /** {@code total} spent in all; never more than was earned. */
    public FisherProfile withSpent(long total) {
        return new FisherProfile(experience, total, nodes, caught, chests, rods, heaviest, skins, freeUnlearn, voidDry);
    }

    public int rank(String node) {
        return nodes.getOrDefault(node, 0);
    }

    public int caught(String fishId) {
        return caught.getOrDefault(fishId, 0);
    }

    public int totalCaught() {
        return caught.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int opened(Tier tier) {
        return chests.getOrDefault(tier, 0);
    }

    public FisherProfile withExperience(long more) {
        return new FisherProfile(experience + Math.max(0, more), spent, nodes, caught, chests, rods, heaviest, skins,
                freeUnlearn, voidDry);
    }

    public FisherProfile withNode(String node, int rank) {
        Map<String, Integer> changed = new HashMap<>(nodes);
        changed.put(node, rank);
        return new FisherProfile(experience, spent, changed, caught, chests, rods, heaviest, skins, freeUnlearn, voidDry);
    }

    /** Every node given back; a free unlearning is used up by it. */
    public FisherProfile withoutNodes() {
        return new FisherProfile(experience, spent, Map.of(), caught, chests, rods, heaviest, skins, false, voidDry);
    }

    public FisherProfile withFreeUnlearn(boolean free) {
        return new FisherProfile(experience, spent, nodes, caught, chests, rods, heaviest, skins, free, voidDry);
    }

    public FisherProfile withCatch(String fishId, int amount) {
        Map<String, Integer> changed = new HashMap<>(caught);
        changed.merge(fishId, Math.max(0, amount), Integer::sum);
        return new FisherProfile(experience, spent, nodes, changed, chests, rods, heaviest, skins, freeUnlearn, voidDry);
    }

    public double heaviest(String fishId) {
        return heaviest.getOrDefault(fishId, 0.0);
    }

    /** Remembers a weight when it beats the heaviest of that fish so far. */
    public FisherProfile withWeight(String fishId, double kilos) {
        if (kilos <= heaviest(fishId)) {
            return this;
        }
        Map<String, Double> changed = new HashMap<>(heaviest);
        changed.put(fishId, kilos);
        return new FisherProfile(experience, spent, nodes, caught, chests, rods, changed, skins, freeUnlearn, voidDry);
    }

    public boolean hasUnlocked(RodTier rod) {
        return rods.contains(rod);
    }

    public FisherProfile withRod(RodTier rod) {
        EnumSet<RodTier> changed = rods.isEmpty() ? EnumSet.noneOf(RodTier.class) : EnumSet.copyOf(rods);
        changed.add(rod);
        return new FisherProfile(experience, spent, nodes, caught, chests, changed, heaviest, skins, freeUnlearn, voidDry);
    }

    public FisherProfile withChestOpened(Tier tier) {
        EnumMap<Tier, Integer> changed = new EnumMap<>(Tier.class);
        changed.putAll(chests);
        changed.merge(tier, 1, Integer::sum);
        return new FisherProfile(experience, spent, nodes, caught, changed, rods, heaviest, skins, freeUnlearn, voidDry);
    }

    public boolean hasSkin(RodSkin skin) {
        return skins.contains(skin);
    }

    public FisherProfile withSkin(RodSkin skin) {
        EnumSet<RodSkin> changed = skins.isEmpty() ? EnumSet.noneOf(RodSkin.class) : EnumSet.copyOf(skins);
        changed.add(skin);
        return new FisherProfile(experience, spent, nodes, caught, chests, rods, heaviest, changed, freeUnlearn, voidDry);
    }
}
