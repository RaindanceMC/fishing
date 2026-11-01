package de.raindancer.modules.fishing.service;

import de.raindancer.core.platform.util.Cooldowns;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.economy.Cash;
import de.raindancer.core.social.economy.EconomyResult;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.Reward;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.rules.ChestLootRule;
import de.raindancer.modules.fishing.visual.ChestShow;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Opening a sea chest that stands in the world: its owner right-clicks it, it plays its show where it stands, and
 * what was in it is handed over one thing at a time as each one comes out — into the bag, or at the opener's feet
 * when the bag is full. The money comes out too, as one cheque from the chest; only an economy without cheques
 * puts it straight onto the balance. Only the opener hears about it. A show cut short (the
 * chunk unloaded, the server stopped) still hands over everything it had not shown yet.
 */
public final class ChestService implements IFishingService {

    public static final String SOURCE = "fishing.sea-chest";

    private final Plugin plugin;
    private final Server server;
    private final Messages messages;
    private final Effects effects;
    private final FishForge forge;
    private final ProfileService profiles;
    private final SeaChestBlocks blocks;
    private final ChestLootRule loot;
    private final Random random;
    /** A chest's money comes out as one cheque, never as stacks of loose coins. */
    static final int MOST_COINS = 0;

    private final Cooldowns<UUID> between = Cooldowns.shared("fishing:chest");
    /** Chests whose money is being paid out; they stand until it is ready. */
    private final java.util.Set<Location> opening = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile FishingSettings settings;

    public ChestService(Plugin plugin, Server server, Messages messages, Effects effects, FishForge forge,
                        ProfileService profiles, SeaChestBlocks blocks, ChestLootRule loot, Random random,
                        FishingSettings settings) {
        this.plugin = plugin;
        this.server = server;
        this.messages = messages;
        this.effects = effects;
        this.forge = forge;
        this.profiles = profiles;
        this.blocks = blocks;
        this.loot = loot;
        this.random = random;
        this.settings = settings;
        // Long enough that a double click does not open two chests standing side by side at once.
        between.every(Duration.ofMillis(1500));
    }

    @Override
    public void settings(FishingSettings settings) {
        this.settings = settings;
    }

    /** Opens the sea chest at {@code block}; call on the player's (and the block's) thread. */
    public void open(Player player, Block block) {
        Tier tier = blocks.tierOf(block).orElse(null);
        if (tier == null || opening.contains(block.getLocation())) {
            return;
        }
        if (!blocks.ownerOf(block).map(player.getUniqueId()::equals).orElse(false)) {
            messages.send(player, "fishing.chest.not-yours");
            effects.play(player.getUniqueId(), Cues.NO);
            return;
        }
        if (!between.tryUse(player.getUniqueId())) {
            messages.send(player, "fishing.chest.one-at-a-time");
            effects.play(player.getUniqueId(), Cues.NO);
            return;
        }
        List<Reward> rewards = loot.open(tier, settings.chestMoneyMinor(), random);
        List<ItemStack> items = new ArrayList<>();
        long money = 0;
        for (Reward reward : rewards) {
            if (reward instanceof Reward.Money cash) {
                money += cash.amount();
            } else {
                items.add(stack(reward));
            }
        }
        UUID opener = player.getUniqueId();
        effects.play(opener, Cues.COUNTDOWN);
        if (money <= 0) {
            show(block, tier, items, opener, Money.ZERO);
            return;
        }
        // The money is paid first, off the world's thread, and comes out as coins with everything else; the chest
        // stays standing (and cannot be taken back) until they are ready.
        Location at = block.getLocation();
        opening.add(at);
        long base = money;
        Scheduling.async(plugin, () -> {
            EconomyResult paid = Fees.pay(opener, Money.of(base), "Sea chest", SOURCE);
            List<ItemStack> cash = paid.succeeded()
                    ? Cash.takeOut(opener, paid.amount(), MOST_COINS, tier.chestName()).join() : List.of();
            Money onBalance = paid.succeeded() && cash.isEmpty() ? paid.amount() : Money.ZERO;
            Scheduling.region(plugin, at, () -> {
                opening.remove(at);
                List<ItemStack> all = new ArrayList<>(items);
                all.addAll(cash);
                if (blocks.tierOf(block).isEmpty()) {
                    // gone meanwhile (an admin, a reload): what was paid out still reaches its owner
                    hand(opener, at, cash, new boolean[cash.size()], 0, cash.size());
                    return;
                }
                show(block, tier, all, opener, onBalance);
            });
        });
    }

