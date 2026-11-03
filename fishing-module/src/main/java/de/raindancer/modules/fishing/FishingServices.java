package de.raindancer.modules.fishing;

import de.raindancer.core.RainsCore;
import de.raindancer.core.data.settings.SettingsStore;
import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.ui.chat.Brand;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.service.CatchService;
import de.raindancer.modules.fishing.service.ChestService;
import de.raindancer.modules.fishing.service.DishService;
import de.raindancer.modules.fishing.service.FishForge;
import de.raindancer.modules.fishing.service.LineService;
import de.raindancer.modules.fishing.service.ProfileService;
import de.raindancer.modules.fishing.service.RodService;
import de.raindancer.modules.fishing.service.SpotService;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

/** Everything the module has built, carried to listeners, screens and commands — it owns nothing. */
public record FishingServices(
        Plugin plugin,
        Server server,
        LogChannel log,
        Messages messages,
        Brand brand,
        RainsCore core,

        Supplier<FishingSettings> settings,
        SettingsStore<FishingSettings> store,

        Catalog catalog,
        FishForge forge,
        ProfileService profiles,
        RodService rods,
        SpotService spots,
        CatchService catches,
        LineService lines,
        ChestService chests,
        de.raindancer.modules.fishing.service.SeaChestBlocks seaChests,
        DishService dishes,
        de.raindancer.modules.fishing.service.RodWorkshopService workshop,
        de.raindancer.modules.fishing.service.BaitService baits,
        de.raindancer.modules.fishing.service.NetService nets,
        de.raindancer.modules.fishing.service.AutoService auto,
        IFishingScreensOpener screens) {

    public FishingSettings config() {
        return settings.get();
    }

    public Effects effects() {
        return core.effects();
    }
}
