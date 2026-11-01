package de.raindancer.modules.fishing.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FishForgeTest {

    @Test
    @DisplayName("effect keys read as names, levels as numerals, durations in minutes when whole")
    void wording() {
        assertThat(FishForge.effectTitle("water_breathing")).isEqualTo("Water Breathing");
        assertThat(FishForge.effectTitle("minecraft:luck")).isEqualTo("Luck");
        assertThat(FishForge.roman(2)).isEqualTo("II");
        assertThat(FishForge.seconds(600)).isEqualTo("10 min");
        assertThat(FishForge.seconds(45)).isEqualTo("45 s");
    }
}
