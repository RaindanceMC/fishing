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
 * @param skills     ranks bought, each 1..{@link Skill#MOST}
 * @param caught     how many of each fish id they have landed
 * @param chests     how many sea chests of each tier they have opened
 * @param rods       the rod tiers they have unlocked, which they may craft and cast
 * @param heaviest   the heaviest of each fish id they have landed, in kilograms
 * @param skins      the rod skins they got from sea chest tokens (the ones earned by fishing follow from the rest)
 */
public record FisherProfile(long experience, long spent, Map<Skill, Integer> skills, Map<String, Integer> caught,
                            Map<Tier, Integer> chests, Set<RodTier> rods,
                            Map<String, Double> heaviest, Set<RodSkin> skins) {

    public static final FisherProfile NEW = new FisherProfile(0, 0, Map.of(), Map.of(), Map.of(), Set.of(), Map.of(),
            Set.of());

    public FisherProfile(long experience, long spent, Map<Skill, Integer> skills, Map<String, Integer> caught,
                         Map<Tier, Integer> chests, Set<RodTier> rods, Map<String, Double> heaviest) {
        this(experience, spent, skills, caught, chests, rods, heaviest, Set.of());
    }

    public FisherProfile {
        experience = Math.max(0, experience);
        spent = Math.max(0, Math.min(experience, spent));
        EnumMap<Skill, Integer> ranks = new EnumMap<>(Skill.class);
        skills.forEach((skill, rank) -> {
            int value = Math.max(0, Math.min(Skill.MOST, rank == null ? 0 : rank));
            if (value > 0) {
                ranks.put(skill, value);
            }
        });
        skills = Map.copyOf(ranks);
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
    }

    /** Fishing experience that can still be spent on rod upgrades. */
    public long available() {
        return experience - spent;
    }

    /** {@code total} spent in all; never more than was earned. */
    public FisherProfile withSpent(long total) {
        return new FisherProfile(experience, total, skills, caught, chests, rods, heaviest, skins);
    }

    public int rank(Skill skill) {
        return skills.getOrDefault(skill, 0);
    }

    public int pointsSpent() {
        return skills.values().stream().mapToInt(Integer::intValue).sum();
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
        return new FisherProfile(experience + Math.max(0, more), spent, skills, caught, chests, rods, heaviest, skins);
    }

    public FisherProfile withSkill(Skill skill, int rank) {
        EnumMap<Skill, Integer> changed = new EnumMap<>(Skill.class);
        changed.putAll(skills);
        changed.put(skill, rank);
        return new FisherProfile(experience, spent, changed, caught, chests, rods, heaviest, skins);
    }

    public FisherProfile withoutSkills() {
        return new FisherProfile(experience, spent, Map.of(), caught, chests, rods, heaviest, skins);
    }

    public FisherProfile withCatch(String fishId, int amount) {
        Map<String, Integer> changed = new HashMap<>(caught);
        changed.merge(fishId, Math.max(0, amount), Integer::sum);
        return new FisherProfile(experience, spent, skills, changed, chests, rods, heaviest, skins);
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
        return new FisherProfile(experience, spent, skills, caught, chests, rods, changed, skins);
    }

    public boolean hasUnlocked(RodTier rod) {
        return rods.contains(rod);
    }

    public FisherProfile withRod(RodTier rod) {
        EnumSet<RodTier> changed = rods.isEmpty() ? EnumSet.noneOf(RodTier.class) : EnumSet.copyOf(rods);
        changed.add(rod);
        return new FisherProfile(experience, spent, skills, caught, chests, changed, heaviest, skins);
    }

    public FisherProfile withChestOpened(Tier tier) {
        EnumMap<Tier, Integer> changed = new EnumMap<>(Tier.class);
        changed.putAll(chests);
        changed.merge(tier, 1, Integer::sum);
        return new FisherProfile(experience, spent, skills, caught, changed, rods, heaviest, skins);
    }

    public boolean hasSkin(RodSkin skin) {
        return skins.contains(skin);
    }

    public FisherProfile withSkin(RodSkin skin) {
        EnumSet<RodSkin> changed = skins.isEmpty() ? EnumSet.noneOf(RodSkin.class) : EnumSet.copyOf(skins);
        changed.add(skin);
        return new FisherProfile(experience, spent, skills, caught, chests, rods, heaviest, changed);
    }
}
