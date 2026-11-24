package de.raindancer.modules.fishing.screen;

import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.model.Aquarium;
import de.raindancer.modules.fishing.rules.TankRule;
import de.raindancer.modules.fishing.service.AquariumService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A whole aquarium, opened like a chest: one slot per fish it can hold, three per block of the tank. Nothing shown is
 * a real item; every click asks the tank to move a fish, so nothing can be copied out. Click a fish to take it,
 * shift-click fish in your inventory or click with fish on the cursor to put them in, one fish per slot. Every action
 * checks again that the tank is still there and that the viewer may still open it.
 */
public final class AquariumMenu extends Menu implements IFishingScreen {

    private static final Set<ClickType> TAKING = Set.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT,
            ClickType.SHIFT_RIGHT);
    private static final int AREA = MenuLayout.TOOLBAR_ROW * 9;
    private static final double REACH = 6;

    private final FishingServices services;
    private final Block anchor;

    public AquariumMenu(FishingServices services, Player viewer, Block anchor) {
        super(viewer, services.brand(), null);
        this.services = services;
        this.anchor = anchor;
    }

    private AquariumService tanks() {
        return services.aquariums();
    }

    @Override
    protected Component title() {
        int blocks = tanks().group(anchor).size();
        return Component.text(blocks > 1 ? "Aquarium · " + blocks + " tanks" : "Aquarium", NamedTextColor.DARK_GRAY);
    }

    @Override
    public String breadcrumb() {
        return "Aquarium";
    }

    @Override
    protected void render() {
        List<AquariumService.Slot> fish = tanks().contents(anchor);
        for (int slot = 0; slot < fish.size() && slot < AREA; slot++) {
            int at = slot;
            AquariumService.Slot shown = fish.get(slot);
            tanks().item(shown.resident()).ifPresent(item -> set(at, item, event -> {
                if (TAKING.contains(event.getClick())) {
                    take(shown);
                }
            }));
        }
    }

    @Override
    protected void decorate() {
        int capacity = Math.min(AREA, tanks().capacity(anchor));
        for (int slot = capacity; slot < AREA; slot++) {
            set(slot, Icons.filler(Material.BLACK_STAINED_GLASS_PANE));
        }
        fillRow(MenuLayout.TOOLBAR_ROW, Material.BLACK_STAINED_GLASS_PANE);
        toolbar(4, Icons.of(Material.TROPICAL_FISH, "<aqua>Put every fish in",
                "<gray>Every fish in your inventory goes in,", "<gray>one per slot, as far as there is room."),
                click -> putAll());
    }

    /** Still a tank, still within reach, still allowed; closes the menu and says so when not. */
    private boolean stillOpen() {
        boolean there = tanks().at(anchor).isPresent();
        boolean near = viewer.getWorld().equals(anchor.getWorld())
                && viewer.getLocation().distanceSquared(anchor.getLocation().add(0.5, 0.5, 0.5)) <= REACH * REACH;
        if (there && near && tanks().mayOpen(viewer, anchor)) {
            return true;
        }
        viewer.closeInventory();
        services.messages().send(viewer, "fishing.aquarium.gone");
        return false;
    }

    private void take(AquariumService.Slot slot) {
        if (!stillOpen()) {
            return;
        }
        Optional<ItemStack> taken = tanks().take(slot);
        taken.ifPresentOrElse(item -> {
            viewer.getInventory().addItem(item).values()
                    .forEach(left -> viewer.getWorld().dropItemNaturally(viewer.getLocation(), left));
            services.effects().play(viewer.getUniqueId(), Cues.CLICK);
        }, () -> services.effects().play(viewer.getUniqueId(), Cues.NO));
        refresh();
    }

    /**
     * Puts fish from {@code offered} in, one per slot, while there is room.
     *
     * @return what did not go in, or null when all of it did
     */
    private ItemStack putIn(ItemStack offered) {
        Optional<Aquarium.Resident> resident = tanks().resident(offered);
        if (resident.isEmpty()) {
            services.messages().send(viewer, "fishing.aquarium.only-fish");
            return offered;
        }
        int left = offered.getAmount();
        while (left > 0 && tanks().put(anchor, resident.get())) {
            left--;
        }
        if (left > 0) {
            services.messages().send(viewer, "fishing.aquarium.full", "most", String.valueOf(tanks().capacity(anchor)));
        }
        if (left == offered.getAmount()) {
            return offered;
        }
        services.effects().play(viewer.getUniqueId(), Cues.CLICK);
        if (left == 0) {
            return null;
        }
        ItemStack rest = offered.clone();
        rest.setAmount(left);
        return rest;
    }

    private void putAll() {
        if (!stillOpen()) {
            return;
        }
        ItemStack[] contents = viewer.getInventory().getStorageContents();
        boolean any = false;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || tanks().resident(stack).isEmpty()) {
                continue;
            }
            any = true;
            ItemStack rest = putIn(stack.clone());
            viewer.getInventory().setItem(slot, rest);
            if (rest != null) {
                break;
            }
        }
        if (!any) {
            services.messages().send(viewer, "fishing.aquarium.no-fish");
        }
        refresh();
    }

    /** A click with something on the cursor, on one of the tank's slots, puts it in. */
    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        ItemStack cursor = event.getCursor();
        if (slot >= 0 && slot < AREA && !cursor.isEmpty()) {
            event.setCancelled(true);
            if (!stillOpen()) {
                return;
            }
            viewer.setItemOnCursor(putIn(cursor.clone()));
            refresh();
            return;
        }
        super.handleClick(event);
    }

    @Override
    public boolean allowBottomInventoryInteraction() {
        return true;
    }

    /** Their own inventory: shift-click puts fish in; a double click may not gather anything out of the tank. */
    @Override
    public void handleBottomClick(InventoryClickEvent event) {
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        if (!event.isShiftClick()) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.isEmpty() || !stillOpen()) {
            return;
        }
        event.setCurrentItem(putIn(clicked.clone()));
        refresh();
    }

    @Override
    protected List<String> helpLines() {
        return List.of("<gray>Shift-click fish in your inventory to put",
                "<gray>them in, or click with fish on the cursor.",
                "<gray>Click a fish to take it out.",
                "<dark_gray>Tanks side by side or stacked are one aquarium:",
                "<dark_gray>three fish per tank, up to " + TankRule.MAX_TANKS + " tanks.");
    }

    @Override
    public String describe() {
        return "an aquarium's fish, all of its tanks together";
    }
}
