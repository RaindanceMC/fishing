package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.Mutation;
import de.raindancer.modules.fishing.model.Tier;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** What the fishmonger pays: the shop's price, double for the day's wanted fish, times a mutation's worth. */
public final class MerchantRule implements IFishingRule {

    public static final int WANTED_FACTOR = 2;
    private static final List<Tier> WANTED_TIERS = List.of(Tier.UNCOMMON, Tier.RARE, Tier.EPIC);

    private final Catalog catalog;
    private final PriceRule prices = new PriceRule();

    public MerchantRule(Catalog catalog) {
        this.catalog = catalog;
    }

    /** One fish of each of uncommon, rare and epic, the same for everybody all day. */
    public List<FishKind> wanted(LocalDate day) {
        Random random = new Random(day.toEpochDay() * 0x9E3779B97F4A7C15L);
        List<FishKind> picked = new ArrayList<>();
        for (Tier tier : WANTED_TIERS) {
            List<FishKind> kinds = catalog.ofTier(tier).stream().sorted(Comparator.comparing(FishKind::id)).toList();
            if (!kinds.isEmpty()) {
                picked.add(kinds.get(random.nextInt(kinds.size())));
            }
        }
        return picked;
    }

    public boolean isWanted(FishKind kind, LocalDate day) {
        return wanted(day).contains(kind);
    }

    /** In minor units, for one fish. */
    public long price(FishKind kind, Mutation mutation, LocalDate day, long base) {
        long price = prices.fish(kind.tier(), base) * (mutation == null ? 1 : mutation.priceFactor());
        return isWanted(kind, day) ? price * WANTED_FACTOR : price;
    }

    @Override
    public String describe() {
        return "the fishmonger's prices and the fish he wants today";
    }
}
