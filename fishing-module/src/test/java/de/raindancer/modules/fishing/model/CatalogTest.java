package de.raindancer.modules.fishing.model;

import de.raindancer.modules.fishing.store.CatalogReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogTest {

    private final Catalog catalog = CatalogReader.shipped();

    @Test
    @DisplayName("every tier up to mythic has fish, and every water has something to catch")
    void everyTierAndWaterIsCovered() {
        for (Tier tier : Tier.values()) {
            assertThat(catalog.ofTier(tier)).as(tier + " fish").isNotEmpty();
        }
        for (Water water : Water.values()) {
            assertThat(catalog.in(water)).as(water + " fish").isNotEmpty();
        }
    }

    @Test
    @DisplayName("mythic fish live in the void and nowhere else — the void is the best water")
    void mythicIsTheVoids() {
        for (FishKind kind : catalog.ofTier(Tier.MYTHIC)) {
            assertThat(kind.waters()).containsExactly(Water.VOID);
        }
    }

    @Test
    @DisplayName("ids are unique, and every dish ingredient that names a fish names a real one")
    void idsAndIngredientsHoldTogether() {
        Set<String> ids = new HashSet<>();
        catalog.fish().forEach(kind -> assertThat(ids.add(kind.id())).as(kind.id()).isTrue());
        catalog.dishes().forEach(dish -> assertThat(ids.add(dish.id())).as(dish.id()).isTrue());
        for (Dish dish : catalog.dishes()) {
            for (String ingredient : dish.ingredients()) {
                if (ingredient.equals("any:fish")) {
                    continue;
                }
                if (ingredient.startsWith("any:")) {
                    assertThat(Tier.parse(ingredient.substring(4))).as(dish.id()).isPresent();
                } else if (!ingredient.startsWith("minecraft:")) {
                    assertThat(catalog.fish(ingredient)).as(dish.id() + " needs " + ingredient).isPresent();
                }
            }
            assertThat(dish.food()).isBetween(1, 20);
        }
    }

    @Test
    @DisplayName("everyday meals take any fish at all, vanilla cod and salmon too — Fish 'n' Chips among them")
    void everydayMeals() {
        assertThat(catalog.dish("fish_and_chips")).isPresent();
        assertThat(catalog.dishes().stream().filter(dish -> dish.ingredients().contains("any:fish")).count())
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("every tier has a sushi")
    void sushiOnEveryTier() {
        for (Tier tier : Tier.values()) {
            assertThat(catalog.dishes()).as(tier.name()).anyMatch(dish -> dish.tier() == tier
                    && (dish.name().contains("Sushi") || dish.name().contains("Nigiri") || dish.name().contains("Maki")
                    || dish.name().contains("Roll")));
        }
    }

    @Test
    @DisplayName("every tier has a dish, so every catch can end up on a plate")
    void everyTierHasADish() {
        for (Tier tier : Tier.values()) {
            assertThat(catalog.dishes()).as(tier.name()).anyMatch(dish -> dish.tier() == tier);
        }
    }
}
