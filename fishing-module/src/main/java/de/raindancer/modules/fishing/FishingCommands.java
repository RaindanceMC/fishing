package de.raindancer.modules.fishing;

import de.raindancer.modules.api.ModuleCommand;
import de.raindancer.modules.fishing.command.FishingCommand;

import java.util.List;

/** What the module declares at bootstrap — before it runs, so the command holds a supplier, not the services. */
public final class FishingCommands {

    private static volatile FishingServices services;

    private FishingCommands() {
    }

    public static List<ModuleCommand> declared() {
        return List.of(
                ModuleCommand.of("fishing", "Your fishing level, skills, rod upgrades and fish collection",
                                new FishingCommand(FishingCommands::require))
                        .aliased("fish")
                        .taking("[rods|skills|rod|collection]"));
    }

    static void ready(FishingServices live) {
        services = live;
    }

    static void stopped() {
        services = null;
    }

    public static boolean isRunning() {
        return services != null;
    }

    private static FishingServices require() {
        FishingServices live = services;
        if (live == null) {
            throw new IllegalStateException("the fishing module is not running");
        }
        return live;
    }
}
