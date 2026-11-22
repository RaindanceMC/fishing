package de.raindancer.modules.fishing.model;

/** What came up on the line. */
public sealed interface Catch {

    Tier tier();

    /**
     * One or more fish of a kind.
     *
     * @param amount   two when a double catch came off
     * @param mutation the rare variant it came up as, or null for a plain one
     */
    record Fish(FishKind kind, int amount, Mutation mutation) implements Catch {

        public Fish(FishKind kind, int amount) {
            this(kind, amount, null);
        }

        public Fish mutated(Mutation to) {
            return new Fish(kind, amount, to);
        }

        @Override
        public Tier tier() {
            return kind.tier();
        }
    }

    /** A sea chest, to be opened later. */
    record Chest(Tier tier) implements Catch {
    }

    /** Whatever vanilla had put on the hook — treasure and junk stay vanilla's. */
    record Vanilla() implements Catch {

        @Override
        public Tier tier() {
            return Tier.COMMON;
        }
    }
}
