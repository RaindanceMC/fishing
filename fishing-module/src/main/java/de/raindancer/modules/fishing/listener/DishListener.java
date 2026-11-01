package de.raindancer.modules.fishing.listener;

import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.model.Dish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

/** Eating a dish gives what the dish promises; joining puts the dish recipes in the recipe book. */
public final class DishListener implements IFishingListener {

    private final FishingServices services;

    public DishListener(FishingServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        Dish dish = services.forge().dishOf(event.getItem()).orElse(null);
        if (dish != null) {
            services.dishes().eaten(event.getPlayer(), dish, event.getItem().getType());
        }
    }

    /** Every dish recipe in the recipe book from the first join on. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        services.dishes().discover(event.getPlayer());
    }

    @Override
    public void forget(UUID player) {
        // Nothing remembered per player.
    }
}
