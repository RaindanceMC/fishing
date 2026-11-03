package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.store.CatalogReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class WeightRuleTest {

    private final Catalog catalog = CatalogReader.shipped();
    private final WeightRule rule = new WeightRule();

    @Test
    @DisplayName("a rarer fish weighs more, heavy ones are rare, and about one in ten is a whopper")
    void weights() {
        Random random = new Random(3);
        double previousAverage = 0;
        for (Tier tier : Tier.values()) {
            FishKind kind = catalog.ofTier(tier).getFirst();
            double total = 0;
            int whoppers = 0;
            for (int n = 0; n < 4000; n++) {
                WeightRule.Weighing weighing = rule.weigh(kind, random);
                assertThat(weighing.kilos()).isPositive();
                total += weighing.kilos();
                whoppers += weighing.whopper() ? 1 : 0;
            }
            double average = total / 4000;
            assertThat(average).as(tier.name()).isGreaterThan(previousAverage);
            assertThat(whoppers).as(tier.name()).isBetween(300, 500);
            previousAverage = average;
        }
    }

    @Test
    @DisplayName("only a rare-or-better whopper is worth telling everybody about")
    void broadcast() {
        assertThat(rule.worthTelling(Tier.UNCOMMON, new WeightRule.Weighing(9, true))).isFalse();
        assertThat(rule.worthTelling(Tier.RARE, new WeightRule.Weighing(20, false))).isFalse();
        assertThat(rule.worthTelling(Tier.RARE, new WeightRule.Weighing(24, true))).isTrue();
    }

    @Test
    @DisplayName("every mythic fish is told to everybody, a whopper as a whopper, any other as a mythic catch")
    void everyMythicIsTold() {
        assertThat(rule.telling(Tier.MYTHIC, new WeightRule.Weighing(80, false)))
                .isEqualTo(WeightRule.Telling.MYTHIC);
        assertThat(rule.telling(Tier.MYTHIC, new WeightRule.Weighing(120, true)))
                .isEqualTo(WeightRule.Telling.WHOPPER);
        assertThat(rule.telling(Tier.LEGENDARY, new WeightRule.Weighing(40, false)))
                .isEqualTo(WeightRule.Telling.NOBODY);
        assertThat(rule.telling(Tier.RARE, new WeightRule.Weighing(24, true)))
                .isEqualTo(WeightRule.Telling.WHOPPER);
    }

    @Test
    @DisplayName("weights read as a fisher would say them")
    void format() {
        assertThat(WeightRule.format(1.4234)).isEqualTo("1.42 kg");
        assertThat(WeightRule.format(312.47)).isEqualTo("312.5 kg");
        assertThat(WeightRule.format(0.08)).isEqualTo("80 g");
    }

    @Test
    @DisplayName("Heavy Hands makes the same fish heavier, without making it a whopper any more often")
    void heavier() {
        var kind = de.raindancer.modules.fishing.store.CatalogReader.shipped().fish().getFirst();
        var plain = rule.weigh(kind, new java.util.Random(3));
        var heavy = rule.weigh(kind, new java.util.Random(3), 1.12);
        assertThat(heavy.kilos()).isCloseTo(plain.kilos() * 1.12, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(heavy.whopper()).isEqualTo(plain.whopper());
    }
}
