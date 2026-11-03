package de.raindancer.modules.fishing;

import de.raindancer.core.data.settings.SettingsStore;
import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.api.FlexModule;
import de.raindancer.modules.api.ModuleCommand;
import de.raindancer.modules.api.ModuleContext;
import de.raindancer.modules.api.ModuleInfo;
import de.raindancer.modules.fishing.listener.ChestListener;
import de.raindancer.modules.fishing.listener.DishListener;
import de.raindancer.modules.fishing.listener.FishListener;
import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.rules.BiteRule;
import de.raindancer.modules.fishing.rules.CatchRule;
import de.raindancer.modules.fishing.rules.ChestLootRule;
import de.raindancer.modules.fishing.rules.WaterRule;
import de.raindancer.modules.fishing.screen.CollectionMenu;
import de.raindancer.modules.fishing.screen.FishingRootMenu;
import de.raindancer.modules.fishing.screen.RodMenu;
import de.raindancer.modules.fishing.screen.SkillTreeMenu;
import de.raindancer.modules.fishing.service.CatchService;
import de.raindancer.modules.fishing.service.ChestService;
import de.raindancer.modules.fishing.service.DishService;
import de.raindancer.modules.fishing.service.FishForge;
import de.raindancer.modules.fishing.service.LineService;
import de.raindancer.modules.fishing.service.ProfileService;
import de.raindancer.modules.fishing.service.RodService;
import de.raindancer.modules.fishing.service.SpotService;
import de.raindancer.modules.fishing.store.CatalogReader;
import de.raindancer.modules.fishing.store.FishingPack;
import de.raindancer.modules.fishing.store.ProfileStore;
import de.raindancer.modules.fishing.util.PermissionNodes;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * Fishing worth doing. Shipped through the standard wrapper this is {@code RainsFishing}.
 *
 * <p>Thirty-odd fish in six tiers, each living in its own waters; sea chests from Driftwood to Void that bite now
 * and then and open with a show; fishing spots on the water near players; rods that take upgrades and fishers that
 * take levels and skills; lava and the void fished with the right line; dishes cooked from the catch.
 *
 * <h2>Order</h2>
 * The catalogue first (everything else is about it), then the items it makes, the profiles, the services, and only
 * then the listeners and timers that set it all moving.
 */
public final class FishingModule implements FlexModule {

    private static final ModuleInfo INFO = ModuleInfo.of("fishing", "Fishing", "0.5.1")
            .describedAs("New fish in six tiers, sea chests, fishing spots, rod upgrades and skills, lava and "
                    + "void fishing, and dishes cooked from the catch.")
            .by("Raindancer118");

    /** How often spots come and go, in ticks. */
    private static final long SPOT_PERIOD = 200L;

    private LogChannel log;
    private FishingServices services;
    private SpotService spots;
    private DishService dishes;
    private de.raindancer.modules.fishing.service.SeaChestBlocks seaChests;
    private de.raindancer.modules.fishing.service.RodWorkshopService workshop;
    private de.raindancer.modules.fishing.service.BaitService baits;
    private de.raindancer.modules.fishing.service.NetService nets;
    private de.raindancer.core.content.pack.ResourcePacks packs;

    @Override
    public ModuleInfo info() {
        return INFO;
    }

