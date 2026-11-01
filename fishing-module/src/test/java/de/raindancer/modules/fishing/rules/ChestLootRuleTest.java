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
}
