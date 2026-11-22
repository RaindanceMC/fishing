package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Catch;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.model.Water;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TugRuleTest {

    private final TugRule rule = new TugRule();

    private static Catch.Fish fish(Tier tier) {
        return new Catch.Fish(new FishKind("x", "X", tier, Set.of(Water.RIVER), false), 1);
    }

    @Test
    @DisplayName("only legendary and mythic fish fight; chests and lesser fish come straight up")
    void whoFights() {
        assertThat(rule.fights(fish(Tier.EPIC))).isFalse();
        assertThat(rule.fights(fish(Tier.LEGENDARY))).isTrue();
        assertThat(rule.fights(fish(Tier.MYTHIC))).isTrue();
        assertThat(rule.fights(new Catch.Chest(Tier.MYTHIC))).isFalse();
    }

    @Test
    @DisplayName("a fisher who does nothing is pulled in")
    void idleLoses() {
        Random random = new Random(1);
        TugRule.Fight fight = rule.start(Tier.LEGENDARY, random);
        while (fight.outcome() == TugRule.Outcome.FIGHTING) {
            rule.step(fight, random);
        }
        assertThat(fight.outcome()).isEqualTo(TugRule.Outcome.PULLED_IN);
    }

    @Test
    @DisplayName("reeling hard while it is calm and letting go when it runs lands it")
    void goodPlayWins() {
        for (Tier tier : new Tier[]{Tier.LEGENDARY, Tier.MYTHIC}) {
            for (long seed = 0; seed < 20; seed++) {
                Random random = new Random(seed);
                TugRule.Fight fight = rule.start(tier, random);
                int step = 0;
                while (fight.outcome() == TugRule.Outcome.FIGHTING) {
                    // a held right click repeats every 4 ticks: one reel every second step
                    if (step++ % 2 == 0 && fight.phase() != TugRule.Phase.SURGE) {
                        rule.reel(fight);
                    }
                    rule.step(fight, random);
                }
                assertThat(fight.outcome()).as(tier + " seed " + seed).isEqualTo(TugRule.Outcome.LANDED);
            }
        }
    }

    @Test
    @DisplayName("holding on through every run snaps the line")
    void holdingThroughSnaps() {
        Random random = new Random(5);
        TugRule.Fight fight = rule.start(Tier.LEGENDARY, random);
        int step = 0;
        while (fight.outcome() == TugRule.Outcome.FIGHTING) {
            if (step++ % 2 == 0) {
                rule.reel(fight);
            }
            rule.step(fight, random);
        }
        assertThat(fight.outcome()).isEqualTo(TugRule.Outcome.SNAPPED);
    }

    @Test
    @DisplayName("a reel while it is calm draws the fish in; while it runs it strains the line instead")
    void reelByPhase() {
        Random random = new Random(2);
        TugRule.Fight fight = rule.start(Tier.LEGENDARY, random);
        double before = fight.rope();
        rule.reel(fight);
        assertThat(fight.rope()).isGreaterThan(before);
        assertThat(fight.strain()).isZero();
        while (fight.phase() != TugRule.Phase.SURGE) {
            rule.step(fight, random);
        }
        double rope = fight.rope();
        rule.reel(fight);
        assertThat(fight.rope()).isEqualTo(rope);
        assertThat(fight.strain()).isEqualTo(1);
    }

    @Test
    @DisplayName("a mythic pulls harder than a legendary")
    void mythicIsStronger() {
        assertThat(rule.pull(Tier.MYTHIC)).isGreaterThan(rule.pull(Tier.LEGENDARY));
    }

    @Test
    @DisplayName("a stalemate does not last for ever: the fish gets away")
    void stalemateEscapes() {
        Random random = new Random(4);
        TugRule.Fight fight = rule.start(Tier.LEGENDARY, random);
        while (fight.outcome() == TugRule.Outcome.FIGHTING) {
            // just enough to hold the rope where it is
            if (fight.phase() != TugRule.Phase.SURGE && fight.rope() < 0.5) {
                rule.reel(fight);
            }
            if (fight.rope() > 0.6) {
                fight.rope(0.5);
            }
            rule.step(fight, random);
        }
        assertThat(fight.outcome()).isEqualTo(TugRule.Outcome.ESCAPED);
    }
}
