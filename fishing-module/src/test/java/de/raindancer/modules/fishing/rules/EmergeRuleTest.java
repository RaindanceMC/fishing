package de.raindancer.modules.fishing.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EmergeRuleTest {

    private final EmergeRule rule = new EmergeRule();

    @Test
    @DisplayName("the surface is the top of the liquid the hook floats in, however deep it is")
    void surfaceOfDeepWater() {
        // water from y=60 to y=64; the bobber floats at 64.1, inside block 64
        assertThat(rule.surface(64, y -> y >= 60 && y <= 64)).isCloseTo(64 + EmergeRule.FULL_LIQUID, within(1e-9));
    }

    @Test
    @DisplayName("a hook resting on top of the water block (bobbing just above it) still finds that water")
    void hookJustAbove() {
        // the hook's block (65) is air, the water below it ends at 64
        assertThat(rule.surface(65, y -> y <= 64)).isCloseTo(64 + EmergeRule.FULL_LIQUID, within(1e-9));
    }

    @Test
    @DisplayName("no liquid at or right under the hook (the void): no surface")
    void noLiquid() {
        assertThat(rule.surface(30, y -> false)).isNaN();
    }

    @Test
    @DisplayName("the catch starts well under the surface but never below the liquid's bottom")
    void depth() {
        assertThat(rule.depth(5)).isEqualTo(EmergeRule.DEPTH);
        assertThat(rule.depth(1)).as("a single block of water").isLessThan(EmergeRule.DEPTH).isPositive();
    }
}
