package de.raindancer.modules.fishing.service;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.world.protection.Land;
import de.raindancer.core.world.protection.LandAction;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.Aquarium;
import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.TankPos;
import de.raindancer.modules.fishing.rules.TankRule;
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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aquariums standing in the world, kept like sea chests: a barrier block wearing a tank model on a display, its fish
 * in the chunk's own data under its position. Tanks side by side (not stacked) are one big tank ({@link TankRule}): each block wears
 * the look for the sides it is joined on, its fish swim through the whole tank, and the tank opens as one inventory.
 * Every block still keeps its own three fish, so picking one block up gives back what it held. None of the displays is
 * saved with the world; a chunk coming in dresses its tanks again.
 *
 * <p>Who may open a tank is who may open a chest on that ground; who may pick one up is who may break a block there.
 */
public final class AquariumService implements IFishingService {

    private static final NamespacedKey DISPLAY = new NamespacedKey(FishingPack.NAMESPACE, "aquarium_display");
    private static final String PREFIX = "aquarium_";
    private static final int SWIM_TICKS = 30;
    private static final float FISH_SCALE = 0.32f;

    /** The looks of one block and the timer moving its fish. */
    private record Dressed(UUID tank, List<UUID> fish, ScheduledTask swim) {
    }

    /** One fish's place in a whole tank: which block holds it, and where in that block's list. */
    public record Slot(Block block, int index, Aquarium.Resident resident) {
    }

    private final Plugin plugin;
    private final Server server;
    private final FishForge forge;
    private final Catalog catalog;
    private final Land land;
    private final TankRule rule = new TankRule();
    private final Random random = new Random();
    private final Map<String, Dressed> dressed = new ConcurrentHashMap<>();
    /** The block of its tank each fish is swimming in now, relative to its own block. */
    private final Map<UUID, TankPos> swimmingIn = new ConcurrentHashMap<>();
    private NamespacedKey recipe;

    public AquariumService(Plugin plugin, Server server, FishForge forge, Catalog catalog, Land land) {
        this.plugin = plugin;
        this.server = server;
        this.forge = forge;
        this.catalog = catalog;
        this.land = land;
    }

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

