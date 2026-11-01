package de.raindancer.modules.fishing.store;

import de.raindancer.core.content.enchant.CustomEnchantments;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.RodPart;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.Map;

/**
 * A rod's upgrades, written onto the rod in its persistent data — where a player cannot edit them, and where they
 * travel with the rod through chests, trades and deaths.
 */
public final class RodTags {

    private static final String NAMESPACE = "rainsfishing";

    private RodTags() {
    }

    private static NamespacedKey key(RodPart part) {
        return new NamespacedKey(NAMESPACE, "rod_" + part.id());
    }

    public static boolean isRod(ItemStack stack) {
        return stack != null && stack.getType() == Material.FISHING_ROD;
    }

    /** The parts fitted and the fishing enchantments on it; which rod tier it is, the forge knows. */
    public static Rod read(ItemStack stack) {
        if (!isRod(stack) || !stack.hasItemMeta()) {
            return Rod.PLAIN;
        }
        PersistentDataContainer data = stack.getItemMeta().getPersistentDataContainer();
        Map<RodPart, Integer> ranks = new EnumMap<>(RodPart.class);
        for (RodPart part : RodPart.values()) {
            Integer rank = data.get(key(part), PersistentDataType.INTEGER);
            if (rank != null) {
                ranks.put(part, rank);
            }
        }
        Map<String, Integer> enchants = new java.util.HashMap<>();
        for (String enchantment : FISHING_ENCHANTS) {
            int level = CustomEnchantments.levelOn(stack, enchantment);
            if (level > 0) {
                enchants.put(enchantment, level);
            }
        }
        return new Rod(ranks, null, enchants);
    }

    private static final java.util.List<String> FISHING_ENCHANTS = java.util.List.of(Rod.DEEP_BITE, Rod.TWIN_CATCH,
            Rod.TREASURE_HUNTER, Rod.MYTHIC_LURE);

    /** Writes the ranks; the lore that names them is the caller's (it is wording). */
    public static void write(ItemStack stack, Rod rod) {
        if (!isRod(stack)) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        for (RodPart part : RodPart.values()) {
            int rank = rod.rank(part);
            if (rank > 0) {
                data.set(key(part), PersistentDataType.INTEGER, rank);
            } else {
                data.remove(key(part));
            }
        }
        stack.setItemMeta(meta);
    }
}
