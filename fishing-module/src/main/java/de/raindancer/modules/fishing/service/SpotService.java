package de.raindancer.modules.fishing.service;

import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.Spot;
import de.raindancer.modules.fishing.model.Water;
import de.raindancer.modules.fishing.rules.CatchRule;
import de.raindancer.modules.fishing.rules.WaterRule;
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
 * is a river spot, one off an End island's edge hangs in the void — and go again when fished out or after a while.
 */
public final class SpotService implements IFishingService {

    /** How far around a player a spot may come up, and how close counts as "near them" for the limit. */
    private static final int NEAREST = 10;
    private static final int FARTHEST = 36;
    private static final double NEAR = 64.0;
    /** A hook this close to a spot's centre is in it. */
    public static final double RADIUS = 4.0;

    private record Live(Spot spot, SpotVisual visual) {
    }

    private final Plugin plugin;
    private final LogChannel log;
    private final Messages messages;
    private final WaterRule waters;
    private final CatchRule catches;
    private final LongSupplier clock;
    private final Map<UUID, Live> spots = new ConcurrentHashMap<>();
    private volatile FishingSettings settings;

    public SpotService(Plugin plugin, LogChannel log, Messages messages, WaterRule waters, CatchRule catches,
                       LongSupplier clock, FishingSettings settings) {
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
        long near = spots.values().stream().filter(each -> each.spot().world().equals(at.getWorld().getUID())
                && distanceSquared(each.spot(), at) <= NEAR * NEAR).count();
        if (near >= live.spotsPerPlayer()) {
            return;
        }
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double distance = ThreadLocalRandom.current().nextDouble(NEAREST, FARTHEST);
        Location probe = at.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
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
            return Optional.of(new Found(new Location(world, x + 0.5, top - 5, z + 0.5), Water.VOID));
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

    /** Puts a spot on the surface at {@code at}; call on its region thread. */
    public Spot spawn(Location at, Water water, FishingSettings live) {
        Spot spot = new Spot(UUID.randomUUID(), at.getWorld().getUID(), at.getX(), at.getY(), at.getZ(), water,
                catches.bestChest(water), live.spotCatches(), clock.getAsLong() + live.spotMinutes() * 60_000L);
        SpotVisual visual = SpotVisual.spawn(plugin, at, spot, sign(spot));
        spots.put(spot.id(), new Live(spot, visual));
        return spot;
    }

    private Component sign(Spot spot) {
        // Whatever the wording leaves uncoloured wears the shoal's tier colour.
        return messages.get("fishing.spot.sign", "shoal", spot.shoalName(), "chest", spot.best().chestName(),
                "water", spot.water().title())
                .colorIfAbsent(net.kyori.adventure.text.format.TextColor.color(spot.best().colour()));
    }

    /** The spot a hook at {@code at} is in, if any. */
    public Optional<Spot> at(Location at) {
        UUID world = at.getWorld().getUID();
        return spots.values().stream().map(Live::spot)
                .filter(spot -> spot.covers(world, at.getX(), at.getY(), at.getZ(), RADIUS)).findFirst();
    }

    /** One catch taken out of a spot; it goes when it is fished out. */
    public void fished(Spot spot) {
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
        for (Live live : List.copyOf(spots.values())) {
            try {
                live.visual().remove();
            } catch (RuntimeException notOnItsThread) {
                log.debug("A fishing spot's displays could not be removed here; they are not saved with the "
                        + "world and go with the next restart.");
            }
        }
        spots.clear();
        return count;
    }

    private static double distanceSquared(Spot spot, Location at) {
        double dx = spot.x() - at.getX();
        double dz = spot.z() - at.getZ();
        return dx * dx + dz * dz;
    }
}
