package de.raindancer.modules.fishing.model;

import java.util.Locale;
import java.util.Optional;

/**
 * The kind of water a line is in, which decides what lives there. Lava and the void are waters too: a rod with the
 * right line fishes them like any lake.
 *
 * @param bobber the colour of the big float over a fishing spot here, which names its model
 */
public enum Water {
    RIVER("River", "river", 0xD33A33),
    OCEAN("Ocean", "ocean", 0x2F7FD6),
    WARM_OCEAN("Warm Ocean", "warm_ocean", 0xFF7A3C),
    SWAMP("Swamp", "swamp", 0x6A9A2C),
    FROZEN("Frozen Water", "frozen", 0x9FDCFF),
    CAVE("Cave Water", "cave", 0xE2C13C),
    LAVA("Lava", "river", 0xFF6A1A),
    VOID("The Void", "void", 0x7FF6FF);

    private final String title;
    private final String bobber;
    private final int ripple;

    Water(String title, String bobber, int ripple) {
        this.title = title;
        this.bobber = bobber;
        this.ripple = ripple;
    }

    public String title() {
        return title;
    }

    /** Which float model a spot here shows; lava borrows the river's red one. */
    public String bobber() {
        return bobber;
    }

    /** The ripple's tint at a spot here. */
    public int ripple() {
        return ripple;
    }

    /** Needs a line made for it — lava and the void eat an ordinary one. */
    public boolean needsSpecialLine() {
        return this == LAVA || this == VOID;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Water> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(text.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }
}
