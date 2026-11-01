package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.Reward;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.Tier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What is in a sea chest: money, a few fish no better than the chest, and things from a pool that gets richer each
 * tier — every pool also holds everything the pools below it hold, so a better chest is never a narrower one.
 */
public final class ChestLootRule implements IFishingRule {

    private record Entry(Tier from, int weight, String material, int least, int most, int bookLevels, String shard) {

        Reward roll(Random random) {
            if (shard != null) {
                return new Reward.Shard(shard, least + random.nextInt(most - least + 1));
            }
            if (bookLevels > 0) {
                return new Reward.Book(Math.min(30, bookLevels + random.nextInt(Math.max(1, bookLevels / 2) + 1)));
            }
            return new Reward.Item(material, least + random.nextInt(most - least + 1));
        }
    }

    private static Entry item(Tier from, int weight, String material, int least, int most) {
        return new Entry(from, weight, material, least, most, 0, null);
    }

    private static Entry book(Tier from, int weight, int levels) {
        return new Entry(from, weight, "enchanted_book", 1, 1, levels, null);
    }

    private static Entry shard(Tier from, int weight, String enchantment, int least, int most) {
        return new Entry(from, weight, "echo_shard", least, most, 0, enchantment);
    }

    private static final List<Entry> POOL = List.of(
            // the fishing enchantments' shards, from the better chests
            shard(Tier.EPIC, 6, Rod.DEEP_BITE, 1, 2),
            shard(Tier.EPIC, 4, Rod.TWIN_CATCH, 1, 1),
            shard(Tier.LEGENDARY, 4, Rod.TREASURE_HUNTER, 1, 2),
            shard(Tier.MYTHIC, 3, Rod.MYTHIC_LURE, 1, 1),
            item(Tier.COMMON, 30, "iron_ingot", 2, 5),
            item(Tier.COMMON, 25, "gold_nugget", 4, 12),
            item(Tier.COMMON, 20, "string", 2, 5),
            item(Tier.COMMON, 15, "experience_bottle", 2, 4),
            item(Tier.COMMON, 10, "lily_pad", 1, 3),
            item(Tier.COMMON, 5, "name_tag", 1, 1),
            book(Tier.COMMON, 8, 6),
            item(Tier.UNCOMMON, 20, "gold_ingot", 2, 4),
            item(Tier.UNCOMMON, 15, "lapis_lazuli", 4, 9),
            item(Tier.UNCOMMON, 10, "prismarine_crystals", 2, 6),
            book(Tier.UNCOMMON, 12, 12),
            item(Tier.RARE, 15, "diamond", 1, 2),
            item(Tier.RARE, 12, "emerald", 2, 5),
            item(Tier.RARE, 8, "nautilus_shell", 1, 1),
            item(Tier.RARE, 6, "golden_apple", 1, 1),
            book(Tier.RARE, 12, 18),
            item(Tier.EPIC, 14, "diamond", 2, 4),
            item(Tier.EPIC, 8, "nautilus_shell", 1, 2),
            item(Tier.EPIC, 5, "heart_of_the_sea", 1, 1),
            item(Tier.EPIC, 6, "golden_apple", 1, 2),
            book(Tier.EPIC, 12, 24),
            item(Tier.LEGENDARY, 10, "netherite_scrap", 1, 2),
            item(Tier.LEGENDARY, 10, "diamond", 4, 8),
            item(Tier.LEGENDARY, 6, "heart_of_the_sea", 1, 1),
            item(Tier.LEGENDARY, 3, "trident", 1, 1),
            book(Tier.LEGENDARY, 12, 30),
            item(Tier.MYTHIC, 10, "netherite_ingot", 1, 1),
            item(Tier.MYTHIC, 6, "enchanted_golden_apple", 1, 1),
            item(Tier.MYTHIC, 6, "shulker_shell", 2, 4),
            item(Tier.MYTHIC, 4, "totem_of_undying", 1, 1),
            item(Tier.MYTHIC, 2, "elytra", 1, 1));

    /** What a chest's money is worth against a Driftwood chest's. */
    private static final int[] MONEY = {1, 3, 8, 20, 60, 200};

    private final Catalog catalog;

    public ChestLootRule(Catalog catalog) {
        this.catalog = catalog;
    }

    /** How many things besides money and fish come out of a chest of this tier. */
    public int rolls(Tier tier) {
        return 2 + tier.ordinal();
    }

    /**
     * Opens a chest.
     *
     * @param moneyBase what a Driftwood chest pays, about; zero for no money at all
     */
    public List<Reward> open(Tier tier, long moneyBase, Random random) {
        List<Reward> rewards = new ArrayList<>();
        if (moneyBase > 0) {
            long money = Math.round(moneyBase * MONEY[tier.ordinal()] * (0.8 + random.nextDouble() * 0.4));
            rewards.add(new Reward.Money(Math.max(1, money)));
        }
        List<FishKind> fish = catalog.fish().stream().filter(kind -> !kind.tier().atLeast(tier)
                || kind.tier() == tier).toList();
        if (!fish.isEmpty()) {
            FishKind kind = fish.get(random.nextInt(fish.size()));
            rewards.add(new Reward.Fish(kind, 1 + random.nextInt(3)));
        }
        List<Entry> pool = POOL.stream().filter(entry -> tier.atLeast(entry.from())).toList();
        int total = pool.stream().mapToInt(Entry::weight).sum();
        for (int n = 0; n < rolls(tier); n++) {
            int point = random.nextInt(total);
            for (Entry entry : pool) {
                point -= entry.weight();
                if (point < 0) {
                    rewards.add(entry.roll(random));
                    break;
                }
            }
        }
        return rewards;
    }

    @Override
    public String describe() {
        return "what comes out of a sea chest of each tier";
    }
}
