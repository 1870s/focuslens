package dev.focuslens.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.focuslens.gui.FocusLensScreen;

/** Opens the settings from Mod Menu's mod list. Only loaded when Mod Menu is installed. */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return FocusLensScreen::new;
    }
}
