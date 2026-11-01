package de.raindancer.modules.fishing.rules;

import java.util.Locale;
import java.util.Set;

/**
 * What vanilla put on the hook, and whether it is swapped for a catch of ours: its fish and its junk are — nobody
 * fishes for lily pads and old boots — its treasure is not. A damaged fishing rod is junk; an enchanted one treasure,
 * which vanilla gives as a fishing rod too, so a rod counts as junk only by name here and the caller keeps an
 * enchanted one.
 */
public final class VanillaCatchRule implements IFishingRule {

    private static final Set<String> FISH = Set.of("cod", "salmon", "pufferfish", "tropical_fish");
    private static final Set<String> JUNK = Set.of("lily_pad", "bowl", "leather", "leather_boots", "rotten_flesh",
            "stick", "string", "potion", "bone", "ink_sac", "tripwire_hook", "bamboo", "fishing_rod",
            // treasure once, but craftable now: nobody wants one off the hook
            "saddle");

    /** @param material the item's material key, {@code lily_pad} */
    public boolean replaced(String material) {
        String key = material.toLowerCase(Locale.ROOT).replace("minecraft:", "");
        return FISH.contains(key) || JUNK.contains(key);
    }

    @Override
    public String describe() {
        return "which of vanilla's catches are swapped for ours: fish and junk, never treasure";
    }
}
