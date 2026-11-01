package de.raindancer.modules.fishing.listener;

import de.raindancer.core.ui.effect.Cues;
import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.util.PermissionNodes;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

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

    /** A sea chest from before 0.1.1 is still a chest underneath and is placed by the game itself. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Tier tier = services.forge().chestOf(event.getItemInHand()).orElse(null);
        if (tier == null || event.getBlockPlaced().getType() != Material.CHEST) {
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
        Tier held = services.forge().chestOf(event.getItem()).orElse(null);
        if (held == null) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            services.messages().send(player, "fishing.chest.place-it");
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null
                && event.getItem().getType() != Material.CHEST) {
            event.setCancelled(true);
            setDown(player, block, event.getBlockFace(), held, event.getItem());
        }
    }

    /**
     * Sets a sea chest down against {@code clicked}, as the game would a block: into a free (or replaceable) spot,
     * not into anybody, and only where a {@link BlockPlaceEvent} for it is let through — claims decide as for any
     * block. Its front faces the player.
     */
    private void setDown(Player player, Block clicked, BlockFace face, Tier tier, ItemStack item) {
        Block target = clicked.isReplaceable() ? clicked : clicked.getRelative(face);
        if (!target.isReplaceable() || target.isLiquid() && target.getType() == Material.LAVA
                || !target.getWorld().getNearbyEntities(BoundingBox.of(target),
                        entity -> entity instanceof org.bukkit.entity.LivingEntity).isEmpty()) {
            return;
        }
        BlockPlaceEvent check = new BlockPlaceEvent(target, target.getState(), clicked, item, player, true,
                EquipmentSlot.HAND);
        if (!check.callEvent()) {
            return;
        }
        services.seaChests().placed(target, player.getUniqueId(), tier, player.getFacing().getOppositeFace());
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
        player.swingMainHand();
        services.effects().play(player.getUniqueId(), Cues.CLICK);
        services.messages().send(player, "fishing.chest.placed", "chest", tier.chestName());
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
