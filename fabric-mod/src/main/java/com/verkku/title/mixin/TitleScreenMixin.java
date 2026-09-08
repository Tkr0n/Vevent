package com.verkku.title.mixin;

import com.verkku.title.VerkkuTitleMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    private static final Identifier LOGO_TEXTURE = Identifier.of("verkku", "textures/title/logo.png");

    @Redirect(
        method = "render",
        require = 0,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/LogoDrawer;draw(Lnet/minecraft/client/gui/DrawContext;IF)V"
        )
    )
    private void verkku$redirectLogo(LogoDrawer instance, DrawContext context, int width, float alpha) {
    }

    @Redirect(
        method = "render",
        require = 0,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/LogoDrawer;draw(Lnet/minecraft/client/gui/DrawContext;IFI)V"
        )
    )
    private void verkku$redirectLogoWithY(LogoDrawer instance, DrawContext context, int width, float alpha, int y) {
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void verkku$modifyTitleScreen(CallbackInfo ci) {
        TitleScreen screen = (TitleScreen) (Object) this;
        int centerX = screen.width / 2;
        int buttonY = screen.height / 2 + 20;

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
            this.remove(element);
        }

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Conectar"),
                button -> connectToServer()
        ).dimensions(centerX - 100, buttonY, 200, 20).build());
    }

    @Unique
    private void connectToServer() {
        VerkkuTitleMod.LOGGER.info("Connecting to {}:{}", VerkkuTitleMod.SERVER_ADDRESS, VerkkuTitleMod.SERVER_PORT);
        try {
            ServerAddress address = new ServerAddress(VerkkuTitleMod.SERVER_ADDRESS, VerkkuTitleMod.SERVER_PORT);
            ServerInfo info = new ServerInfo("VerkkuCraft", address.toString(), ServerInfo.ServerType.OTHER);
            ConnectScreen.connect((Screen) (Object) this, client, address, info, false, new CookieStorage(new HashMap<>()));
        } catch (Exception e) {
            VerkkuTitleMod.LOGGER.error("Failed to connect: {}", e.getMessage(), e);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void verkku$renderLogo(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        TitleScreen screen = (TitleScreen) (Object) this;
        int logoWidth = 256;
        int logoHeight = 80;
        context.drawTexture(LOGO_TEXTURE, (screen.width - logoWidth) / 2, 30, logoWidth, logoHeight, 0.0F, 0.0F, logoWidth, logoHeight, logoWidth, logoHeight);
    }
}
