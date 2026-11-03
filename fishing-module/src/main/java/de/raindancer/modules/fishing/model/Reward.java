package de.raindancer.modules.fishing.model;

/** One thing out of a sea chest. Values, so a whole chest can be rolled and checked without a server. */
public sealed interface Reward {

    /** Money in the currency's whole units. */
    record Money(long amount) implements Reward {
    }

    /** A vanilla item, by its material key ({@code diamond}). */
    record Item(String material, int amount) implements Reward {
    }

    /** An enchanted book with this many enchantment levels' worth (like an enchanting table's 1..30). */
    record Book(int levels) implements Reward {
    }

    record Fish(FishKind kind, int amount) implements Reward {
    }

    /** Enchantment shards, by the enchantment's key ({@code rainsenchants:deep_bite}). */
    record Shard(String enchantment, int amount) implements Reward {
    }

    /** The module's own bait, ready for the off hand. */
    record Bait(de.raindancer.modules.fishing.model.Bait bait, int amount) implements Reward {
    }

    /** A rod skin's token: used, the skin is the fisher's for good. */
    record Skin(RodSkin skin) implements Reward {
    }
}
