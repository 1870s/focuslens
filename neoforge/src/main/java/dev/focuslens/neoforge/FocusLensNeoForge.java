package dev.focuslens.neoforge;

import dev.focuslens.FocusLens;
import dev.focuslens.client.FocusLensClient;
import dev.focuslens.client.FocusLensHud;
import dev.focuslens.client.KeyBindings;
import dev.focuslens.gui.FocusLensScreen;
import dev.focuslens.mc.McCompat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = FocusLens.MOD_ID, dist = Dist.CLIENT)
public final class FocusLensNeoForge {
    public FocusLensNeoForge(IEventBus modBus, ModContainer container) {
        FocusLens.init();
        FocusLensClient.init();

        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            KeyMapping.Category category = new KeyMapping.Category(KeyBindings.CATEGORY_ID);
            event.registerCategory(category);
            KeyBindings.create(category).forEach(event::register);
        });
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAboveAll(
                Identifier.fromNamespaceAndPath(FocusLens.MOD_ID, "hud"), (graphics, deltaTracker) -> FocusLensHud.extract(graphics)));

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> FocusLensClient.onClientTick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, event -> {
            if (event.getScreen() instanceof VideoSettingsScreen screen) {
                event.addListener(Button.builder(Component.translatable("focuslens.button.settings"),
                                button -> McCompat.setScreen(Minecraft.getInstance(), new FocusLensScreen(screen)))
                        .bounds(screen.width - 96, 6, 90, 20)
                        .build());
            }
        });

        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new FocusLensScreen(parent));
    }
}
