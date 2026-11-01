package de.raindancer.modules.fishing.model;

import java.util.Locale;
import java.util.Optional;

/**
 * What a fishing rod can be fitted with. The ranks are written onto the rod itself, so an upgraded rod is worth
 * keeping — and worth not losing.
 */
public enum RodPart {
    HOOK("Hook", 5, "Fish bite sooner: 8% less waiting per rank."),
    LURE("Chest Lure", 3, "Sea chests bite more often: +1.5% chance per rank."),
    REEL("Reel", 3, "Sometimes two fish come up at once: +5% chance per rank."),
    LINE("Line", 2, "Rank I is the Ember Line, which fishes lava. Rank II is the Void Line, which fishes the void.");

    private final String title;
    private final int most;
    private final String effect;

    RodPart(String title, int most, String effect) {
        this.title = title;
        this.most = most;
        this.effect = effect;
    }

    public String title() {
        return title;
    }

    public int most() {
        return most;
    }

    public String effect() {
        return effect;
    }

    /** What rank {@code rank} of this part is called on the rod. */
    public String rankName(int rank) {
        if (this == LINE) {
            return switch (rank) {
                case 0 -> "Silk Line";
                case 1 -> "Ember Line";
                default -> "Void Line";
            };
        }
        return rank <= 0 ? title + ": none" : title + " " + numeral(rank);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<RodPart> parse(String text) {
        try {
            return Optional.of(valueOf(text.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException | NullPointerException unknown) {
            return Optional.empty();
        }
    }

    public static String numeral(int rank) {
        return switch (rank) {
            case 0 -> "0";
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(rank);
        };
    }
}
