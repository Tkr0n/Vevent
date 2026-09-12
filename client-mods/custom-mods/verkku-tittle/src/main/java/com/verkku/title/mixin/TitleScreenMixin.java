package com.verkku.title.mixin;

import com.verkku.title.VerkkuTitleMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        TitleScreen screen = (TitleScreen) (Object) this;
        screen.clearWidgets();

        Minecraft client = Minecraft.getInstance();
        int centerX = screen.width / 2;
        int buttonWidth = 200;
        int buttonHeight = 20;

        Button connectButton = Button.builder(
                Component.literal("Conectar al Servidor"),
                button -> {
                    ServerAddress address = new ServerAddress(
                            VerkkuTitleMod.SERVER_ADDRESS,
                            VerkkuTitleMod.SERVER_PORT
                    );
                    ServerData serverData = new ServerData(
                            "VerkkuCraft",
                            address.toString(),
                            ServerData.Type.LAN
                    );
                    TransferState transferState = new TransferState(
                            Map.of(),
                            Map.of(),
                            false
                    );
                    ConnectScreen.startConnecting(
                            screen, client, address, serverData, false, transferState
                    );
                }
        ).bounds(centerX - buttonWidth / 2, screen.height - 50, buttonWidth, buttonHeight).build();

        screen.addRenderableWidget(connectButton);
    }
}