    @Override
    public void enable(ModuleContext context) {
        log = context.log();
        Server server = context.plugin().getServer();
        SettingsStore<FishingSettings> settings = context.settings(FishingSettings.class, FishingSettings.DEFAULTS);
        FishingSettings now = settings.current();

        context.core().messages().defineFrom(FishingModule.class.getResourceAsStream("messages.yml"),
                context.chat().brand()::chatPrefix);
        context.core().messages().seriousFrom(FishingModule.class.getResourceAsStream("messages-serious.yml"));
        int registered = PermissionNodes.register(server);
        if (registered > 0) {
            log.info("{} permission(s) registered.", registered);
        }

        Catalog catalog = CatalogReader.shipped();
        FishForge forge = new FishForge(catalog, context.core().items(), context.core().itemFactory());
        int defined = forge.defineAll();

        ProfileStore store = new ProfileStore(context.plugin().getDataFolder().toPath());
        if (!store.load()) {
            log.warn("fishers.yml could not be read; levels and skills are kept as they are and not changed until "
                    + "it can be.");
        }
        Random random = new Random();
        CatchRule catchRule = new CatchRule(catalog);
        ProfileService profiles = new ProfileService(context.plugin(), store, context.core().messages(),
                context.core().effects(), now);
        RodService rods = new RodService(context.plugin(), context.core().messages(), context.core().effects(),
                profiles, store, forge, now);
        spots = new SpotService(context.plugin(), log, context.core().messages(), new WaterRule(), catchRule,
                System::currentTimeMillis, now,
                new de.raindancer.modules.fishing.store.FixedSpotStore(context.plugin().getDataFolder().toPath()));
        CatchService catches = new CatchService(context.plugin(), server, context.core(), context.core().messages(), context.core().effects(), forge,
                profiles, spots, catchRule, random, now);
        baits = new de.raindancer.modules.fishing.service.BaitService(context.plugin(), server, log, catalog, forge);
        nets = new de.raindancer.modules.fishing.service.NetService(context.plugin(), server, log, forge,
                new de.raindancer.modules.fishing.store.NetStore(context.plugin().getDataFolder().toPath()));
        if (!nets.load()) {
            log.warn("The fishing nets could not be read now; they are read again the first time one is opened.");
        }
        catches.nets(nets);
        de.raindancer.modules.fishing.service.AutoService auto = new de.raindancer.modules.fishing.service
                .AutoService(context.plugin(), context.core().messages(), context.core().effects(), profiles, random);
        LineService lines = new LineService(context.plugin(), context.core().messages(), context.core().effects(),
                catches, profiles, new BiteRule(), random, baits, auto);
        seaChests = new de.raindancer.modules.fishing.service.SeaChestBlocks(context.plugin(), server);
        ChestService chests = new ChestService(context.plugin(), server, context.core().messages(),
                context.core().effects(), forge, profiles, seaChests, new ChestLootRule(catalog), random, now);
        dishes = new DishService(context.plugin(), server, log, context.core().messages(), catalog, forge);
        dishes.lastingFor(player -> profiles.perks(player).more(de.raindancer.modules.fishing.model.Perk.DISH));
        workshop = new de.raindancer.modules.fishing.service.RodWorkshopService(context.plugin(), server,
                context.core().messages(), context.core().effects(), profiles, store, forge, catalog);

        settings.onChange(profiles::settings);
        settings.onChange(rods::settings);
        settings.onChange(spots::settings);
        settings.onChange(catches::settings);
        settings.onChange(chests::settings);

        services = new FishingServices(context.plugin(), server, log, context.core().messages(),
                context.chat().brand(), context.core(), settings::current, settings, catalog, forge, profiles, rods,
                spots, catches, lines, chests, seaChests, dishes, workshop, baits, nets, auto, new LiveScreens());

        context.listener(new FishListener(services));
        context.listener(new ChestListener(services));
        context.listener(new de.raindancer.modules.fishing.listener.SpotListener(services));
        context.listener(new de.raindancer.modules.fishing.listener.NetListener(services));
        context.listener(new DishListener(services));
        context.listener(new de.raindancer.modules.fishing.listener.RodListener(services));
        FishingCommands.ready(services);

        int recipes = dishes.registerRecipes();
        int rodRecipes = workshop.registerRecipes();
        int baitRecipes = baits.registerRecipes() + nets.registerRecipe();
        server.getOnlinePlayers().forEach(online -> Scheduling.entity(context.plugin(), online,
                () -> {
                    dishes.discover(online);
                    workshop.discover(online);
                    baits.discover(online);
                    nets.discover(online);
                }));
        packs = context.core().resourcePacks();
        try {
            FishingPack.offer(context.plugin(), packs);
        } catch (IOException unwritable) {
            log.warn("The fishing looks could not be written out ({}); fish and chests show as their vanilla items.",
                    unwritable.getMessage());
        }

        seaChests.start();
        // The shop buys our fish and dishes at our prices; a fish is never sold as the cod it is made of.
        de.raindancer.modules.fishing.rules.PriceRule prices = new de.raindancer.modules.fishing.rules.PriceRule();
        de.raindancer.core.social.economy.ItemPricer pricer = stack -> {
            long base = settings.current().fishPriceMinor();
            java.util.Optional<Long> price = forge.fishOf(stack).map(kind -> prices.fish(kind.tier(), base))
                    .or(() -> forge.dishOf(stack).map(dish -> prices.dish(dish.tier(), base)));
            return price.filter(each -> each > 0).map(de.raindancer.core.social.economy.Money::of);
        };
        de.raindancer.core.social.economy.ItemPrices.provide(context.plugin(), pricer);
        context.closeWith(() -> de.raindancer.core.social.economy.ItemPrices.retract(pricer));
        // Rod skins are a thank-you for supporters: wearing one hangs off Core's rains.supporter.
        try {
            var supporter = de.raindancer.core.social.support.SupporterPermission.permission(server.getPluginManager());
            supporter.getChildren().put(de.raindancer.modules.fishing.util.PermissionNodes.SKINS, true);
            supporter.recalculatePermissibles();
        } catch (LinkageError olderCore) {
            log.info("RainsCore knows no supporters yet; rod skins are worn by whoever is given rainsfishing.skins.");
        }
        de.raindancer.modules.fishing.service.FishingBoards boards = new de.raindancer.modules.fishing.service
                .FishingBoards(server, context.core().messages(), store, profiles, catalog);
        // Screens for /screen came with Core 2.10; the leaderboards with 2.23 — an older Core simply has neither.
        try {
            var screens = context.core().screens();
            boards.register(context.plugin(), screens);
            context.closeWith(() -> boards.unregister(screens));
        } catch (LinkageError olderCore) {
            log.info("RainsCore is too old for fishing's screens and leaderboards; there are none until it is updated.");
        }
        SpotService fixing = spots;
        Scheduling.async(context.plugin(), () -> log.info("{} fixed fishing spot(s).",
                fixing.loadFixed(server::getWorld)));
        var timer = Scheduling.globalTimer(context.plugin(), SPOT_PERIOD, SPOT_PERIOD,
                task -> spots.tick(server.getOnlinePlayers()));
        if (timer != null) {
            context.closeWith(timer::cancel);
        }

        log.info("Fishing is up: {} fish and {} dishes ({} recipes), {} rods, {} new item definition(s); spots {}; "
                        + "{} bait and net recipes.",
                catalog.fish().size(), catalog.dishes().size(), recipes, rodRecipes, defined,
                now.spotsEnabled() ? "on, " + now.spotsPerPlayer() + " per player" : "off", baitRecipes);
    }

