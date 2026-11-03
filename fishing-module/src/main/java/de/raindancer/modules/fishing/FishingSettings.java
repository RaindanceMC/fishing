package de.raindancer.modules.fishing;

import de.raindancer.core.data.settings.Describe;
import de.raindancer.core.data.settings.In;
import de.raindancer.core.data.settings.Key;
import de.raindancer.core.data.settings.Range;
import de.raindancer.core.data.settings.Settings;
import de.raindancer.core.data.settings.Title;
import de.raindancer.core.data.settings.Topic;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.modules.fishing.rules.UpgradeRule;
import org.bukkit.Material;

/**
 * What an owner can decide about fishing. The record is the schema: the file, its comments, its validation and the
 * {@code /settings} screens all come from it.
 */
@Settings(id = "fishing", topics = {
        @Topic(path = "fishing", title = "Fishing", icon = Material.FISHING_ROD),
        @Topic(path = "fishing/spots", title = "Fishing spots", icon = Material.LILY_PAD),
        @Topic(path = "fishing/chests", title = "Sea chests", icon = Material.CHEST),
        @Topic(path = "fishing/rod", title = "Rod upgrades", icon = Material.STRING),
        @Topic(path = "fishing/skills", title = "Fishing levels and skills", icon = Material.EXPERIENCE_BOTTLE),
})
public record FishingSettings(

        @In("fishing") @Title("New fish replace vanilla fish")
        @Describe("Whether a fish — or junk — vanilla would have put on the hook becomes one of this module's catches. "
                + "Vanilla's treasure always stays. Off leaves cod, salmon and junk alone; sea chests still bite, "
                + "and lava and the void are fished all the same.")
        @Key("catch.replace-vanilla-fish")
        boolean replaceVanillaFish,

        @In("fishing/spots") @Title("Fishing spots appear")
        @Describe("Shoals that show up on water near players — ripples, a big bobber and a sign over them. Fishing "
                + "in one bites sooner, gives better fish and more sea chests.")
        @Key("spots.enabled")
        boolean spotsEnabled,

        @In("fishing/spots") @Title("Spots near each player") @Range(min = 0, max = 6)
        @Describe("How many fishing spots at most are kept around every player at once.")
        @Key("spots.per-player")
        int spotsPerPlayer,

        @In("fishing/spots") @Title("A spot lasts") @Range(min = 1, max = 120)
        @Describe("Minutes before a spot wanders off, fished out or not.")
        @Key("spots.minutes")
        int spotMinutes,

        @In("fishing/spots") @Title("Catches in a spot") @Range(min = 1, max = 200)
        @Describe("How many catches a spot gives before it is fished out.")
        @Key("spots.catches")
        int spotCatches,

        @In("fishing/spots") @Title("Extra sea chest chance in a spot") @Range(min = 0, max = 1000)
        @Describe("Out of a thousand casts, added to the usual chance while the hook is in a spot. 50 is 5%.")
        @Key("spots.chest-bonus-permille")
        int spotChestPermille,

        @In("fishing/chests") @Title("Sea chest chance") @Range(min = 0, max = 1000)
        @Describe("Out of a thousand casts, how often a sea chest bites instead of a fish, before a lure, treasure "
                + "sense or a spot add to it. 25 is 2.5%.")
        @Key("chests.chance-permille")
        int chestPermille,

        @In("fishing/chests") @Title("Money in a Driftwood chest")
        @Describe("Roughly what the plainest sea chest pays, such as 100 or 1.5k; every better tier pays a multiple "
                + "of it, up to two hundred times as much from a Void chest. 0 for no money in chests at all.")
        @Key("chests.money")
        String chestMoney,

        @In("fishing/rod") @Title("Hook I costs") @Range(min = 0, max = 1000000)
        @Describe("Fishing experience. Upgrades are earned, not bought: they cost experience the fisher has caught "
                + "their way to, never money. Every further rank costs the one before times the growth below.")
        @Key("rod.hook-cost")
        int hookCost,

        @In("fishing/rod") @Title("Chest Lure I costs") @Range(min = 0, max = 1000000)
        @Describe("Fishing experience. Every further rank costs the one before times the growth below.")
        @Key("rod.lure-cost")
        int lureCost,

        @In("fishing/rod") @Title("Reel I costs") @Range(min = 0, max = 1000000)
        @Describe("Fishing experience. Every further rank costs the one before times the growth below.")
        @Key("rod.reel-cost")
        int reelCost,

        @In("fishing/rod") @Title("The Ember Line costs") @Range(min = 0, max = 10000000)
        @Describe("Fishing experience. The line that fishes lava.")
        @Key("rod.ember-line-cost")
        int emberLineCost,

        @In("fishing/rod") @Title("The Void Line costs") @Range(min = 0, max = 10000000)
        @Describe("Fishing experience. The line that fishes the void, cast off an End island's edge.")
        @Key("rod.void-line-cost")
        int voidLineCost,

        @In("fishing/rod") @Title("Each rank costs this much more") @Range(min = 100, max = 1000)
        @Describe("A percentage of the rank before: 200 makes rank II cost twice rank I.")
        @Key("rod.cost-growth-percent")
        int costGrowthPercent,

        @In("fishing/rod") @Title("Fishing levels between ranks") @Range(min = 0, max = 50)
        @Describe("Rank I of a hook, chest lure or reel needs fishing level 1; every rank after it needs this many "
                + "levels more. 5 puts Hook V at level 21.")
        @Key("rod.levels-per-rank")
        int levelsPerRank,

        @In("fishing/rod") @Title("The Ember Line needs fishing level") @Range(min = 0, max = 200)
        @Describe("The fishing level a player must have reached before they can fit the Ember Line.")
        @Key("rod.ember-line-level")
        int emberLineLevel,

        @In("fishing/rod") @Title("The Void Line needs fishing level") @Range(min = 0, max = 200)
        @Describe("The fishing level a player must have reached before they can fit the Void Line.")
        @Key("rod.void-line-level")
        int voidLineLevel,

        @In("fishing/skills") @Title("Highest fishing level") @Range(min = 5, max = 200)
        @Describe("Fishing levels stop here. Every level is one skill point.")
        @Key("levels.highest")
        int highestLevel,

        @In("fishing/skills") @Title("Unlearning skills costs")
        @Describe("Money to take back every skill point and spend them again, such as 1k. 0 for free.")
        @Key("skills.reset-fee")
        String skillResetFee,

        @In("fishing/chests") @Title("A common fish sells for")
        @Describe("What the shop pays for one common fish, such as 8; rarer fish fetch up to 75 times as much, a "
                + "dish half again its fish. 0 and the shop takes no fish or dishes.")
        @Key("market.fish-price")
        String fishPrice) {

    public static final FishingSettings DEFAULTS = new FishingSettings(
            true,
            true, 2, 8, 12, 50,
            25, "100",
            40, 120, 100, 1500, 6000, 200, 5, 10, 25,
            100, "1000",
            "8");

    /** What rod upgrades cost in fishing experience, for {@link UpgradeRule}. */
    public UpgradeRule.Costs costs() {
        return new UpgradeRule.Costs(hookCost, lureCost, reelCost, emberLineCost, voidLineCost, costGrowthPercent,
                levelsPerRank, emberLineLevel, voidLineLevel);
    }

    public long chestMoneyMinor() {
        return minor(chestMoney);
    }

    public long fishPriceMinor() {
        return minor(fishPrice);
    }

    private static long minor(String written) {
        try {
            return Math.max(0, Fees.amount(written).minor());
        } catch (RuntimeException unreadable) {
            return 0;
        }
    }
}
