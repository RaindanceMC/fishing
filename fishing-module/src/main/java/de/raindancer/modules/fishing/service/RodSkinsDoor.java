package de.raindancer.modules.fishing.service;

import de.raindancer.core.ui.cosmetic.CosmeticDoor;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.screen.SkinsMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/** Rod skins as a door on the server's cosmetics page, where everything worn is put on. */
public final class RodSkinsDoor implements CosmeticDoor {

    private final FishingServices services;

    public RodSkinsDoor(FishingServices services) {
        this.services = services;
    }

    @Override
    public String id() {
        return "fishing:rod-skins";
    }

    @Override
    public ItemStack icon(Player viewer) {
        return Icons.of(Material.FISHING_ROD, "<light_purple>Rod skins",
                "<gray>A look for the rod in your hand.", "<gray>Earned by fishing, worn by supporters.", "",
                "<dark_gray>Hold a rod and click.");
    }

    @Override
    public Optional<String> locked(Player viewer) {
        return Optional.empty();
    }

    @Override
    public void open(Player viewer, Menu parent) {
        new SkinsMenu(services, viewer, parent).open();
    }

    /** Whether the cosmetics page shows the doors, so the rod skins live there and not in /fishing rod. */
    public static boolean onCosmeticsPage() {
        try {
            return de.raindancer.core.ui.cosmetic.CosmeticDoors.hosted();
        } catch (LinkageError olderCore) {
            return false;
        }
    }
}
