package de.raindancer.modules.fishing.service;

import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.Spot;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.model.Water;
import de.raindancer.modules.fishing.rules.CatchRule;
import de.raindancer.modules.fishing.rules.WaterRule;
import de.raindancer.modules.fishing.store.FixedSpotStore;
import de.raindancer.modules.fishing.visual.SpotVisual;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/**
 * Fishing spots: shoals that come up on the water around players, in whatever water is there — a spot on a river
 * is a river spot, one off an End island's edge hangs in the void — and go again when fished out or after a while;
 * and the fixed ones admins put down, with their own size and best tier, which stay. No spot comes up inside another.
 * A fixed spot is shown while its chunk is loaded.
 */
public final class SpotService implements IFishingService {

    /** How far around a player a spot may come up, and how close counts as "near them" for the limit. */
    private static final int NEAREST = 10;
    private static final int FARTHEST = 36;
    private static final double NEAR = 64.0;
    /** A hook this close to a wandering spot's centre is in it. */
    public static final double RADIUS = Spot.WANDERING_RADIUS;

    private record Live(Spot spot, SpotVisual visual) {
    }

    private final Plugin plugin;
    private final LogChannel log;
    private final Messages messages;
    private final WaterRule waters;
    private final CatchRule catches;
    private final LongSupplier clock;
    private final Map<UUID, Live> spots = new ConcurrentHashMap<>();
    private final Map<UUID, Spot> fixed = new ConcurrentHashMap<>();
    private final Map<UUID, SpotVisual> fixedShown = new ConcurrentHashMap<>();
    private final FixedSpotStore store;
    private final Map<String, de.raindancer.modules.fishing.model.NoSpotZone> zones = new ConcurrentHashMap<>();
    private volatile de.raindancer.modules.fishing.store.NoSpotZoneStore zoneStore;
    private volatile FishingSettings settings;

    public SpotService(Plugin plugin, LogChannel log, Messages messages, WaterRule waters, CatchRule catches,
                       LongSupplier clock, FishingSettings settings) {
        this(plugin, log, messages, waters, catches, clock, settings, null);
    }

    /** @param store where the fixed spots are kept; null for none */
    public SpotService(Plugin plugin, LogChannel log, Messages messages, WaterRule waters, CatchRule catches,
                       LongSupplier clock, FishingSettings settings, FixedSpotStore store) {
        this.store = store;
        this.plugin = plugin;
        this.log = log;
        this.messages = messages;
        this.waters = waters;
        this.catches = catches;
        this.clock = clock;
        this.settings = settings;
    }

    @Override
    public void settings(FishingSettings settings) {
        this.settings = settings;
    }

    /** Called from the global region every few seconds with everybody online. */
    public void tick(Collection<? extends Player> online) {
        long now = clock.getAsLong();
        for (Live live : List.copyOf(spots.values())) {
            if (live.spot().isOver(now)) {
                end(live.spot().id());
            }
        }
        FishingSettings live = settings;
        if (!live.spotsEnabled() || live.spotsPerPlayer() <= 0) {
            return;
        }
        for (Player player : online) {
            Scheduling.entity(plugin, player, () -> maybeSpawnNear(player, live));
        }
    }

    private void maybeSpawnNear(Player player, FishingSettings live) {
        if (!player.isOnline() || ThreadLocalRandom.current().nextInt(100) >= 35) {
            return;
        }
        Location at = player.getLocation();
        // Fixed spots are an admin's and do not use up anybody's share.
        long near = spots.values().stream().filter(each -> each.spot().world().equals(at.getWorld().getUID())
                && distanceSquared(each.spot(), at) <= NEAR * NEAR).count();
        if (near >= live.spotsPerPlayer()) {
            return;
        }
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double distance = ThreadLocalRandom.current().nextDouble(NEAREST, FARTHEST);
        Location probe = at.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
        if (noSpotsAt(probe.getWorld().getUID(), probe.getX(), probe.getZ())) {
            return;
        }
        if (!probe.getWorld().isChunkLoaded(probe.getBlockX() >> 4, probe.getBlockZ() >> 4)) {
            return;
        }
        Scheduling.region(plugin, probe, () -> findSurface(probe, at.getY()).ifPresent(found ->
                spawn(found.location(), found.water(), live)));
    }