    /** Opening this module's screens without the command knowing the menu classes. */
    private final class LiveScreens implements IFishingScreensOpener {

        @Override
        public void root(Player viewer) {
            new FishingRootMenu(services, viewer).open();
        }

        @Override
        public void skills(Player viewer) {
            new SkillTreeMenu(services, viewer, new FishingRootMenu(services, viewer)).open();
        }

        @Override
        public void rod(Player viewer) {
            new RodMenu(services, viewer, new FishingRootMenu(services, viewer)).open();
        }

        @Override
        public void rods(Player viewer) {
            new de.raindancer.modules.fishing.screen.RodsMenu(services, viewer, new FishingRootMenu(services, viewer))
                    .open();
        }

        @Override
        public void collection(Player viewer) {
            new CollectionMenu(services, viewer, new FishingRootMenu(services, viewer)).open();
        }
    }

    @Override
    public List<ModuleCommand> commands() {
        return FishingCommands.declared();
    }

    /** Takes the spots down, the recipes and the looks out; nothing else here holds anything that would be lost. */
    @Override
    public void disable() {
        if (spots != null) {
            int cleared = spots.clear();
            if (cleared > 0 && log != null) {
                log.info("{} fishing spot(s) taken down.", cleared);
            }
        }
        if (seaChests != null) {
            seaChests.stop();
        }
        if (dishes != null) {
            dishes.unregisterRecipes();
        }
        if (workshop != null) {
            workshop.unregisterRecipes();
        }
        if (baits != null) {
            baits.unregisterRecipes();
        }
        if (nets != null) {
            nets.unregisterRecipe();
        }
        if (packs != null) {
            FishingPack.withdraw(packs);
        }
        FishingCommands.stopped();
    }
}
