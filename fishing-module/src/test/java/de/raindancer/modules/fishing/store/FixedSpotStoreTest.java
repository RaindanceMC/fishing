package de.raindancer.modules.fishing.store;

import de.raindancer.modules.fishing.model.Spot;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.model.Water;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FixedSpotStoreTest {

    @Test
    @DisplayName("a fixed spot written and read back is the same spot")
    void roundTrip() throws Exception {
        UUID id = UUID.randomUUID();
        Spot spot = Spot.fixed(id, UUID.randomUUID(), 10.5, 62.9, -40.5, Water.WARM_OCEAN, Tier.LEGENDARY, 6.5);
        YamlConfiguration yaml = new YamlConfiguration();
        FixedSpotStore.write(yaml, "spots." + id, spot);
        YamlConfiguration reread = new YamlConfiguration();
        reread.loadFromString(yaml.saveToString());
        assertThat(FixedSpotStore.read(reread, "spots." + id)).isEqualTo(spot);
    }

    @Test
    @DisplayName("an entry missing its world or water is no spot, and a silly radius is kept sensible")
    void broken() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        String id = UUID.randomUUID().toString();
        yaml.loadFromString("spots:\n  " + id + ":\n    water: ocean\n    best: rare\n");
        assertThat(FixedSpotStore.read(yaml, "spots." + id)).isNull();
        String world = UUID.randomUUID().toString();
        yaml.loadFromString("spots:\n  " + id + ":\n    world: " + world
                + "\n    water: ocean\n    best: rare\n    radius: 900\n");
        assertThat(FixedSpotStore.read(yaml, "spots." + id).radius()).isEqualTo(FixedSpotStore.MOST_RADIUS);
    }
}
