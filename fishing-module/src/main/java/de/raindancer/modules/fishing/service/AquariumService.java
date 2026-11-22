package de.raindancer.modules.fishing.service;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.Aquarium;
import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.store.FishingPack;
import de.raindancer.modules.fishing.visual.Looks;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aquariums standing in the world, kept like sea chests: a barrier block wearing the tank's model on a display, its
 * owner and fish in the chunk's own data under its position. Up to three fish swim in each, every one a small display
 * of the fish item, drifting from point to point inside the water; none of the displays is saved with the world, so a
 * chunk coming in dresses its tanks again.
 */
public final class AquariumService implements IFishingService {

    private static final NamespacedKey DISPLAY = new NamespacedKey(FishingPack.NAMESPACE, "aquarium_display");
    private static final String PREFIX = "aquarium_";
    private static final String MODEL = FishingPack.NAMESPACE + ":aquarium";
    private static final int SWIM_TICKS = 30;
    private static final float FISH_SCALE = 0.32f;

    /** The looks of one tank and the timer moving its fish. */
    private record Dressed(UUID tank, List<UUID> fish, ScheduledTask swim) {
    }

    private final Plugin plugin;
    private final Server server;
    private final FishForge forge;
    private final Catalog catalog;
    private final Random random = new Random();
    private final Map<String, Dressed> dressed = new ConcurrentHashMap<>();

    public AquariumService(Plugin plugin, Server server, FishForge forge, Catalog catalog) {
        this.plugin = plugin;
        this.server = server;
        this.forge = forge;
        this.catalog = catalog;
    }

    private NamespacedKey recipe;

    @Override
    public void settings(FishingSettings settings) {
        // A tank is what was put in it; nothing here reads the settings.
    }

    /** Glass all round, a water bucket inside, sand and planks underneath. */
    public int registerRecipe() {
        org.bukkit.inventory.ShapedRecipe shaped = new org.bukkit.inventory.ShapedRecipe(
                new NamespacedKey(plugin, "fishing_aquarium"), forge.aquarium());
        shaped.shape("GGG", "GWG", "PSP");
        shaped.setIngredient('G', Material.GLASS);
        shaped.setIngredient('W', Material.WATER_BUCKET);
        shaped.setIngredient('S', Material.SAND);
        shaped.setIngredient('P', new org.bukkit.inventory.RecipeChoice.MaterialChoice(org.bukkit.Tag.PLANKS));
        shaped.setCategory(org.bukkit.inventory.recipe.CraftingBookCategory.MISC);
        if (server.addRecipe(shaped, false)) {
            recipe = shaped.getKey();
            server.updateRecipes();
            return 1;
        }
        return 0;
    }

    public void discover(org.bukkit.entity.Player player) {
        if (recipe != null) {
            player.discoverRecipe(recipe);
        }
    }

    public void unregisterRecipe() {
        if (recipe != null) {
            server.removeRecipe(recipe, false);
            recipe = null;
            server.updateRecipes();
        }
    }

