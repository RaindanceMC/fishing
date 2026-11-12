package de.raindancer.modules.fishing.store;

import de.raindancer.core.data.store.KeyedDocument;
import de.raindancer.core.data.store.YamlStore;
import de.raindancer.modules.fishing.model.NoSpotZone;
import org.bukkit.configuration.ConfigurationSection;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The no-spot zones, in {@code no-spot-zones.yml} — the network's one document, like the fixed spots. */
public final class NoSpotZoneStore {

    private static final String SECTION = "zones";

    private final KeyedDocument<NoSpotZone> document;

    public NoSpotZoneStore(Path dataFolder) {
        this(YamlStore.network(dataFolder.resolve("no-spot-zones.yml")));
    }

    public NoSpotZoneStore(YamlStore store) {
        this.document = new KeyedDocument<>(store, SECTION, NoSpotZoneStore::read, NoSpotZoneStore::write);
    }

    public boolean load() {
        return document.load();
    }

    public List<NoSpotZone> all() {
        return new ArrayList<>(document.all().values());
    }

    public boolean put(NoSpotZone zone) {
        return document.change(zone.key(), current -> zone);
    }

    public boolean remove(String key) {
        return document.get(key).isPresent() && document.change(key, current -> null);
    }

    static NoSpotZone read(ConfigurationSection yaml, String path) {
        ConfigurationSection entry = yaml.getConfigurationSection(path);
        if (entry == null) {
            return null;
        }
        UUID world;
        try {
            world = UUID.fromString(entry.getString("world", ""));
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
        String name = entry.getString("name", path.substring(path.lastIndexOf('.') + 1));
        return NoSpotZone.of(name, world, entry.getInt("min-x"), entry.getInt("min-z"), entry.getInt("max-x"),
                entry.getInt("max-z"));
    }

    static void write(ConfigurationSection yaml, String path, NoSpotZone zone) {
        yaml.set(path + ".name", zone.name());
        yaml.set(path + ".world", zone.world().toString());
        yaml.set(path + ".min-x", zone.minX());
        yaml.set(path + ".min-z", zone.minZ());
        yaml.set(path + ".max-x", zone.maxX());
        yaml.set(path + ".max-z", zone.maxZ());
    }
}
