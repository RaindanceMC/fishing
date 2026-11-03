package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Perks;
import de.raindancer.modules.fishing.model.SkillTree;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.model.Water;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BonusRuleTest {

    private final BonusRule rule = new BonusRule();
    private final SkillTree tree = SkillTree.shipped();
    private static final Offset<Double> NEAR = Offset.offset(0.001);

    @Test
    @DisplayName("experience: Tide Lore everywhere, Hot Lore in lava and the void, First Find on a kind's first catch")
    void experience() {
        assertThat(rule.experience(100, Perks.NONE, Water.RIVER, false)).isEqualTo(100);
        assertThat(rule.experience(100, tree.perks(Map.of("tide_lore", 2)), Water.RIVER, false)).isEqualTo(110);
        Perks hot = tree.perks(Map.of("hot_lore", 1));
        assertThat(rule.experience(100, hot, Water.LAVA, false)).isEqualTo(110);
        assertThat(rule.experience(100, hot, Water.OCEAN, false)).isEqualTo(100);
        Perks first = tree.perks(Map.of("first_find", 2));
        assertThat(rule.experience(100, first, Water.RIVER, true)).isEqualTo(200);
        assertThat(rule.experience(100, first, Water.RIVER, false)).isEqualTo(100);
    }

    @Test
    @DisplayName("Chest Lore: a sea chest opened gives more experience")
    void chestExperience() {
        assertThat(rule.chestExperience(100, Perks.NONE)).isEqualTo(100);
        assertThat(rule.chestExperience(100, tree.perks(Map.of("chest_lore", 3)))).isEqualTo(145);
    }

    @Test
    @DisplayName("weight: Heavy Hands on all, Leviathan Arms on legendary and mythic, Magma Scales in lava and the void")
    void weight() {
        assertThat(rule.heavier(Perks.NONE, Tier.MYTHIC, Water.VOID)).isCloseTo(1.0, NEAR);
        Perks arms = tree.perks(Map.of("leviathan_arms", 3));
        assertThat(rule.heavier(arms, Tier.LEGENDARY, Water.OCEAN)).isCloseTo(1.15, NEAR);
        assertThat(rule.heavier(arms, Tier.EPIC, Water.OCEAN)).isCloseTo(1.0, NEAR);
        Perks scales = tree.perks(Map.of("magma_scales", 2, "heavy_hands", 1));
        assertThat(rule.heavier(scales, Tier.COMMON, Water.LAVA)).isCloseTo(1.04 * 1.10, NEAR);
        assertThat(rule.heavier(scales, Tier.COMMON, Water.RIVER)).isCloseTo(1.04, NEAR);
    }

    @Test
    @DisplayName("Market Stall pays a share of the shop price of every fish at once; nothing without it")
    void catchMoney() {
        assertThat(rule.catchMoney(1000, Perks.NONE)).isZero();
        assertThat(rule.catchMoney(1000, tree.perks(Map.of("market_stall", 3)))).isEqualTo(150);
        assertThat(rule.catchMoney(0, tree.perks(Map.of("market_stall", 3)))).isZero();
    }

    @Test
    @DisplayName("Long Bite holds a lava or void bite longer")
    void biteWindow() {
        assertThat(rule.biteWindow(30, Perks.NONE)).isEqualTo(30);
        assertThat(rule.biteWindow(30, tree.perks(Map.of("long_bite", 3)))).isEqualTo(48);
    }
}
