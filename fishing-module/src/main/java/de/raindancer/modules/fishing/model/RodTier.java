package de.raindancer.modules.fishing.model;

import java.util.Locale;
import java.util.Optional;

/**
 * The rods a fisher works their way up through, one per tier. Each is unlocked with fishing experience once its level
 * is reached, then crafted — and only a fisher who unlocked one may cast it. Part upgrades and enchantments go on top.
 *
 * @param level       the fishing level it needs
 * @param cost        the fishing experience unlocking it spends
 * @param waitPercent how much sooner fish bite with it
 * @param chestPermille how much likelier a sea chest is, out of a thousand casts
 * @param rarePercent how much likelier rarer fish and chests are
 * @param doublePermille how likely a double catch is, out of a thousand fish
 * @param fishes      the special water it fishes without a line upgrade, if any
 * @param shaft       what its shaft is crafted from
 * @param line        what its line is crafted from
 */
public enum RodTier {
    REED("Reed Rod", Tier.COMMON, 1, 80, 5, 5, 0, 0, null, "bamboo", "string"),
    REEF("Reef Rod", Tier.UNCOMMON, 5, 300, 8, 8, 5, 20, null, "prismarine_shard", "string"),
    PEARL("Pearl Rod", Tier.RARE, 12, 900, 12, 12, 10, 40, null, "prismarine_crystals", "string"),
    EMBER("Ember Rod", Tier.EPIC, 18, 2000, 15, 15, 15, 50, Water.LAVA, "blaze_rod", "magma_cream"),
    LEVIATHAN("Leviathan Rod", Tier.LEGENDARY, 30, 5000, 20, 20, 22, 70, Water.LAVA, "gold_ingot", "string"),
    VOID("Void Rod", Tier.MYTHIC, 40, 12000, 25, 25, 30, 90, Water.VOID, "end_rod", "phantom_membrane");

    private final String title;
    private final Tier tier;
    private final int level;
    private final long cost;
    private final int waitPercent;
    private final int chestPermille;
    private final int rarePercent;
    private final int doublePermille;
    private final Water fishes;
    private final String shaft;
    private final String line;

    RodTier(String title, Tier tier, int level, long cost, int waitPercent, int chestPermille, int rarePercent,
            int doublePermille, Water fishes, String shaft, String line) {
        this.title = title;
        this.tier = tier;
        this.level = level;
        this.cost = cost;
        this.waitPercent = waitPercent;
        this.chestPermille = chestPermille;
        this.rarePercent = rarePercent;
        this.doublePermille = doublePermille;
        this.fishes = fishes;
        this.shaft = shaft;
        this.line = line;
    }

    public String title() {
        return title;
    }

    public Tier tier() {
        return tier;
    }

    public int level() {
        return level;
    }

    public long cost() {
        return cost;
    }

    public int waitPercent() {
        return waitPercent;
    }

    public int chestPermille() {
        return chestPermille;
    }

    public int rarePercent() {
        return rarePercent;
    }

    public int doublePermille() {
        return doublePermille;
    }

    /** Lava for the Ember and Leviathan rods, the void for the Void Rod — and every lesser water too. */
    public boolean fishes(Water water) {
        if (!water.needsSpecialLine()) {
            return true;
        }
        if (fishes == Water.VOID) {
            return true;
        }
        return fishes == Water.LAVA && water == Water.LAVA;
    }

    public Optional<Water> special() {
        return Optional.ofNullable(fishes);
    }

    public String shaft() {
        return shaft;
    }

    public String line() {
        return line;
    }

    /** What the crafted rod needs besides shaft and line: any fish of this tier (the Void Rod: legendary). */
    public Tier fishIngredient() {
        return this == VOID ? Tier.LEGENDARY : tier;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String itemId() {
        return "rod-" + id();
    }

    public String model() {
        return "rainsfishing:rod/" + id();
    }

    public static Optional<RodTier> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String wanted = text.trim().toLowerCase(Locale.ROOT).replace("_rod", "").replace(" rod", "");
        for (RodTier rod : values()) {
            if (rod.id().equals(wanted)) {
                return Optional.of(rod);
            }
        }
        return Optional.empty();
    }
}
