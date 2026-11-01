package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.Catch;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.RodPart;
import de.raindancer.modules.fishing.model.Water;
import de.raindancer.modules.fishing.store.CatalogReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NetherBiomeTest {

    private static final Catalog CATALOG = CatalogReader.shipped();
    private final CatchRule rule = new CatchRule(CATALOG);

    @ParameterizedTest(name = "lava in {0}")
    @ValueSource(strings = {"minecraft:nether_wastes", "minecraft:crimson_forest", "minecraft:warped_forest",
            "minecraft:soul_sand_valley", "minecraft:basalt_deltas", "minecraft:plains"})
    @DisplayName("every biome's lava has fish of its own, and only fish that live there bite")
    void biomes(String biome) {
        Rod rod = Rod.PLAIN.with(RodPart.LINE, 1);
        CatchRule.Cast cast = new CatchRule.Cast(Water.LAVA, rod, FisherProfile.NEW, false, 0, 0, true, null, null,
                biome);
        Random random = new Random(9);
        Set<String> seen = new HashSet<>();
        for (int n = 0; n < 6000; n++) {
            if (rule.roll(cast, random) instanceof Catch.Fish fish) {
                assertThat(fish.kind().bitesIn(Water.LAVA, biome)).as(fish.kind().id() + " in " + biome).isTrue();
                seen.add(fish.kind().id());
            }
        }
        assertThat(seen).as("kinds caught in " + biome).hasSizeGreaterThanOrEqualTo(3);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"crimson_snapper", "warped_tetra", "basalt_bass", "soul_pike"})
    @DisplayName("a nether biome's own fish never bites in another biome's lava, or in the overworld's")
    void ownFish(String id) {
        FishKind kind = CATALOG.fish(id).orElseThrow();
        long homes = Set.of("minecraft:nether_wastes", "minecraft:crimson_forest", "minecraft:warped_forest",
                "minecraft:soul_sand_valley", "minecraft:basalt_deltas", "minecraft:plains").stream()
                .filter(biome -> kind.bitesIn(Water.LAVA, biome)).count();
        assertThat(homes).isEqualTo(1);
    }
}