    private static String spot(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private static NamespacedKey keyOf(Block block) {
        return new NamespacedKey(FishingPack.NAMESPACE, PREFIX + block.getX() + "_" + block.getY() + "_" + block.getZ());
    }

    public Optional<Aquarium> at(Block block) {
        if (block.getType() != Material.BARRIER) {
            return Optional.empty();
        }
        return Aquarium.parse(block.getChunk().getPersistentDataContainer().get(keyOf(block),
                PersistentDataType.STRING));
    }

    /** A tank is set down here by {@code owner}, empty. */
    public void placed(Block block, UUID owner) {
        block.setType(Material.BARRIER, false);
        write(block, Aquarium.empty(owner));
    }

    /** Writes the tank and dresses it as it now is. */
    public void write(Block block, Aquarium tank) {
        block.getChunk().getPersistentDataContainer().set(keyOf(block), PersistentDataType.STRING, tank.format());
        dress(block, tank);
    }

    /** The tank is taken back: its looks, its record and its block go. What swam in it is the caller's to give. */
    public void takeAway(Block block) {
        undress(block);
        block.getChunk().getPersistentDataContainer().remove(keyOf(block));
        block.setType(Material.AIR, false);
    }

    /** The item a resident is, to give back. */
    public Optional<ItemStack> item(Aquarium.Resident resident) {
        return catalog.fish(resident.fishId()).map(kind -> forge.fish(kind, 1, resident.mutation()));
    }

    /** What a fish item would be in a tank, if it is one of this module's fish. */
    public Optional<Aquarium.Resident> resident(ItemStack stack) {
        return forge.fishOf(stack).map(kind -> new Aquarium.Resident(kind.id(), forge.mutationOf(stack).orElse(null)));
    }

    private void dress(Block block, Aquarium tank) {
        undress(block);
        World world = block.getWorld();
        Location centre = block.getLocation().add(0.5, 0.5, 0.5);
        ItemDisplay glass = world.spawn(centre, ItemDisplay.class, made -> {
            prepare(made);
            made.setItemStack(Looks.wearing(MODEL));
        });
        List<ItemDisplay> swimmers = new ArrayList<>();
        for (Aquarium.Resident resident : tank.residents()) {
            Optional<ItemStack> look = item(resident);
            if (look.isEmpty()) {
                continue;
            }
            boolean glows = catalog.fish(resident.fishId()).map(FishKind::glows).orElse(false);
            swimmers.add(world.spawn(centre, ItemDisplay.class, made -> {
                prepare(made);
                made.setItemStack(look.get());
                made.setTransformation(pose(point(), random.nextFloat() * 6.28f));
                if (glows) {
                    made.setBrightness(new Display.Brightness(15, 15));
                }
            }));
        }
        List<UUID> ids = swimmers.stream().map(Entity::getUniqueId).toList();
        ScheduledTask swim = swimmers.isEmpty() ? null
                : Scheduling.regionTimer(plugin, centre, SWIM_TICKS, SWIM_TICKS, task -> swimmers.forEach(this::swim));
        dressed.put(spot(block), new Dressed(glass.getUniqueId(), ids, swim));
    }

    private static void prepare(ItemDisplay display) {
        display.setPersistent(false);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        display.getPersistentDataContainer().set(DISPLAY, PersistentDataType.BYTE, (byte) 1);
    }

    /** Off to a new point in the water, turned to face the way it swims, gliding there over the next beat. */
    private void swim(ItemDisplay fish) {
        if (!fish.isValid()) {
            return;
        }
        Vector3f from = fish.getTransformation().getTranslation();
        Vector3f to = point();
        float dx = to.x - from.x;
        float dz = to.z - from.z;
        // a fish model's head points to -x: turn -x onto the way it swims
        float yaw = (float) Math.atan2(dz, -dx);
        fish.setInterpolationDelay(0);
        fish.setInterpolationDuration(SWIM_TICKS);
        fish.setTransformation(pose(to, yaw));
    }

    /** A point inside the water, clear of the glass and the sand. */
    private Vector3f point() {
        return new Vector3f(-0.3f + random.nextFloat() * 0.6f, -0.15f + random.nextFloat() * 0.45f,
                -0.3f + random.nextFloat() * 0.6f);
    }

    private static Transformation pose(Vector3f at, float yaw) {
        return new Transformation(at, new Quaternionf().rotateY(yaw), new Vector3f(FISH_SCALE), new Quaternionf());
    }

    /** Takes the tank's looks away (the block stays). */
    public void undress(Block block) {
        Dressed gone = dressed.remove(spot(block));
        if (gone == null) {
            return;
        }
        if (gone.swim() != null) {
            gone.swim().cancel();
        }
        remove(gone.tank());
        gone.fish().forEach(this::remove);
    }

    private void remove(UUID id) {
        Entity entity = server.getEntity(id);
        if (entity != null) {
            entity.remove();
        }
    }

    /** A chunk came in: displays left over from a crash go, and every tank in it is dressed again. */
    public void loaded(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof ItemDisplay && entity.getPersistentDataContainer().has(DISPLAY)) {
                entity.remove();
            }
        }
        forEachIn(chunk, block -> at(block).ifPresent(tank -> dress(block, tank)));
    }

    public void unloaded(Chunk chunk) {
        forEachIn(chunk, this::undress);
    }

    private static void forEachIn(Chunk chunk, java.util.function.Consumer<Block> each) {
        for (NamespacedKey key : chunk.getPersistentDataContainer().getKeys()) {
            if (!key.getNamespace().equals(FishingPack.NAMESPACE) || !key.getKey().startsWith(PREFIX)) {
                continue;
            }
            String[] xyz = key.getKey().substring(PREFIX.length()).split("_");
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
        for (Dressed each : dressed.values()) {
            if (each.swim() != null) {
                each.swim().cancel();
            }
            try {
                remove(each.tank());
                each.fish().forEach(this::remove);
            } catch (RuntimeException notOnItsThread) {
                // not saved with the world; the next start's chunk loads clear them
            }
        }
        dressed.clear();
    }
}
