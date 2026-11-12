package de.raindancer.modules.fishing.command;

import de.raindancer.modules.fishing.store.FixedSpotStore;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.platform.command.PlayerLookup;
import de.raindancer.core.platform.command.PlayerTargets;
import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.model.Dish;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.model.Water;
import de.raindancer.modules.fishing.util.PermissionNodes;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code /fishing} opens the fishing page; {@code skills}, {@code rod} and {@code collection} go straight to theirs.
 * Staff: {@code give <player> <fish:id|dish:id|chest:tier> [amount]}, {@code xp <player> <amount>} for fishing
 * experience, and {@code spot}, which puts a wandering fishing spot on the water being looked at; {@code spot create
 * <tier> [radius]} puts down a fixed one for good, {@code spot list} lists this world's, {@code spot remove [id]}
 * takes one (the nearest, without an id) away.
 */
public final class FishingCommand implements IFishingCommand {

    private final Supplier<FishingServices> services;

    public FishingCommand(Supplier<FishingServices> services) {
        this.services = services;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, String @NotNull [] args) {
        FishingServices live = services.get();
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("give")) {
            give(live, sender, args);
            return;
        }
        if (sub.equals("xp")) {
            experience(live, sender, args);
            return;
        }
        if (!(sender instanceof Player viewer)) {
            live.messages().send(sender, "fishing.only-a-player");
            return;
        }
        if (sub.equals("spot")) {
            spot(live, viewer, args);
            return;
        }
        if (sub.equals("nospot")) {
            noSpot(live, viewer, args);
            return;
        }
        if (!viewer.hasPermission(PermissionNodes.MENU)) {
            live.messages().send(viewer, "fishing.menu.not-allowed");
            return;
        }
        switch (sub) {
            case "skills" -> live.screens().skills(viewer);
            case "rod" -> live.screens().rod(viewer);
            case "collection" -> live.screens().collection(viewer);
            case "rods" -> live.screens().rods(viewer);
            case "" -> live.screens().root(viewer);
            default -> live.messages().send(viewer, "fishing.usage");
        }
    }

    private record Target(Location at, Water water) {
    }

    private void spot(FishingServices live, Player viewer, String[] args) {
        if (!viewer.hasPermission(PermissionNodes.SPOT)) {
            live.messages().send(viewer, "fishing.spot.not-allowed");
            return;
        }
        String what = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        switch (what) {
            case "create" -> createFixed(live, viewer, args);
            case "list" -> listFixed(live, viewer);
            case "remove" -> removeFixed(live, viewer, args);
            case "" -> target(live, viewer).ifPresent(target -> {
                if (live.spots().spawn(target.at(), target.water(), live.config()).isEmpty()) {
                    live.messages().send(viewer, "fishing.spot.taken");
                } else {
                    live.messages().send(viewer, "fishing.spot.placed", "water", target.water().title());
                }
            });
            default -> live.messages().send(viewer, "fishing.spot.usage");
        }
    }

    /** {@code /fishing nospot add <name> [radius] | add <name> <x1> <z1> <x2> <z2> | remove <name> | list}. */
    private void noSpot(FishingServices live, Player viewer, String[] args) {
        if (!viewer.hasPermission(PermissionNodes.SPOT)) {
            live.messages().send(viewer, "fishing.spot.not-allowed");
            return;
        }
        String what = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        java.util.UUID world = viewer.getWorld().getUID();
        switch (what) {
            case "add" -> {
                if (args.length != 3 && args.length != 4 && args.length != 7) {
                    live.messages().send(viewer, "fishing.nospot.usage");
                    return;
                }
                de.raindancer.modules.fishing.model.NoSpotZone zone;
                try {
                    zone = args.length == 7
                            ? de.raindancer.modules.fishing.model.NoSpotZone.of(args[2], world,
                                    Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]),
                                    Integer.parseInt(args[6]))
                            : de.raindancer.modules.fishing.model.NoSpotZone.around(args[2], world,
                                    viewer.getLocation().getX(), viewer.getLocation().getZ(),
                                    args.length == 4 ? Math.clamp(Integer.parseInt(args[3]), 1, 512) : 32);
                } catch (NumberFormatException notANumber) {
                    live.messages().send(viewer, "fishing.nospot.usage");
                    return;
                }
                if (!live.spots().addZone(zone)) {
                    live.messages().send(viewer, "fishing.nospot.taken", "name", zone.name());
                    return;
                }
                live.messages().send(viewer, "fishing.nospot.added", "name", zone.name(),
                        "x1", String.valueOf(zone.minX()), "x2", String.valueOf(zone.maxX()),
                        "z1", String.valueOf(zone.minZ()), "z2", String.valueOf(zone.maxZ()));
            }
            case "remove" -> {
                if (args.length < 3) {
                    live.messages().send(viewer, "fishing.nospot.usage");
                    return;
                }
                live.messages().send(viewer, live.spots().removeZone(args[2]) ? "fishing.nospot.removed"
                        : "fishing.nospot.not-found", "name", args[2]);
            }
            case "list" -> {
                List<de.raindancer.modules.fishing.model.NoSpotZone> zones = live.spots().zonesIn(world);
                if (zones.isEmpty()) {
                    live.messages().send(viewer, "fishing.nospot.none");
                    return;
                }
                live.messages().send(viewer, "fishing.nospot.list", "count", String.valueOf(zones.size()));
                for (de.raindancer.modules.fishing.model.NoSpotZone zone : zones) {
                    double cx = (zone.minX() + zone.maxX() + 1) / 2.0;
                    double cz = (zone.minZ() + zone.maxZ() + 1) / 2.0;
                    live.messages().send(viewer, "fishing.nospot.line", "name", zone.name(),
                            "x1", String.valueOf(zone.minX()), "x2", String.valueOf(zone.maxX()),
                            "z1", String.valueOf(zone.minZ()), "z2", String.valueOf(zone.maxZ()),
                            "distance", String.valueOf(Math.round(Math.hypot(cx - viewer.getLocation().getX(),
                                    cz - viewer.getLocation().getZ()))));
                }
            }
            default -> live.messages().send(viewer, "fishing.nospot.usage");
        }
    }

    /** The water being looked at — or, in the End, the void out in front. */
    private Optional<Target> target(FishingServices live, Player viewer) {
        Block target = viewer.getTargetBlockExact(40, FluidCollisionMode.ALWAYS);
        Location at;
        Optional<Water> water;
        if (target != null && (target.getType() == Material.WATER || target.getType() == Material.LAVA)) {
            at = target.getLocation().add(0.5, 0.9, 0.5);
            water = live.spots().waterAt(target.getLocation(), target.getType() == Material.LAVA, false);
        } else if (target == null && viewer.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END) {
            at = viewer.getEyeLocation().add(viewer.getLocation().getDirection().multiply(8));
            at.setY(de.raindancer.modules.fishing.rules.VoidSurfaceRule.SURFACE);
            water = Optional.of(Water.VOID);
        } else {
            water = Optional.empty();
            at = null;
        }
        if (water.isEmpty()) {
            live.messages().send(viewer, "fishing.spot.look-at-water");
            return Optional.empty();
        }
        return Optional.of(new Target(at, water.get()));
    }

    private void createFixed(FishingServices live, Player viewer, String[] args) {
        Tier tier = args.length < 3 ? null : Tier.parse(args[2]).orElse(null);
        double radius = de.raindancer.modules.fishing.model.Spot.WANDERING_RADIUS;
        if (args.length >= 4) {
            try {
                radius = Double.parseDouble(args[3]);
            } catch (NumberFormatException notANumber) {
                tier = null;
            }
        }
        if (tier == null || radius < FixedSpotStore.LEAST_RADIUS || radius > FixedSpotStore.MOST_RADIUS) {
            live.messages().send(viewer, "fishing.spot.usage");
            return;
        }
        Tier best = tier;
        double reach = radius;
        target(live, viewer).ifPresent(target -> {
            if (best == Tier.MYTHIC && target.water() != Water.VOID) {
                live.messages().send(viewer, "fishing.spot.void-only");
                return;
            }
            Scheduling.region(live.plugin(), target.at(), () -> {
                Optional<de.raindancer.modules.fishing.model.Spot> made =
                        live.spots().createFixed(target.at(), target.water(), best, reach);
                Scheduling.entity(live.plugin(), viewer, () -> made.ifPresentOrElse(
                        spot -> live.messages().send(viewer, "fishing.spot.fixed", "shoal", spot.shoalName(),
                                "water", spot.water().title(), "radius", blocks(spot.radius()),
                                "id", spot.id().toString().substring(0, 8)),
                        () -> live.messages().send(viewer, "fishing.spot.taken")));
            });
        });
    }

    /** 4 for 4.0, 4.5 for 4.5. */
    private static String blocks(double radius) {
        return radius == Math.rint(radius) ? String.valueOf((long) radius) : String.valueOf(radius);
    }

    private void listFixed(FishingServices live, Player viewer) {
        List<de.raindancer.modules.fishing.model.Spot> all = live.spots().fixedIn(viewer.getWorld(),
                viewer.getLocation());
        if (all.isEmpty()) {
            live.messages().send(viewer, "fishing.spot.none");
            return;
        }
        live.messages().send(viewer, "fishing.spot.list", "count", String.valueOf(all.size()));
        for (de.raindancer.modules.fishing.model.Spot spot : all) {
            live.messages().send(viewer, "fishing.spot.line", "id", spot.id().toString().substring(0, 8),
                    "shoal", spot.shoalName(), "water", spot.water().title(), "radius", blocks(spot.radius()),
                    "x", String.valueOf((int) Math.floor(spot.x())), "y", String.valueOf((int) Math.floor(spot.y())),
                    "z", String.valueOf((int) Math.floor(spot.z())),
                    "distance", String.valueOf((int) Math.round(viewer.getLocation().distance(
                            new Location(viewer.getWorld(), spot.x(), spot.y(), spot.z())))));
        }
    }

    private void removeFixed(FishingServices live, Player viewer, String[] args) {
        List<de.raindancer.modules.fishing.model.Spot> all = live.spots().fixedIn(viewer.getWorld(),
                viewer.getLocation());
        Optional<de.raindancer.modules.fishing.model.Spot> chosen;
        if (args.length >= 3) {
            String wanted = args[2].toLowerCase(Locale.ROOT);
            chosen = all.stream().filter(spot -> spot.id().toString().startsWith(wanted)).findFirst();
        } else {
            chosen = all.stream().findFirst().filter(spot -> viewer.getLocation().distance(
                    new Location(viewer.getWorld(), spot.x(), spot.y(), spot.z())) <= 32);
        }
        if (chosen.isEmpty() || !live.spots().removeFixed(chosen.get().id())) {
            live.messages().send(viewer, "fishing.spot.not-found");
            return;
        }
        live.messages().send(viewer, "fishing.spot.removed", "shoal", chosen.get().shoalName(),
                "id", chosen.get().id().toString().substring(0, 8));
    }

    private void give(FishingServices live, CommandSender sender, String[] args) {
        if (!sender.hasPermission(PermissionNodes.GIVE)) {
            live.messages().send(sender, "fishing.give.not-allowed");
            return;
        }
        if (args.length < 3) {
            live.messages().send(sender, "fishing.usage");
            return;
        }
        PlayerLookup found = PlayerTargets.lookup(live.server(), sender, args[1]);
        if (found.isEmpty() || found.isOfflineOnly()) {
            live.messages().send(sender, "fishing.give.unknown", "player", args[1]);
            return;
        }
        Optional<ItemStack> stack = item(live, args[2].toLowerCase(Locale.ROOT));
        if (stack.isEmpty()) {
            live.messages().send(sender, "fishing.give.bad-item", "item", args[2]);
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException notANumber) {
                live.messages().send(sender, "fishing.give.bad-amount", "amount", args[3]);
                return;
            }
        }
        for (Player target : found.online()) {
            ItemStack given = stack.get().clone();
            given.setAmount(amount);
            target.getInventory().addItem(given).values()
                    .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        }
        live.messages().send(sender, "fishing.give.given", "item", args[2], "amount", String.valueOf(amount),
                "count", String.valueOf(found.online().size()));
    }

    /** Staff: grants fishing experience, which also levels the fisher up. */
    private void experience(FishingServices live, CommandSender sender, String[] args) {
        if (!sender.hasPermission(PermissionNodes.GIVE)) {
            live.messages().send(sender, "fishing.give.not-allowed");
            return;
        }
        if (args.length < 3) {
            live.messages().send(sender, "fishing.usage");
            return;
        }
        PlayerLookup found = PlayerTargets.lookup(live.server(), sender, args[1]);
        if (found.isEmpty() || found.isOfflineOnly()) {
            live.messages().send(sender, "fishing.give.unknown", "player", args[1]);
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException notANumber) {
            live.messages().send(sender, "fishing.give.bad-amount", "amount", args[2]);
            return;
        }
        if (amount <= 0 || amount > 10_000_000L) {
            live.messages().send(sender, "fishing.give.bad-amount", "amount", args[2]);
            return;
        }
        for (Player target : found.online()) {
            live.profiles().grant(target, amount);
        }
        live.messages().send(sender, "fishing.give.experience", "amount", String.valueOf(amount),
                "count", String.valueOf(found.online().size()));
    }

    private static Optional<ItemStack> item(FishingServices live, String written) {
        int colon = written.indexOf(':');
        if (colon < 0) {
            return Optional.empty();
        }
        String kind = written.substring(0, colon);
        String id = written.substring(colon + 1);
        return switch (kind) {
            case "fish" -> live.catalog().fish(id).map(fish -> live.forge().fish(fish, 1));
            case "dish" -> live.catalog().dish(id).map(dish -> live.forge().dish(dish, 1));
            case "chest" -> Tier.parse(id).map(tier -> live.forge().chest(tier, 1));
            case "rod" -> de.raindancer.modules.fishing.model.RodTier.parse(id).map(rod -> live.forge().rod(rod));
            case "bait" -> de.raindancer.modules.fishing.model.Bait.parse(id).map(bait -> live.forge().bait(bait, 1));
            case "net" -> Optional.of(live.forge().net());
            case "skin" -> de.raindancer.modules.fishing.model.RodSkin.parse(id).map(skin -> live.forge().skinToken(skin));
            default -> Optional.empty();
        };
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, String @NotNull [] args) {
        CommandSender sender = source.getSender();
        if (args.length <= 1) {
            String started = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> subs = new ArrayList<>(List.of("rods", "skills", "rod", "collection"));
            if (sender.hasPermission(PermissionNodes.GIVE)) {
                subs.add("give");
                subs.add("xp");
            }
            if (sender.hasPermission(PermissionNodes.SPOT)) {
                subs.add("spot");
                subs.add("nospot");
            }
            return subs.stream().filter(sub -> sub.startsWith(started)).toList();
        }
        if (args[0].equalsIgnoreCase("nospot") && sender.hasPermission(PermissionNodes.SPOT)) {
            String started = args[args.length - 1].toLowerCase(Locale.ROOT);
            List<String> options = switch (args.length) {
                case 2 -> List.of("add", "remove", "list");
                case 3 -> args[1].equalsIgnoreCase("remove") && sender instanceof Player player
                        ? services.get().spots().zonesIn(player.getWorld().getUID()).stream()
                                .map(de.raindancer.modules.fishing.model.NoSpotZone::name).toList()
                        : List.of();
                case 4 -> args[1].equalsIgnoreCase("add") ? List.of("16", "32", "64") : List.of();
                default -> List.of();
            };
            return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(started)).toList();
        }
        if (args[0].equalsIgnoreCase("spot") && sender.hasPermission(PermissionNodes.SPOT)) {
            String started = args[args.length - 1].toLowerCase(Locale.ROOT);
            List<String> options = switch (args.length) {
                case 2 -> List.of("create", "list", "remove");
                case 3 -> args[1].equalsIgnoreCase("create")
                        ? java.util.Arrays.stream(Tier.values()).map(Tier::id).toList()
                        : args[1].equalsIgnoreCase("remove") && sender instanceof Player player
                        ? services.get().spots().fixedIn(player.getWorld(), player.getLocation()).stream()
                                .map(spot -> spot.id().toString().substring(0, 8)).toList()
                        : List.of();
                case 4 -> args[1].equalsIgnoreCase("create") ? List.of("4", "6", "8", "12") : List.of();
                default -> List.of();
            };
            return options.stream().filter(option -> option.startsWith(started)).toList();
        }
        boolean giving = args[0].equalsIgnoreCase("give");
        if ((!giving && !args[0].equalsIgnoreCase("xp")) || !sender.hasPermission(PermissionNodes.GIVE)) {
            return List.of();
        }
        if (args.length == 2) {
            return PlayerTargets.suggest(services.get().server(), sender, args[1], who -> true);
        }
        if (args.length == 3 && !giving) {
            return List.of("100", "1000", "10000");
        }
        if (args.length == 3) {
            String started = args[2].toLowerCase(Locale.ROOT);
            List<String> items = new ArrayList<>();
            for (FishKind kind : services.get().catalog().fish()) {
                items.add("fish:" + kind.id());
            }
            for (Dish dish : services.get().catalog().dishes()) {
                items.add("dish:" + dish.id());
            }
            for (Tier tier : Tier.values()) {
                items.add("chest:" + tier.chest());
            }
            for (de.raindancer.modules.fishing.model.RodTier rod : de.raindancer.modules.fishing.model.RodTier.values()) {
                items.add("rod:" + rod.id());
            }
            for (de.raindancer.modules.fishing.model.Bait bait : de.raindancer.modules.fishing.model.Bait.values()) {
                items.add("bait:" + bait.id());
            }
            items.add("net:fishing");
            for (de.raindancer.modules.fishing.model.RodSkin skin : de.raindancer.modules.fishing.model.RodSkin.values()) {
                items.add("skin:" + skin.id());
            }
            return items.stream().filter(item -> item.startsWith(started)).toList();
        }
        return List.of();
    }

    @Override
    public String describe() {
        return "the fishing pages, giving out fish, dishes and sea chests, and placing fishing spots — wandering or "
                + "fixed for good";
    }
}
