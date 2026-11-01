package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Water;
import de.raindancer.modules.fishing.rules.WaterRule.Place;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WaterRuleTest {

    private final WaterRule rule = new WaterRule();

    private static Place water(String biome) {
        return new Place(biome, WaterRule.Dimension.OVERWORLD, false, false, true);
    }

    @Test
    @DisplayName("every overworld biome family lands in its own water")
    void biomes() {
        assertThat(rule.classify(water("minecraft:river"))).contains(Water.RIVER);
        assertThat(rule.classify(water("minecraft:plains"))).contains(Water.RIVER);
        assertThat(rule.classify(water("minecraft:ocean"))).contains(Water.OCEAN);
        assertThat(rule.classify(water("minecraft:deep_ocean"))).contains(Water.OCEAN);
        assertThat(rule.classify(water("minecraft:beach"))).contains(Water.OCEAN);
        assertThat(rule.classify(water("minecraft:warm_ocean"))).contains(Water.WARM_OCEAN);
        assertThat(rule.classify(water("minecraft:lukewarm_ocean"))).contains(Water.WARM_OCEAN);
        assertThat(rule.classify(water("minecraft:deep_lukewarm_ocean"))).contains(Water.WARM_OCEAN);
        assertThat(rule.classify(water("minecraft:frozen_ocean"))).contains(Water.FROZEN);
        assertThat(rule.classify(water("minecraft:frozen_river"))).contains(Water.FROZEN);
        assertThat(rule.classify(water("minecraft:snowy_plains"))).contains(Water.FROZEN);
        assertThat(rule.classify(water("minecraft:swamp"))).contains(Water.SWAMP);
        assertThat(rule.classify(water("minecraft:mangrove_swamp"))).contains(Water.SWAMP);
        assertThat(rule.classify(water("minecraft:lush_caves"))).contains(Water.CAVE);
        assertThat(rule.classify(water("minecraft:dripstone_caves"))).contains(Water.CAVE);
    }

    @Test
    @DisplayName("water out of the sky's reach is cave water, whatever the biome above says")
    void underground() {
        assertThat(rule.classify(new Place("minecraft:plains", WaterRule.Dimension.OVERWORLD, false, false, false)))
                .contains(Water.CAVE);
    }

    @Test
    @DisplayName("lava is lava in any world, and the End's void is the void")
    void lavaAndVoid() {
        assertThat(rule.classify(new Place("minecraft:nether_wastes", WaterRule.Dimension.NETHER, true, false, true)))
                .contains(Water.LAVA);
        assertThat(rule.classify(new Place("minecraft:plains", WaterRule.Dimension.OVERWORLD, true, false, false)))
                .contains(Water.LAVA);
        assertThat(rule.classify(new Place("minecraft:the_end", WaterRule.Dimension.END, false, true, true)))
                .contains(Water.VOID);
    }

    @Test
    @DisplayName("the void only counts in the End, and plain air is no water at all")
    void notWater() {
        assertThat(rule.classify(new Place("minecraft:plains", WaterRule.Dimension.OVERWORLD, false, true, true)))
                .isEmpty();
        assertThat(rule.classify(new Place("minecraft:the_end", WaterRule.Dimension.END, false, false, true)))
                .contains(Water.RIVER);
    }
}
