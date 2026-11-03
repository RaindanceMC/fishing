package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.RodPart;
import de.raindancer.modules.fishing.rules.UpgradeRule.Costs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressRulesTest {

    private final LevelRule levels = new LevelRule(50);

    @Test
    @DisplayName("levels grow ever slower, and the experience for a level always reaches it")
    void levelCurve() {
        assertThat(levels.level(0)).isZero();
        long previous = -1;
        for (int level = 1; level <= 50; level++) {
            long needed = levels.experienceFor(level);
            assertThat(needed).isGreaterThan(previous);
            assertThat(levels.level(needed)).isEqualTo(level);
            assertThat(levels.level(needed - 1)).isEqualTo(level - 1);
            previous = needed;
        }
        assertThat(levels.level(Long.MAX_VALUE / 4)).isEqualTo(50);
    }

    @Test
    @DisplayName("waiting shrinks with the hook, quick bite and a spot, but never below a third")
    void biteTime() {
        BiteRule bite = new BiteRule();
        assertThat(bite.waitFactor(Rod.PLAIN, FisherProfile.NEW, false)).isEqualTo(1.0);
        double hooked = bite.waitFactor(Rod.PLAIN.with(RodPart.HOOK, 5), FisherProfile.NEW, false);
        assertThat(hooked).isCloseTo(0.6, org.assertj.core.data.Offset.offset(1e-9));
        double everything = bite.waitFactor(Rod.PLAIN.with(RodPart.HOOK, 5),
                FisherProfile.NEW.withNode("quick_bite", 5), true);
        assertThat(everything).isGreaterThanOrEqualTo(BiteRule.FASTEST);
    }

    @Test
    @DisplayName("the tree's waits: everywhere, in a spot, in the rain, in lava and over the void; patience waits longer")
    void treeWaits() {
        BiteRule bite = new BiteRule();
        org.assertj.core.data.Offset<Double> near = org.assertj.core.data.Offset.offset(1e-9);
        FisherProfile quick = FisherProfile.NEW.withNode("quick_bite", 5);
        assertThat(bite.waitFactor(Rod.PLAIN, quick, false)).isCloseTo(0.7, near);
        FisherProfile storm = FisherProfile.NEW.withNode("rain_dancer", 3);
        assertThat(bite.waitFactor(Rod.PLAIN, storm, false, null, null, true)).isCloseTo(0.7, near);
        assertThat(bite.waitFactor(Rod.PLAIN, storm, false, null, null, false)).isCloseTo(1.0, near);
        FisherProfile hot = FisherProfile.NEW.withNode("strange_waters", 2);
        assertThat(bite.waitFactor(Rod.PLAIN, hot, false, null, de.raindancer.modules.fishing.model.Water.LAVA, false))
                .isCloseTo(0.9, near);
        assertThat(bite.waitFactor(Rod.PLAIN, hot, false, null, de.raindancer.modules.fishing.model.Water.RIVER, false))
                .isCloseTo(1.0, near);
        FisherProfile patient = FisherProfile.NEW.withNode("patience", 1);
        assertThat(bite.waitFactor(Rod.PLAIN, patient, false)).isCloseTo(1.2, near);
        FisherProfile spotter = FisherProfile.NEW.withNode("spot_reader", 3);
        assertThat(bite.waitFactor(Rod.PLAIN, spotter, true)).isCloseTo(0.8 * 0.76, near);
    }

    @Test
    @DisplayName("upgrades are earned, not bought: fishing experience, a level per rank, and nothing past the top")
    void upgrades() {
        LevelRule levelRule = new LevelRule(50);
        UpgradeRule upgrades = new UpgradeRule(levelRule);
        Costs costs = new Costs(40, 120, 100, 1500, 6000, 200, 5, 10, 25);
        FisherProfile novice = FisherProfile.NEW.withExperience(levelRule.experienceFor(1));
        FisherProfile veteran = FisherProfile.NEW.withExperience(levelRule.experienceFor(30));

        assertThat(upgrades.cost(Rod.PLAIN, RodPart.HOOK, costs)).isEqualTo(40);
        assertThat(upgrades.cost(Rod.PLAIN.with(RodPart.HOOK, 1), RodPart.HOOK, costs)).isEqualTo(80);
        assertThat(upgrades.cost(Rod.PLAIN.with(RodPart.HOOK, 4), RodPart.HOOK, costs)).isEqualTo(640);
        assertThat(upgrades.levelNeeded(Rod.PLAIN, RodPart.HOOK, costs)).isEqualTo(1);
        assertThat(upgrades.levelNeeded(Rod.PLAIN.with(RodPart.HOOK, 2), RodPart.HOOK, costs)).isEqualTo(11);

        assertThat(upgrades.refusal(Rod.PLAIN, RodPart.HOOK, novice, costs)).isEmpty();
        assertThat(upgrades.refusal(Rod.PLAIN.with(RodPart.HOOK, 1), RodPart.HOOK, novice, costs))
                .contains(UpgradeRule.Refusal.LEVEL_TOO_LOW);
        assertThat(upgrades.refusal(Rod.PLAIN.with(RodPart.HOOK, 5), RodPart.HOOK, veteran, costs))
                .contains(UpgradeRule.Refusal.MAXED);
        assertThat(upgrades.refusal(Rod.PLAIN, RodPart.LINE, novice, costs))
                .contains(UpgradeRule.Refusal.LEVEL_TOO_LOW);
        assertThat(upgrades.refusal(Rod.PLAIN, RodPart.LINE, veteran, costs)).isEmpty();
        assertThat(upgrades.cost(Rod.PLAIN.with(RodPart.LINE, 1), RodPart.LINE, costs)).isEqualTo(6000);
        assertThat(upgrades.levelNeeded(Rod.PLAIN.with(RodPart.LINE, 1), RodPart.LINE, costs)).isEqualTo(25);

        FisherProfile spent = veteran.withSpent(veteran.experience() - 10);
        assertThat(spent.available()).isEqualTo(10);
        assertThat(levelRule.level(spent.experience())).as("spending never costs a level").isEqualTo(30);
        assertThat(upgrades.refusal(Rod.PLAIN, RodPart.HOOK, spent, costs))
                .contains(UpgradeRule.Refusal.NOT_ENOUGH_EXPERIENCE);
    }
}
