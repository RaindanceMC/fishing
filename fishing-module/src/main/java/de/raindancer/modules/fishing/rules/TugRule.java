package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Catch;
import de.raindancer.modules.fishing.model.Tier;

import java.util.Random;

/**
 * A legendary or better fish fights. The fish keeps pulling it towards the water, and
 * every reel draws it back. Every few seconds the fish runs, pulling hard, after a short warning; a reel during a run
 * does not draw, it strains the line, and too much strain in one run snaps it. Rope at the fisher: landed. Rope at the
 * water: the fisher goes in after it.
 *
 * The rope starts on the fish's side.
 *
 * <p>One step is two ticks. A held right click reels every four ticks, so about every second step.
 */
public final class TugRule implements IFishingRule {

    public enum Phase { CALM, WARNING, SURGE }

    public enum Outcome { FIGHTING, LANDED, PULLED_IN, SNAPPED, ESCAPED }

    static final double REEL = 0.04;
    static final double SURGE_FACTOR = 3.0;
    static final int WARNING_STEPS = 6;
    /** The fish starts ahead: nobody lands a legendary in its first quiet moment. */
    static final double START = 0.3;
    public static final int MAX_STRAIN = 4;
    /** 45 seconds: a fight nobody is winning ends with the fish gone. */
    static final int MAX_STEPS = 450;

    /** One fight's state; touched only from the fight's own thread. */
    public static final class Fight {
        private final Tier tier;
        private double rope = START;
        private Phase phase = Phase.CALM;
        private int phaseLeft;
        private int strain;
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

        public Phase phase() {
            return phase;
        }

        public int strain() {
            return strain;
        }

        public Outcome outcome() {
            return outcome;
        }

        /** Seconds left before the fish gets away on its own. */
        public int secondsLeft() {
            return Math.max(0, (MAX_STEPS - steps) / 10);
        }
    }

    public boolean fights(Catch caught) {
        return caught instanceof Catch.Fish fish && fish.tier().atLeast(Tier.LEGENDARY);
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

    public void reel(Fight fight) {
        if (fight.outcome != Outcome.FIGHTING) {
            return;
        }
        if (fight.phase == Phase.SURGE) {
            fight.strain++;
            if (fight.strain >= MAX_STRAIN) {
                fight.outcome = Outcome.SNAPPED;
            }
            return;
        }
        fight.rope = Math.min(1, fight.rope + REEL);
        if (fight.rope >= 1) {
            fight.outcome = Outcome.LANDED;
        }
    }

    public void step(Fight fight, Random random) {
        if (fight.outcome != Outcome.FIGHTING) {
            return;
        }
        fight.steps++;
        fight.rope -= pull(fight.tier) * (fight.phase == Phase.SURGE ? SURGE_FACTOR : 1);
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
                fight.strain = 0;
            }
        }
    }

    private static int calm(Random random) {
        return 15 + random.nextInt(16);
    }

    @Override
    public String describe() {
        return "the tug of war with a legendary or mythic fish: reel while it is calm, let go while it runs";
    }
}
