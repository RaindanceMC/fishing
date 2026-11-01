package de.raindancer.modules.fishing.service;

import de.raindancer.core.ui.board.Leaderboard;
import de.raindancer.core.ui.board.Leaderboards;
import de.raindancer.core.ui.hologram.ScreenKind;
import de.raindancer.core.ui.hologram.Screens;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.rules.BoardRule;
import de.raindancer.modules.fishing.rules.WeightRule;
import de.raindancer.modules.fishing.store.ProfileStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fishing's rankings — level, fish landed, heaviest catch, kinds found — offered to Core's leaderboards (for
 * statues and anything else that shows a ranking) and as screens for {@code /screen}, with everybody's own record
 * beside them. The profiles live in memory, so asking is cheap.
 */
public final class FishingBoards implements IFishingService {

    public static final List<String> KINDS = List.of("fishing-you", "fishing-top", "fishing-heaviest",
            "fishing-landed", "fishing-collectors");
    public static final List<String> BOARDS = List.of("fishing:level", "fishing:fish", "fishing:heaviest",
            "fishing:kinds");
    private static final int SHOWN = 5;

    private final Server server;
    private final Messages messages;
    private final ProfileStore store;
    private final ProfileService profiles;
    private final Catalog catalog;
    private final Map<UUID, String> names = new ConcurrentHashMap<>();

    public FishingBoards(Server server, Messages messages, ProfileStore store, ProfileService profiles,
                         Catalog catalog) {
        this.server = server;
        this.messages = messages;
        this.store = store;
        this.profiles = profiles;
        this.catalog = catalog;
    }

    @Override
    public void settings(FishingSettings settings) {
        // The boards read the profiles; the level rule they use comes from the profile service, kept current there.
    }

    private BoardRule rule() {
        return new BoardRule(profiles.levels(), id -> catalog.fish(id).map(kind -> kind.name()).orElse(id));
    }

    public void register(Plugin owner, Screens screens) {
        ClassLoader loader = getClass().getClassLoader();
        Leaderboards.register(loader, new Leaderboard("fishing:level", "Fishing level",
                "The fishers with the highest fishing level.", Material.FISHING_ROD,
                () -> standings(BoardRule.Board.LEVEL)));
        Leaderboards.register(loader, new Leaderboard("fishing:fish", "Fish landed",
                "The fishers who have landed the most fish.", Material.COD, () -> standings(BoardRule.Board.FISH)));
        Leaderboards.register(loader, new Leaderboard("fishing:heaviest", "Heaviest catch",
                "The heaviest fish anybody has landed.", Material.ANVIL, () -> standings(BoardRule.Board.HEAVIEST)));
        Leaderboards.register(loader, new Leaderboard("fishing:kinds", "Fish collection",
                "The fishers who have found the most kinds of fish.", Material.BOOK,
                () -> standings(BoardRule.Board.KINDS)));
        if (screens == null) {
            return;
        }
        screens.register(owner, ScreenKind.personal("fishing-you", "Your fishing record",
                "Everybody's own fishing level, catches, best fish and chests.", Material.FISHING_ROD,
                viewer -> own(viewer.id())));
        screens.register(owner, ScreenKind.everyone("fishing-top", "Fishing leaderboard",
                "The fishers with the highest fishing level.", Material.GOLDEN_HELMET,
                () -> board("fishing.screen.top", BoardRule.Board.LEVEL)));
        screens.register(owner, ScreenKind.everyone("fishing-heaviest", "Heaviest catches",
                "The heaviest fish anybody has landed.", Material.ANVIL,
                () -> board("fishing.screen.heaviest", BoardRule.Board.HEAVIEST)));
        screens.register(owner, ScreenKind.everyone("fishing-landed", "Most fish landed",
                "The fishers who have landed the most fish.", Material.COD,
                () -> board("fishing.screen.landed", BoardRule.Board.FISH)));
        screens.register(owner, ScreenKind.everyone("fishing-collectors", "Fish collectors",
                "The fishers who have found the most kinds of fish.", Material.BOOK,
                () -> board("fishing.screen.collectors", BoardRule.Board.KINDS)));
    }

    public void unregister(Screens screens) {
        BOARDS.forEach(Leaderboards::unregister);
        if (screens != null) {
            KINDS.forEach(screens::unregister);
        }
    }

    private List<Leaderboard.Standing> standings(BoardRule.Board board) {
        List<Leaderboard.Standing> out = new ArrayList<>();
        for (BoardRule.Entry entry : rule().top(store.all(), board, 10)) {
            out.add(new Leaderboard.Standing(entry.player(), nameOf(entry.player()), entry.value()));
        }
        return out;
    }

    private Component board(String heading, BoardRule.Board board) {
        Component text = messages.get(heading);
        List<BoardRule.Entry> top = rule().top(store.all(), board, SHOWN);
        if (top.isEmpty()) {
            return text.append(Component.newline()).append(messages.get("fishing.screen.nobody"));
        }
        int place = 1;
        for (BoardRule.Entry entry : top) {
            text = text.append(Component.newline()).append(messages.get("fishing.screen.row", "place",
                    String.valueOf(place++), "player", nameOf(entry.player()), "value", entry.value()));
        }
        return text;
    }

    private Component own(UUID viewer) {
        BoardRule.Own own = rule().own(profiles.of(viewer), catalog.fish().size());
        return messages.get("fishing.screen.you", "level", String.valueOf(own.level()),
                "next", String.valueOf(own.toNext()), "landed", String.valueOf(own.landed()),
                "kinds", String.valueOf(own.kinds()), "of", String.valueOf(own.ofKinds()),
                "best", own.heaviestFish().isEmpty() ? "-" : WeightRule.format(own.heaviest()) + " "
                        + own.heaviestFish(), "chests", String.valueOf(own.chests()));
    }

    private String nameOf(UUID player) {
        return names.computeIfAbsent(player, id -> {
            String name = server.getOfflinePlayer(id).getName();
            return name == null ? id.toString().substring(0, 8) : name;
        });
    }
}
