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

    private static int fights(TugRule rule, Catch caught, TugRule.Odds odds) {
        Random random = new Random(7);
        int fights = 0;
        for (int i = 0; i < 10_000; i++) {
            if (rule.fights(caught, odds, random)) {
                fights++;
            }
        }
        return fights;
    }

    @Test
    @DisplayName("only legendary and mythic fish can fight; chests and lesser fish come straight up")
    void whoFights() {
        TugRule.Odds always = new TugRule.Odds(100, 100);
        assertThat(fights(rule, fish(Tier.EPIC), always)).isZero();
        assertThat(fights(rule, fish(Tier.RARE), always)).isZero();
        assertThat(fights(rule, new Catch.Chest(Tier.MYTHIC), always)).isZero();
        assertThat(fights(rule, fish(Tier.LEGENDARY), always)).isEqualTo(10_000);
        assertThat(fights(rule, fish(Tier.MYTHIC), always)).isEqualTo(10_000);
    }

    @Test
    @DisplayName("each tier fights only its configured share of catches")
    void sharePerTier() {
        TugRule.Odds odds = new TugRule.Odds(30, 80);
        assertThat(fights(rule, fish(Tier.LEGENDARY), odds)).isBetween(2_800, 3_200);
        assertThat(fights(rule, fish(Tier.MYTHIC), odds)).isBetween(7_800, 8_200);
        assertThat(fights(rule, fish(Tier.LEGENDARY), new TugRule.Odds(0, 0))).isZero();
    }

    @Test
    @DisplayName("a fisher who does nothing loses the fish")
    void idleLoses() {
        Random random = new Random(1);
        TugRule.Fight fight = rule.start(Tier.LEGENDARY, random);
        while (fight.outcome() == TugRule.Outcome.FIGHTING) {
            rule.step(fight, false, random);
        }
        assertThat(fight.outcome()).isEqualTo(TugRule.Outcome.PULLED_IN);
    }

    @Test
    @DisplayName("holding sneak until it runs and letting go while it runs lands it")
    void goodPlayWins() {
        for (Tier tier : new Tier[]{Tier.LEGENDARY, Tier.MYTHIC}) {
            for (long seed = 0; seed < 40; seed++) {
                Random random = new Random(seed);
                TugRule.Fight fight = rule.start(tier, random);
                while (fight.outcome() == TugRule.Outcome.FIGHTING) {
                    rule.step(fight, fight.phase() != TugRule.Phase.SURGE && fight.tension() < 0.8, random);
                }
                assertThat(fight.outcome()).as(tier + " seed " + seed).isEqualTo(TugRule.Outcome.LANDED);
            }
        }
    }

    @Test
    @DisplayName("holding on through every run snaps the line")
    void holdingThroughSnaps() {
        for (long seed = 0; seed < 20; seed++) {
            Random random = new Random(seed);
            TugRule.Fight fight = rule.start(Tier.LEGENDARY, random);
            while (fight.outcome() == TugRule.Outcome.FIGHTING) {
                rule.step(fight, true, random);
            }
            assertThat(fight.outcome()).as("seed " + seed).isEqualTo(TugRule.Outcome.SNAPPED);
        }
    }

    @Test
    @DisplayName("reeling while calm draws the fish in and tightens the line a little; while it runs only the line suffers")
    void reelByPhase() {
        Random random = new Random(2);
        TugRule.Fight fight = rule.start(Tier.LEGENDARY, random);
        double rope = fight.rope();
        rule.step(fight, true, random);
        assertThat(fight.rope()).isGreaterThan(rope);
        assertThat(fight.tension()).isGreaterThan(0).isLessThan(0.05);
        while (fight.phase() != TugRule.Phase.SURGE) {
            rule.step(fight, false, random);
        }
        rope = fight.rope();
        double tension = fight.tension();
        rule.step(fight, true, random);
        assertThat(fight.rope()).isLessThan(rope);
        assertThat(fight.tension()).isGreaterThan(tension + 0.05);
    }

    @Test
    @DisplayName("letting go eases the line")
    void lettingGoEases() {
        Random random = new Random(3);
        TugRule.Fight fight = rule.start(Tier.LEGENDARY, random);
        for (int i = 0; i < 10; i++) {
            rule.step(fight, true, random);
        }
        double tension = fight.tension();
        rule.step(fight, false, random);
        assertThat(fight.tension()).isLessThan(tension);
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
            if (fight.rope() > 0.6) {
                fight.rope(0.5);
            }
            rule.step(fight, fight.phase() == TugRule.Phase.CALM && fight.rope() < 0.5 && fight.tension() < 0.8,
                    random);
        }
        assertThat(fight.outcome()).isEqualTo(TugRule.Outcome.ESCAPED);
    }
}
