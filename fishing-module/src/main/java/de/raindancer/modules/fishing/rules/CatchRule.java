package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Bait;
import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.Catch;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.RodPart;
import de.raindancer.modules.fishing.model.Perk;
import de.raindancer.modules.fishing.model.Perks;
import de.raindancer.modules.fishing.model.SkillTree;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.model.Water;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * What comes up on the line. The heart of the module, and a pure function of the cast and a {@link Random}, so the
 * odds are tested rather than guessed at.
 *
 * <h2>Two rolls</h2>
 * First whether a sea chest bit at all (a small chance that a lure, treasure sense and a fishing spot raise), then —
 * for a chest or a fish — which tier. Tiers are weighted out of a thousand; rare instinct and a spot lean the
 * weights toward the top. Each water has its own band of chest tiers: the void chest is the void's alone, and a
 * river never gives up anything better than a pearl chest.
 */
public final class CatchRule implements IFishingRule {

    /** Out of 1000: how often each tier bites before anything leans on the odds. */
    private static final Map<Tier, Integer> WEIGHTS = new EnumMap<>(Map.of(
            Tier.COMMON, 600, Tier.UNCOMMON, 250, Tier.RARE, 100, Tier.EPIC, 40,
            Tier.LEGENDARY, 9, Tier.MYTHIC, 1));

    /**
     * One cast.
     *
     * @param inSpot             the hook is in a fishing spot
     * @param chestPermille      the base chance of a sea chest, out of 1000
     * @param spotChestPermille  what a spot adds to it
     * @param vanillaWasFish     vanilla had put a fish on the hook rather than treasure or junk; lava and the void
     *                           have no vanilla catch, and count as fish
     * @param spotTier           the best a fixed spot the hook is in gives up (what its admin set), or null — a
     *                           wandering spot promises its water's best
     * @param bait               the bait on the hook, or null
     * @param biome              the biome the hook is in ({@code minecraft:crimson_forest}), or null for anywhere
     */
    public record Cast(Water water, Rod rod, FisherProfile profile, boolean inSpot, int chestPermille,
                       int spotChestPermille, boolean vanillaWasFish, Tier spotTier, Bait bait, String biome) {

        public Cast(Water water, Rod rod, FisherProfile profile, boolean inSpot, int chestPermille,
                    int spotChestPermille, boolean vanillaWasFish) {
            this(water, rod, profile, inSpot, chestPermille, spotChestPermille, vanillaWasFish, null, null, null);
        }

        public Cast(Water water, Rod rod, FisherProfile profile, boolean inSpot, int chestPermille,
                    int spotChestPermille, boolean vanillaWasFish, Tier spotTier, Bait bait) {
            this(water, rod, profile, inSpot, chestPermille, spotChestPermille, vanillaWasFish, spotTier, bait, null);
        }
    }

    private final Catalog catalog;
    private final SkillTree tree;

    public CatchRule(Catalog catalog) {
        this(catalog, SkillTree.shipped());
    }

    public CatchRule(Catalog catalog, SkillTree tree) {
        this.catalog = catalog;
        this.tree = tree;
    }

    private Perks perks(Cast cast) {
        return tree.perks(cast.profile().nodes());
    }

    public Catch roll(Cast cast, Random random) {
        if (random.nextInt(1000) < chestPermille(cast)) {
            List<Tier> band = chestBand(cast.water(), cast.spotTier());
            Tier tier = pickTier(band, cast, random);
            int at = band.indexOf(tier);
            if (at + 1 < band.size() && random.nextDouble() * 100 < perks(cast).get(Perk.CHEST_UP)) {
                tier = band.get(at + 1);
            }
            return new Catch.Chest(tier);
        }
        if (!cast.vanillaWasFish() && !cast.water().needsSpecialLine()) {
            return new Catch.Vanilla();
        }
        List<Tier> present = capped(catalog.tiersIn(cast.water(), cast.biome()), cast.spotTier());
        if (present.isEmpty()) {
            return new Catch.Vanilla();
        }
        Tier tier = pickTier(present, cast, random);
        List<FishKind> kinds = catalog.in(cast.water(), cast.biome(), tier);
        FishKind kind = pickKind(kinds, cast, random);
        int amount = random.nextInt(1000) < doublePermille(cast) ? 2 : 1;
        if (amount == 2 && random.nextDouble() * 100 < perks(cast).get(Perk.TRIPLE)) {
            amount = 3;
        }
        return new Catch.Fish(kind, amount);
    }

    /** The chance of a sea chest on this cast, out of 1000. */
    public int chestPermille(Cast cast) {
        Perks perks = perks(cast);
        int chance = Math.max(0, cast.chestPermille())
                + cast.rod().rank(RodPart.LURE) * 15
                + cast.rod().ownChestPermille()
                + (int) Math.round(perks.get(Perk.CHEST)
                + (cast.inSpot() ? perks.get(Perk.SPOT_CHEST) : 0)
                + (cast.water() == Water.LAVA ? perks.get(Perk.LAVA_CHEST) : 0)
                + (cast.water() == Water.VOID ? perks.get(Perk.VOID_CHEST) : 0))
                + (cast.inSpot() ? Math.max(0, cast.spotChestPermille()) : 0)
                + (cast.bait() == null ? 0 : cast.bait().chestPermille());
        return Math.min(1000, chance);
    }

    /** The chance a fish comes up as two, out of 1000. */
    public int doublePermille(Cast cast) {
        return Math.max(0, Math.min(1000, cast.rod().rank(RodPart.REEL) * 50 + cast.rod().ownDoublePermille()
                + (int) Math.round(perks(cast).get(Perk.DOUBLE))));
    }

