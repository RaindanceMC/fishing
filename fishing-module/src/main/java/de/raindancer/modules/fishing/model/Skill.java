package de.raindancer.modules.fishing.model;

import java.util.Locale;
import java.util.Optional;

/** What a fisher can get better at, one skill point per rank. Every skill has five ranks. */
public enum Skill {
    QUICK_BITE("Quick Bite", "Fish bite sooner: 6% less waiting per rank."),
    TREASURE_SENSE("Treasure Sense", "Sea chests bite more often: +1% chance per rank."),
    RARE_INSTINCT("Rare Instinct", "Rarer fish and chests come up more often: +10% weight per rank."),
    DOUBLE_CATCH("Double Catch", "Sometimes two fish come up at once: +4% chance per rank.");

    public static final int MOST = 5;

    private final String title;
    private final String effect;

    Skill(String title, String effect) {
        this.title = title;
        this.effect = effect;
    }

    public String title() {
        return title;
    }

    public String effect() {
        return effect;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Skill> parse(String text) {
        try {
            return Optional.of(valueOf(text.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException | NullPointerException unknown) {
            return Optional.empty();
        }
    }
}
