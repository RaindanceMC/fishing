package de.raindancer.modules.fishing.screen;

import de.raindancer.core.ui.menu.ConfirmMenu;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.modules.fishing.FishingServices;
import org.bukkit.entity.Player;

import java.util.List;

/** Core's confirmation, so this module's danger buttons can be seen to ask first. */
public final class ConfirmScreen extends ConfirmMenu implements IFishingScreen {

    public ConfirmScreen(FishingServices services, Player viewer, Menu parent, String question,
                         List<String> consequences, Runnable onYes) {
        super(viewer, services.brand(), parent, question, consequences, onYes);
    }

    @Override
    public String describe() {
        return "asking before something that cannot be undone";
    }
}
