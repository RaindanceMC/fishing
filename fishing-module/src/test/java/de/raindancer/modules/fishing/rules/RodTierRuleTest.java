package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.RodPart;
import de.raindancer.modules.fishing.model.RodTier;
import de.raindancer.modules.fishing.model.Water;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RodTierRuleTest {

    private final LevelRule levels = new LevelRule(50);
    private final RodTierRule rule = new RodTierRule(levels);

    private FisherProfile at(int level) {
        return FisherProfile.NEW.withExperience(levels.experienceFor(level));
    }

    @Test
    @DisplayName("each rod is better than the one before it, asks a higher level and more experience")
    void aLadder() {
        RodTier[] rods = RodTier.values();
        for (int n = 1; n < rods.length; n++) {
            assertThat(rods[n].level()).isGreaterThan(rods[n - 1].level());
            assertThat(rods[n].cost()).isGreaterThan(rods[n - 1].cost());
            assertThat(rods[n].waitPercent()).isGreaterThanOrEqualTo(rods[n - 1].waitPercent());
            assertThat(rods[n].chestPermille()).isGreaterThan(rods[n - 1].chestPermille());
            assertThat(rods[n].tier().ordinal()).isGreaterThan(rods[n - 1].tier().ordinal());
        }
    }

    @Test
    @DisplayName("the Ember and Leviathan rods fish lava, only the Void Rod fishes the void")
    void specialWaters() {
        assertThat(RodTier.PEARL.fishes(Water.LAVA)).isFalse();
        assertThat(RodTier.EMBER.fishes(Water.LAVA)).isTrue();
        assertThat(RodTier.LEVIATHAN.fishes(Water.VOID)).isFalse();
        assertThat(RodTier.VOID.fishes(Water.VOID)).isTrue();
        assertThat(RodTier.VOID.fishes(Water.LAVA)).isTrue();
        assertThat(Rod.PLAIN.withTier(RodTier.EMBER).fishes(Water.LAVA)).isTrue();
        assertThat(Rod.PLAIN.withTier(RodTier.REED).with(RodPart.LINE, 2).fishes(Water.VOID)).isTrue();
    }

    @Test
    @DisplayName("unlocking needs the level, the experience and the rod before it")
    void unlocking() {
        assertThat(rule.refusal(FisherProfile.NEW, RodTier.REED)).contains(RodTierRule.Refusal.LEVEL_TOO_LOW);
        assertThat(rule.refusal(at(1), RodTier.REED)).as("40 experience, the rod costs 80")
                .contains(RodTierRule.Refusal.NOT_ENOUGH_EXPERIENCE);
        assertThat(rule.refusal(at(2), RodTier.REED)).isEmpty();
        assertThat(rule.refusal(at(50), RodTier.REEF)).contains(RodTierRule.Refusal.PREVIOUS_FIRST);
        assertThat(rule.refusal(at(4).withRod(RodTier.REED), RodTier.REEF))
                .contains(RodTierRule.Refusal.LEVEL_TOO_LOW);
        assertThat(rule.refusal(at(5).withRod(RodTier.REED), RodTier.REEF)).isEmpty();
        assertThat(rule.refusal(at(50).withRod(RodTier.REED), RodTier.REED)).contains(RodTierRule.Refusal.ALREADY);
    }

    @Test
    @DisplayName("a vanilla rod is always yours to cast; one of ours only once it is unlocked")
    void casting() {
        assertThat(rule.mayCast(FisherProfile.NEW, Rod.PLAIN)).isTrue();
        assertThat(rule.mayCast(FisherProfile.NEW, Rod.PLAIN.withTier(RodTier.REEF))).isFalse();
        assertThat(rule.mayCast(FisherProfile.NEW.withRod(RodTier.REEF), Rod.PLAIN.withTier(RodTier.REEF))).isTrue();
    }

    @Test
    @DisplayName("a better rod and the fishing enchantments speed bites up and bring more chests and doubles")
    void bonuses() {
        Rod plain = Rod.PLAIN;
        Rod voidRod = Rod.PLAIN.withTier(RodTier.VOID).withEnchant(Rod.DEEP_BITE, 3)
                .withEnchant(Rod.TREASURE_HUNTER, 3).withEnchant(Rod.TWIN_CATCH, 2).withEnchant(Rod.MYTHIC_LURE, 3);
        assertThat(voidRod.ownWaitFactor()).isLessThan(plain.ownWaitFactor());
        assertThat(voidRod.ownChestPermille()).isEqualTo(25 + 30);
        assertThat(voidRod.ownDoublePermille()).isEqualTo(90 + 100);
        assertThat(voidRod.ownRareBonus()).isCloseTo(0.30 + 0.24, org.assertj.core.data.Offset.offset(1e-9));
        BiteRule bites = new BiteRule();
        assertThat(bites.waitFactor(voidRod, FisherProfile.NEW, false))
                .isLessThan(bites.waitFactor(plain, FisherProfile.NEW, false));
    }
}
