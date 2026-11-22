package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.Duo;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.model.Water;
import de.raindancer.modules.fishing.store.CatalogReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DuoRuleTest {

    private static final Catalog CATALOG = CatalogReader.shipped();
    private final DuoRule rule = new DuoRule(CATALOG);

    @Test
    @DisplayName("Rain and Hydro have their own legendary fish: the hoodfin in frozen water, the dragonet in rivers")
    void theOwnersFish() {
        assertThat(CATALOG.fish("raindancer_hoodfin")).hasValueSatisfying(kind -> {
            assertThat(kind.tier()).isEqualTo(Tier.LEGENDARY);
            assertThat(kind.waters()).containsExactly(Water.FROZEN);
        });
        assertThat(CATALOG.fish("hydro_dragonet")).hasValueSatisfying(kind -> {
            assertThat(kind.tier()).isEqualTo(Tier.LEGENDARY);
            assertThat(kind.waters()).containsExactly(Water.RIVER);
        });
        assertThat(CATALOG.duo("rain_and_hydro")).hasValueSatisfying(duo ->
                assertThat(duo.fish()).containsExactly("raindancer_hoodfin", "hydro_dragonet"));
    }

    @Test
    @DisplayName("a duo is done with one of each caught, not with two of one")
    void doneWithOneOfEach() {
        FisherProfile hoodfins = FisherProfile.NEW.withCatch("raindancer_hoodfin", 2);
        assertThat(rule.completed(FisherProfile.NEW)).isEmpty();
        assertThat(rule.completed(hoodfins)).isEmpty();
        assertThat(rule.completed(hoodfins.withCatch("hydro_dragonet", 1)))
                .extracting(Duo::id).containsExactly("rain_and_hydro");
    }

    @Test
    @DisplayName("only a catch of one of its fish can finish a duo; a trout never asks")
    void onlyItsFishFinishIt() {
        FisherProfile both = FisherProfile.NEW.withCatch("raindancer_hoodfin", 1).withCatch("hydro_dragonet", 1);
        assertThat(rule.finishedBy(both, "hydro_dragonet")).extracting(Duo::id).containsExactly("rain_and_hydro");
        assertThat(rule.finishedBy(both, "pebble_trout")).isEmpty();
        assertThat(rule.finishedBy(FisherProfile.NEW.withCatch("hydro_dragonet", 1), "hydro_dragonet")).isEmpty();
    }

    @Test
    @DisplayName("every duo names two or more real fish, and its title badge is set")
    void everyDuoHoldsTogether() {
        assertThat(CATALOG.duos()).isNotEmpty();
        for (Duo duo : CATALOG.duos()) {
            assertThat(duo.fish()).as(duo.id()).hasSizeGreaterThanOrEqualTo(2).doesNotHaveDuplicates();
            duo.fish().forEach(id -> assertThat(CATALOG.fish(id)).as(duo.id() + " needs " + id).isPresent());
            assertThat(duo.title()).isNotBlank();
            assertThat(duo.badge()).isNotBlank();
            assertThat(duo.achievement()).isEqualTo("duo-" + duo.id());
        }
    }

    @Test
    @DisplayName("the title worn is the first earned duo's; nobody wears one they have not earned")
    void titleWorn() {
        assertThat(rule.worn(key -> false)).isEmpty();
        assertThat(rule.worn(key -> key.equals("duo-rain_and_hydro")))
                .hasValueSatisfying(duo -> assertThat(duo.id()).isEqualTo("rain_and_hydro"));
    }

    @Test
    @DisplayName("the duo's dish needs one of each of its fish")
    void theDuoDish() {
        assertThat(CATALOG.dish("rain_and_hydro_platter")).hasValueSatisfying(dish -> {
            assertThat(dish.tier()).isEqualTo(Tier.LEGENDARY);
            assertThat(dish.ingredients()).contains("raindancer_hoodfin", "hydro_dragonet");
        });
    }

    @Test
    @DisplayName("a duo naming a fish the catalogue does not have is refused when read, not found out on a catch")
    void unknownFishRefused() {
        String json = """
                {"fish": [{"id": "a", "name": "A", "tier": "common", "waters": ["river"]}], "dishes": [],
                 "duos": [{"id": "x", "title": "X", "badge": "X", "fish": ["a", "ghost"]}]}""";
        assertThatThrownBy(() -> CatalogReader.read(new StringReader(json)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ghost");
    }

    @Test
    @DisplayName("an older catalogue without duos still reads, with none")
    void noDuosIsFine() {
        String json = """
                {"fish": [{"id": "a", "name": "A", "tier": "common", "waters": ["river"]}], "dishes": []}""";
        assertThat(CatalogReader.read(new StringReader(json)).duos()).isEmpty();
    }
}
