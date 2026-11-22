package de.raindancer.modules.fishing.listener;

import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.screen.MerchantMenu;
import de.raindancer.modules.fishing.service.MerchantService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

/** A right click on a fishmonger opens his counter. */
public final class MerchantListener implements IFishingListener {

    private final FishingServices services;

    public MerchantListener(FishingServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !MerchantService.isMerchant(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        new MerchantMenu(services, event.getPlayer()).open();
    }

    @Override
    public void forget(UUID player) {
        // Nothing remembered: every visit reads the bag afresh.
    }
}
