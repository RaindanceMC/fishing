package de.raindancer.modules.fishing.service;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.store.FishingPack;
import de.raindancer.modules.fishing.visual.Looks;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sea chests standing in the world. One is placed like any chest — so claims and protection decide where — and then
 * becomes a barrier block wearing its tier's model on a display: solid, blast-proof and holding nothing a hopper could
 * take, with no plain chest showing through. Its tier and owner are kept in the chunk's own data under its position;
 * the display is not saved with the world, so every chunk that comes in is dressed again.
 *
 * <p>The owner opens it with a right click, which plays the show, or takes it back with a sneaking left click.
 */
public final class SeaChestBlocks implements IFishingService {

    private static final NamespacedKey DISPLAY = new NamespacedKey(FishingPack.NAMESPACE, "sea_chest_display");
    private static final float SCALE = 1.03f;

    private final Plugin plugin;
    private final Server server;
    private final Map<String, UUID> displays = new ConcurrentHashMap<>();

    public SeaChestBlocks(Plugin plugin, Server server) {
        this.plugin = plugin;
        this.server = server;
    }

    @Override
    public void settings(FishingSettings settings) {
        // A placed chest is what it was placed as; nothing here reads the settings.
    }

    private static String spot(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private static NamespacedKey keyOf(Block block) {
        // a key may hold '-', so negative coordinates are written as they are
        return new NamespacedKey(FishingPack.NAMESPACE, "sea_chest_" + block.getX() + "_" + block.getY() + "_"
                + block.getZ());
    }

    /** {@code tier|owner|facing}, or nothing when this block is no sea chest. */
    private static Optional<String[]> record(Block block) {
        if (block.getType() != Material.BARRIER) {
            return Optional.empty();
        }
        String value = block.getChunk().getPersistentDataContainer().get(keyOf(block), PersistentDataType.STRING);
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split("\\|");
        return parts.length == 3 ? Optional.of(parts) : Optional.empty();
    }

    public Optional<Tier> tierOf(Block block) {
        return record(block).flatMap(parts -> Tier.parse(parts[0]));
    }

    public Optional<UUID> ownerOf(Block block) {
        return record(block).flatMap(parts -> {
            try {
                return Optional.of(UUID.fromString(parts[1]));
            } catch (IllegalArgumentException broken) {
                return Optional.empty();
            }
        });
    }

    public float yawOf(Block block) {
        return record(block).map(parts -> yaw(BlockFace.valueOf(parts[2]))).orElse(0f);
    }

    /** A chest item was just placed here by {@code owner}: it becomes a sea chest. */
    public void placed(Block block, UUID owner, Tier tier) {
        BlockFace facing = block.getBlockData() instanceof org.bukkit.block.data.type.Chest data
                ? data.getFacing() : BlockFace.SOUTH;
        block.setType(Material.BARRIER, false);
        block.getChunk().getPersistentDataContainer().set(keyOf(block), PersistentDataType.STRING,
                tier.id() + "|" + owner + "|" + facing.name());
        dress(block, tier, yaw(facing));
    }

    /** Which way the chest looks, as the radians the model is turned by (its front is north). */
    static float yaw(BlockFace facing) {
        return switch (facing) {
            case SOUTH -> (float) Math.PI;
            case EAST -> (float) (-Math.PI / 2);
            case WEST -> (float) (Math.PI / 2);
            default -> 0f;
        };
    }

    private void dress(Block block, Tier tier, float yaw) {
        undress(block);
        Location centre = block.getLocation().add(0.5, 0.5, 0.5);
        ItemDisplay display = block.getWorld().spawn(centre, ItemDisplay.class, made -> {
            made.setPersistent(false);
            made.setItemStack(Looks.wearing(FishingPack.chestModel(tier.chest(), "")));
            made.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            made.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(yaw),
                    new Vector3f(SCALE), new Quaternionf()));
            if (tier.atLeast(Tier.EPIC)) {
                made.setBrightness(new Display.Brightness(15, 15));
            }
            made.getPersistentDataContainer().set(DISPLAY, PersistentDataType.BYTE, (byte) 1);
        });
        displays.put(spot(block), display.getUniqueId());
    }

    /** Takes the chest's look away (the block stays). */
    public void undress(Block block) {
        UUID id = displays.remove(spot(block));
        if (id != null) {
            Entity entity = server.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    /** The chest is being opened or taken back: its look, its record and its block go. */
    public void takeAway(Block block) {
        undress(block);
        block.getChunk().getPersistentDataContainer().remove(keyOf(block));
        block.setType(Material.AIR, false);
    }

    /** A chunk came in: displays left over from a crash go, and every sea chest in it is dressed again. */
    public void loaded(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof ItemDisplay && entity.getPersistentDataContainer().has(DISPLAY)) {
                entity.remove();
            }
        }
        forEachIn(chunk, block -> record(block).ifPresent(parts -> Tier.parse(parts[0]).ifPresent(tier ->
                dress(block, tier, yaw(BlockFace.valueOf(parts[2]))))));
    }

    public void unloaded(Chunk chunk) {
        forEachIn(chunk, this::undress);
    }

    private static void forEachIn(Chunk chunk, java.util.function.Consumer<Block> each) {
        for (NamespacedKey key : chunk.getPersistentDataContainer().getKeys()) {
            if (!key.getNamespace().equals(FishingPack.NAMESPACE) || !key.getKey().startsWith("sea_chest_")) {
                continue;
            }
            String[] xyz = key.getKey().substring("sea_chest_".length()).split("_");
            if (xyz.length != 3) {
                continue;
            }
            try {
                each.accept(chunk.getWorld().getBlockAt(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]),
                        Integer.parseInt(xyz[2])));
            } catch (NumberFormatException broken) {
                // a hand-edited key; nothing to dress
            }
        }
    }

    /** Every chunk already loaded when the module starts, each on its own region's thread. */
    public void start() {
        for (World world : server.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                Scheduling.region(plugin, new Location(world, chunk.getX() << 4, 0, chunk.getZ() << 4), () -> {
                    if (chunk.isLoaded()) {
                        loaded(chunk);
                    }
                });
            }
        }
    }

    /** Takes every look down — the module is stopping, so this runs where it is called. */
    public void stop() {
        for (UUID id : displays.values()) {
            Entity entity = server.getEntity(id);
            if (entity != null) {
                try {
                    entity.remove();
                } catch (RuntimeException notOnItsThread) {
                    // not saved with the world; the next start's chunk loads clear it
                }
            }
        }
        displays.clear();
    }
}