    private record Found(Location location, Water water) {
    }

    /** Water or lava with air above it near the player's height — or, in the End, nothing at all below. */
    private Optional<Found> findSurface(Location probe, double playerY) {
        World world = probe.getWorld();
        int x = probe.getBlockX();
        int z = probe.getBlockZ();
        if (world.getEnvironment() == World.Environment.THE_END) {
            int top = (int) Math.floor(playerY);
            for (int y = top + 2; y >= world.getMinHeight(); y--) {
                if (!world.getBlockAt(x, y, z).isEmpty()) {
                    return Optional.empty();
                }
            }
            // On the void's surface, where a float rests (VoidSurfaceRule).
            return Optional.of(new Found(new Location(world, x + 0.5,
                    de.raindancer.modules.fishing.rules.VoidSurfaceRule.SURFACE, z + 0.5), Water.VOID));
        }
        int from = (int) Math.floor(playerY) + 8;
        int to = Math.max(world.getMinHeight() + 1, (int) Math.floor(playerY) - 16);
        if (world.getEnvironment() == World.Environment.NORMAL) {
            Block highest = world.getHighestBlockAt(x, z);
            if (Math.abs(highest.getY() - playerY) <= 24) {
                from = Math.min(from, highest.getY());
            }
        }
        for (int y = from; y >= to; y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if ((type == Material.WATER || type == Material.LAVA) && block.getRelative(0, 1, 0).isEmpty()
                    && block.getRelative(0, -1, 0).getType() == type) {
                Optional<Water> water = waterAt(block.getLocation(), type == Material.LAVA, false);
                if (water.isPresent()) {
                    return Optional.of(new Found(new Location(world, x + 0.5, y + 0.9, z + 0.5), water.get()));
                }
            }
        }
        return Optional.empty();
    }

    /** Which water a place is, read off the world; call on its region thread. */
    public Optional<Water> waterAt(Location at, boolean lava, boolean voidBelow) {
        World world = at.getWorld();
        WaterRule.Dimension dimension = switch (world.getEnvironment()) {
            case NORMAL -> WaterRule.Dimension.OVERWORLD;
            case NETHER -> WaterRule.Dimension.NETHER;
            case THE_END -> WaterRule.Dimension.END;
            default -> WaterRule.Dimension.OTHER;
        };
        Block block = at.getBlock();
        boolean sky = world.getEnvironment() != World.Environment.NORMAL
                || block.getRelative(0, 1, 0).getLightFromSky() > 0;
        String biome = world.getBiome(at).getKey().toString();
        return waters.classify(new WaterRule.Place(biome, dimension, lava, voidBelow, sky));
    }

    /**
     * Puts a wandering spot on the surface at {@code at}; call on its region thread.
     *
     * @return the spot; empty when it would come up inside another
     */
    public Optional<Spot> spawn(Location at, Water water, FishingSettings live) {
        Spot spot = new Spot(UUID.randomUUID(), at.getWorld().getUID(), at.getX(), at.getY(), at.getZ(), water,
                catches.bestChest(water), live.spotCatches(), clock.getAsLong() + live.spotMinutes() * 60_000L);
        if (overlapsAny(spot)) {
            return Optional.empty();
        }
        SpotVisual visual = SpotVisual.spawn(plugin, at, spot, sign(spot));
        spots.put(spot.id(), new Live(spot, visual));
        return Optional.of(spot);
    }

    private boolean overlapsAny(Spot spot) {
        return spots.values().stream().anyMatch(each -> each.spot().overlaps(spot))
                || fixed.values().stream().anyMatch(each -> each.overlaps(spot));
    }

    // ---------------------------------------------------------------------------- no-spot zones

    /** Where the zones are kept; null keeps them in memory only. */
    public void zones(de.raindancer.modules.fishing.store.NoSpotZoneStore store) {
        this.zoneStore = store;
    }

    /** Reads the zones (the database: off the world's threads); once, at start. */
    public int loadZones() {
        var keeping = zoneStore;
        if (keeping == null || !keeping.load()) {
            return 0;
        }
        keeping.all().forEach(zone -> zones.put(zone.key(), zone));
        return zones.size();
    }

