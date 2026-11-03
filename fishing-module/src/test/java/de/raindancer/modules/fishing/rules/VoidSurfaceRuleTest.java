package de.raindancer.modules.fishing.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoidSurfaceRuleTest {

    private final VoidSurfaceRule rule = new VoidSurfaceRule();

    @Test
    @DisplayName("over the End's void a hook comes to rest on the surface at y 47, however far it fell past it")
    void restsAtTheSurface() {
        assertThat(rule.restAt(true, true, 47.0)).hasValue(VoidSurfaceRule.SURFACE);
        assertThat(rule.restAt(true, true, 39.5)).as("it fell past it between two looks").hasValue(VoidSurfaceRule.SURFACE);
        assertThat(rule.restAt(true, true, 47.6)).hasValue(VoidSurfaceRule.SURFACE);
    }

    @Test
    @DisplayName("still above the surface it falls on; nothing below it, or outside the End, is no void water")
    void otherwise() {
        assertThat(rule.restAt(true, true, 60.0)).isEmpty();
        assertThat(rule.restAt(true, false, 40.0)).as("ground below").isEmpty();
        assertThat(rule.restAt(false, true, 40.0)).as("the overworld's void").isEmpty();
    }
}
