package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Tier;

/** What the shop pays for a fish or a dish: a base price for a common fish, more for every rarer tier. */
public final class PriceRule implements IFishingRule {

    /** Times the base, common to mythic. */
    private static final int[] TIMES = {1, 2, 4, 10, 25, 75};

    public long fish(Tier tier, long base) {
        return Math.max(0, base) * TIMES[tier.ordinal()];
    }

    /** Half again a fish of the same tier: the cooking is worth something. */
    public long dish(Tier tier, long base) {
        return fish(tier, base) * 3 / 2;
    }

    @Override
    public String describe() {
        return "what the shop pays for a fish or a dish";
    }
}
