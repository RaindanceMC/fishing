package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.RodPart;
import de.raindancer.modules.fishing.model.Skill;

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

    public double waitFactor(Rod rod, FisherProfile profile, boolean inSpot) {
        double factor = (1.0 - 0.08 * rod.rank(RodPart.HOOK))
                * rod.ownWaitFactor()
                * (1.0 - 0.06 * profile.rank(Skill.QUICK_BITE))
                * (inSpot ? 0.8 : 1.0);
        return Math.max(FASTEST, factor);
    }

    /** Ticks until a bite in lava or the void. */
    public int customWait(Rod rod, FisherProfile profile, boolean inSpot, Random random) {
        int plain = QUICKEST_TICKS + random.nextInt(SLOWEST_TICKS - QUICKEST_TICKS + 1);
        return Math.max(20, (int) Math.round(plain * waitFactor(rod, profile, inSpot)));
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
