package de.raindancer.modules.fishing.model;

import java.util.Locale;
import java.util.Optional;

/**
 * What a skill tree node does, per rank. Every value is a percentage, except the chest and double chances (out of a
 * thousand, shown as percent) and {@link #FIREPROOF} (on or off). A negative value is the price of a choice.
 */
public enum Perk {
    WAIT("Fish bite %s%% sooner", false),
    SPOT_WAIT("In a fishing spot, fish bite %s%% sooner", false),
    STORM_WAIT("While it rains, fish bite %s%% sooner", false),
    LAVA_WAIT("In lava, fish bite %s%% sooner", false),
    VOID_WAIT("Over the void, fish bite %s%% sooner", false),
    RARE("Rare catches and better come up %s%% more often", false),
    EPIC("Epic catches and better come up %s%% more often", false),
    LEGEND("Legendary and mythic catches come up %s%% more often", false),
    SPOT_RARE("In a fishing spot, rare catches and better come up %s%% more often", false),
    HOT_RARE("In lava and over the void, rare catches and better come up %s%% more often", false),
    CHEST("Sea chests bite %s%% more often", true),
    SPOT_CHEST("In a fishing spot, sea chests bite %s%% more often", true),
    LAVA_CHEST("In lava, sea chests bite %s%% more often", true),
    VOID_CHEST("Over the void, sea chests bite %s%% more often", true),
    DOUBLE("%s%% chance of two fish on one hook", true),
    CHEST_UP("%s%% chance a sea chest comes up a tier better", false),
    CHEST_ROLL("%s%% chance of an extra find in every sea chest", false),
    CHEST_MONEY("Sea chests pay %s%% more", false),
    WEIGHT("Fish weigh %s%% more", false),
    XP("%s%% more fishing experience", false),
    HOT_XP("%s%% more fishing experience in lava and over the void", false),
    BAIT_SAVE("%s%% chance bait is not used up", false),
    DISH("Dishes last %s%% longer", false),
    FIREPROOF("Fire resistance while your line is in lava", false);

    private final String text;
    /** Counted out of a thousand, shown as a percentage. */
    private final boolean permille;

    Perk(String text, boolean permille) {
        this.text = text;
        this.permille = permille;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** "Fish bite 6% sooner"; a negative value reads as the opposite: "Fish bite 20% later". */
    public String describe(double value) {
        if (this == FIREPROOF) {
            return text;
        }
        double shown = permille ? value / 10.0 : value;
        String number = shown == Math.rint(shown) ? String.valueOf((long) Math.abs(shown))
                : String.valueOf(Math.abs(shown));
        String line = String.format(Locale.ROOT, text, number);
        if (value < 0) {
            line = line.replace("sooner", "later").replace("more often", "less often").replace(" more", " less")
                    .replace("chance of", "less chance of");
        }
        return line;
    }

    public static Optional<Perk> parse(String text) {
        try {
            return Optional.of(valueOf(text.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException | NullPointerException unknown) {
            return Optional.empty();
        }
    }
}
