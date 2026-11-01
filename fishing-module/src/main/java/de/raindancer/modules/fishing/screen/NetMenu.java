package de.raindancer.modules.fishing.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.service.NetService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A fishing net, opened like a shulker box: its fish on the top three rows. Nothing up there is a real item — every
 * click asks the net to move something, the vault's way, because a copy that can be picked up is a duplicated fish.
 * Click a stack to take it (right click: half), shift-click fish in your inventory or click with fish on the cursor
 * to put them in. Only fish go in.
 */
public final class NetMenu extends Menu implements IFishingScreen {

    private static final Set<ClickType> TAKING = Set.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT,
            ClickType.SHIFT_RIGHT);

    private final FishingServices services;
    private final UUID net;
    private volatile List<ItemStack> held = List.of();

    private NetMenu(FishingServices services, Player viewer, UUID net) {
        super(viewer, services.brand(), null);
        this.services = services;
        this.net = net;
    }

    /** Opens the net once its fish are read — off the server's threads. */
    public static void show(FishingServices services, Player viewer, UUID net) {
        NetMenu menu = new NetMenu(services, viewer, net);
        menu.load(menu::open);
    }

    private void load(Runnable then) {
        de.raindancer.core.platform.util.Scheduling.async(services.plugin(), () -> {
            List<ItemStack> now = services.nets().contents(net);
            de.raindancer.core.platform.util.Scheduling.entity(services.plugin(), viewer, () -> {
                held = now;
                then.run();
            });
        });
    }

    @Override
    protected Component title() {
        return Component.text("Fishing Net", NamedTextColor.DARK_GRAY);
    }

    @Override
    public String breadcrumb() {
        return "Net";
    }

    @Override
    protected void render() {
        List<ItemStack> items = held;
        for (int slot = 0; slot < NetService.CAPACITY && slot < items.size(); slot++) {
            int index = slot;
            ItemStack shown = items.get(index);
            set(slot, shown, event -> {
                if (TAKING.contains(event.getClick())) {
                    int amount = event.getClick() == ClickType.RIGHT ? Math.max(1, shown.getAmount() / 2)
                            : shown.getAmount();
                    take(index, amount);
                }
            });
        }
    }

    @Override
    protected void decorate() {
        for (int slot = NetService.CAPACITY; slot < MenuLayout.TOOLBAR_ROW * 9; slot++) {
            set(slot, Icons.filler(Material.BLACK_STAINED_GLASS_PANE));
        }
        fillRow(MenuLayout.TOOLBAR_ROW, Material.BLACK_STAINED_GLASS_PANE);
        toolbar(4, Icons.of(Material.COD, "<aqua>Put every fish in",
                "<gray>Every fish in your inventory goes into the net,", "<gray>as far as it has room."),
                click -> putAll());
    }

    private void take(int index, int amount) {
        services.nets().take(viewer, net, index, amount, taken -> {
            taken.ifPresent(fish -> {
                Map<Integer, ItemStack> left = viewer.getInventory().addItem(fish);
                if (!left.isEmpty()) {
                    // A full bag: what does not fit goes back into the net, never on the floor.
                    services.messages().send(viewer, "fishing.net.bag-full");
                    services.nets().put(viewer, net, List.copyOf(left.values()), this::giveBack);
                }
            });
            load(this::refresh);
        });
    }

    private void putIn(ItemStack offered) {
        if (!services.nets().holds(offered)) {
            services.messages().send(viewer, "fishing.net.only-fish");
            giveBack(List.of(offered));
            return;
        }
        services.nets().put(viewer, net, List.of(offered), left -> {
            if (!left.isEmpty()) {
                services.messages().send(viewer, "fishing.net.full");
            }
            giveBack(left);
            load(this::refresh);
        });
    }

    private void putAll() {
        List<ItemStack> fish = new ArrayList<>();
        ItemStack[] contents = viewer.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (services.nets().holds(stack)) {
                fish.add(stack.clone());
                viewer.getInventory().setItem(slot, null);
            }
        }
        if (fish.isEmpty()) {
            services.messages().send(viewer, "fishing.net.no-fish");
            return;
        }
        services.nets().put(viewer, net, fish, left -> {
            if (!left.isEmpty()) {
                services.messages().send(viewer, "fishing.net.full");
            }
            giveBack(left);
            load(this::refresh);
        });
    }

    /** What the net did not take: the cursor if it is free, else the inventory, else the floor. */
    private void giveBack(List<ItemStack> back) {
        for (ItemStack each : back) {
            if (viewer.getItemOnCursor().isEmpty() && viewer.getOpenInventory().getTopInventory().getHolder(false)
                    == this) {
                viewer.setItemOnCursor(each);
                continue;
            }
            viewer.getInventory().addItem(each).values()
                    .forEach(rest -> viewer.getWorld().dropItem(viewer.getLocation(), rest));
        }
    }

    /** A click with something on the cursor, anywhere in the net, puts it in. */
    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        ItemStack cursor = event.getCursor();
        if (slot >= 0 && slot < NetService.CAPACITY && !cursor.isEmpty()) {
            event.setCancelled(true);
            ItemStack offered = cursor.clone();
            viewer.setItemOnCursor(null);
            putIn(offered);
            return;
        }
        super.handleClick(event);
    }

    @Override
    public boolean allowBottomInventoryInteraction() {
        return true;
    }

    /** Their own inventory: shift-click puts fish in; a double click may not gather copies out of the net. */
    @Override
    public void handleBottomClick(InventoryClickEvent event) {
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && services.forge().isNet(clicked)) {
            // The net itself stays put while it is open.
            event.setCancelled(true);
            return;
        }
        if (!event.isShiftClick()) {
            return;
        }
        event.setCancelled(true);
        if (clicked == null || clicked.isEmpty()) {
            return;
        }
        ItemStack offered = clicked.clone();
        if (!services.nets().holds(offered)) {
            services.messages().send(viewer, "fishing.net.only-fish");
            return;
        }
        event.setCurrentItem(null);
        putIn(offered);
    }

    @Override
    protected List<String> helpLines() {
        return List.of("<gray>Shift-click fish in your inventory to put",
                "<gray>them in, or click with fish on the cursor.",
                "<gray>Click a stack to take it out, right click for half.",
                "<dark_gray>Only fish go into a net. While you carry one,",
                "<dark_gray>what you catch goes straight in.");
    }

    @Override
    public String describe() {
        return "a fishing net's fish";
    }
}