    public void discover(Player player) {
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

    // ------------------------------------------------------------------ who may

    /** Whoever may open a chest standing here. */
    public boolean mayOpen(Player player, Block block) {
        return may(player, block, LandAction.CONTAINERS);
    }

    /** Whoever may break a block here. */
    public boolean mayPickUp(Player player, Block block) {
        return may(player, block, LandAction.BREAK);
    }

    private boolean may(Player player, Block block, LandAction action) {
        return land == null || land.areaAt(block.getLocation()).map(area -> area.may(player.getUniqueId(), action))
                .orElse(true);
    }

    // ------------------------------------------------------------------ records

    private static String spot(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private static NamespacedKey keyOf(Block block) {
        return new NamespacedKey(FishingPack.NAMESPACE, PREFIX + block.getX() + "_" + block.getY() + "_" + block.getZ());
    }

    private static TankPos pos(Block block) {
        return new TankPos(block.getX(), block.getY(), block.getZ());
    }

    public Optional<Aquarium> at(Block block) {
        if (block.getType() != Material.BARRIER) {
            return Optional.empty();
        }
        return Aquarium.parse(block.getChunk().getPersistentDataContainer().get(keyOf(block),
                PersistentDataType.STRING));
    }

    /** Whether a tank stands there — never loading a chunk to find out. */
    private boolean isTank(World world, TankPos at) {
        return world.isChunkLoaded(at.x() >> 4, at.z() >> 4) && at(world.getBlockAt(at.x(), at.y(), at.z())).isPresent();
    }

    /** The blocks of the tank this block is part of, in slot order. */
    public List<Block> group(Block block) {
        World world = block.getWorld();
        return rule.group(pos(block), at -> isTank(world, at)).stream()
                .map(at -> world.getBlockAt(at.x(), at.y(), at.z())).toList();
    }

    /** A tank is set down here by {@code owner}, empty, and joins the tanks beside it. */
    public void placed(Block block, UUID owner) {
        block.setType(Material.BARRIER, false);
        block.getChunk().getPersistentDataContainer().set(keyOf(block), PersistentDataType.STRING,
                Aquarium.empty(owner).format());
        redress(block);
    }

    /** Writes one block's fish and dresses its whole tank as it now is. */
    public void write(Block block, Aquarium tank) {
        block.getChunk().getPersistentDataContainer().set(keyOf(block), PersistentDataType.STRING, tank.format());
        redress(block);
    }

    /**
     * One block is taken back: its looks, its record and its block go, and the tanks it joined are dressed again,
     * maybe now as two. What swam in it is the caller's to give.
     */
    public void takeAway(Block block) {
        List<Block> beside = new ArrayList<>();
        for (int[] side : new int[][]{{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}}) {
            Block next = block.getRelative(side[0], side[1], side[2]);
            if (at(next).isPresent()) {
                beside.add(next);
            }
        }
        undress(block);
        block.getChunk().getPersistentDataContainer().remove(keyOf(block));
        block.setType(Material.AIR, false);
        Set<String> done = new HashSet<>();
        for (Block next : beside) {
            if (!done.contains(spot(next))) {
                redress(next).forEach(each -> done.add(spot(each)));
            }
        }
    }

    // ------------------------------------------------------------------ the whole tank as one inventory

    /** Every fish in the tank this block is part of, block by block. */
    public List<Slot> contents(Block any) {
        List<Slot> slots = new ArrayList<>();
        for (Block block : group(any)) {
            List<Aquarium.Resident> residents = at(block).map(Aquarium::residents).orElse(List.of());
            for (int index = 0; index < residents.size(); index++) {
                slots.add(new Slot(block, index, residents.get(index)));
            }
        }
        return slots;
    }

    /** How many fish the whole tank holds at most. */
    public int capacity(Block any) {
        return group(any).size() * Aquarium.CAPACITY;
    }

    /**
     * Takes the fish shown in {@code slot} out, if it is still there — someone else may have taken it meanwhile.
     *
     * @return the fish taken, as an item
     */
    public Optional<ItemStack> take(Slot slot) {
        Optional<Aquarium> now = at(slot.block());
        if (now.isEmpty() || slot.index() >= now.get().residents().size()
                || !now.get().residents().get(slot.index()).equals(slot.resident())) {
            return Optional.empty();
        }
        Optional<ItemStack> item = item(slot.resident());
        if (item.isPresent()) {
            write(slot.block(), now.get().without(slot.index()));
        }
        return item;
    }

    /** Puts a fish into the first block of the tank with room; false when the whole tank is full. */
    public boolean put(Block any, Aquarium.Resident resident) {
        for (Block block : group(any)) {
            Optional<Aquarium> tank = at(block);
            if (tank.isPresent() && !tank.get().full()) {
                write(block, tank.get().with(resident));
                return true;
            }
        }
        return false;
    }

    /** The item a resident is, to give back. */
    public Optional<ItemStack> item(Aquarium.Resident resident) {
        return catalog.fish(resident.fishId()).map(kind -> forge.fish(kind, 1, resident.mutation()));
    }

    /** What a fish item would be in a tank, if it is one of this module's fish. */
    public Optional<Aquarium.Resident> resident(ItemStack stack) {
        return forge.fishOf(stack).map(kind -> new Aquarium.Resident(kind.id(), forge.mutationOf(stack).orElse(null)));
    }

    // ------------------------------------------------------------------ looks

    /** Dresses every block of this block's tank; the blocks dressed. */
    private List<Block> redress(Block block) {
        List<Block> members = group(block);
        Set<TankPos> joined = new HashSet<>();
        members.forEach(each -> joined.add(pos(each)));
        for (Block member : members) {
            at(member).ifPresent(tank -> dress(member, tank, joined));
        }
        return members;
    }

    private void dress(Block block, Aquarium tank, Set<TankPos> joined) {
        undress(block);
        World world = block.getWorld();
        TankPos home = pos(block);
        Location centre = block.getLocation().add(0.5, 0.5, 0.5);
        int look = rule.look(home, joined::contains);
        ItemDisplay glass = world.spawn(centre, ItemDisplay.class, made -> {
            prepare(made);
            made.setItemStack(Looks.wearing(FishingPack.NAMESPACE + ":aquarium_" + look));
        });
        List<ItemDisplay> swimmers = new ArrayList<>();
        for (Aquarium.Resident resident : tank.residents()) {
            Optional<ItemStack> fishLook = item(resident);
            if (fishLook.isEmpty()) {
                continue;
            }
            boolean glows = catalog.fish(resident.fishId()).map(FishKind::glows).orElse(false);
            swimmers.add(world.spawn(centre, ItemDisplay.class, made -> {
                prepare(made);
                made.setItemStack(fishLook.get());
                made.setTransformation(pose(point(new TankPos(0, 0, 0)), random.nextFloat() * 6.28f));
                if (glows) {
                    made.setBrightness(new Display.Brightness(15, 15));
                }
            }));
        }
        List<TankPos> others = new ArrayList<>();
        joined.forEach(each -> others.add(new TankPos(each.x() - home.x(), each.y() - home.y(), each.z() - home.z())));
        ScheduledTask swim = swimmers.isEmpty() ? null : Scheduling.regionTimer(plugin, centre, SWIM_TICKS,
                SWIM_TICKS, task -> swimmers.forEach(fish -> swim(fish, others)));
        dressed.put(spot(block), new Dressed(glass.getUniqueId(),
                swimmers.stream().map(Entity::getUniqueId).toList(), swim));
    }

    private static void prepare(ItemDisplay display) {
        display.setPersistent(false);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        display.getPersistentDataContainer().set(DISPLAY, PersistentDataType.BYTE, (byte) 1);
    }

    /**
     * Off to a new point, in the block it is in or one beside it within the tank, turned to face the way it swims,
     * gliding there over the next beat.
     *
     * @param tank every block of the tank, relative to the fish's own block
     */
    private void swim(ItemDisplay fish, List<TankPos> tank) {
        if (!fish.isValid()) {
            swimmingIn.remove(fish.getUniqueId());
            return;
        }
        TankPos in = swimmingIn.getOrDefault(fish.getUniqueId(), new TankPos(0, 0, 0));
        if (!tank.contains(in)) {
            in = new TankPos(0, 0, 0);
        }
        TankPos current = in;
        List<TankPos> beside = tank.stream().filter(each -> Math.abs(each.x() - current.x())
                + Math.abs(each.y() - current.y()) + Math.abs(each.z() - current.z()) == 1).toList();
        TankPos next = beside.isEmpty() || random.nextBoolean() ? in : beside.get(random.nextInt(beside.size()));
        swimmingIn.put(fish.getUniqueId(), next);
        Vector3f from = fish.getTransformation().getTranslation();
        Vector3f to = point(next);
        // a fish model's head points to -x: turn -x onto the way it swims
        float yaw = (float) Math.atan2(to.z - from.z, -(to.x - from.x));
        fish.setInterpolationDelay(0);
        fish.setInterpolationDuration(SWIM_TICKS);
        fish.setTransformation(pose(to, yaw));
    }

    /** A point inside the water of the block at {@code in}, clear of the glass and the sand. */
    private Vector3f point(TankPos in) {
        return new Vector3f(in.x() - 0.3f + random.nextFloat() * 0.6f,
                in.y() - 0.15f + random.nextFloat() * 0.45f, in.z() - 0.3f + random.nextFloat() * 0.6f);
    }

    private static Transformation pose(Vector3f at, float yaw) {
        return new Transformation(at, new Quaternionf().rotateY(yaw), new Vector3f(FISH_SCALE), new Quaternionf());
    }

    /** Takes the block's looks away (the block stays). */
    public void undress(Block block) {
        Dressed gone = dressed.remove(spot(block));
        if (gone == null) {
            return;
        }
        if (gone.swim() != null) {
            gone.swim().cancel();
        }
        remove(gone.tank());
        gone.fish().forEach(id -> {
            swimmingIn.remove(id);
            remove(id);
        });
    }

    private void remove(UUID id) {
        Entity entity = server.getEntity(id);
        if (entity != null) {
            entity.remove();
        }
    }

    /** A chunk came in: displays left over from a crash go, and every tank in it is dressed again, once. */
    public void loaded(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof ItemDisplay && entity.getPersistentDataContainer().has(DISPLAY)) {
                entity.remove();
            }
        }
        Set<String> done = new HashSet<>();
        Set<Block> here = new LinkedHashSet<>();
        forEachIn(chunk, block -> {
            if (at(block).isPresent()) {
                here.add(block);
            }
        });
        for (Block block : here) {
            if (!done.contains(spot(block))) {
                redress(block).forEach(each -> done.add(spot(each)));
            }
        }
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
        swimmingIn.clear();
    }
}
