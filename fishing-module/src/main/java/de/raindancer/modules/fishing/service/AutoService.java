package de.raindancer.modules.fishing.service;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.presence.Away;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.rules.AutoRule;
import de.raindancer.modules.fishing.util.PermissionNodes;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto-Reel and Auto-Rethrow from the skill tree: a bite reels itself in a moment later, and after a catch the line
 * goes back out. The rethrow rests for a fisher marked away (Core's {@link Away}) and after thirty casts without the
 * fisher looking around, unless they hold {@link PermissionNodes#AUTO_UNGUARDED}.
 */
public final class AutoService implements IFishingService {

    private static final de.raindancer.core.ui.effect.Effect THROW = de.raindancer.core.ui.effect.Effect.of(
            new de.raindancer.core.ui.effect.SoundCue("entity.fishing_bobber.throw", 0.5f, 0.4f));

    private final Plugin plugin;
    private final Messages messages;
    private final de.raindancer.core.ui.effect.Effects effects;
    private final ProfileService profiles;
    private final Random random;
    private final AutoRule rule = new AutoRule();
    private final Map<UUID, AutoRule.Watch> watches = new ConcurrentHashMap<>();
    /** The last reason a fisher was told the rethrow rests, so it is said once, not every catch. */
    private final Map<UUID, AutoRule.Stop> told = new ConcurrentHashMap<>();
    /** Fishers whose line this service is putting out right now: their cast is not one by hand. */
    private final Set<UUID> casting = ConcurrentHashMap.newKeySet();

    public AutoService(Plugin plugin, Messages messages, de.raindancer.core.ui.effect.Effects effects,
                       ProfileService profiles, Random random) {
        this.plugin = plugin;
        this.effects = effects;
        this.messages = messages;
        this.profiles = profiles;
        this.random = random;
    }

    @Override
    public void settings(FishingSettings settings) {
        // Nothing to swap: the guard's limits are the rule's.
    }

    /** A cast went out; one by hand starts the guard's count again. */
    public void cast(Player player) {
        UUID id = player.getUniqueId();
        if (!casting.contains(id)) {
            watches.computeIfAbsent(id, any -> new AutoRule.Watch()).castByHand();
            told.remove(id);
        }
    }

    /** Something bit: with Auto-Reel, the rod reels in a moment later, as a player would. */
    public void bitten(Player player, FishHook hook) {
        if (!rule.reels(profiles.perks(player.getUniqueId()))) {
            return;
        }
        Scheduling.entityLater(plugin, player, rule.reelDelay(random), () -> {
            if (!hook.isValid() || !player.isOnline() || !player.equals(hook.getShooter())) {
                return;
            }
            EquipmentSlot hand = handOf(player);
            if (hand == null) {
                return;
            }
            ItemStack rod = player.getInventory().getItem(hand);
            int before = damageOf(rod);
            int wear = hook.retrieve(hand);
            ItemStack after = player.getInventory().getItem(hand);
            // Paper's retrieve lands the catch but, unlike a click, may leave the rod unworn.
            if (wear > 0 && after.getType() == Material.FISHING_ROD && damageOf(after) == before) {
                player.getInventory().setItem(hand, after.damage(wear, player));
            }
            player.swingHand(hand);
        });
    }

    /** A catch was landed: with Auto-Rethrow, the line goes back out, unless the guard says rest. */
    public void landed(Player player) {
        if (!rule.rethrows(profiles.perks(player.getUniqueId()))) {
            return;
        }
        Scheduling.entityLater(plugin, player, rule.rethrowDelay(random), () -> rethrow(player));
    }

    private void rethrow(Player player) {
        UUID id = player.getUniqueId();
        if (!player.isOnline() || player.isDead() || player.getFishHook() != null) {
            return;
        }
        EquipmentSlot hand = handOf(player);
        if (hand == null) {
            return;
        }
        AutoRule.Stop stop = rule.rethrow(watches.computeIfAbsent(id, any -> new AutoRule.Watch()),
                Away.isAway(id), player.hasPermission(PermissionNodes.AUTO_UNGUARDED),
                player.getLocation().getYaw(), player.getLocation().getPitch());
        if (stop != AutoRule.Stop.NONE) {
            if (told.put(id, stop) != stop) {
                messages.send(player, stop == AutoRule.Stop.AWAY ? "fishing.auto.away" : "fishing.auto.still");
            }
            return;
        }
        told.remove(id);
        casting.add(id);
        try {
            FishHook hook = player.launchProjectile(FishHook.class);
            int lure = player.getInventory().getItem(hand).getEnchantmentLevel(Enchantment.LURE);
            if (lure > 0) {
                // Launched by hand the hook knows nothing of the rod's Lure: take its five seconds a level off here.
                int least = Math.max(1, hook.getMinWaitTime() - lure * 100);
                hook.setWaitTime(least, Math.max(least, hook.getMaxWaitTime() - lure * 100));
            }
            PlayerFishEvent event = new PlayerFishEvent(player, null, hook, hand, PlayerFishEvent.State.FISHING);
            plugin.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                hook.remove();
                return;
            }
            player.swingHand(hand);
            var at = player.getLocation();
            effects.playAt(at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), THROW);
        } finally {
            casting.remove(id);
        }
    }

    private static EquipmentSlot handOf(Player player) {
        if (player.getInventory().getItemInMainHand().getType() == Material.FISHING_ROD) {
            return EquipmentSlot.HAND;
        }
        if (player.getInventory().getItemInOffHand().getType() == Material.FISHING_ROD) {
            return EquipmentSlot.OFF_HAND;
        }
        return null;
    }

    private static int damageOf(ItemStack stack) {
        return stack != null && stack.getItemMeta() instanceof Damageable damageable ? damageable.getDamage() : 0;
    }

    public void forget(UUID player) {
        watches.remove(player);
        told.remove(player);
        casting.remove(player);
    }
}
