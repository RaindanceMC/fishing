package de.raindancer.modules.fishing.util;

import org.bukkit.Server;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.List;

/** The module's permissions, registered with their defaults so an owner sees them before writing a single node. */
public final class PermissionNodes {

    public static final String MENU = "rainsfishing.menu";
    public static final String CHEST = "rainsfishing.chest.open";
    public static final String UPGRADE = "rainsfishing.rod.upgrade";
    public static final String GIVE = "rainsfishing.give";
    public static final String SPOT = "rainsfishing.spot";
    /** Wearing a rod skin: a thank-you for supporters (a child of Core's rains.supporter). */
    public static final String SKINS = "rainsfishing.skins";

    private PermissionNodes() {
    }

    public static List<Permission> declared() {
        return List.of(
                new Permission(MENU, "Open the fishing screens", PermissionDefault.TRUE),
                new Permission(CHEST, "Open a sea chest", PermissionDefault.TRUE),
                new Permission(UPGRADE, "Upgrade a fishing rod", PermissionDefault.TRUE),
                new Permission(GIVE, "Give somebody fish, dishes or sea chests", PermissionDefault.OP),
                new Permission(SPOT, "Put a fishing spot where you are looking", PermissionDefault.OP),
                new Permission(SKINS, "Put a rod skin on a rod — supporters and operators", PermissionDefault.OP));
    }

    public static int register(Server server) {
        if (server == null) {
            return 0;
        }
        int added = 0;
        for (Permission permission : declared()) {
            if (server.getPluginManager().getPermission(permission.getName()) != null) {
                continue;
            }
            try {
                server.getPluginManager().addPermission(permission);
                added++;
            } catch (IllegalArgumentException alreadyThere) {
                // Registered by another copy between the check and the add.
            }
        }
        return added;
    }
}
