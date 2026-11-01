package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Tier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BoardRuleTest {

    private final LevelRule levels = new LevelRule(50);
    private final BoardRule rule = new BoardRule(levels);

    private final UUID ada = UUID.randomUUID();
    private final UUID bo = UUID.randomUUID();
    private final UUID cy = UUID.randomUUID();

    private Map<UUID, FisherProfile> everyone() {
        Map<UUID, FisherProfile> all = new LinkedHashMap<>();
        all.put(ada, FisherProfile.NEW.withExperience(levels.experienceFor(20)).withCatch("pebble_trout", 30)
                .withWeight("pebble_trout", 3.2).withCatch("astral_leviathan", 1).withWeight("astral_leviathan", 940));
        all.put(bo, FisherProfile.NEW.withExperience(levels.experienceFor(25)).withCatch("pebble_trout", 5)
                .withWeight("pebble_trout", 1.1));
        all.put(cy, FisherProfile.NEW);
        return all;
    }

    @Test
    @DisplayName("boards rank by level, by fish landed, by heaviest catch and by kinds found; nobody with nothing")
    void boards() {
        List<BoardRule.Entry> top = rule.top(everyone(), BoardRule.Board.LEVEL, 5);
        assertThat(top).extracting(BoardRule.Entry::player).containsExactly(bo, ada);
        assertThat(top.getFirst().value()).isEqualTo("level 25");

        assertThat(rule.top(everyone(), BoardRule.Board.FISH, 5)).extracting(BoardRule.Entry::player)
                .containsExactly(ada, bo);
        List<BoardRule.Entry> heaviest = rule.top(everyone(), BoardRule.Board.HEAVIEST, 5);
        assertThat(heaviest.getFirst().player()).isEqualTo(ada);
        assertThat(heaviest.getFirst().value()).isEqualTo("940.0 kg astral_leviathan");
        assertThat(rule.top(everyone(), BoardRule.Board.KINDS, 1)).extracting(BoardRule.Entry::player)
                .containsExactly(ada);
    }

    @Test
    @DisplayName("a fisher's own sheet says their level, how far to the next, and their best")
    void own() {
        BoardRule.Own own = rule.own(everyone().get(ada), 32);
        assertThat(own.level()).isEqualTo(20);
        assertThat(own.landed()).isEqualTo(31);
        assertThat(own.kinds()).isEqualTo(2);
        assertThat(own.heaviestFish()).isEqualTo("astral_leviathan");
        assertThat(own.chests()).isZero();
        assertThat(rule.own(FisherProfile.NEW.withChestOpened(Tier.MYTHIC), 32).chests()).isEqualTo(1);
    }
}
