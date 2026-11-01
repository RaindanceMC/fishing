package de.raindancer.modules.fishing.listener;

import de.raindancer.core.ui.effect.Cues;
import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.model.RodTier;
import org.bukkit.Keyed;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Optional;
import java.util.UUID;

/** A rod comes off the crafting table only for a fisher who unlocked it; joining puts their rod recipes in the book. */
public final class RodListener implements IFishingListener {

    private final FishingServices services;

    public RodListener(FishingServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        Optional<RodTier> rod = rodOf(event.getRecipe());
        if (rod.isPresent() && !unlocked(event.getView().getPlayer(), rod.get())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        Optional<RodTier> rod = rodOf(event.getRecipe());
        if (rod.isPresent() && !unlocked(event.getWhoClicked(), rod.get())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                services.messages().send(player, "fishing.rods.not-yours", "rod", rod.get().title());
                services.effects().play(player.getUniqueId(), Cues.NO);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        services.workshop().discover(event.getPlayer());
    }

    private Optional<RodTier> rodOf(org.bukkit.inventory.Recipe recipe) {
        return recipe instanceof Keyed keyed ? services.workshop().rodMadeBy(keyed.getKey()) : Optional.empty();
    }

    private boolean unlocked(HumanEntity who, RodTier rod) {
        return services.workshop().hasUnlocked(who.getUniqueId(), rod);
    }

    @Override
    public void forget(UUID player) {
        // Nothing remembered per player.
    }
}
