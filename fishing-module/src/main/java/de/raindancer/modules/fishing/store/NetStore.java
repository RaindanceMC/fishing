package de.raindancer.modules.fishing.store;

import de.raindancer.core.data.store.KeyedDocument;
import de.raindancer.core.data.store.YamlStore;
import de.raindancer.modules.fishing.rules.NetRule;
import org.bukkit.configuration.ConfigurationSection;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** What is in every fishing net, in {@code nets.yml} — the network's one document, so a net is the same everywhere. */
public final class NetStore {

    private static final String SECTION = "nets";

    private final KeyedDocument<List<NetRule.Slot>> document;

    public NetStore(Path dataFolder) {
        this(YamlStore.network(dataFolder.resolve("nets.yml")));
    }

    public NetStore(YamlStore store) {
        this.document = new KeyedDocument<>(store, SECTION, NetStore::read, NetStore::write);
    }

    public boolean load() {
        return document.load();
    }

    public List<NetRule.Slot> of(UUID net) {
        return document.get(net.toString()).orElse(List.of());
    }

    /** @return whether it was written; false when the document cannot be read right now */
    public boolean change(UUID net, UnaryOperator<List<NetRule.Slot>> change) {
        return document.change(net.toString(), current -> {
            List<NetRule.Slot> changed = change.apply(current == null ? List.of() : current);
            return changed == null || changed.isEmpty() ? null : changed;
        });
    }

    static List<NetRule.Slot> read(ConfigurationSection yaml, String path) {
        List<NetRule.Slot> slots = new ArrayList<>();
        for (java.util.Map<?, ?> entry : yaml.getMapList(path)) {
            Object item = entry.get("item");
            Object amount = entry.get("amount");
            if (item instanceof String text && amount instanceof Number count && count.intValue() > 0) {
                slots.add(new NetRule.Slot(text, Math.min(NetRule.STACK, count.intValue())));
            }
        }
        return slots.isEmpty() ? null : slots;
    }

    static void write(ConfigurationSection yaml, String path, List<NetRule.Slot> slots) {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        for (NetRule.Slot slot : slots) {
            list.add(java.util.Map.of("item", slot.item(), "amount", slot.amount()));
        }
        yaml.set(path, list);
    }
}