    private void show(Block block, Tier tier, List<ItemStack> items, UUID opener, Money onBalance) {
        float yaw = blocks.yawOf(block);
        Location at = block.getLocation();
        blocks.takeAway(block);
        boolean[] given = new boolean[items.size()];
        ChestShow.play(plugin, at, yaw, tier, items, new ChestShow.Moments() {
            @Override
            public void opened() {
                Player online = server.getPlayer(opener);
                if (online == null) {
                    return;
                }
                effects.play(opener, tier.atLeast(Tier.LEGENDARY) ? Cues.MAGIC : Cues.REWARD);
                if (onBalance.isPositive()) {
                    // no coins to be had here (no cash in this economy): the money went onto the balance
                    Scheduling.entity(plugin, online, () -> messages.send(online, "fishing.chest.money",
                            "money", Fees.format(onBalance)));
                }
            }

            @Override
            public void revealed(int index) {
                hand(opener, at, items, given, index, index + 1);
            }

            @Override
            public void done(int next) {
                hand(opener, at, items, given, next, items.size());
                Player online = server.getPlayer(opener);
                if (online != null) {
                    Scheduling.entity(plugin, online, () -> {
                        profiles.openedChest(online, tier);
                        messages.send(online, "fishing.chest.opened", "chest", tier.chestName(),
                                "count", String.valueOf(items.size()));
                    });
                }
            }
        });
    }

    /** The owner takes a placed chest back into their bag (sneaking, left click). */
    public void pickUp(Player player, Block block) {
        Tier tier = blocks.tierOf(block).orElse(null);
        if (tier == null) {
            return;
        }
        if (!blocks.ownerOf(block).map(player.getUniqueId()::equals).orElse(false)) {
            messages.send(player, "fishing.chest.not-yours");
            effects.play(player.getUniqueId(), Cues.NO);
            return;
        }
        if (opening.contains(block.getLocation())) {
            return;
        }
        blocks.takeAway(block);
        player.getInventory().addItem(forge.chest(tier, 1)).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        effects.play(player.getUniqueId(), Cues.CLICK);
    }

    /** Things {@code from} to {@code to} (exclusive) not handed over yet: to the opener, or at the chest. */
    private void hand(UUID opener, Location at, List<ItemStack> items, boolean[] given, int from, int to) {
        List<ItemStack> now = new ArrayList<>();
        for (int n = Math.max(0, from); n < Math.min(to, items.size()); n++) {
            if (!given[n]) {
                given[n] = true;
                now.add(items.get(n).clone());
            }
        }
        if (now.isEmpty()) {
            return;
        }
        Player online = server.getPlayer(opener);
        if (online == null) {
            now.forEach(item -> at.getWorld().dropItemNaturally(at.clone().add(0.5, 0.5, 0.5), item));
            return;
        }
        Scheduling.entity(plugin, online, () -> {
            for (ItemStack item : now) {
                online.getInventory().addItem(item).values()
                        .forEach(left -> online.getWorld().dropItemNaturally(online.getLocation(), left));
            }
            effects.play(opener, Cues.CLICK);
        });
    }

    private ItemStack stack(Reward reward) {
        return switch (reward) {
            case Reward.Item item -> {
                Material material = Material.matchMaterial(item.material().toUpperCase(Locale.ROOT));
                yield new ItemStack(material == null ? Material.COD : material, item.amount());
            }
            case Reward.Book book -> enchantedBook(book.levels());
            case Reward.Fish fish -> forge.fish(fish.kind(), fish.amount());
            case Reward.Shard shard -> de.raindancer.core.content.enchant.EnchantShards.of(shard.enchantment(),
                    shard.amount());
            case Reward.Money money -> new ItemStack(Material.GOLD_NUGGET);
            case Reward.Skin token -> forge.skinToken(token.skin());
        };
    }

    private ItemStack enchantedBook(int levels) {
        ItemStack book = new ItemStack(Material.BOOK);
        return server.getItemFactory().enchantWithLevels(book, levels, false, random);
    }
}
