package de.raindancer.modules.fishing.listener;

import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.model.Dish;
import org.bukkit.Tag;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

/**
 * Eating a dish gives what the dish promises; joining puts the dish recipes in the recipe book. A fish of ours is
 * cooked into a dish, never on its own: it is a cod underneath, and the game's own recipes would turn a Silver
 * Herring into a cooked cod.
 */
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCampfire(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !Tag.CAMPFIRES.isTagged(event.getClickedBlock().getType())) {
            return;
        }
        services.forge().fishOf(event.getItem()).ifPresent(kind -> {
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
            services.messages().send(event.getPlayer(), "fishing.dish.cook-a-dish", "fish", kind.name());
        });
    }

    /** Furnaces, smokers and campfires reached some other way (a hopper, a dispenser) leave our fish raw. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCook(BlockCookEvent event) {
        if (services.forge().fishOf(event.getSource()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** Every dish recipe in the recipe book from the first join on. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        services.dishes().discover(event.getPlayer());
        services.baits().discover(event.getPlayer());
        services.nets().discover(event.getPlayer());
    }

    @Override
    public void forget(UUID player) {
        // Nothing remembered per player.
    }
}
