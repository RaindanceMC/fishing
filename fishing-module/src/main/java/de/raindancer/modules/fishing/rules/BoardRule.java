package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.FisherProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Who leads at fishing, and what a fisher's own record says — plain numbers, worded by the screens. */
public final class BoardRule implements IFishingRule {

    public enum Board { LEVEL, FISH, HEAVIEST, KINDS }

    /** One line of a board; {@code value} is what it is ranked by, as shown. */
    public record Entry(UUID player, double score, String value) {
    }

    /** A fisher's own record. */
    public record Own(int level, long toNext, int landed, int kinds, int ofKinds, String heaviestFish,
                      double heaviest, int chests) {
    }

    private final LevelRule levels;
    private final java.util.function.UnaryOperator<String> fishName;

    public BoardRule(LevelRule levels) {
        this(levels, java.util.function.UnaryOperator.identity());
    }

    /** @param fishName a fish's name from its id, for the heaviest-catch board */
    public BoardRule(LevelRule levels, java.util.function.UnaryOperator<String> fishName) {
        this.levels = levels;
        this.fishName = fishName;
    }

    public List<Entry> top(Map<UUID, FisherProfile> everyone, Board board, int shown) {
        List<Entry> entries = new ArrayList<>();
        everyone.forEach((player, profile) -> {
            Entry entry = switch (board) {
                case LEVEL -> profile.experience() <= 0 ? null : new Entry(player, profile.experience(),
                        "level " + levels.level(profile.experience()));
                case FISH -> profile.totalCaught() <= 0 ? null : new Entry(player, profile.totalCaught(),
                        profile.totalCaught() + " fish");
                case KINDS -> profile.caught().isEmpty() ? null : new Entry(player, profile.caught().size(),
                        profile.caught().size() + " kinds");
                case HEAVIEST -> profile.heaviest().entrySet().stream().max(Map.Entry.comparingByValue())
                        .map(best -> new Entry(player, best.getValue(),
                                WeightRule.format(best.getValue()) + " " + fishName.apply(best.getKey())))
                        .orElse(null);
            };
            if (entry != null) {
                entries.add(entry);
            }
        });
        entries.sort(Comparator.comparingDouble(Entry::score).reversed());
        return entries.subList(0, Math.min(shown, entries.size()));
    }

    public Own own(FisherProfile profile, int ofKinds) {
        int level = levels.level(profile.experience());
        long toNext = level >= levels.highest() ? 0 : levels.experienceFor(level + 1) - profile.experience();
        var best = profile.heaviest().entrySet().stream().max(Map.Entry.comparingByValue());
        int chests = profile.chests().values().stream().mapToInt(Integer::intValue).sum();
        return new Own(level, toNext, profile.totalCaught(), profile.caught().size(), ofKinds,
                best.map(Map.Entry::getKey).map(fishName).orElse(""), best.map(Map.Entry::getValue).orElse(0.0), chests);
    }

    @Override
    public String describe() {
        return "who leads at fishing by level, fish landed, heaviest catch and kinds found";
    }
}
