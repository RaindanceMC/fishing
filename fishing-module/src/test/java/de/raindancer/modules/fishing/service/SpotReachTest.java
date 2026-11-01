package de.raindancer.modules.fishing.service;

import de.raindancer.modules.fishing.model.Spot;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.model.Water;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpotReachTest {

    private final UUID world = UUID.randomUUID();
    private final Spot spot = new Spot(UUID.randomUUID(), world, 0.5, 62, 0.5, Water.RIVER, Tier.RARE, 5, Long.MAX_VALUE);

    @Test
    @DisplayName("a hook up to four blocks from a spot's centre is in it, a little further is not")
    void fourBlocks() {
        assertThat(SpotService.RADIUS).isEqualTo(4.0);
        assertThat(spot.covers(world, 0.5 + 3.9, 62, 0.5, SpotService.RADIUS)).isTrue();
        assertThat(spot.covers(world, 0.5 + 2.8, 62, 0.5 + 2.8, SpotService.RADIUS)).as("diagonally, 3.96 away").isTrue();
        assertThat(spot.covers(world, 0.5 + 4.2, 62, 0.5, SpotService.RADIUS)).isFalse();
    }
}