    /** Whether no spot may come up on its own at this column. */
    public boolean noSpotsAt(UUID world, double x, double z) {
        for (de.raindancer.modules.fishing.model.NoSpotZone zone : zones.values()) {
            if (zone.contains(world, x, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Marks a zone; wandering spots already in it go. @return false when the name is taken
     */
    public boolean addZone(de.raindancer.modules.fishing.model.NoSpotZone zone) {
        if (zones.putIfAbsent(zone.key(), zone) != null) {
            return false;
        }
        for (Live live : List.copyOf(spots.values())) {
            if (zone.contains(live.spot().world(), live.spot().x(), live.spot().z())) {
                end(live.spot().id());
            }
        }
        var keeping = zoneStore;
        if (keeping != null) {
            Scheduling.async(plugin, () -> {
                if (!keeping.put(zone) && log != null) {
                    log.warn("The no-spot zone {} could not be saved; it is gone after a restart.", zone.name());
                }
            });
        }
        return true;
    }

    /** @return whether a zone of that name was there */
    public boolean removeZone(String name) {
        String key = name == null ? "" : name.strip().toLowerCase(java.util.Locale.ROOT);
        if (zones.remove(key) == null) {
            return false;
        }
        var keeping = zoneStore;
        if (keeping != null) {
            Scheduling.async(plugin, () -> keeping.remove(key));
        }
        return true;
    }

    /** The zones of one world, by name. */
    public List<de.raindancer.modules.fishing.model.NoSpotZone> zonesIn(UUID world) {
        return zones.values().stream().filter(zone -> zone.world().equals(world))
                .sorted(java.util.Comparator.comparing(de.raindancer.modules.fishing.model.NoSpotZone::key))
                .toList();
    }

    // ---------------------------------------------------------------------------- fixed spots

    /** Reads the fixed spots (the database: off the world's threads) and shows those in loaded chunks; once, at start. */
    public int loadFixed(java.util.function.Function<UUID, World> worlds) {
        if (store == null || !store.load()) {
            return 0;
        }
        for (Spot spot : store.all()) {
            fixed.put(spot.id(), spot);
            World world = worlds.apply(spot.world());
            if (world != null && world.isChunkLoaded(chunk(spot.x()), chunk(spot.z()))) {
                Location at = new Location(world, spot.x(), spot.y(), spot.z());
                Scheduling.region(plugin, at, () -> show(spot, at));
            }
        }
        return fixed.size();
    }

    /**
     * An admin's spot, for good: centred at {@code at}, {@code radius} blocks round, giving up chests and fish up to
     * {@code best}. Call on {@code at}'s region thread.
     *
     * @return the spot; empty when it would lie over another fixed spot
     */
    public Optional<Spot> createFixed(Location at, Water water, Tier best, double radius) {
        Spot spot = Spot.fixed(UUID.randomUUID(), at.getWorld().getUID(), at.getX(), at.getY(), at.getZ(), water,
                best, Math.clamp(radius, FixedSpotStore.LEAST_RADIUS, FixedSpotStore.MOST_RADIUS));
        if (fixed.values().stream().anyMatch(each -> each.overlaps(spot))) {
            return Optional.empty();
        }
        // A wandering spot where the admin wants theirs simply makes room.
        for (Live live : List.copyOf(spots.values())) {
            if (live.spot().overlaps(spot)) {
                end(live.spot().id());
            }
        }
        fixed.put(spot.id(), spot);
        show(spot, at);
        if (store != null) {
            Scheduling.async(plugin, () -> {
                if (!store.put(spot)) {
                    log.warn("The fixed fishing spot {} could not be saved; it is gone after a restart.", spot.id());
                }
            });
        }
        return Optional.of(spot);
    }

    /** Takes a fixed spot away for good. */
    public boolean removeFixed(UUID id) {
        Spot gone = fixed.remove(id);
        if (gone == null) {
            return false;
        }
        hide(id);
        if (store != null) {
            Scheduling.async(plugin, () -> store.remove(id));
        }
        return true;
    }

    /** The fixed spots of one world, nearest to {@code from} first. */
    public List<Spot> fixedIn(World world, Location from) {
        return fixed.values().stream().filter(spot -> spot.world().equals(world.getUID()))
                .sorted(java.util.Comparator.comparingDouble(spot -> distanceSquared(spot, from))).toList();
    }

    /** A chunk came in: show the fixed spots in it. Call on its region thread. */
    public void chunkLoaded(org.bukkit.Chunk chunk) {
        UUID world = chunk.getWorld().getUID();
        for (Spot spot : fixed.values()) {
            if (spot.world().equals(world) && chunk(spot.x()) == chunk.getX() && chunk(spot.z()) == chunk.getZ()
                    && !fixedShown.containsKey(spot.id())) {
                show(spot, new Location(chunk.getWorld(), spot.x(), spot.y(), spot.z()));
            }
        }
    }

    /** A chunk goes: its fixed spots' displays go with it (they are never saved with the world). */
    public void chunkUnloaded(org.bukkit.Chunk chunk) {
        UUID world = chunk.getWorld().getUID();
        for (Spot spot : fixed.values()) {
            if (spot.world().equals(world) && chunk(spot.x()) == chunk.getX() && chunk(spot.z()) == chunk.getZ()) {
                SpotVisual shown = fixedShown.remove(spot.id());
                if (shown != null) {
                    shown.remove();
                }
            }
        }
    }

    private void show(Spot spot, Location at) {
        hide(spot.id());
        fixedShown.put(spot.id(), SpotVisual.spawn(plugin, at, spot, sign(spot)));
    }

    private void hide(UUID id) {
        SpotVisual shown = fixedShown.remove(id);
        if (shown != null) {
            Scheduling.region(plugin, shown.location(), shown::remove);
        }
    }

    private static int chunk(double coordinate) {
        return (int) Math.floor(coordinate) >> 4;
    }

    private Component sign(Spot spot) {
        // Whatever the wording leaves uncoloured wears the shoal's tier colour.
        return messages.get("fishing.spot.sign", "shoal", spot.shoalName(), "chest", spot.best().chestName(),
                "water", spot.water().title())
                .colorIfAbsent(net.kyori.adventure.text.format.TextColor.color(spot.best().colour()));
    }

    /** The spot a hook at {@code at} is in, if any — a fixed one first. */
    public Optional<Spot> at(Location at) {
        UUID world = at.getWorld().getUID();
        return fixed.values().stream().filter(spot -> spot.covers(world, at.getX(), at.getY(), at.getZ())).findFirst()
                .or(() -> spots.values().stream().map(Live::spot)
                        .filter(spot -> spot.covers(world, at.getX(), at.getY(), at.getZ())).findFirst());
    }

    /** One catch taken out of a spot; it goes when it is fished out. A fixed spot never runs out. */
    public void fished(Spot spot) {
        if (spot.fixed()) {
            return;
        }
        Live live = spots.get(spot.id());
        if (live == null) {
            return;
        }
        Spot left = live.spot().fished();
        if (left.isOver(clock.getAsLong())) {
            end(spot.id());
        } else {
            spots.put(spot.id(), new Live(left, live.visual()));
        }
    }

    private void end(UUID id) {
        Live live = spots.remove(id);
        if (live != null) {
            Scheduling.region(plugin, live.visual().location(), live.visual()::remove);
        }
    }

    public List<Spot> all() {
        return new ArrayList<>(spots.values().stream().map(Live::spot).toList());
    }

    /** Takes every spot down at once — the module is stopping, so this runs where it is called. */
    public int clear() {
        int count = spots.size();
        List<SpotVisual> shown = new ArrayList<>(spots.values().stream().map(Live::visual).toList());
        shown.addAll(fixedShown.values());
        for (SpotVisual visual : shown) {
            try {
                visual.remove();
            } catch (RuntimeException notOnItsThread) {
                log.debug("A fishing spot's displays could not be removed here; they are not saved with the "
                        + "world and go with the next restart.");
            }
        }
        spots.clear();
        fixedShown.clear();
        return count;
    }

    private static double distanceSquared(Spot spot, Location at) {
        double dx = spot.x() - at.getX();
        double dz = spot.z() - at.getZ();
        return dx * dx + dz * dz;
    }
}
