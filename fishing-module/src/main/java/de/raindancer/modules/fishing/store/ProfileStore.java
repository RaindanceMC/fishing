package de.raindancer.modules.fishing.store;

import de.raindancer.core.data.store.KeyedDocument;
import de.raindancer.core.data.store.YamlStore;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Skill;
import de.raindancer.modules.fishing.model.Tier;
import org.bukkit.configuration.ConfigurationSection;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Every fisher's progress, in {@code fishers.yml} — the network's one document, so a level earned on one server is
 * the level on the next. Each change writes that player's entry alone, starting from what the database holds.
 */
public final class ProfileStore {

    private static final String SECTION = "fishers";

    private final KeyedDocument<FisherProfile> document;

    public ProfileStore(Path dataFolder) {
        this(YamlStore.network(dataFolder.resolve("fishers.yml")));
    }

    public ProfileStore(YamlStore store) {
        this.document = new KeyedDocument<>(store, SECTION, ProfileStore::read, ProfileStore::write);
    }

    public boolean load() {
        return document.load();
    }

    public FisherProfile of(UUID player) {
        return document.get(player.toString()).orElse(FisherProfile.NEW);
    }

    public Optional<FisherProfile> find(UUID player) {
        return document.get(player.toString());
    }

    /** @return whether the change was written; false when the document cannot be read right now */
    public boolean change(UUID player, UnaryOperator<FisherProfile> change) {
        return document.change(player.toString(), current ->
                change.apply(current == null ? FisherProfile.NEW : current));
    }

    public Map<UUID, FisherProfile> all() {
        Map<UUID, FisherProfile> everyone = new HashMap<>();
        document.all().forEach((key, profile) -> {
            try {
                everyone.put(UUID.fromString(key), profile);
            } catch (IllegalArgumentException notAnId) {
                // Somebody's hand-edit; it has no player to belong to.
            }
        });
        return everyone;
    }

    static FisherProfile read(ConfigurationSection yaml, String path) {
        ConfigurationSection entry = yaml.getConfigurationSection(path);
        if (entry == null) {
            return null;
        }
        Map<Skill, Integer> skills = new EnumMap<>(Skill.class);
        ConfigurationSection skillSection = entry.getConfigurationSection("skills");
        if (skillSection != null) {
            for (String key : skillSection.getKeys(false)) {
                Skill.parse(key).ifPresent(skill -> skills.put(skill, skillSection.getInt(key)));
            }
        }
        Map<String, Integer> caught = new HashMap<>();
        ConfigurationSection caughtSection = entry.getConfigurationSection("caught");
        if (caughtSection != null) {
            for (String key : caughtSection.getKeys(false)) {
                caught.put(key, caughtSection.getInt(key));
            }
        }
        Map<Tier, Integer> chests = new EnumMap<>(Tier.class);
        ConfigurationSection chestSection = entry.getConfigurationSection("chests");
        if (chestSection != null) {
            for (String key : chestSection.getKeys(false)) {
                Tier.parse(key).ifPresent(tier -> chests.put(tier, chestSection.getInt(key)));
            }
        }
        java.util.Set<de.raindancer.modules.fishing.model.RodTier> rods = new java.util.HashSet<>();
        for (String rod : entry.getStringList("rods")) {
            de.raindancer.modules.fishing.model.RodTier.parse(rod).ifPresent(rods::add);
        }
        Map<String, Double> heaviest = new HashMap<>();
        ConfigurationSection heavySection = entry.getConfigurationSection("heaviest");
        if (heavySection != null) {
            for (String key : heavySection.getKeys(false)) {
                heaviest.put(key, heavySection.getDouble(key));
            }
        }
        return new FisherProfile(entry.getLong("experience"), entry.getLong("spent"), skills, caught, chests, rods,
                heaviest);
    }

    static void write(ConfigurationSection yaml, String path, FisherProfile profile) {
        yaml.set(path + ".experience", profile.experience());
        if (profile.spent() > 0) {
            yaml.set(path + ".spent", profile.spent());
        }
        profile.skills().forEach((skill, rank) -> yaml.set(path + ".skills." + skill.id(), rank));
        profile.caught().forEach((fish, count) -> yaml.set(path + ".caught." + fish, count));
        profile.chests().forEach((tier, count) -> yaml.set(path + ".chests." + tier.id(), count));
        profile.heaviest().forEach((fish, kilos) -> yaml.set(path + ".heaviest." + fish, kilos));
        if (!profile.rods().isEmpty()) {
            yaml.set(path + ".rods", profile.rods().stream().sorted().map(rod -> rod.id()).toList());
        }
    }
}
