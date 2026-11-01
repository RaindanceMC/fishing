package de.raindancer.modules.fishing.service;

import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.rules.NetRule;
import de.raindancer.modules.fishing.store.NetStore;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Fishing nets: a bag for fish. Each net has its own id and its fish are kept in the network's database under it —
 * never on the item, so a copied net shares its fish rather than doubling them. While a fisher carries a net with room,
 * what they catch goes into it; opening it takes fish out or puts every fish carried in.
 */
public final class NetService implements IFishingService {

    /** How many stacks a net holds: a chest's worth. */
    public static final int CAPACITY = 27;
    private static final Set<Material> VANILLA_FISH =
            Set.of(Material.COD, Material.SALMON, Material.PUFFERFISH, Material.TROPICAL_FISH);

    private final Plugin plugin;
    private final Server server;
    private final LogChannel log;
    private final FishForge forge;
    private final NetStore store;
    private final NetRule rule = new NetRule();
    private final NamespacedKey idKey;
    private NamespacedKey recipe;

    public NetService(Plugin plugin, Server server, LogChannel log, FishForge forge, NetStore store) {
        this.plugin = plugin;
        this.server = server;
        this.log = log;
        this.forge = forge;
        this.store = store;
        this.idKey = new NamespacedKey(plugin, "net-id");
    }

    @Override
    public void settings(FishingSettings settings) {
        // A net is a net.
    }

    public boolean load() {
        return store.load();
    }

    public int registerRecipe() {
        ShapedRecipe shaped = new ShapedRecipe(new NamespacedKey(plugin, "fishing_net"), forge.net());
        shaped.shape("S S", "SLS", "SSS");
        shaped.setIngredient('S', Material.STRING);
        shaped.setIngredient('L', Material.LEAD);
        shaped.setCategory(CraftingBookCategory.EQUIPMENT);
        if (server.addRecipe(shaped, false)) {
            recipe = ((Keyed) shaped).getKey();
            server.updateRecipes();
            return 1;
        }
        return 0;
    }

    public void discover(Player player) {
        if (recipe != null) {
            player.discoverRecipe(recipe);
        }
    }

    public void unregisterRecipe() {
        if (recipe != null) {
            server.removeRecipe(recipe, false);
            recipe = null;
            server.updateRecipes();
        }
    }

    /** Whether this is something a net takes: our fish, or a plain vanilla one. */
    public boolean holds(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        return forge.fishOf(stack).isPresent()
                || VANILLA_FISH.contains(stack.getType()) && stack.isSimilar(new ItemStack(stack.getType()));
    }

    /** The net's id, given to it the first time it is used. Call on the holder's thread. */
    public UUID idOf(ItemStack net) {
        ItemMeta meta = net.getItemMeta();
        String known = meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (known != null) {
            try {
                return UUID.fromString(known);
            } catch (IllegalArgumentException damaged) {
                // A hand-edited id: the net starts afresh.
            }
        }
        UUID id = UUID.randomUUID();
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id.toString());
        net.setItemMeta(meta);
        return id;
    }

    /** The first net in a player's inventory, if they carry one. */
    public Optional<ItemStack> carried(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (forge.isNet(stack)) {
                return Optional.of(stack);
            }
        }
        return Optional.empty();
    }

    /** What is in a net, as items to show, in slot order. */
    public List<ItemStack> contents(UUID net) {
        List<ItemStack> shown = new ArrayList<>();
        for (NetRule.Slot slot : store.of(net)) {
            ItemStack item = decode(slot.item());
            if (item != null) {
                item.setAmount(slot.amount());
                shown.add(item);
            } else {
                shown.add(new ItemStack(Material.BARRIER));
            }
        }
        return shown;
    }

    /**
     * Puts fish into a net, off the world's threads; {@code back} gets — on the player's thread — whatever did not fit
     * (or everything, when the database could not take it), for the caller to hand back.
     */
    public void put(Player player, UUID net, List<ItemStack> fish, Consumer<List<ItemStack>> back) {
        List<ItemStack> copies = fish.stream().map(ItemStack::clone).toList();
        Scheduling.async(plugin, () -> {
            List<ItemStack> left = new ArrayList<>();
            boolean written = store.change(net, current -> {
                left.clear();
                List<NetRule.Slot> now = current;
                for (ItemStack each : copies) {
                    NetRule.Added added = rule.add(now, encode(each), each.getAmount(), CAPACITY);
                    now = added.contents();
                    if (added.left() > 0) {
                        ItemStack rest = each.clone();
                        rest.setAmount(added.left());
                        left.add(rest);
                    }
                }
                return now;
            });
            List<ItemStack> returned = written ? List.copyOf(left) : copies;
            if (!written) {
                log.warn("A fishing net could not be written to; its fish went back to {}.", player.getName());
            }
            Scheduling.entity(plugin, player, () -> back.accept(returned));
        });
    }

    /**
     * Takes up to {@code amount} out of a slot, off the world's threads; {@code taken} gets the fish (or nothing) on
     * the player's thread.
     */
    public void take(Player player, UUID net, int slot, int amount, Consumer<Optional<ItemStack>> taken) {
        Scheduling.async(plugin, () -> {
            ItemStack[] out = {null};
            boolean written = store.change(net, current -> {
                out[0] = null;
                if (slot < 0 || slot >= current.size()) {
                    return current;
                }
                NetRule.Slot was = current.get(slot);
                NetRule.Taken result = rule.take(current, slot, amount);
                ItemStack item = decode(was.item());
                if (item == null || result.taken() <= 0) {
                    return current;
                }
                item.setAmount(result.taken());
                out[0] = item;
                return result.contents();
            });
            Optional<ItemStack> fish = written ? Optional.ofNullable(out[0]) : Optional.empty();
            Scheduling.entity(plugin, player, () -> taken.accept(fish));
        });
    }

    /**
     * A catch for a fisher carrying a net: into the net if it is fish and fits, the rest to {@code rest}. Call on the
     * player's thread.
     */
    public boolean catchInto(Player player, ItemStack item, Consumer<List<ItemStack>> rest) {
        if (!holds(item)) {
            return false;
        }
        Optional<ItemStack> net = carried(player);
        if (net.isEmpty()) {
            return false;
        }
        put(player, idOf(net.get()), List.of(item), rest);
        return true;
    }

    static String encode(ItemStack item) {
        ItemStack one = item.clone();
        one.setAmount(1);
        return Base64.getEncoder().encodeToString(one.serializeAsBytes());
    }

    private static ItemStack decode(String text) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(text));
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
}
