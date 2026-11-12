package de.raindancer.modules.fishing.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NoSpotZoneTest {

    private final UUID world = UUID.randomUUID();

    @Test
    void cornersInAnyOrderMakeTheSameBox() {
        NoSpotZone zone = NoSpotZone.of("Spawn Lake", world, 20, -5, -10, 15);
        assertThat(zone.minX()).isEqualTo(-10);
        assertThat(zone.maxX()).isEqualTo(20);
        assertThat(zone.minZ()).isEqualTo(-5);
        assertThat(zone.maxZ()).isEqualTo(15);
        assertThat(zone.key()).isEqualTo("spawn lake");
    }

    @Test
    void insideIsTheWholeBlockColumnsToTheEdge() {
        NoSpotZone zone = NoSpotZone.of("lake", world, 0, 0, 10, 10);
        assertThat(zone.contains(world, 0.0, 0.0)).isTrue();
        assertThat(zone.contains(world, 10.9, 10.9)).isTrue();
        assertThat(zone.contains(world, 11.0, 5)).isFalse();
        assertThat(zone.contains(world, -0.1, 5)).isFalse();
        assertThat(zone.contains(UUID.randomUUID(), 5, 5)).as("another world").isFalse();
    }

    @Test
    void aroundAPointIsASquare() {
        NoSpotZone zone = NoSpotZone.around("harbour", world, 100.7, -50.2, 32);
        assertThat(zone.minX()).isEqualTo(68);
        assertThat(zone.maxX()).isEqualTo(132);
        assertThat(zone.minZ()).isEqualTo(-83);
        assertThat(zone.maxZ()).isEqualTo(-19);
    }
}
