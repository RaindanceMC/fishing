package de.raindancer.modules.fishing.listener;

import de.raindancer.modules.fishing.FishingServices;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.UUID;

/** Fixed fishing spots are shown while their chunk is loaded; their displays are never saved with the world. */
public final class SpotListener implements IFishingListener {

    private final FishingServices services;

    public SpotListener(FishingServices services) {
        this.services = services;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        services.spots().chunkLoaded(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        services.spots().chunkUnloaded(event.getChunk());
    }

    @Override
    public void forget(UUID player) {
        // Nothing remembered per player.
    }
}
