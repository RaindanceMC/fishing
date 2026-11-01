package de.raindancer.modules.fishing.service;

import org.bukkit.block.BlockFace;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SeaChestFacingTest {

    @ParameterizedTest(name = "facing {0}")
    @EnumSource(value = BlockFace.class, names = {"NORTH", "EAST", "SOUTH", "WEST"})
    @DisplayName("the lock looks the way the chest faces — an item display shows the model's front to the south")
    void lockFacesOut(BlockFace facing) {
        Vector3f front = new Quaternionf().rotateY(SeaChestBlocks.yaw(facing)).transform(new Vector3f(0, 0, 1));
        assertThat(front.x).isCloseTo(facing.getModX(), within(1e-5f));
        assertThat(front.z).isCloseTo(facing.getModZ(), within(1e-5f));
    }
}
