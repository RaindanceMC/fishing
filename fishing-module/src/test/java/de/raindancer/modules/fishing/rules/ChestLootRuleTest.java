package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Reward;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.store.CatalogReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ChestLootRuleTest {

    private final ChestLootRule rule = new ChestLootRule(CatalogReader.shipped());

    private double averageMoney(Tier tier) {
        Random random = new Random(2);
        long total = 0;
        for (int n = 0; n < 500; n++) {
            total += rule.open(tier, 100, random).stream().filter(Reward.Money.class::isInstance)
                    .mapToLong(reward -> ((Reward.Money) reward).amount()).sum();
        }
        return total / 500.0;
    }

    @Test
    @DisplayName("a better chest pays better, every tier over the one below")
    void betterChestsPayMore() {
        double previous = 0;
        for (Tier tier : Tier.values()) {
            double average = averageMoney(tier);
            assertThat(average).as(tier.name()).isGreaterThan(previous);
            previous = average;
        }
    }

    @Test
    @DisplayName("every chest holds money, at least one fish no better than itself, and nothing empty")
    void everyChestIsWorthOpening() {
        Random random = new Random(4);
        for (Tier tier : Tier.values()) {
            for (int n = 0; n < 200; n++) {
                List<Reward> rewards = rule.open(tier, 100, random);
                assertThat(rewards).anyMatch(Reward.Money.class::isInstance);
                assertThat(rewards).anyMatch(reward -> reward instanceof Reward.Fish);
                assertThat(rewards).noneMatch(reward -> reward instanceof Reward.Fish fish
                        && fish.kind().tier().ordinal() > tier.ordinal());
                for (Reward reward : rewards) {
                    switch (reward) {
                        case Reward.Money money -> assertThat(money.amount()).isPositive();
                        case Reward.Item item -> assertThat(item.amount()).isPositive();
                        case Reward.Book book -> assertThat(book.levels()).isBetween(1, 30);
                        case Reward.Fish fish -> assertThat(fish.amount()).isPositive();
                        case Reward.Shard shard -> assertThat(shard.amount()).isPositive();
                        case Reward.Skin token -> assertThat(token.skin().chest()).isEqualTo(tier);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("more things come out of a better chest")
    void betterChestsHoldMore() {
        assertThat(rule.rolls(Tier.MYTHIC)).isGreaterThan(rule.rolls(Tier.COMMON));
    }

    @Test
    @DisplayName("fishing shards come out of the better chests only, and Mythic Lure out of the void's alone")
    void shards() {
        java.util.Map<Tier, java.util.Set<String>> seen = new java.util.EnumMap<>(Tier.class);
        Random random = new Random(12);
        for (Tier tier : Tier.values()) {
            seen.put(tier, new java.util.HashSet<>());
            for (int n = 0; n < 3000; n++) {
                for (Reward reward : rule.open(tier, 100, random)) {
                    if (reward instanceof Reward.Shard shard) {
                        seen.get(tier).add(shard.enchantment());
                    }
                }
            }
        }
        assertThat(seen.get(Tier.COMMON)).isEmpty();
        assertThat(seen.get(Tier.RARE)).isEmpty();
        assertThat(seen.get(Tier.EPIC)).contains(de.raindancer.modules.fishing.model.Rod.DEEP_BITE)
                .doesNotContain(de.raindancer.modules.fishing.model.Rod.MYTHIC_LURE);
        assertThat(seen.get(Tier.LEGENDARY)).contains(de.raindancer.modules.fishing.model.Rod.TREASURE_HUNTER)
                .doesNotContain(de.raindancer.modules.fishing.model.Rod.MYTHIC_LURE);
        assertThat(seen.get(Tier.MYTHIC)).contains(de.raindancer.modules.fishing.model.Rod.MYTHIC_LURE);
    }

    @Test
    @DisplayName("a skin token comes only out of its own chest tier, now and then")
    void skinTokens() {
        Random random = new Random(11);
        for (Tier tier : Tier.values()) {
            int tokens = 0;
            for (int n = 0; n < 4000; n++) {
                for (Reward reward : rule.open(tier, 100, random)) {
                    if (reward instanceof Reward.Skin token) {
                        assertThat(token.skin().chest()).isEqualTo(tier);
                        tokens++;
                    }
                }
            }
            boolean gives = java.util.Arrays.stream(de.raindancer.modules.fishing.model.RodSkin.values())
                    .anyMatch(skin -> skin.chest() == tier);
            if (gives) {
                assertThat(tokens).as(tier + " tokens").isBetween(160, 340);
            } else {
                assertThat(tokens).as(tier + " tokens").isZero();
            }
        }
    }

    private static final java.util.Set<String> PRIZES = java.util.Set.of("diamond", "diamond_block",
            "netherite_scrap", "netherite_ingot", "netherite_upgrade_smithing_template", "nether_star", "elytra");
    private static final java.util.Set<String> CHEAP = java.util.Set.of("string", "lily_pad", "iron_ingot",
            "gold_nugget", "lapis_lazuli", "emerald", "experience_bottle", "prismarine_crystals", "name_tag");

    @Test
    @DisplayName("an Abyss chest or better always holds diamonds or netherite, and never the cheap stuff of the low chests")
    void bigChestsAreBig() {
        Random random = new Random(21);
        for (Tier tier : List.of(Tier.EPIC, Tier.LEGENDARY, Tier.MYTHIC)) {
            for (int n = 0; n < 500; n++) {
                List<Reward> rewards = rule.open(tier, 100, random);
                assertThat(rewards).as(tier + " " + rewards).anyMatch(reward -> reward instanceof Reward.Item item
                        && PRIZES.contains(item.material()));
                assertThat(rewards).as(tier + " " + rewards).noneMatch(reward -> reward instanceof Reward.Item item
                        && CHEAP.contains(item.material()));
            }
        }
    }

    private static int count(List<Reward> rewards, String material) {
        return rewards.stream().filter(reward -> reward instanceof Reward.Item item && item.material().equals(material))
                .mapToInt(reward -> ((Reward.Item) reward).amount()).sum();
    }

    @Test
    @DisplayName("a Void chest averages at least one netherite ingot, a Leviathan chest a handful of diamonds")
    void topChestsPayInNetherite() {
        Random random = new Random(22);
        double ingots = 0;
        double diamonds = 0;
        for (int n = 0; n < 1000; n++) {
            List<Reward> mythic = rule.open(Tier.MYTHIC, 100, random);
            ingots += count(mythic, "netherite_ingot");
            List<Reward> legendary = rule.open(Tier.LEGENDARY, 100, random);
            diamonds += count(legendary, "diamond") + 9 * count(legendary, "diamond_block");
        }
        assertThat(ingots / 1000).isGreaterThanOrEqualTo(1.0);
        assertThat(diamonds / 1000).isGreaterThanOrEqualTo(6.0);
    }

    @Test
    @DisplayName("the opener's tree: Heavy Purse pays more, Lucky Lid now and then finds one thing more")
    void treePerks() {
        var tree = de.raindancer.modules.fishing.model.SkillTree.shipped();
        var purse = tree.perks(java.util.Map.of("heavy_purse", 3));
        Random a = new Random(40);
        Random b = new Random(40);
        long plain = 0;
        long rich = 0;
        for (int n = 0; n < 300; n++) {
            plain += money(rule.open(Tier.RARE, 100, a));
            rich += money(rule.open(Tier.RARE, 100, b, purse));
        }
        assertThat((double) rich / plain).isCloseTo(1.24, org.assertj.core.data.Offset.offset(0.02));

        var lid = tree.perks(java.util.Map.of("gambler", 1, "lucky_lid", 3));
        Random random = new Random(41);
        int extra = 0;
        for (int n = 0; n < 2000; n++) {
            extra += ChestLootRule.extraRolls(lid, random);
        }
        assertThat(extra).isBetween(1300, 1500);
        assertThat(ChestLootRule.extraRolls(tree.perks(java.util.Map.of("hoarder", 1)), random)).isZero();
    }

    private static long money(List<Reward> rewards) {
        return rewards.stream().filter(Reward.Money.class::isInstance).mapToLong(r -> ((Reward.Money) r).amount()).sum();
    }
}
