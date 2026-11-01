package de.raindancer.modules.fishing.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpotTest {

    private final UUID world = UUID.randomUUID();

    private Spot at(double x, double z, double radius, boolean fixed) {
        return new Spot(UUID.randomUUID(), world, x, 62, z, Water.OCEAN, Tier.EPIC, 5, 1000L, radius, fixed);
    }

    @Test
    @DisplayName("two spots overlap when their circles touch; another world or far below never does")
    void overlaps() {
        Spot one = at(0, 0, 4, false);
        assertThat(one.overlaps(at(7.9, 0, 4, false))).isTrue();
        assertThat(one.overlaps(at(8.1, 0, 4, false))).isFalse();
        assertThat(one.overlaps(at(0, 11, 8, true))).as("a big fixed spot reaches further").isTrue();
        assertThat(one.overlaps(new Spot(UUID.randomUUID(), UUID.randomUUID(), 0, 62, 0, Water.OCEAN, Tier.EPIC, 5,
                1000L, 4, false))).isFalse();
        assertThat(one.overlaps(new Spot(UUID.randomUUID(), world, 0, 20, 0, Water.CAVE, Tier.EPIC, 5, 1000L, 4,
                false))).as("a cave pool far below").isFalse();
    }

    @Test
    @DisplayName("a fixed spot never runs out or wanders off; its own radius decides what it covers")
    void fixed() {
        Spot fixed = at(0, 0, 6, true);
        assertThat(fixed.fished().fished().fished().fished().fished().fished()).isEqualTo(fixed);
        assertThat(fixed.isOver(Long.MAX_VALUE)).isFalse();
        assertThat(fixed.covers(world, 5.5, 62, 0)).isTrue();
        assertThat(at(0, 0, 4, false).covers(world, 5.5, 62, 0)).isFalse();
    }

    @Test
    @DisplayName("a wandering spot is fished out and wanders off as before")
    void wandering() {
        Spot spot = new Spot(UUID.randomUUID(), world, 0, 62, 0, Water.OCEAN, Tier.EPIC, 1, 1000L);
        assertThat(spot.fixed()).isFalse();
        assertThat(spot.fished().isOver(0)).isTrue();
        assertThat(spot.isOver(1000L)).isTrue();
    }
}
