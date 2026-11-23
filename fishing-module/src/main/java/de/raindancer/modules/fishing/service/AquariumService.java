package de.raindancer.modules.fishing.service;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.world.protection.Land;
import de.raindancer.core.world.protection.LandAction;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.Aquarium;
import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.TankPos;
import de.raindancer.modules.fishing.rules.SwimRule;
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
 * the look for the sides it is joined on, all its fish share one brain ({@link SwimRule}: each kind's temper, chases
 * and schools) and swim through the whole tank, and the tank opens as one inventory.
 * Every block still keeps its own three fish, so picking one block up gives back what it held. None of the displays is
 * saved with the world; a chunk coming in dresses its tanks again.
 *
 * <p>Who may open a tank is who may open a chest on that ground; breaking one is a block break the claims decide.
 */
public final class AquariumService implements IFishingService {

    private static final NamespacedKey DISPLAY = new NamespacedKey(FishingPack.NAMESPACE, "aquarium_display");
    private static final String PREFIX = "aquarium_";
    /** A beat of the fish's brains; the client glides them between beats, so two a second look smooth. */
    private static final int SWIM_TICKS = 10;
    /** Fish swim only while somebody is this close; nobody near, a tank costs one look around per beat. */
    private static final double WATCHED = 32;
    /** Clients draw fish within about 32 blocks and tanks within about 48 (a display's range is a share of 64). */
    private static final float FISH_RANGE = 0.5f;
    private static final float TANK_RANGE = 0.75f;
    private static final SwimRule SWIM = new SwimRule();
    private static final float FISH_SCALE = 0.32f;

    /** The looks of one block: its glass and the fish it holds. */
    private record Dressed(UUID tank, List<UUID> fish) {
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
    /** Each tank's brain, by its first block, and which tank each block's fish swim in. */
    private final Map<String, ScheduledTask> schools = new ConcurrentHashMap<>();
    private final Map<String, String> schoolOf = new ConcurrentHashMap<>();
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

    /** A fish swimming in a tank: its display, its brain, and its own block relative to the tank's first. */
    private record Swimming(ItemDisplay display, SwimRule.Swimmer swimmer, SwimRule.V3 home) {
    }

    /**
     * Dresses every block of this block's tank and puts one brain over all its fish, so they swim through the whole
     * tank and notice each other; the blocks dressed.
     */
    private List<Block> redress(Block block) {
        List<Block> members = group(block);
        if (members.isEmpty()) {
            return members;
        }
        members.forEach(this::stopSchool);
        Set<TankPos> joined = new HashSet<>();
        members.forEach(each -> joined.add(pos(each)));
        TankPos anchor = pos(members.getFirst());
        List<SwimRule.V3> layout = members.stream().map(each -> relative(pos(each), anchor)).toList();
        SwimRule.Water water = SwimRule.Water.of(layout);
        List<Swimming> fish = new ArrayList<>();
        for (Block member : members) {
            at(member).ifPresent(tank -> dress(member, tank, joined, relative(pos(member), anchor), fish));
        }
        if (!fish.isEmpty()) {
            Block first = members.getFirst();
            Location centre = first.getLocation().add(0.5, 0.5, 0.5);
            List<SwimRule.Swimmer> brains = fish.stream().map(Swimming::swimmer).toList();
            ScheduledTask school = Scheduling.regionTimer(plugin, centre, SWIM_TICKS, SWIM_TICKS, task -> {
                // nobody watching, nobody swimming: a tank far from every player costs nothing
                if (centre.getWorld().getNearbyPlayers(centre, WATCHED).isEmpty()) {
                    return;
                }
                for (SwimRule.Nip nip : SWIM.step(brains, water, random)) {
                    centre.getWorld().spawnParticle(org.bukkit.Particle.BUBBLE_POP, centre.clone().add(nip.at().x(),
                            nip.at().y(), nip.at().z()), 6, 0.05, 0.05, 0.05, 0.02);
                }
                // a resting fish sends nothing
                fish.stream().filter(each -> each.swimmer().moved()).forEach(AquariumService::show);
            });
            String key = spot(first);
            schools.put(key, school);
            members.forEach(each -> schoolOf.put(spot(each), key));
        }
        return members;
    }

