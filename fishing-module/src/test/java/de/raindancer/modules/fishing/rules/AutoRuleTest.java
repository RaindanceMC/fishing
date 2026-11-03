package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Perks;
import de.raindancer.modules.fishing.model.SkillTree;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class AutoRuleTest {

    private final AutoRule rule = new AutoRule();
    private final SkillTree tree = SkillTree.shipped();

    @Test
    @DisplayName("auto-reel and auto-rethrow come from their nodes alone")
    void fromTheTree() {
        assertThat(rule.reels(Perks.NONE)).isFalse();
        assertThat(rule.rethrows(Perks.NONE)).isFalse();
        assertThat(rule.reels(tree.perks(Map.of("auto_reel", 1)))).isTrue();
        assertThat(rule.rethrows(tree.perks(Map.of("auto_rethrow", 1)))).isTrue();
    }

    @Test
    @DisplayName("a fisher who keeps looking around is never stopped")
    void lookingAroundKeepsItGoing() {
        AutoRule.Watch watch = new AutoRule.Watch();
        for (int n = 0; n < 200; n++) {
            assertThat(rule.rethrow(watch, false, false, n * 7f, 0f)).isEqualTo(AutoRule.Stop.NONE);
        }
    }

    @Test
    @DisplayName("thirty rethrows without turning, and the thirty-first is refused — until the fisher turns or casts")
    void stillFisherIsStopped() {
        AutoRule.Watch watch = new AutoRule.Watch();
        for (int n = 0; n < AutoRule.STILL_CASTS; n++) {
            assertThat(rule.rethrow(watch, false, false, 90f, 10f)).as("cast " + n).isEqualTo(AutoRule.Stop.NONE);
        }
        assertThat(rule.rethrow(watch, false, false, 90.5f, 10f)).isEqualTo(AutoRule.Stop.STILL);
        assertThat(rule.rethrow(watch, false, false, 120f, 10f)).isEqualTo(AutoRule.Stop.NONE);

        for (int n = 0; n < AutoRule.STILL_CASTS; n++) {
            rule.rethrow(watch, false, false, 120f, 10f);
        }
        watch.castByHand();
        assertThat(rule.rethrow(watch, false, false, 120f, 10f)).isEqualTo(AutoRule.Stop.NONE);
    }

    @Test
    @DisplayName("a fisher marked away gets no rethrow")
    void awayStops() {
        assertThat(rule.rethrow(new AutoRule.Watch(), true, false, 0f, 0f)).isEqualTo(AutoRule.Stop.AWAY);
    }

    @Test
    @DisplayName("with the bypass permission neither being away nor standing still stops it")
    void unguarded() {
        AutoRule.Watch watch = new AutoRule.Watch();
        for (int n = 0; n < 500; n++) {
            assertThat(rule.rethrow(watch, true, true, 0f, 0f)).isEqualTo(AutoRule.Stop.NONE);
        }
    }

    @Test
    @DisplayName("the reel comes a moment after the bite, well inside any bite's window; the rethrow a second later")
    void delays() {
        Random random = new Random(3);
        for (int n = 0; n < 500; n++) {
            assertThat(rule.reelDelay(random)).isBetween(4, 12);
            assertThat(rule.rethrowDelay(random)).isBetween(15, 30);
        }
    }
}
