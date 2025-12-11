package com.spacewaltz.decrees;

import com.spacewaltz.decrees.client.economy.ClientEconomyState;
import com.spacewaltz.decrees.client.economy.EconomyClientNetworking;
import com.spacewaltz.decrees.client.ui.HexarchateMainScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class SpacewaltzDecreesOfTheSixClient implements ClientModInitializer {

    private static KeyBinding OPEN_PANEL_KEY;

    @Override
    public void onInitializeClient() {
        // Register S2C handlers for the economy packets
        EconomyClientNetworking.init();

        // G keybinding
        OPEN_PANEL_KEY = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.decrees.open_hexarchate_panel",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_G,
                        "category.decrees"
                )
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_PANEL_KEY.wasPressed()) {
                openPanel(client);
            }
        });
    }

    private void openPanel(MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }

        // Request fresh snapshot from the server
        ClientEconomyState.requestSnapshot();

        client.setScreen(new HexarchateMainScreen());
    }
}
