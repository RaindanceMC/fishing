package de.raindancer.modules.fishing.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HeldInputRuleTest {

    private final UUID fisher = UUID.randomUUID();

    @Test
    @DisplayName("nothing is blocked before a fight ended")
    void quietByDefault() {
        HeldInputRule rule = new HeldInputRule(750);
        assertThat(rule.blocks(fisher, 0)).isFalse();
    }

    @Test
    @DisplayName("a held right click keeps being swallowed for as long as it repeats")
    void heldClickNeverCasts() {
        HeldInputRule rule = new HeldInputRule(750);
        rule.arm(fisher, 1_000);
        // a held use repeats every 4 ticks; a lag spike may stretch one gap to 600 ms
        for (long now = 1_200; now < 20_000; now += now % 3_000 == 0 ? 600 : 200) {
            assertThat(rule.blocks(fisher, now)).as("at " + now).isTrue();
        }
    }

    @Test
    @DisplayName("let go and click again later: the rod casts")
    void freshClickCasts() {
        HeldInputRule rule = new HeldInputRule(750);
        rule.arm(fisher, 1_000);
        assertThat(rule.blocks(fisher, 1_200)).isTrue();
        assertThat(rule.blocks(fisher, 1_200 + 751)).isFalse();
        assertThat(rule.blocks(fisher, 1_200 + 900)).isFalse();
    }

    @Test
    @DisplayName("one fisher's guard is not another's")
    void perFisher() {
        HeldInputRule rule = new HeldInputRule(750);
        rule.arm(fisher, 1_000);
        assertThat(rule.blocks(UUID.randomUUID(), 1_100)).isFalse();
        rule.forget(fisher);
        assertThat(rule.blocks(fisher, 1_100)).isFalse();
    }
}
