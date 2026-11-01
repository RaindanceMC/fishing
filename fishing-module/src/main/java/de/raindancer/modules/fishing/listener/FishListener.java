package de.raindancer.modules.fishing.listener;

import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.model.Bait;
import de.raindancer.modules.fishing.model.Catch;
import de.raindancer.modules.fishing.model.Water;
import de.raindancer.modules.fishing.rules.BiteRule;
import de.raindancer.modules.fishing.service.CatchService;
import org.bukkit.Material;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;

/**
 * The rod: a cast is quickened by the hook and quick bite and watched for lava and the void; a vanilla catch is
 * swapped for what the catch rule decides and goes straight into the bag; a reel-in lands what bit in lava or the void.
 */
public final class FishListener implements IFishingListener {

    private static final Set<Material> VANILLA_FISH =
            Set.of(Material.COD, Material.SALMON, Material.PUFFERFISH, Material.TROPICAL_FISH);

    private final FishingServices services;
    private final BiteRule bites = new BiteRule();
    private final de.raindancer.modules.fishing.rules.VanillaCatchRule junkOrFish =
            new de.raindancer.modules.fishing.rules.VanillaCatchRule();

    public FishListener(FishingServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        FishHook hook = event.getHook();
        switch (event.getState()) {
            case FISHING -> {
                if (!services.workshop().mayUse(player, services.catches().rodOf(player))) {
                    // One of the module's rods that this fisher has not unlocked: the line does not leave the rod.
                    event.setCancelled(true);
                    services.messages().send(player, "fishing.rods.not-yours", "rod",
                            services.catches().rodOf(player).tier().title());
                    services.effects().play(player.getUniqueId(), de.raindancer.core.ui.effect.Cues.NO);
                    return;
                }
                cast(player, hook);
            }
            case CAUGHT_FISH -> caught(event, player, hook);
            default -> {
                services.lines().reeled(player, hook);
                // Reeled in empty, or the line came in some other way: the bait went with the cast.
                services.baits().take(hook.getUniqueId());
            }
        }
    }

    private void cast(Player player, FishHook hook) {
        // An anvil may have put a fishing enchantment on since: the lore says what the rod does now.
        services.forge().describe(services.catches().rodStack(player));
        Bait bait = services.baits().castWith(player, hook).orElse(null);
        double factor = bites.waitFactor(services.catches().rodOf(player),
                services.profiles().of(player.getUniqueId()), false, bait);
        if (factor < 1.0) {
            int least = Math.max(1, (int) Math.round(hook.getMinWaitTime() * factor));
            int most = Math.max(least, (int) Math.round(hook.getMaxWaitTime() * factor));
            hook.setWaitTime(least, most);
        }
        services.lines().cast(player, hook);
    }

    private void caught(PlayerFishEvent event, Player player, FishHook hook) {
        if (!(event.getCaught() instanceof Item item)) {
            return;
        }
        Water water = services.spots().waterAt(hook.getLocation(), false, false).orElse(Water.RIVER);
        ItemStack hooked = item.getItemStack();
        // Fish and junk are swapped for a catch of ours; treasure (an enchanted rod among it) stays.
        boolean fish = junkOrFish.replaced(hooked.getType().getKey().getKey())
                && !(hooked.getType() == Material.FISHING_ROD && !hooked.getEnchantments().isEmpty());
        Catch caught = services.catches().decide(player, hook.getLocation(), water, fish,
                services.baits().take(hook.getUniqueId()));
        ItemStack landed = services.catches().stack(caught).orElse(item.getItemStack());
        // Not thrown at the fisher like vanilla's: it comes up out of the water, turns, and goes into the bag —
        // never into a neighbour's hands; a full bag puts the rest at the fisher's feet.
        item.remove();
        services.catches().bringUp(player, hook.getLocation(), water, landed);
        services.catches().credit(player, caught);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @Override
    public void forget(UUID player) {
        services.lines().forget(player);
    }
}
