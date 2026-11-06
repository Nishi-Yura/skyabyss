package jp.revati.skyabyss.client.mount;

import jp.revati.skyabyss.mount.MountData;
import jp.revati.skyabyss.mount.MountNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// 乗り物に名前を付ける (絆 II から。docs/design/38 の 8)。打って「決める」でサーバーへ送り、店の画面へ戻る。空なら名前を外す
public class MountNameScreen extends Screen {
    private final Screen parent;
    private final String current;
    private EditBox box;

    public MountNameScreen(Screen parent, String current) {
        super(Component.translatable("gui.skyabyss.mount.name_title"));
        this.parent = parent;
        this.current = current;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;
        box = new EditBox(font, cx - 80, cy - 10, 160, 20, Component.translatable("gui.skyabyss.mount.name_title"));
        box.setMaxLength(MountData.NAME_MAX);
        box.setValue(current);
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.mount.name_ok"), b -> done(true)).bounds(cx - 82, cy + 18, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.mount.name_cancel"), b -> done(false)).bounds(cx + 2, cy + 18, 80, 20).build());
    }

    private void done(boolean send) {
        if (send && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new MountNetwork.Rename(box.getValue()));
        }
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) {
            done(true);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, height / 2 - 30, 0xFFE9E0C8);
        g.drawCenteredString(font, Component.translatable("gui.skyabyss.mount.name_hint", MountData.NAME_MAX), width / 2, height / 2 + 46, 0xFF8A93A6);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
