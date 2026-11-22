package de.raindancer.modules.fishing.service;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.economy.EconomyResult;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.core.world.npc.Attendants;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.Mutation;
import de.raindancer.modules.fishing.rules.MerchantRule;
import de.raindancer.modules.fishing.store.FishingPack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The fishmonger: an attendant who buys fish — at the shop's price, double for the three he wants today, times what a
 * rare variant is worth. Staff put one where they stand; any number may stand around the server.
 */
public final class MerchantService implements IFishingService {

    private static final NamespacedKey TAG = new NamespacedKey(FishingPack.NAMESPACE, "fishmonger");
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    static final String SOURCE = "fishing.fishmonger";

    /** What one sale would be: the bag slots and the stacks in them, and what they fetch in minor units. */
    public record Offer(List<Integer> slots, List<ItemStack> stacks, long minor, int fish) {
    }

    private final Plugin plugin;
    private final Messages messages;
    private final Effects effects;
    private final FishForge forge;
    private final MerchantRule rule;
    private volatile FishingSettings settings;

    public MerchantService(Plugin plugin, Messages messages, Effects effects, FishForge forge, Catalog catalog,
                           FishingSettings settings) {
        this.plugin = plugin;
        this.messages = messages;
        this.effects = effects;
        this.forge = forge;
        this.rule = new MerchantRule(catalog);
        this.settings = settings;
    }

    @Override
    public void settings(FishingSettings fresh) {
        settings = fresh;
    }

    public static LocalDate today() {
        return LocalDate.now(ZoneId.systemDefault());
    }

    public List<FishKind> wanted() {
        return rule.wanted(today());
    }

    public boolean isWanted(FishKind kind) {
        return rule.isWanted(kind, today());
    }

    /** What he pays for one of these, in minor units; zero when the shop takes no fish. */
    public long price(FishKind kind, Mutation mutation) {
        return rule.price(kind, mutation, today(), settings.fishPriceMinor());
    }

    /** One stack of fish, or every fish in the bag. */
    public Offer offer(Player player, Predicate<ItemStack> which) {
        List<Integer> slots = new ArrayList<>();
        List<ItemStack> stacks = new ArrayList<>();
        long minor = 0;
        int fish = 0;
        ItemStack[] bag = player.getInventory().getStorageContents();
        for (int slot = 0; slot < bag.length; slot++) {
            ItemStack stack = bag[slot];
            if (stack == null || !which.test(stack)) {
                continue;
            }
            Optional<FishKind> kind = forge.fishOf(stack);
            if (kind.isEmpty()) {
                continue;
            }
            slots.add(slot);
            stacks.add(stack.clone());
            minor += price(kind.get(), forge.mutationOf(stack).orElse(null)) * stack.getAmount();
            fish += stack.getAmount();
        }
        return new Offer(slots, stacks, minor, fish);
    }

    public Offer everything(Player player) {
        return offer(player, stack -> true);
    }

    /** The offer made again from the bag as it is now, for the same kinds of stack. */
    private Offer everythingIn(Player player, Offer stale) {
        return offer(player, stack -> stale.stacks().stream().anyMatch(stack::isSimilar));
    }

    /**
     * Sells what the offer holds: the fish leave the bag at once, the money is paid off the server's threads, and if
     * the bank cannot pay they come back.
     */
    public void sell(Player player, Offer offer) {
        if (offer.fish() == 0 || offer.minor() <= 0) {
            messages.send(player, "fishing.merchant.nothing");
            effects.play(player.getUniqueId(), Cues.NO);
            return;
        }
        // The bag may have changed since the offer was made: a slot that no longer holds the same stack is not sold.
        for (int n = 0; n < offer.slots().size(); n++) {
            ItemStack now = player.getInventory().getItem(offer.slots().get(n));
            if (now == null || !now.equals(offer.stacks().get(n))) {
                sell(player, everythingIn(player, offer));
                return;
            }
        }
        List<ItemStack> taken = new ArrayList<>(offer.stacks());
        offer.slots().forEach(slot -> player.getInventory().setItem(slot, null));
        Money money = Money.of(offer.minor());
        Scheduling.async(plugin, () -> {
            EconomyResult paid = Fees.pay(player.getUniqueId(), money, "Fishmonger", SOURCE);
            Scheduling.entity(plugin, player, () -> {
                if (paid.succeeded()) {
                    effects.play(player.getUniqueId(), Cues.REWARD);
                    messages.send(player, "fishing.merchant.sold", "count", String.valueOf(offer.fish()),
                            "money", Fees.format(paid.amount()));
                } else {
                    taken.forEach(stack -> player.getInventory().addItem(stack).values()
                            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left)));
                    effects.play(player.getUniqueId(), Cues.NO);
                    messages.send(player, "fishing.merchant.cannot-pay");
                }
            });
        });
    }

    /** A fishmonger where the player stands. On the player's own region thread. */
    public Mannequin place(Player player) {
        Mannequin placed = Attendants.place(player, MINI.deserialize("<aqua><bold>Fishmonger"),
                MINI.deserialize("<gray>Right click to sell fish"), Attendants.look(null),
                made -> made.getPersistentDataContainer().set(TAG, PersistentDataType.BYTE, (byte) 1));
        Attendants.dress(placed, settings.merchantSkin());
        placed.getEquipment().setItemInMainHand(new ItemStack(Material.FISHING_ROD));
        placed.getEquipment().setItemInOffHand(new ItemStack(Material.COD));
        return placed;
    }

    public static boolean isMerchant(Entity entity) {
        return entity instanceof Mannequin && entity.getPersistentDataContainer().has(TAG);
    }

    /** Takes away the nearest fishmonger within a few blocks; whether there was one. */
    public boolean remove(Player player) {
        Optional<Entity> nearest = Attendants.nearest(player, 4, MerchantService::isMerchant);
        nearest.ifPresent(Entity::remove);
        return nearest.isPresent();
    }
}
