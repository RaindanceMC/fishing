package de.raindancer.modules.fishing.model;

import java.util.Locale;
import java.util.Optional;

/**
 * A rod's look, worn over whatever rod it is — the rod still fishes as itself. Most are earned by fishing (a level,
 * or every fish of one water caught); the rarest only come out of a sea chest, as a token.
 *
 * @param level  the fishing level that earns it; 0 when it is not earned that way
 * @param water  the water whose every fish earns it; null when not
 * @param chest  the sea chest whose token alone gives it; null when it is earned
 */
public enum RodSkin {
    DRIFTWOOD("Driftwood", "#c9b48a", 10, null, null),
    FROSTBITE("Frostbite", "#9fd8f2", 0, Water.FROZEN, null),
    TIDEPOOL("Tidepool", "#ff9a70", 0, Water.WARM_OCEAN, null),
    MAGMA("Magma", "#ff7a1a", 0, Water.LAVA, null),
    GOLDEN("Golden", "#f0c650", 40, null, null),
    ABYSSAL("Abyssal", "#3ad8c8", 0, null, Tier.EPIC),
    BONEWHALE("Bonewhale", "#ece4d0", 0, null, Tier.LEGENDARY),
    STARFALL("Starfall", "#ffe88a", 0, null, Tier.MYTHIC);

    private final String title;
    private final String hex;
    private final int level;
    private final Water water;
    private final Tier chest;

    RodSkin(String title, String hex, int level, Water water, Tier chest) {
        this.title = title;
        this.hex = hex;
        this.level = level;
        this.water = water;
        this.chest = chest;
    }

    public String title() {
        return title;
    }

    public String hex() {
        return hex;
    }

    public int level() {
        return level;
    }

    public Water water() {
        return water;
    }

    /** The sea chest tier its token comes out of; null when it is earned by fishing. */
    public Tier chest() {
        return chest;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The item model the rod wears: idle, and cast. */
    public String model() {
        return "rainsfishing:rod/skin_" + id();
    }

    public static Optional<RodSkin> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String wanted = text.trim().toLowerCase(Locale.ROOT);
        for (RodSkin skin : values()) {
            if (skin.id().equals(wanted)) {
                return Optional.of(skin);
            }
        }
        return Optional.empty();
    }
}
