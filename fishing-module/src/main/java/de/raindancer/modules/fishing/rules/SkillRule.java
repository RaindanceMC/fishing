package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Skill;

import java.util.Optional;

/** Whether a fisher may take another rank of a skill: one point per fishing level, five ranks per skill. */
public final class SkillRule implements IFishingRule {

    public enum Refusal { NO_POINTS, MAXED }

    private final LevelRule levels;

    public SkillRule(LevelRule levels) {
        this.levels = levels;
    }

    public int pointsLeft(FisherProfile profile) {
        return Math.max(0, levels.level(profile.experience()) - profile.pointsSpent());
    }

    public Optional<Refusal> refusal(FisherProfile profile, Skill skill) {
        if (profile.rank(skill) >= Skill.MOST) {
            return Optional.of(Refusal.MAXED);
        }
        if (pointsLeft(profile) <= 0) {
            return Optional.of(Refusal.NO_POINTS);
        }
        return Optional.empty();
    }

    @Override
    public String describe() {
        return "whether a fisher has a skill point to spend on a skill that is not yet at its top rank";
    }
}
