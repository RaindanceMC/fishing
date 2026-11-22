package de.raindancer.modules.fishing.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AquariumTest {

    private static final UUID OWNER = UUID.fromString("c9e2c821-7b5e-44e9-a5f7-7554a4da0120");

    @Test
    @DisplayName("a tank is written down and read back as it was, mutations and all")
    void roundTrip() {
        Aquarium tank = Aquarium.empty(OWNER).with(new Aquarium.Resident("pebble_trout", null))
                .with(new Aquarium.Resident("hydro_dragonet", Mutation.GOLDEN));
        assertThat(Aquarium.parse(tank.format())).contains(tank);
        assertThat(Aquarium.parse(Aquarium.empty(OWNER).format())).contains(Aquarium.empty(OWNER));
    }

    @Test
    @DisplayName("three fish fit in a tank, no more; the last one in is the first one out")
    void capacity() {
        Aquarium tank = Aquarium.empty(OWNER);
        for (int n = 0; n < Aquarium.CAPACITY; n++) {
            assertThat(tank.full()).isFalse();
            tank = tank.with(new Aquarium.Resident("fish_" + n, null));
        }
        assertThat(tank.full()).isTrue();
        assertThat(tank.with(new Aquarium.Resident("one_more", null))).isEqualTo(tank);
        assertThat(tank.last()).contains(new Aquarium.Resident("fish_2", null));
        assertThat(tank.withoutLast().residents()).extracting(Aquarium.Resident::fishId)
                .containsExactly("fish_0", "fish_1");
        assertThat(Aquarium.empty(OWNER).last()).isEmpty();
    }

    @Test
    @DisplayName("a broken record is no tank rather than an error")
    void broken() {
        assertThat(Aquarium.parse("not-a-uuid|x")).isEmpty();
        assertThat(Aquarium.parse(null)).isEmpty();
        assertThat(Aquarium.parse(OWNER + "|a:nonsense").map(Aquarium::residents))
                .contains(List.of(new Aquarium.Resident("a", null)));
    }
}
