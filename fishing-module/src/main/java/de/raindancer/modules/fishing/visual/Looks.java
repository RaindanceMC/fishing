package de.raindancer.modules.fishing.visual;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.List;

/** Item stacks that exist only to wear a model on a display. */
public final class Looks {

    private Looks() {
    }

    public static ItemStack wearing(String itemModel) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString(itemModel));
        stack.setItemMeta(meta);
        return stack;
    }

    /** The same, tinted: the model's {@code custom_model_data} tint reads this colour. */
    public static ItemStack wearing(String itemModel, int rgb) {
        ItemStack stack = wearing(itemModel);
        ItemMeta meta = stack.getItemMeta();
        CustomModelDataComponent data = meta.getCustomModelDataComponent();
        data.setColors(List.of(org.bukkit.Color.fromRGB(rgb & 0xFFFFFF)));
        meta.setCustomModelDataComponent(data);
        stack.setItemMeta(meta);
        return stack;
    }
}
