package de.raindancer.modules.fishing.store;

import de.raindancer.core.data.store.KeyedDocument;
import de.raindancer.core.data.store.YamlStore;
import de.raindancer.modules.fishing.model.FisherProfile;
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
    /** Written with every entry: its ranks are under "tree", not the old "skills". */
    static final int TREE_VERSION = 2;

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
        // Before the skill tree (Fishing 0.4.0) there were four skills under "skills"; each became the tree node of
        // the same name, and their owners get one free unlearning to spend their points on the new tree.
        boolean tree = entry.getInt("tree-version") >= TREE_VERSION;
        Map<String, Integer> nodes = new HashMap<>();
        ConfigurationSection nodeSection = entry.getConfigurationSection(tree ? "tree" : "skills");
        if (nodeSection != null) {
            for (String key : nodeSection.getKeys(false)) {
                nodes.put(key, nodeSection.getInt(key));
            }
        }
        boolean freeUnlearn = tree ? entry.getBoolean("free-unlearn") : !nodes.isEmpty();
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
        java.util.Set<de.raindancer.modules.fishing.model.RodSkin> skins = new java.util.HashSet<>();
        for (String skin : entry.getStringList("skins")) {
            de.raindancer.modules.fishing.model.RodSkin.parse(skin).ifPresent(skins::add);
        }
        return new FisherProfile(entry.getLong("experience"), entry.getLong("spent"), nodes, caught, chests, rods,
                heaviest, skins, freeUnlearn, entry.getInt("void-dry"));
    }

    static void write(ConfigurationSection yaml, String path, FisherProfile profile) {
        yaml.set(path + ".experience", profile.experience());
        if (profile.spent() > 0) {
            yaml.set(path + ".spent", profile.spent());
        }
        yaml.set(path + ".tree-version", TREE_VERSION);
        profile.nodes().forEach((node, rank) -> yaml.set(path + ".tree." + node, rank));
        if (profile.voidDry() > 0) {
            yaml.set(path + ".void-dry", profile.voidDry());
        }
        if (profile.freeUnlearn()) {
            yaml.set(path + ".free-unlearn", true);
        }
        profile.caught().forEach((fish, count) -> yaml.set(path + ".caught." + fish, count));
        profile.chests().forEach((tier, count) -> yaml.set(path + ".chests." + tier.id(), count));
        profile.heaviest().forEach((fish, kilos) -> yaml.set(path + ".heaviest." + fish, kilos));
        if (!profile.rods().isEmpty()) {
            yaml.set(path + ".rods", profile.rods().stream().sorted().map(rod -> rod.id()).toList());
        }
        if (!profile.skins().isEmpty()) {
            yaml.set(path + ".skins", profile.skins().stream().sorted().map(skin -> skin.id()).toList());
        }
    }
}
