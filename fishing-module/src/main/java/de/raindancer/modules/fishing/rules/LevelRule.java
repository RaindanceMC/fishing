package de.raindancer.modules.fishing.rules;

/**
 * Fishing levels from fishing experience: level {@code n} needs {@code 40·n^1.6} in all, so the first levels come
 * after an evening and the last ones after a season.
 */
public final class LevelRule implements IFishingRule {

    private final int highest;

    public LevelRule(int highest) {
        this.highest = Math.max(1, highest);
    }

    public int highest() {
        return highest;
    }

    /** Experience needed, in all, to reach {@code level}. */
    public long experienceFor(int level) {
        if (level <= 0) {
            return 0;
        }
        return Math.round(40.0 * Math.pow(level, 1.6));
    }

    public int level(long experience) {
        int level = 0;
        while (level < highest && experience >= experienceFor(level + 1)) {
            level++;
        }
        return level;
    }

    /** How far through the current level, 0..1; 1 at the top. */
    public double progress(long experience) {
        int level = level(experience);
        if (level >= highest) {
            return 1.0;
        }
        long from = experienceFor(level);
        long to = experienceFor(level + 1);
        return Math.max(0, Math.min(1, (experience - from) / (double) (to - from)));
    }

    @Override
    public String describe() {
        return "the fishing level that an amount of fishing experience is worth";
    }
}
