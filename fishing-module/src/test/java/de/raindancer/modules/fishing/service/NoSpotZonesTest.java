package de.raindancer.modules.fishing.service;

import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.NoSpotZone;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Where an admin says so, no spot comes up on its own. */
class NoSpotZonesTest {

    private final UUID world = UUID.randomUUID();
    private final SpotService spots = new SpotService(null, null, null, null, null, () -> 0L, FishingSettings.DEFAULTS);

    @Test
    void aZoneKeepsSpotsAwayUntilItIsRemoved() {
        assertThat(spots.noSpotsAt(world, 5, 5)).isFalse();
        assertThat(spots.addZone(NoSpotZone.of("lake", world, 0, 0, 10, 10))).isTrue();
        assertThat(spots.noSpotsAt(world, 5, 5)).isTrue();
        assertThat(spots.noSpotsAt(world, 50, 5)).isFalse();
        assertThat(spots.zonesIn(world)).extracting(NoSpotZone::name).containsExactly("lake");

        assertThat(spots.removeZone("LAKE")).isTrue();
        assertThat(spots.noSpotsAt(world, 5, 5)).isFalse();
        assertThat(spots.removeZone("lake")).isFalse();
    }

    @Test
    void aNameIsTakenOnce() {
        assertThat(spots.addZone(NoSpotZone.of("lake", world, 0, 0, 10, 10))).isTrue();
        assertThat(spots.addZone(NoSpotZone.of("Lake", world, 50, 50, 60, 60))).isFalse();
    }
}
