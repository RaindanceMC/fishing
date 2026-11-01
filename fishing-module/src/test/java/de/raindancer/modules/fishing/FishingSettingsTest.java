package de.raindancer.modules.fishing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FishingSettingsTest {

    private final FishingSettings defaults = FishingSettings.DEFAULTS;

    @Test
    @DisplayName("every default, spelled out — two swapped ints in a long record compile perfectly")
    void defaults() {
        assertThat(defaults.replaceVanillaFish()).isTrue();
        assertThat(defaults.spotsEnabled()).isTrue();
        assertThat(defaults.spotsPerPlayer()).isEqualTo(2);
        assertThat(defaults.spotMinutes()).isEqualTo(8);
        assertThat(defaults.spotCatches()).isEqualTo(12);
        assertThat(defaults.spotChestPermille()).isEqualTo(50);
        assertThat(defaults.chestPermille()).isEqualTo(25);
        assertThat(defaults.chestMoney()).isEqualTo("100");
        assertThat(defaults.fishPrice()).as("a common fish sells for about a cod's value").isEqualTo("8");
        assertThat(defaults.hookCost()).isEqualTo(40);
        assertThat(defaults.lureCost()).isEqualTo(120);
        assertThat(defaults.reelCost()).isEqualTo(100);
        assertThat(defaults.emberLineCost()).isEqualTo(1500);
        assertThat(defaults.voidLineCost()).isEqualTo(6000);
        assertThat(defaults.costGrowthPercent()).isEqualTo(200);
        assertThat(defaults.levelsPerRank()).isEqualTo(5);
        assertThat(defaults.emberLineLevel()).isEqualTo(10);
        assertThat(defaults.voidLineLevel()).isEqualTo(25);
        assertThat(defaults.highestLevel()).isEqualTo(50);
        assertThat(defaults.skillResetFee()).isEqualTo("1000");
    }

    @Test
    @DisplayName("the void line asks more than the ember line, in experience and in level")
    void theVoidIsTheTop() {
        assertThat(defaults.voidLineLevel()).isGreaterThan(defaults.emberLineLevel());
        assertThat(defaults.voidLineCost()).isGreaterThan(defaults.emberLineCost());
    }

}
