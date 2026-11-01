package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.RodTier;

import java.util.Optional;

/**
 * Whether a fisher may unlock a rod, and whether they may cast the one in their hand. The rods are a ladder: each
 * needs its fishing level, its fishing experience and the rod below it unlocked first. A vanilla rod is always
 * anybody's to cast.
 */
public final class RodTierRule implements IFishingRule {

    public enum Refusal { ALREADY, PREVIOUS_FIRST, LEVEL_TOO_LOW, NOT_ENOUGH_EXPERIENCE }

    private final LevelRule levels;

    public RodTierRule(LevelRule levels) {
        this.levels = levels;
    }

    public Optional<Refusal> refusal(FisherProfile profile, RodTier rod) {
        if (profile.hasUnlocked(rod)) {
            return Optional.of(Refusal.ALREADY);
        }
        if (rod.ordinal() > 0 && !profile.hasUnlocked(RodTier.values()[rod.ordinal() - 1])) {
            return Optional.of(Refusal.PREVIOUS_FIRST);
        }
        if (levels.level(profile.experience()) < rod.level()) {
            return Optional.of(Refusal.LEVEL_TOO_LOW);
        }
        if (profile.available() < rod.cost()) {
            return Optional.of(Refusal.NOT_ENOUGH_EXPERIENCE);
        }
        return Optional.empty();
    }

    public boolean mayCast(FisherProfile profile, Rod rod) {
        return rod.tier() == null || profile.hasUnlocked(rod.tier());
    }

    @Override
    public String describe() {
        return "whether a fisher may unlock a rod tier, and cast the rod in their hand";
    }
}
