package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.RodPart;
import de.raindancer.modules.fishing.model.RodTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RodStatsRuleTest {

    private final RodStatsRule rule = new RodStatsRule();

    @Test
    @DisplayName("a plain fishing rod is the yardstick: nothing to say about it")
    void plain() {
        assertThat(rule.lines(Rod.PLAIN)).isEmpty();
    }

    @Test
    @DisplayName("upgrades show as what they add up to against a plain rod")
    void upgrades() {
        Rod rod = Rod.PLAIN.with(RodPart.HOOK, 2).with(RodPart.REEL, 1).with(RodPart.LURE, 3);
        assertThat(rule.lines(rod)).anyMatch(line -> line.contains("16%") && line.contains("sooner"))
                .anyMatch(line -> line.contains("Sea chests") && line.contains("+4.5%"))
                .anyMatch(line -> line.contains("Double catches") && line.contains("5%"))
                .anyMatch(line -> line.contains("Hook II") && line.contains("Reel I"));
    }

    @Test
    @DisplayName("a rod's tier and its enchantments count in, and the waters it opens are named")
    void tierAndEnchants() {
        Rod tiered = Rod.PLAIN.withTier(RodTier.VOID).withEnchant(Rod.DEEP_BITE, 3);
        double factor = new BiteRule().waitFactor(tiered, de.raindancer.modules.fishing.model.FisherProfile.NEW, false);
        long sooner = Math.round((1 - factor) * 100);
        assertThat(rule.lines(tiered)).anyMatch(line -> line.contains(sooner + "%") && line.contains("sooner"))
                .anyMatch(line -> line.contains("lava and the void"))
                .anyMatch(line -> line.contains("Rarer catches"));
        assertThat(rule.lines(Rod.PLAIN.with(RodPart.LINE, 1))).anyMatch(line -> line.contains("Fishes lava"))
                .noneMatch(line -> line.contains("void"));
    }
}
