package com.verkku.title.mixin;

import com.verkku.title.VerkkuTitleMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    @Shadow protected MinecraftClient client;

    @Shadow protected abstract void remove(Element element);

    @Shadow public abstract Element addDrawableChild(Element element);

    private static final Identifier LOGO_TEXTURE = new Identifier("verkku", "textures/title/logo.png");

    @Inject(method = "init", at = @At("TAIL"))
    private void verkku$modifyTitleScreen(CallbackInfo ci) {
        TitleScreen screen = (TitleScreen) (Object) this;
        int centerX = screen.width / 2;
        int buttonY = screen.height / 2 + 20;

        // Collect buttons to remove (can't modify list while iterating)
        List<Element> toRemove = new ArrayList<>();
        for (Element element : screen.children()) {
            if (element instanceof ButtonWidget button) {
                String text = button.getMessage().getString().toLowerCase();
                if (text.contains("singleplayer") || text.contains("single player")
                        || text.contains("multijugador") || text.contains("multiplayer")
                        || text.contains("realm") || text.contains("realms")) {
                    toRemove.add(button);
                }
            }
        }
        for (Element element : toRemove) {
            remove(element);
        }

        // Add connect button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Conectar"),
                button -> {
                    VerkkuTitleMod.LOGGER.info("Connecting to {}:{}", VerkkuTitleMod.SERVER_ADDRESS, VerkkuTitleMod.SERVER_PORT);
                    ServerAddress address = new ServerAddress(VerkkuTitleMod.SERVER_ADDRESS, VerkkuTitleMod.SERVER_PORT);
                    ServerInfo info = new ServerInfo("VerkkuCraft", VerkkuTitleMod.SERVER_ADDRESS, ServerInfo.ServerType.OTHER);
                    client.setScreen(null);
                    ConnectScreen.connect(screen, client, address, info, false);
                }
        ).dimensions(centerX - 100, buttonY, 200, 20).build());
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void verkku$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        TitleScreen screen = (TitleScreen) (Object) this;
        int logoWidth = 256;
        int logoHeight = 80;
        context.drawTexture(LOGO_TEXTURE, (screen.width - logoWidth) / 2, 30, 0, 0, logoWidth, logoHeight, logoWidth, logoHeight);
    }
}
