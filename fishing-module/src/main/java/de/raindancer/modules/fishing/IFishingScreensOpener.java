package de.raindancer.modules.fishing;

import org.bukkit.entity.Player;

/** Opening the module's screens, so a command does not depend on the menu classes. */
public interface IFishingScreensOpener {

    void root(Player viewer);

    void skills(Player viewer);

    void rod(Player viewer);

    void collection(Player viewer);

    void rods(Player viewer);
}
