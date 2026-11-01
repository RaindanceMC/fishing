package de.raindancer.modules.fishing.listener;

import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.screen.NetMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * A fishing net opens like a shulker box would if it could: a right click with it in hand, or a right click on it in
 * your own inventory. While it is open, nothing can be dragged onto its page (that would lay real fish on copies).
 */
public final class NetListener implements IFishingListener {

    private final FishingServices services;

    public NetListener(FishingServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack held = event.getItem();
        if (held == null || !services.forge().isNet(held)) {
            return;
        }
        event.setCancelled(true);
        open(event.getPlayer(), held);
    }

    /** A right click on a net in the player's own inventory, nothing on the cursor. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getClick() != ClickType.RIGHT
                || event.getView().getTopInventory().getType() != InventoryType.CRAFTING
                || !event.getCursor().isEmpty()) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !services.forge().isNet(clicked)) {
            return;
        }
        event.setCancelled(true);
        player.closeInventory();
        open(player, clicked);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof NetMenu)) {
            return;
        }
        int top = event.getInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < top) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void open(Player player, ItemStack net) {
        NetMenu.show(services, player, services.nets().idOf(net));
    }

    @Override
    public void forget(UUID player) {
        // Remembers nobody: a net's fish are in the database.
    }
}
