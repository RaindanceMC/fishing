package de.raindancer.modules.fishing.listener;

import de.raindancer.core.ui.effect.Cues;
import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.model.Aquarium;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aquariums: set down like a block (claims decide where), a fish put in with a right click, taken out with a sneaking
 * right click of an empty hand, the tank picked up with a sneaking left click — all by its owner. Anybody else who
 * right clicks it is told whose it is and what swims in it.
 */
public final class AquariumListener implements IFishingListener {

    private static final de.raindancer.core.ui.effect.Effect SPLASH = de.raindancer.core.ui.effect.Effect.of(
            new de.raindancer.core.ui.effect.SoundCue("entity.fish.swim", 1f, 1f));

    private final FishingServices services;

    public AquariumListener(FishingServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Optional<Aquarium> tank = block == null ? Optional.empty() : services.aquariums().at(block);
        if (tank.isPresent()) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                use(player, block, tank.get(), event.getItem());
            } else if (event.getAction() == Action.LEFT_CLICK_BLOCK && player.isSneaking()) {
                event.setCancelled(true);
                pickUp(player, block, tank.get());
            }
            return;
        }
        if (!services.forge().isAquarium(event.getItem())) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null) {
            event.setCancelled(true);
            setDown(player, block, event.getBlockFace(), event.getItem());
        }
    }

    private void use(Player player, Block block, Aquarium tank, ItemStack held) {
        if (!tank.owner().equals(player.getUniqueId())) {
            String fish = tank.residents().isEmpty() ? "nothing yet" : tank.residents().stream()
                    .map(this::name).collect(Collectors.joining(", "));
            services.messages().send(player, "fishing.aquarium.someone-elses", "owner",
                    nameOf(tank.owner()), "fish", fish);
            return;
        }
        Optional<Aquarium.Resident> resident = services.aquariums().resident(held);
        if (resident.isPresent()) {
            if (tank.full()) {
                services.messages().send(player, "fishing.aquarium.full", "most", String.valueOf(Aquarium.CAPACITY));
                services.effects().play(player.getUniqueId(), Cues.NO);
                return;
            }
            held.setAmount(held.getAmount() - 1);
            services.aquariums().write(block, tank.with(resident.get()));
            services.effects().play(player.getUniqueId(), Cues.CLICK);
            services.effects().playAt(block.getWorld().getName(), block.getX() + 0.5, block.getY() + 0.5,
                    block.getZ() + 0.5, SPLASH);
            services.messages().send(player, "fishing.aquarium.added", "fish", name(resident.get()));
            return;
        }
        if (player.isSneaking() && (held == null || held.getType().isAir())) {
            Optional<Aquarium.Resident> last = tank.last();
            if (last.isEmpty()) {
                services.messages().send(player, "fishing.aquarium.empty");
                return;
            }
            services.aquariums().write(block, tank.withoutLast());
            give(player, last.get());
            services.effects().play(player.getUniqueId(), Cues.CLICK);
            return;
        }
        services.messages().send(player, "fishing.aquarium.how");
    }

    private void pickUp(Player player, Block block, Aquarium tank) {
        if (!tank.owner().equals(player.getUniqueId())) {
            services.messages().send(player, "fishing.aquarium.not-yours");
            services.effects().play(player.getUniqueId(), Cues.NO);
            return;
        }
        services.aquariums().takeAway(block);
        tank.residents().forEach(resident -> give(player, resident));
        give(player, services.forge().aquarium());
        services.effects().play(player.getUniqueId(), Cues.CLICK);
    }

    private void give(Player player, Aquarium.Resident resident) {
        services.aquariums().item(resident).ifPresent(item -> give(player, item));
    }

    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    /** Set down against {@code clicked} as the game would a block, only where a placement is let through. */
    private void setDown(Player player, Block clicked, BlockFace face, ItemStack item) {
        Block target = clicked.isReplaceable() ? clicked : clicked.getRelative(face);
        if (!target.isReplaceable() || target.getType() == Material.LAVA
                || !target.getWorld().getNearbyEntities(BoundingBox.of(target),
                        entity -> entity instanceof org.bukkit.entity.LivingEntity).isEmpty()) {
            return;
        }
        BlockPlaceEvent check = new BlockPlaceEvent(target, target.getState(), clicked, item, player, true,
                EquipmentSlot.HAND);
        if (!check.callEvent()) {
            return;
        }
        services.aquariums().placed(target, player.getUniqueId());
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
        player.swingMainHand();
        services.effects().play(player.getUniqueId(), Cues.CLICK);
        services.messages().send(player, "fishing.aquarium.placed");
    }

    /** A tank is never broken like a block: the fish in it would be lost. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (services.aquariums().at(event.getBlock()).isPresent()) {
            event.setCancelled(true);
            services.messages().send(event.getPlayer(), "fishing.aquarium.how");
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        services.aquariums().loaded(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        services.aquariums().unloaded(event.getChunk());
    }

    private String name(Aquarium.Resident resident) {
        String kind = services.catalog().fish(resident.fishId()).map(each -> each.name()).orElse(resident.fishId());
        return resident.mutation() == null ? kind : resident.mutation().title() + " " + kind;
    }

    private String nameOf(UUID owner) {
        String name = services.server().getOfflinePlayer(owner).getName();
        return name == null ? "someone" : name;
    }

    @Override
    public void forget(UUID player) {
        // Nothing remembered per player: a tank's owner is in the tank.
    }
}
