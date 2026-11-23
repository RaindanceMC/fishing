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
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.Optional;
import java.util.UUID;

/**
 * Aquariums: set down like a block (claims decide where). A right click opens the whole tank like a chest, or with a
 * fish in hand puts it straight in. Mined like a glass block, it breaks and drops itself and its fish. Opening is for
 * whoever may open a chest there, breaking for whoever may break a block there.
 */
public final class AquariumListener implements IFishingListener {

    private static final de.raindancer.core.ui.effect.Effect SPLASH = de.raindancer.core.ui.effect.Effect.of(
            new de.raindancer.core.ui.effect.SoundCue("entity.fish.swim", 1f, 1f));
    private static final de.raindancer.core.ui.effect.Effect SHATTER = de.raindancer.core.ui.effect.Effect.of(
            new de.raindancer.core.ui.effect.SoundCue("block.glass.break", 1f, 1f));
    /** About as long as glass takes by hand. */
    private static final int BREAK_TICKS = 9;

    private final java.util.Map<UUID, Block> mining = new java.util.concurrent.ConcurrentHashMap<>();
    /** Tanks whose break this listener is asking the claims about right now. */
    private final java.util.Set<Block> breaking = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
                use(player, block, event.getItem());
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

    private void use(Player player, Block block, ItemStack held) {
        if (!services.aquariums().mayOpen(player, block)) {
            services.messages().send(player, "fishing.aquarium.not-allowed-open");
            services.effects().play(player.getUniqueId(), Cues.NO);
            return;
        }
        Optional<Aquarium.Resident> resident = services.aquariums().resident(held);
        if (resident.isEmpty()) {
            new de.raindancer.modules.fishing.screen.AquariumMenu(services, player, block).open();
            return;
        }
        // a fish in hand goes straight in, without opening anything
        if (!services.aquariums().put(block, resident.get())) {
            services.messages().send(player, "fishing.aquarium.full", "most",
                    String.valueOf(services.aquariums().capacity(block)));
            services.effects().play(player.getUniqueId(), Cues.NO);
            return;
        }
        held.setAmount(held.getAmount() - 1);
        services.effects().play(player.getUniqueId(), Cues.CLICK);
        services.effects().playAt(block.getWorld().getName(), block.getX() + 0.5, block.getY() + 0.5,
                block.getZ() + 0.5, SPLASH);
        services.messages().send(player, "fishing.aquarium.added", "fish", name(resident.get()));
    }

    // ------------------------------------------------------------------ breaking, like a glass block

    /** Started mining a tank: the cracks grow while they keep at it, and at the end it breaks like a block. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (services.aquariums().at(block).isEmpty() || mining.containsKey(player.getUniqueId())
                || player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        UUID id = player.getUniqueId();
        int[] ticks = {0};
        mining.put(id, block);
        de.raindancer.core.platform.util.Scheduling.entityTimer(services.plugin(), player, 1, 1, task -> {
            boolean still = block.equals(mining.get(id)) && services.aquariums().at(block).isPresent()
                    && block.equals(player.getTargetBlockExact(6));
            if (!still) {
                stopMining(id, block, player);
                task.cancel();
                return;
            }
            ticks[0]++;
            cracks(block, player, (float) ticks[0] / BREAK_TICKS);
            if (ticks[0] >= BREAK_TICKS) {
                stopMining(id, block, player);
                task.cancel();
                breakTank(player, block, true);
            }
        }, () -> mining.remove(id));
    }

    @EventHandler
    public void onAbort(BlockDamageAbortEvent event) {
        Block block = mining.get(event.getPlayer().getUniqueId());
        if (block != null && block.equals(event.getBlock())) {
            stopMining(event.getPlayer().getUniqueId(), block, event.getPlayer());
        }
    }

    private void stopMining(UUID id, Block block, Player player) {
        if (mining.remove(id, block)) {
            cracks(block, player, 0f);
        }
    }

    /** Everybody near sees the cracks, as for any block being mined. */
    private static void cracks(Block block, Player miner, float progress) {
        org.bukkit.Location at = block.getLocation();
        for (Player near : block.getWorld().getNearbyPlayers(at, 32)) {
            near.sendBlockDamage(at, Math.min(1f, progress), miner);
        }
    }

    /**
     * Breaks one tank as a block breaks: only where a block break is let through (claims decide), the tank and its
     * fish dropped where it stood, the glass sound and splinters. In creative only the fish drop, as from a chest.
     */
    private void breakTank(Player player, Block block, boolean dropTank) {
        Optional<Aquarium> tank = services.aquariums().at(block);
        if (tank.isEmpty()) {
            return;
        }
        breaking.add(block);
        try {
            if (!new BlockBreakEvent(block, player).callEvent()) {
                return;
            }
        } finally {
            breaking.remove(block);
        }
        org.bukkit.Location centre = block.getLocation().add(0.5, 0.5, 0.5);
        services.aquariums().takeAway(block);
        for (Aquarium.Resident resident : tank.get().residents()) {
            services.aquariums().item(resident).ifPresent(item -> block.getWorld().dropItemNaturally(centre, item));
        }
        if (dropTank) {
            block.getWorld().dropItemNaturally(centre, services.forge().aquarium());
        }
        services.effects().playAt(block.getWorld().getName(), centre.getX(), centre.getY(), centre.getZ(), SHATTER);
        block.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, centre, 30, 0.3, 0.3, 0.3,
                Material.GLASS.createBlockData());
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

    /**
     * A tank broken the game's own way: in creative, where a barrier goes in one click. The block break the mining
     * above fires itself is let through to the claims and nothing else here.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (breaking.contains(event.getBlock()) || services.aquariums().at(event.getBlock()).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        breakTank(event.getPlayer(), event.getBlock(), event.getPlayer().getGameMode() != GameMode.CREATIVE);
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

    @Override
    public void forget(UUID player) {
        mining.remove(player);
    }
}
