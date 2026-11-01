package de.raindancer.modules.fishing.model;

import java.util.Locale;
import java.util.Optional;

/**
 * How good something is, from a common fish to the best thing the void gives up. Fish, dishes and sea chests share
 * the one ladder, so "a legendary" means the same wherever a player reads it.
 *
 * @param chest  the sea chest of this tier, which is also the name its model files carry
 * @param colour the tier's colour, as a hex number
 */
public enum Tier {
    COMMON("Common", "driftwood", "Driftwood", 0xBFBAB0, 5),
    UNCOMMON("Uncommon", "reef", "Reef", 0x5CDC82, 10),
    RARE("Rare", "pearl", "Pearl", 0x6EAFFF, 20),
    EPIC("Epic", "abyss", "Abyss", 0xBE82FF, 40),
    LEGENDARY("Legendary", "leviathan", "Leviathan", 0xFFBE46, 80),
    MYTHIC("Mythic", "void", "Void", 0x7FF6FF, 160);

    private final String title;
    private final String chest;
    private final String chestTitle;
    private final int colour;
    private final int experience;

    Tier(String title, String chest, String chestTitle, int colour, int experience) {
        this.title = title;
        this.chest = chest;
        this.chestTitle = chestTitle;
        this.colour = colour;
        this.experience = experience;
    }

    public String title() {
        return title;
    }

    public String chest() {
        return chest;
    }

    /** "Driftwood Chest", "Void Chest". */
    public String chestName() {
        return chestTitle + " Chest";
    }

    public int colour() {
        return colour;
    }

    /** {@code #rrggbb}, for a MiniMessage colour tag the caller writes around it. */
    public String hex() {
        return String.format(Locale.ROOT, "#%06x", colour);
    }

    /** Fishing experience for catching something of this tier. */
    public int experience() {
        return experience;
    }

    public boolean atLeast(Tier other) {
        return ordinal() >= other.ordinal();
    }

    public Optional<Tier> below() {
        return ordinal() == 0 ? Optional.empty() : Optional.of(values()[ordinal() - 1]);
    }

    public Optional<Tier> above() {
        return ordinal() == values().length - 1 ? Optional.empty() : Optional.of(values()[ordinal() + 1]);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Tier> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String wanted = text.trim().toLowerCase(Locale.ROOT);
        for (Tier tier : values()) {
            if (tier.id().equals(wanted) || tier.chest.equals(wanted)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }
}
