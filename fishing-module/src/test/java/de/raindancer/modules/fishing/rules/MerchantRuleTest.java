package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.Mutation;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.store.CatalogReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantRuleTest {

    private static final Catalog CATALOG = CatalogReader.shipped();
    private final MerchantRule rule = new MerchantRule(CATALOG);
    private final PriceRule prices = new PriceRule();
    private static final LocalDate DAY = LocalDate.of(2026, 11, 23);

    @Test
    @DisplayName("the merchant wants one uncommon, one rare and one epic fish a day, the same all day")
    void wantedOfTheDay() {
        List<FishKind> wanted = rule.wanted(DAY);
        assertThat(wanted).extracting(FishKind::tier).containsExactly(Tier.UNCOMMON, Tier.RARE, Tier.EPIC);
        assertThat(rule.wanted(DAY)).isEqualTo(wanted);
    }

    @Test
    @DisplayName("the wanted fish change from day to day")
    void changesDaily() {
        Set<List<String>> seen = new HashSet<>();
        for (int n = 0; n < 14; n++) {
            seen.add(rule.wanted(DAY.plusDays(n)).stream().map(FishKind::id).toList());
        }
        assertThat(seen).hasSizeGreaterThan(7);
    }

    @Test
    @DisplayName("a wanted fish pays double, a mutation multiplies, a plain unwanted fish pays the shop's price")
    void pricing() {
        long base = 100;
        FishKind wanted = rule.wanted(DAY).getFirst();
        FishKind other = CATALOG.ofTier(Tier.UNCOMMON).stream().filter(kind -> !kind.equals(wanted)).findFirst()
                .orElseThrow();
        assertThat(rule.price(other, null, DAY, base)).isEqualTo(prices.fish(Tier.UNCOMMON, base));
        assertThat(rule.price(wanted, null, DAY, base)).isEqualTo(prices.fish(Tier.UNCOMMON, base) * 2);
        assertThat(rule.price(other, Mutation.GOLDEN, DAY, base))
                .isEqualTo(prices.fish(Tier.UNCOMMON, base) * Mutation.GOLDEN.priceFactor());
    }
}
