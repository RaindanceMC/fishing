package de.raindancer.modules.fishing.command;

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
 * experience, and {@code spot}, which puts a fishing spot on the water being looked at.
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
            spot(live, viewer);
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

    private void spot(FishingServices live, Player viewer) {
        if (!viewer.hasPermission(PermissionNodes.SPOT)) {
            live.messages().send(viewer, "fishing.spot.not-allowed");
            return;
        }
        Block target = viewer.getTargetBlockExact(40, FluidCollisionMode.ALWAYS);
        Location at;
        Optional<Water> water;
        if (target != null && (target.getType() == Material.WATER || target.getType() == Material.LAVA)) {
            at = target.getLocation().add(0.5, 0.9, 0.5);
            water = live.spots().waterAt(target.getLocation(), target.getType() == Material.LAVA, false);
        } else if (target == null && viewer.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END) {
            at = viewer.getEyeLocation().add(viewer.getLocation().getDirection().multiply(8)).add(0, -6, 0);
            water = Optional.of(Water.VOID);
        } else {
            live.messages().send(viewer, "fishing.spot.look-at-water");
            return;
        }
        if (water.isEmpty()) {
            live.messages().send(viewer, "fishing.spot.look-at-water");
            return;
        }
        live.spots().spawn(at, water.get(), live.config());
        live.messages().send(viewer, "fishing.spot.placed", "water", water.get().title());
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
            }
            return subs.stream().filter(sub -> sub.startsWith(started)).toList();
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
            return items.stream().filter(item -> item.startsWith(started)).toList();
        }
        return List.of();
    }

    @Override
    public String describe() {
        return "the fishing pages, giving out fish, dishes and sea chests, and placing a fishing spot";
    }
}
