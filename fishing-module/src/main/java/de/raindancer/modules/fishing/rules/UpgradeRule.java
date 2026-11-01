package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.RodPart;

import java.util.Optional;

/**
 * What the next rank of a rod part costs and whether it may be fitted. Upgrades are earned, not bought: each rank
 * costs fishing experience (the one before times the growth) and needs a fishing level, so nobody buys their way
 * to a Void Line. Spent experience comes off what is available, never off the level.
 */
public final class UpgradeRule implements IFishingRule {

    public enum Refusal { MAXED, LEVEL_TOO_LOW, NOT_ENOUGH_EXPERIENCE }

    /**
     * Costs in fishing experience.
     *
     * @param growthPercent  what each further rank costs, as a percentage of the one before
     * @param levelsPerRank  fishing levels between one rank of a hook, lure or reel and the next; rank I needs level 1
     * @param emberLevel     the fishing level the Ember Line needs
     * @param voidLevel      the fishing level the Void Line needs
     */
    public record Costs(long hook, long lure, long reel, long emberLine, long voidLine, int growthPercent,
                        int levelsPerRank, int emberLevel, int voidLevel) {
    }

    private final LevelRule levels;

    public UpgradeRule(LevelRule levels) {
        this.levels = levels;
    }

    public long cost(Rod rod, RodPart part, Costs costs) {
        int rank = rod.rank(part);
        if (part == RodPart.LINE) {
            return rank == 0 ? costs.emberLine() : costs.voidLine();
        }
        long base = switch (part) {
            case HOOK -> costs.hook();
            case LURE -> costs.lure();
            case REEL -> costs.reel();
            case LINE -> costs.emberLine();
        };
        double growth = Math.max(100, costs.growthPercent()) / 100.0;
        return (long) Math.floor(base * Math.pow(growth, rank));
    }

    public int levelNeeded(Rod rod, RodPart part, Costs costs) {
        int rank = rod.rank(part);
        if (part == RodPart.LINE) {
            return rank == 0 ? costs.emberLevel() : costs.voidLevel();
        }
        return 1 + rank * Math.max(0, costs.levelsPerRank());
    }

    public Optional<Refusal> refusal(Rod rod, RodPart part, FisherProfile profile, Costs costs) {
        if (rod.rank(part) >= part.most()) {
            return Optional.of(Refusal.MAXED);
        }
        if (levels.level(profile.experience()) < levelNeeded(rod, part, costs)) {
            return Optional.of(Refusal.LEVEL_TOO_LOW);
        }
        if (profile.available() < cost(rod, part, costs)) {
            return Optional.of(Refusal.NOT_ENOUGH_EXPERIENCE);
        }
        return Optional.empty();
    }

    @Override
    public String describe() {
        return "the fishing experience and level a rod part's next rank needs";
    }
}
