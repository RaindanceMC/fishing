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

    @Test
    @DisplayName("/give knows every item fishing makes: fish, dishes raw and cooked, chests, rods, bait, skins, the net")
    void giverKnowsEverything(@org.junit.jupiter.api.io.TempDir java.nio.file.Path folder) {
        var catalog = de.raindancer.modules.fishing.store.CatalogReader.shipped();
        var ids = new FishForge(catalog, new de.raindancer.core.content.items.CustomItems(folder.resolve("items.yml")),
                null).giver().ids();
        assertThat(ids).contains("rod-void", "rod-reed", "bait-voidlure", "bait-wriggler", "net", "chest-void",
                "chest-driftwood", "skin-starfall", "fish-astral_leviathan");
        assertThat(ids).hasSize(catalog.fish().size() + 2 * catalog.dishes().size() + 6 + 6
                + de.raindancer.modules.fishing.model.Bait.values().length
                + de.raindancer.modules.fishing.model.RodSkin.values().length + 1);
        assertThat(ids).doesNotHaveDuplicates();
    }
}
