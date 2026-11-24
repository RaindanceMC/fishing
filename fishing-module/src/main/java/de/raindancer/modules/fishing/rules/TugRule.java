package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Catch;
import de.raindancer.modules.fishing.model.Tier;

import java.util.Random;

/**
 * A legendary or mythic fish sometimes fights. The fish keeps pulling it towards the water, and holding sneak reels
 * it back, tightening the line a little. Every few seconds the fish runs, pulling hard, after a short warning; reeling
 * during a run gains nothing and tightens the line fast, and a line pulled tight snaps. Letting go eases it. Rope at
 * the fisher: landed. Rope at the water: the fish wins.
 *
 * <p>The input is sneak, never the rod: a held right click would cast again the moment the fight ends. The rope
 * starts on the fish's side. One step is two ticks.
 */
public final class TugRule implements IFishingRule {

    public enum Phase { CALM, WARNING, SURGE }

    public enum Outcome { FIGHTING, LANDED, PULLED_IN, SNAPPED, ESCAPED }

    /** In percent of the catches of that tier. */
    public record Odds(int legendaryPercent, int mythicPercent) {
    }

    static final double REEL = 0.02;
    static final double SURGE_FACTOR = 3.0;
    /** Line tension gained per step reeled while calm, while it runs, and lost per step let go. */
    static final double TIGHTEN = 0.01;
    static final double TIGHTEN_RUNNING = 0.1;
    static final double EASE = 0.05;
    static final int WARNING_STEPS = 6;
    /** The fish starts ahead: nobody lands a legendary in its first quiet moment. */
    static final double START = 0.3;
    /** 45 seconds: a fight nobody is winning ends with the fish gone. */
    static final int MAX_STEPS = 450;

    /** One fight's state; touched only from the fight's own thread. */
    public static final class Fight {
        private final Tier tier;
        private double rope = START;
        private double tension;
        private Phase phase = Phase.CALM;
        private int phaseLeft;
        private int steps;
        private Outcome outcome = Outcome.FIGHTING;

        Fight(Tier tier) {
            this.tier = tier;
        }

        public Tier tier() {
            return tier;
        }

        /** 0 is the water, 1 the fisher. */
        public double rope() {
            return rope;
        }

        void rope(double to) {
            rope = to;
        }

        /** 0 is slack, 1 snaps. */
        public double tension() {
            return tension;
        }

        public Phase phase() {
            return phase;
        }

        public Outcome outcome() {
            return outcome;
        }

        /** Seconds left before the fish gets away on its own. */
        public int secondsLeft() {
            return Math.max(0, (MAX_STEPS - steps) / 10);
        }
    }

    /** Whether this catch fights before it comes up: only legendary and mythic fish, each its share of the time. */
    public boolean fights(Catch caught, Odds odds, Random random) {
        if (!(caught instanceof Catch.Fish fish) || !fish.tier().atLeast(Tier.LEGENDARY)) {
            return false;
        }
        int percent = fish.tier().atLeast(Tier.MYTHIC) ? odds.mythicPercent() : odds.legendaryPercent();
        return random.nextInt(100) < percent;
    }

    /** How hard this fish pulls each step while calm. */
    double pull(Tier tier) {
        return tier.atLeast(Tier.MYTHIC) ? 0.0075 : 0.006;
    }

    public Fight start(Tier tier, Random random) {
        Fight fight = new Fight(tier);
        fight.phaseLeft = calm(random);
        return fight;
    }

    /** One step of the fight; {@code reeling} is whether the fisher holds sneak. */
    public void step(Fight fight, boolean reeling, Random random) {
        if (fight.outcome != Outcome.FIGHTING) {
            return;
        }
        fight.steps++;
        boolean running = fight.phase == Phase.SURGE;
        if (reeling) {
            fight.tension = Math.min(1, fight.tension + (running ? TIGHTEN_RUNNING : TIGHTEN));
            if (fight.tension >= 1) {
                fight.outcome = Outcome.SNAPPED;
                return;
            }
            if (!running) {
                fight.rope = Math.min(1, fight.rope + REEL);
                if (fight.rope >= 1) {
                    fight.outcome = Outcome.LANDED;
                    return;
                }
            }
        } else {
            fight.tension = Math.max(0, fight.tension - EASE);
        }
        fight.rope -= pull(fight.tier) * (running ? SURGE_FACTOR : 1);
        if (fight.rope <= 0) {
            fight.rope = 0;
            fight.outcome = Outcome.PULLED_IN;
            return;
        }
        if (fight.steps >= MAX_STEPS) {
            fight.outcome = Outcome.ESCAPED;
            return;
        }
        if (--fight.phaseLeft > 0) {
            return;
        }
        switch (fight.phase) {
            case CALM -> {
                fight.phase = Phase.WARNING;
                fight.phaseLeft = WARNING_STEPS;
            }
            case WARNING -> {
                fight.phase = Phase.SURGE;
                fight.phaseLeft = 8 + random.nextInt(9);
            }
            case SURGE -> {
                fight.phase = Phase.CALM;
                fight.phaseLeft = calm(random);
            }
        }
    }

    private static int calm(Random random) {
        return 15 + random.nextInt(16);
    }

    @Override
    public String describe() {
        return "the reel-in battle a legendary or mythic fish sometimes puts up: hold sneak while it is calm, "
                + "let go before it runs, and keep the line from snapping";
    }
}
