package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Perk;
import de.raindancer.modules.fishing.model.Perks;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.model.Water;

/** What the skill tree adds after a catch: experience, weight, money at the stall, and how long a hot bite lasts. */
public final class BonusRule implements IFishingRule {

    private static boolean hot(Water water) {
        return water == Water.LAVA || water == Water.VOID;
    }

    /** @param firstOfKind the fisher had never caught this kind before */
    public long experience(long plain, Perks perks, Water water, boolean firstOfKind) {
        return Math.round(plain * perks.more(Perk.XP) * (hot(water) ? perks.more(Perk.HOT_XP) : 1.0)
                * (firstOfKind ? perks.more(Perk.FIRST_XP) : 1.0));
    }

    public long chestExperience(long plain, Perks perks) {
        return Math.round(plain * perks.more(Perk.XP) * perks.more(Perk.CHEST_XP));
    }

    /** A factor on a fish's weight. */
    public double heavier(Perks perks, Tier tier, Water water) {
        return perks.more(Perk.WEIGHT)
                * (tier.atLeast(Tier.LEGENDARY) ? perks.more(Perk.BIG_WEIGHT) : 1.0)
                * (hot(water) ? perks.more(Perk.HOT_WEIGHT) : 1.0);
    }

    /** Market Stall: a share of what the shop would pay for the catch, in minor units, paid at once. */
    public long catchMoney(long shopPrice, Perks perks) {
        return Math.max(0, Math.round(shopPrice * perks.get(Perk.CATCH_MONEY) / 100.0));
    }

    public int biteWindow(int plain, Perks perks) {
        return (int) Math.round(plain * perks.more(Perk.HOT_WINDOW));
    }

    @Override
    public String describe() {
        return "what the skill tree adds to a catch: experience, weight, stall money, a longer hot bite";
    }
}
