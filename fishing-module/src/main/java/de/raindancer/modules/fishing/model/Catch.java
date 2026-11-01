package de.raindancer.modules.fishing.model;

/** What came up on the line. */
public sealed interface Catch {

    Tier tier();

    /**
     * One or more fish of a kind.
     *
     * @param amount two when a double catch came off
     */
    record Fish(FishKind kind, int amount) implements Catch {

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