    /** Which sea chests can bite in a water, lowest first. */
    public List<Tier> chestBand(Water water) {
        return switch (water) {
            case RIVER, FROZEN -> List.of(Tier.COMMON, Tier.UNCOMMON, Tier.RARE);
            case SWAMP, CAVE -> List.of(Tier.COMMON, Tier.UNCOMMON, Tier.RARE, Tier.EPIC);
            case OCEAN, WARM_OCEAN -> List.of(Tier.COMMON, Tier.UNCOMMON, Tier.RARE, Tier.EPIC, Tier.LEGENDARY);
            case LAVA -> List.of(Tier.UNCOMMON, Tier.RARE, Tier.EPIC);
            case VOID -> List.of(Tier.RARE, Tier.EPIC, Tier.LEGENDARY, Tier.MYTHIC);
        };
    }

    /**
     * Which sea chests can bite at a fixed spot of {@code spotTier} in a water: from the water's lowest up to the
     * spot's tier, past what the water alone gives up — but a Void chest only ever over the void.
     */
    public List<Tier> chestBand(Water water, Tier spotTier) {
        List<Tier> band = chestBand(water);
        if (spotTier == null) {
            return band;
        }
        Tier low = band.getFirst();
        Tier top = water == Water.VOID || spotTier != Tier.MYTHIC ? spotTier : Tier.LEGENDARY;
        if (!top.atLeast(low)) {
            top = low;
        }
        List<Tier> spanned = new java.util.ArrayList<>();
        for (Tier tier : Tier.values()) {
            if (tier.atLeast(low) && top.atLeast(tier)) {
                spanned.add(tier);
            }
        }
        return spanned;
    }

    /** The tiers present, none above a fixed spot's; the lowest is kept when the spot is below all of them. */
    private static List<Tier> capped(List<Tier> present, Tier spotTier) {
        if (spotTier == null || present.isEmpty()) {
            return present;
        }
        List<Tier> kept = present.stream().filter(spotTier::atLeast).toList();
        return kept.isEmpty() ? List.of(present.getFirst()) : kept;
    }

    /** The best sea chest a spot in this water can give up — what its hologram promises. */
    public Tier bestChest(Water water) {
        List<Tier> band = chestBand(water);
        return band.getLast();
    }

    /** What the void owes for each fish it gave up that was not mythic, as a share of a mythic's weight. */
    static final double DEBT_STEP = 0.03;
    /** The most the debt ever multiplies a mythic's weight by. */
    static final double DEBT_MOST = 4.0;

    /** The void's debt: a mythic's weight after {@code dry} void fish in a row that were not mythic. */
    public static double debtFactor(int dry) {
        return Math.min(DEBT_MOST, 1.0 + DEBT_STEP * Math.max(0, dry));
    }

    /** Evenly among the kinds, except that New Waters leans toward the ones the fisher has never caught. */
    private FishKind pickKind(List<FishKind> kinds, Cast cast, Random random) {
        double unseen = perks(cast).more(Perk.NEW_KIND);
        if (unseen == 1.0 || kinds.size() == 1) {
            return kinds.get(random.nextInt(kinds.size()));
        }
        double[] weights = new double[kinds.size()];
        double total = 0;
        for (int n = 0; n < kinds.size(); n++) {
            weights[n] = cast.profile().caught(kinds.get(n).id()) > 0 ? 1.0 : unseen;
            total += weights[n];
        }
        double point = random.nextDouble() * total;
        for (int n = 0; n < weights.length; n++) {
            point -= weights[n];
            if (point < 0) {
                return kinds.get(n);
            }
        }
        return kinds.getLast();
    }

    private Tier pickTier(List<Tier> allowed, Cast cast, Random random) {
        Perks perks = perks(cast);
        boolean hot = cast.water() == Water.LAVA || cast.water() == Water.VOID;
        double instinct = Math.max(0.1, 1.0 + (perks.get(Perk.RARE) + (cast.inSpot() ? perks.get(Perk.SPOT_RARE) : 0)
                + (hot ? perks.get(Perk.HOT_RARE) : 0)) / 100.0 + cast.rod().ownRareBonus());
        double total = 0;
        double[] weights = new double[allowed.size()];
        for (int n = 0; n < allowed.size(); n++) {
            Tier tier = allowed.get(n);
            double weight = WEIGHTS.get(tier);
            if (tier.atLeast(Tier.RARE)) {
                weight *= instinct;
            }
            if (tier.atLeast(Tier.EPIC)) {
                weight *= perks.more(Perk.EPIC);
            }
            if (tier.atLeast(Tier.LEGENDARY)) {
                weight *= perks.more(Perk.LEGEND);
            }
            if (tier == Tier.MYTHIC) {
                weight *= perks.more(Perk.MYTHIC) * debtFactor(cast.profile().voidDry());
            }
            if (cast.inSpot() && tier.atLeast(Tier.UNCOMMON)) {
                weight *= 1.5;
            }
            if (cast.bait() != null) {
                weight *= cast.bait().weight(tier);
            }
            weights[n] = weight;
            total += weight;
        }
        double point = random.nextDouble() * total;
        for (int n = 0; n < weights.length; n++) {
            point -= weights[n];
            if (point < 0) {
                return allowed.get(n);
            }
        }
        return allowed.getLast();
    }

    @Override
    public String describe() {
        return "what comes up on the line: a sea chest or a fish, and of which tier";
    }
}
