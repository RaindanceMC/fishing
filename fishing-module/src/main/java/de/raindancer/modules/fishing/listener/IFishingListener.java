package de.raindancer.modules.fishing.listener;

import org.bukkit.event.Listener;

import java.util.UUID;

/** The events. A listener that remembers a player is told when they leave. */
public interface IFishingListener extends Listener {

    void forget(UUID player);

    default String describe() {
        String name = getClass().getSimpleName();
        return name.isEmpty() ? getClass().getName() : name;
    }
}
