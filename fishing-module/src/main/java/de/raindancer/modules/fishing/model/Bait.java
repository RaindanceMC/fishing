package de.raindancer.modules.fishing.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Bait, held in the off hand: every cast takes one, and that cast bites the way the bait promises.
 *
 * @param waitFactor    how long the bite takes, against no bait
 * @param chestPermille added to the chance of a sea chest, out of 1000
 * @param rareFactor    weight of rare and better fish and chests
 * @param epicFactor    weight of epic and better
 * @param legendFactor  weight of legendary and better
 */
public enum Bait {
    WRIGGLER("Wriggler Bait", Tier.COMMON, 0.65, 0, 1.0, 1.0, 1.0, "Fish bite a third sooner."),
    SHIMMER("Shimmer Bait", Tier.UNCOMMON, 1.0, 0, 1.6, 1.0, 1.0, "Rare fish and better bite more often."),
    GILDED("Gilded Bait", Tier.RARE, 1.0, 30, 1.0, 1.0, 1.0, "Sea chests bite far more often."),
    ABYSS("Abyss Bait", Tier.EPIC, 0.9, 0, 1.0, 2.5, 1.0, "Epic catches and better bite more than twice as often."),
    STARLURE("Starlure", Tier.LEGENDARY, 0.85, 10, 1.0, 1.0, 3.0,
            "Legendary and mythic catches bite three times as often.");

    private final String title;
    private final Tier tier;
    private final double waitFactor;
    private final int chestPermille;
    private final double rareFactor;
    private final double epicFactor;
    private final double legendFactor;
    private final String promise;

    Bait(String title, Tier tier, double waitFactor, int chestPermille, double rareFactor, double epicFactor,
         double legendFactor, String promise) {
        this.title = title;
        this.tier = tier;
        this.waitFactor = waitFactor;
        this.chestPermille = chestPermille;
        this.rareFactor = rareFactor;
        this.epicFactor = epicFactor;
        this.legendFactor = legendFactor;
        this.promise = promise;
    }

    public String title() {
        return title;
    }

    /** Its colour, and how hard it is to make. */
    public Tier tier() {
        return tier;
    }

    public double waitFactor() {
        return waitFactor;
    }

    public int chestPermille() {
        return chestPermille;
    }

    /** What a catch of this tier weighs on the odds with this bait on the hook. */
    public double weight(Tier catchTier) {
        double weight = 1.0;
        if (catchTier.atLeast(Tier.RARE)) {
            weight *= rareFactor;
        }
        if (catchTier.atLeast(Tier.EPIC)) {
            weight *= epicFactor;
        }
        if (catchTier.atLeast(Tier.LEGENDARY)) {
            weight *= legendFactor;
        }
        return weight;
    }

    public String promise() {
        return promise;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String itemId() {
        return "bait-" + id();
    }

    public static Optional<Bait> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String wanted = text.trim().toLowerCase(Locale.ROOT);
        for (Bait bait : values()) {
            if (bait.id().equals(wanted)) {
                return Optional.of(bait);
            }
        }
        return Optional.empty();
    }
}
