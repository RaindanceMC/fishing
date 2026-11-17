package de.raindancer.modules.fishing.util;

/** Who a rod skin thanks, in words: Core's answer (supporters, or Prime members), "supporters" on an older Core. */
public final class Thanked {

    private Thanked() {
    }

    public static String who() {
        try {
            return de.raindancer.core.social.support.Thanks.who();
        } catch (LinkageError olderCore) {
            return "supporters";
        }
    }

    public static String command() {
        try {
            return de.raindancer.core.social.support.Thanks.command();
        } catch (LinkageError olderCore) {
            return "/support";
        }
    }
}
