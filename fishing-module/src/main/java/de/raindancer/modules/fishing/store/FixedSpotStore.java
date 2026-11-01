package de.raindancer.modules.fishing.store;

import de.raindancer.core.data.store.KeyedDocument;
import de.raindancer.core.data.store.YamlStore;
import de.raindancer.modules.fishing.model.Spot;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.model.Water;
import org.bukkit.configuration.ConfigurationSection;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The spots admins put down for good, in {@code spots.yml} — the network's one document; each server shows the ones
 * in its own worlds.
 */
public final class FixedSpotStore {

    private static final String SECTION = "spots";
    public static final double LEAST_RADIUS = 1.0;
    public static final double MOST_RADIUS = 32.0;

    private final KeyedDocument<Spot> document;

    public FixedSpotStore(Path dataFolder) {
        this(YamlStore.network(dataFolder.resolve("spots.yml")));
    }

    public FixedSpotStore(YamlStore store) {
        this.document = new KeyedDocument<>(store, SECTION, FixedSpotStore::read, FixedSpotStore::write);
    }

    public boolean load() {
        return document.load();
    }

    public List<Spot> all() {
        return new ArrayList<>(document.all().values());
    }

    /** @return whether it was written */
    public boolean put(Spot spot) {
        return document.change(spot.id().toString(), current -> spot);
    }

    /** @return whether it was there and is gone */
    public boolean remove(UUID id) {
        Optional<Spot> there = document.get(id.toString());
        return there.isPresent() && document.change(id.toString(), current -> null);
    }

    static Spot read(ConfigurationSection yaml, String path) {
        ConfigurationSection entry = yaml.getConfigurationSection(path);
        if (entry == null) {
            return null;
        }
        UUID id;
        UUID world;
        try {
            id = UUID.fromString(path.substring(path.lastIndexOf('.') + 1));
            world = UUID.fromString(entry.getString("world", ""));
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
        Water water = Water.parse(entry.getString("water")).orElse(null);
        Tier best = Tier.parse(entry.getString("best")).orElse(null);
        if (water == null || best == null) {
            return null;
        }
        double radius = Math.clamp(entry.getDouble("radius", Spot.WANDERING_RADIUS), LEAST_RADIUS, MOST_RADIUS);
        return Spot.fixed(id, world, entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"), water, best,
                radius);
    }

    static void write(ConfigurationSection yaml, String path, Spot spot) {
        yaml.set(path + ".world", spot.world().toString());
        yaml.set(path + ".x", spot.x());
        yaml.set(path + ".y", spot.y());
        yaml.set(path + ".z", spot.z());
        yaml.set(path + ".water", spot.water().id());
        yaml.set(path + ".best", spot.best().id());
        yaml.set(path + ".radius", spot.radius());
    }
}
