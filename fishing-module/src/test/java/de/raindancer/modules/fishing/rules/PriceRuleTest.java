package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Tier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PriceRuleTest {

    private final PriceRule rule = new PriceRule();

    @Test
    @DisplayName("a fish fetches more the rarer it is, a common one the base price")
    void fish() {
        assertThat(rule.fish(Tier.COMMON, 8)).isEqualTo(8);
        assertThat(rule.fish(Tier.RARE, 8)).isEqualTo(32);
        assertThat(rule.fish(Tier.MYTHIC, 8)).isEqualTo(600);
        for (int t = 1; t < Tier.values().length; t++) {
            assertThat(rule.fish(Tier.values()[t], 8)).isGreaterThan(rule.fish(Tier.values()[t - 1], 8));
        }
    }

    @Test
    @DisplayName("a dish is worth half again what a fish of its tier is: cooking pays")
    void dish() {
        assertThat(rule.dish(Tier.COMMON, 8)).isEqualTo(12);
        assertThat(rule.dish(Tier.EPIC, 8)).isEqualTo(120);
    }

    @Test
    @DisplayName("base 0 switches selling off")
    void off() {
        assertThat(rule.fish(Tier.MYTHIC, 0)).isZero();
        assertThat(rule.dish(Tier.MYTHIC, 0)).isZero();
    }
}
