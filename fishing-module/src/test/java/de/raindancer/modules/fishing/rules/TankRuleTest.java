package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Aquarium;
import de.raindancer.modules.fishing.model.TankPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TankRuleTest {

    private final TankRule rule = new TankRule();

    private static Set<TankPos> tanks(TankPos... at) {
        return new HashSet<>(List.of(at));
    }

    @Test
    @DisplayName("tanks side by side and stacked are one tank; a corner is not a side")
    void sidesJoin() {
        Set<TankPos> world = tanks(new TankPos(0, 0, 0), new TankPos(1, 0, 0), new TankPos(1, 1, 0),
                new TankPos(2, 0, 1), new TankPos(2, 2, 0), new TankPos(5, 0, 0));
        assertThat(rule.group(new TankPos(0, 0, 0), world::contains))
                .containsExactlyInAnyOrder(new TankPos(0, 0, 0), new TankPos(1, 0, 0), new TankPos(1, 1, 0));
        assertThat(rule.group(new TankPos(2, 2, 0), world::contains)).as("diagonally above is a corner")
                .containsExactly(new TankPos(2, 2, 0));
        assertThat(rule.group(new TankPos(5, 0, 0), world::contains)).containsExactly(new TankPos(5, 0, 0));
    }

    @Test
    @DisplayName("a stacked tower is one tank too, capped like a row")
    void stackedJoin() {
        Set<TankPos> tower = new HashSet<>();
        for (int y = 0; y < 20; y++) {
            tower.add(new TankPos(0, y, 0));
        }
        assertThat(rule.group(new TankPos(0, 0, 0), tower::contains)).hasSize(TankRule.MAX_TANKS);
        assertThat(rule.group(new TankPos(0, 3, 0), tanks(new TankPos(0, 3, 0), new TankPos(0, 4, 0))::contains))
                .containsExactly(new TankPos(0, 3, 0), new TankPos(0, 4, 0));
    }

    @Test
    @DisplayName("a tank is never bigger than twelve blocks, however many are joined")
    void capped() {
        Set<TankPos> row = new HashSet<>();
        for (int x = 0; x < 30; x++) {
            row.add(new TankPos(x, 0, 0));
        }
        List<TankPos> group = rule.group(new TankPos(0, 0, 0), row::contains);
        assertThat(group).hasSize(TankRule.MAX_TANKS).contains(new TankPos(0, 0, 0));
        assertThat(TankRule.MAX_TANKS * Aquarium.CAPACITY).as("fits the menu's four rows").isLessThanOrEqualTo(36);
    }

    @Test
    @DisplayName("the group comes in a fixed order, whichever block is asked from, so the slots stay put")
    void stableOrder() {
        Set<TankPos> world = tanks(new TankPos(0, 0, 0), new TankPos(1, 0, 0), new TankPos(0, 0, 1));
        assertThat(rule.group(new TankPos(1, 0, 0), world::contains))
                .isEqualTo(rule.group(new TankPos(0, 0, 1), world::contains));
    }

    @Test
    @DisplayName("a block's look names the sides it is joined on: north 1, east 2, south 4, west 8, up 16, down 32")
    void look() {
        Set<TankPos> group = tanks(new TankPos(0, 0, 0), new TankPos(1, 0, 0), new TankPos(0, 0, -1),
                new TankPos(0, 1, 0));
        assertThat(rule.look(new TankPos(0, 0, 0), group::contains)).isEqualTo(2 | 1 | 16);
        assertThat(rule.look(new TankPos(1, 0, 0), group::contains)).isEqualTo(8);
        assertThat(rule.look(new TankPos(0, 0, -1), group::contains)).isEqualTo(4);
        assertThat(rule.look(new TankPos(0, 1, 0), group::contains)).isEqualTo(32);
        assertThat(rule.look(new TankPos(9, 9, 9), Set.<TankPos>of()::contains)).isZero();
        assertThat(TankRule.LOOKS).isEqualTo(64);
    }

    @Test
    @DisplayName("a fish taken out of the middle closes the gap; one out of range changes nothing")
    void without() {
        java.util.UUID owner = java.util.UUID.randomUUID();
        Aquarium tank = Aquarium.empty(owner).with(new Aquarium.Resident("a", null))
                .with(new Aquarium.Resident("b", null)).with(new Aquarium.Resident("c", null));
        assertThat(tank.without(1).residents()).extracting(Aquarium.Resident::fishId).containsExactly("a", "c");
        assertThat(tank.without(7)).isEqualTo(tank);
    }
}
