package de.raindancer.modules.fishing.listener;

import de.raindancer.core.ui.effect.Cues;
import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.util.PermissionNodes;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

/**
 * A sea chest is placed like any chest — protection decides where — and then stands there as itself. Its owner
 * opens it with a right click and takes it back with a sneaking left click.
 */
public final class ChestListener implements IFishingListener {

    private final FishingServices services;

    public ChestListener(FishingServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Tier tier = services.forge().chestOf(event.getItemInHand()).orElse(null);
        if (tier == null) {
            return;
        }
        services.seaChests().placed(event.getBlockPlaced(), event.getPlayer().getUniqueId(), tier);
        services.messages().send(event.getPlayer(), "fishing.chest.placed", "chest", tier.chestName());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (block != null && block.getType() == Material.BARRIER && services.seaChests().tierOf(block).isPresent()) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                if (!player.hasPermission(PermissionNodes.CHEST)) {
                    services.messages().send(player, "fishing.chest.not-allowed");
                    services.effects().play(player.getUniqueId(), Cues.NO);
                    return;
                }
                services.chests().open(player, block);
            } else if (event.getAction() == Action.LEFT_CLICK_BLOCK && player.isSneaking()) {
                event.setCancelled(true);
                services.chests().pickUp(player, block);
            }
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_AIR && services.forge().chestOf(event.getItem()).isPresent()) {
            services.messages().send(player, "fishing.chest.place-it");
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        services.seaChests().loaded(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        services.seaChests().unloaded(event.getChunk());
    }

    @Override
    public void forget(UUID player) {
        // Nothing remembered per player: the wait between chests is Core's Cooldowns.
    }
}
