package dev.focuslens.fabric;

import dev.focuslens.FocusLens;
import dev.focuslens.client.FocusLensClient;
import dev.focuslens.client.FocusLensHud;
import dev.focuslens.client.KeyBindings;
import dev.focuslens.gui.FocusLensScreen;
import dev.focuslens.mc.McCompat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class FocusLensFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FocusLens.init();
        FocusLensClient.init();

        KeyMapping.Category category = KeyMapping.Category.register(KeyBindings.CATEGORY_ID);
        KeyBindings.create(category).forEach(KeyMappingHelper::registerKeyMapping);

        ClientTickEvents.END_CLIENT_TICK.register(FocusLensClient::onClientTick);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(FocusLens.MOD_ID, "hud"), (graphics, deltaTracker) -> FocusLensHud.extract(graphics));

        // A "FocusLens..." button in the top-right corner of Video Settings, where OptiFine kept its options.
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            if (screen instanceof VideoSettingsScreen) {
                Screens.getWidgets(screen).add(Button.builder(Component.translatable("focuslens.button.settings"),
                                button -> McCompat.setScreen(minecraft, new FocusLensScreen(screen)))
                        .bounds(width - 96, 6, 90, 20)
                        .build());
            }
        });
    }
}
