package de.raindancer.modules.fishing.store;

import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Skill;
import de.raindancer.modules.fishing.model.Tier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileStoreTest {

    @Test
    @DisplayName("a profile written and read back is the same profile")
    void roundTrip() throws Exception {
        FisherProfile profile = FisherProfile.NEW.withExperience(1234).withSpent(300)
                .withSkill(Skill.QUICK_BITE, 3).withSkill(Skill.DOUBLE_CATCH, 5)
                .withCatch("pebble_trout", 7).withCatch("astral_leviathan", 1)
                .withChestOpened(Tier.MYTHIC).withChestOpened(Tier.MYTHIC).withChestOpened(Tier.COMMON)
                .withRod(de.raindancer.modules.fishing.model.RodTier.REEF)
                .withRod(de.raindancer.modules.fishing.model.RodTier.VOID)
                .withWeight("pebble_trout", 2.5).withWeight("pebble_trout", 1.0);
        YamlConfiguration yaml = new YamlConfiguration();
        ProfileStore.write(yaml, "fishers.someone", profile);
        YamlConfiguration reread = new YamlConfiguration();
        reread.loadFromString(yaml.saveToString());
        assertThat(ProfileStore.read(reread, "fishers.someone")).isEqualTo(profile);
        assertThat(profile.heaviest("pebble_trout")).as("a lighter one is no record").isEqualTo(2.5);
    }

    @Test
    @DisplayName("nothing written is nothing read, and a hand-edited rank is clamped")
    void absentAndClamped() throws Exception {
        assertThat(ProfileStore.read(new YamlConfiguration(), "fishers.nobody")).isNull();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("fishers:\n  x:\n    experience: -5\n    skills:\n      quick_bite: 99\n      nonsense: 2\n");
        FisherProfile read = ProfileStore.read(yaml, "fishers.x");
        assertThat(read.experience()).isZero();
        assertThat(read.rank(Skill.QUICK_BITE)).isEqualTo(Skill.MOST);
        assertThat(read.skills()).hasSize(1);
    }
}
