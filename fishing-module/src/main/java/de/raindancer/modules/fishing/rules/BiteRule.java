package de.raindancer.modules.fishing.rules;

import de.raindancer.core.ui.tree.SkillTree;
import de.raindancer.modules.fishing.model.Bait;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.RodPart;
import de.raindancer.modules.fishing.model.Perk;
import de.raindancer.modules.fishing.model.FishingTree;
import de.raindancer.modules.fishing.model.Perks;
import de.raindancer.modules.fishing.model.Water;

import java.util.Random;

/**
 * How long a fish takes to bite. Vanilla's own wait is scaled for water; lava and the void have no vanilla wait, so
 * theirs is drawn here, between {@link #SLOWEST_TICKS} and {@link #QUICKEST_TICKS} before the same scaling.
 */
public final class BiteRule implements IFishingRule {

    /** No combination of upgrades makes a fish bite in less than this share of the plain wait. */
    public static final double FASTEST = 0.3;

    static final int QUICKEST_TICKS = 100;
    static final int SLOWEST_TICKS = 500;

    private final SkillTree tree;

    public BiteRule() {
        this(FishingTree.shipped());
    }

    public BiteRule(SkillTree tree) {
        this.tree = tree;
    }

    public double waitFactor(Rod rod, FisherProfile profile, boolean inSpot) {
        return waitFactor(rod, profile, inSpot, null);
    }

    /** @param bait the bait on the hook, or null */
    public double waitFactor(Rod rod, FisherProfile profile, boolean inSpot, Bait bait) {
        return waitFactor(rod, profile, inSpot, bait, null, false);
    }

    /**
     * @param water   where the hook is, for the lava's and the void's own perks; null for anywhere
     * @param raining it rains where the hook is
     */
    public double waitFactor(Rod rod, FisherProfile profile, boolean inSpot, Bait bait, Water water,
                             boolean raining) {
        return waitFactor(rod, profile, inSpot, bait, water, raining, false);
    }

    /** @param night it is night where the hook is */
    public double waitFactor(Rod rod, FisherProfile profile, boolean inSpot, Bait bait, Water water,
                             boolean raining, boolean night) {
        Perks perks = Perks.from(tree, profile.nodes());
        double factor = (1.0 - 0.08 * rod.rank(RodPart.HOOK))
                * rod.ownWaitFactor()
                * perks.sooner(Perk.WAIT)
                * (inSpot ? 0.8 * perks.sooner(Perk.SPOT_WAIT) : 1.0)
                * (raining ? perks.sooner(Perk.STORM_WAIT) : 1.0)
                * (night ? perks.sooner(Perk.NIGHT_WAIT) : 1.0)
                * (water == Water.LAVA ? perks.sooner(Perk.LAVA_WAIT) : 1.0)
                * (water == Water.VOID ? perks.sooner(Perk.VOID_WAIT) : 1.0)
                * (bait == null ? 1.0 : bait.waitFactor());
        return Math.max(FASTEST, factor);
    }

    /** Ticks until a bite in lava or the void. */
    public int customWait(Rod rod, FisherProfile profile, boolean inSpot, Random random) {
        return customWait(rod, profile, inSpot, null, random);
    }

    public int customWait(Rod rod, FisherProfile profile, boolean inSpot, Bait bait, Random random) {
        return customWait(rod, profile, inSpot, bait, null, false, random);
    }

    public int customWait(Rod rod, FisherProfile profile, boolean inSpot, Bait bait, Water water, boolean raining,
                          Random random) {
        return customWait(rod, profile, inSpot, bait, water, raining, false, random);
    }

    public int customWait(Rod rod, FisherProfile profile, boolean inSpot, Bait bait, Water water, boolean raining,
                          boolean night, Random random) {
        int plain = QUICKEST_TICKS + random.nextInt(SLOWEST_TICKS - QUICKEST_TICKS + 1);
        return Math.max(20, (int) Math.round(plain * waitFactor(rod, profile, inSpot, bait, water, raining, night)));
    }

    /** Night by the world's clock: from dusk (13000) until dawn (23000), when monsters are about too. */
    public static boolean night(long worldTime) {
        long time = Math.floorMod(worldTime, 24000L);
        return time >= 13000 && time < 23000;
    }

    /** How long a bite in lava or the void lasts before the fish is gone again. */
    public int biteWindowTicks() {
        return 30;
    }

    @Override
    public String describe() {
        return "how long a fish takes to bite, from the hook, quick bite and a fishing spot";
    }
}
