package de.raindancer.modules.fishing.store;

import de.raindancer.modules.fishing.model.NoSpotZone;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NoSpotZoneStoreTest {

    @Test
    void writtenAndReadBackIsTheSameZone() throws Exception {
        NoSpotZone zone = NoSpotZone.of("Casino Pond", UUID.randomUUID(), 290, 700, 360, 770);
        YamlConfiguration yaml = new YamlConfiguration();
        NoSpotZoneStore.write(yaml, "zones." + zone.key(), zone);
        YamlConfiguration reread = new YamlConfiguration();
        reread.loadFromString(yaml.saveToString());
        assertThat(NoSpotZoneStore.read(reread, "zones." + zone.key())).isEqualTo(zone);
    }

    @Test
    void anEntryWithoutAWorldIsNoZone() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("zones:\n  lake:\n    name: lake\n    min-x: 0\n");
        assertThat(NoSpotZoneStore.read(yaml, "zones.lake")).isNull();
    }
}