    private static SwimRule.V3 relative(TankPos at, TankPos anchor) {
        return new SwimRule.V3(at.x() - anchor.x(), at.y() - anchor.y(), at.z() - anchor.z());
    }

    private void dress(Block block, Aquarium tank, Set<TankPos> joined, SwimRule.V3 home, List<Swimming> fish) {
        undress(block);
        World world = block.getWorld();
        Location centre = block.getLocation().add(0.5, 0.5, 0.5);
        int look = rule.look(pos(block), joined::contains);
        ItemDisplay glass = world.spawn(centre, ItemDisplay.class, made -> {
            prepare(made);
            made.setItemStack(Looks.wearing(FishingPack.NAMESPACE + ":aquarium_" + look));
            made.setViewRange(TANK_RANGE);
            // an item display shows a model turned half round; turned back, its joined sides face their neighbours
            made.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY((float) Math.PI),
                    new Vector3f(1), new Quaternionf()));
        });
        List<UUID> ids = new ArrayList<>();
        for (Aquarium.Resident resident : tank.residents()) {
            Optional<FishKind> kind = catalog.fish(resident.fishId());
            Optional<ItemStack> fishLook = item(resident);
            if (kind.isEmpty() || fishLook.isEmpty()) {
                continue;
            }
            SwimRule.V3 start = new SwimRule.V3(home.x() + (random.nextDouble() - 0.5) * 0.5,
                    home.y() + random.nextDouble() * 0.3 - 0.15, home.z() + (random.nextDouble() - 0.5) * 0.5);
            SwimRule.Swimmer brain = new SwimRule.Swimmer(fish.size(), kind.get().id(), SWIM.personality(kind.get()),
                    start);
            ItemDisplay display = world.spawn(centre, ItemDisplay.class, made -> {
                prepare(made);
                made.setItemStack(fishLook.get());
                made.setViewRange(FISH_RANGE);
                if (kind.get().glows()) {
                    made.setBrightness(new Display.Brightness(15, 15));
                }
            });
            Swimming swimming = new Swimming(display, brain, home);
            show(swimming);
            fish.add(swimming);
            ids.add(display.getUniqueId());
        }
        dressed.put(spot(block), new Dressed(glass.getUniqueId(), ids));
    }

    private static void prepare(ItemDisplay display) {
        display.setPersistent(false);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        display.getPersistentDataContainer().set(DISPLAY, PersistentDataType.BYTE, (byte) 1);
    }

    /** Where its brain says it is, facing the way it last swam, gliding there over the next beat. */
    private static void show(Swimming fish) {
        ItemDisplay display = fish.display();
        if (!display.isValid()) {
            return;
        }
        SwimRule.V3 at = fish.swimmer().at();
        SwimRule.V3 heading = fish.swimmer().heading();
        // a fish model's head points to -x, and a display turns its model half round: so the head points to +x here
        float yaw = (float) Math.atan2(-heading.z(), heading.x());
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(SWIM_TICKS);
        display.setTransformation(new Transformation(new Vector3f((float) (at.x() - fish.home().x()),
                (float) (at.y() - fish.home().y()), (float) (at.z() - fish.home().z())),
                new Quaternionf().rotateY(yaw), new Vector3f(FISH_SCALE), new Quaternionf()));
    }

    private void stopSchool(Block member) {
        String key = schoolOf.remove(spot(member));
        if (key == null) {
            return;
        }
        ScheduledTask school = schools.remove(key);
        if (school != null) {
            school.cancel();
        }
    }

    /** Takes the block's looks away (the block stays), and stops its tank's school. */
    public void undress(Block block) {
        stopSchool(block);
        Dressed gone = dressed.remove(spot(block));
        if (gone == null) {
            return;
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
        schools.values().forEach(ScheduledTask::cancel);
        schools.clear();
        schoolOf.clear();
        for (Dressed each : dressed.values()) {
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
