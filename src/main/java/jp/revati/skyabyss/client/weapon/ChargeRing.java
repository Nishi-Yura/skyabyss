package jp.revati.skyabyss.client.weapon;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.weapon.MoveSet;
import jp.revati.skyabyss.weapon.WeaponStrikes;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// 近接の溜めの輪 (2026-11-04。溜めの弱い一撃はほぼ傷にならず (combat/MeleeHits)、満ちたかが見えないと理不尽に感じるため)。
// 近接の武器を振ったら、照準のまわりに点の輪を出し、溜まった分だけ上から時計回りに明るくする。
//   - 満ちた瞬間: 輪が金に光って少し広がりながら消える (FLASH tick) と、小さな澄んだ音 (設定 chargeSound)
//   - 同じ敵を殴れる間の空振り (サーバーの MeleeHits.Miss): 輪が灰色になって左右に小さく揺れ、鈍い音
//   - 満ちて光り終えたら輪は消える (待っている間は何も出さない)。弓・杖など近接でない手では出さない
//   - 輪を出す武器のあいだは、バニラの照準の下の溜めの印 (攻撃の印 = 照準) を隠す (二重に出さない)
// 一人称と肩越しの視点 (ShoulderCam) で描く。仲間を起こす輪が出ている間・場面の最中・画面を開いている間・F1・F3 では出さない。設定 chargeRing
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class ChargeRing {
    static final int RADIUS = 10;
    static final int FLASH = 8;
    static final int MISS = 8;
    static final int LIT = 0xF0F2E6C8;
    static final int TRACK = 0x58141414;
    static final int SHADOW = 0x60000000;
    static final int GOLD = 0xF2C14E;
    static final int GREY = 0xF09AA3AD;

    // 振ってから、満ちて光り終えるまで
    private static boolean active;
    private static float last = 1f;
    private static int flashAge = -1;
    private static int missAge = -1;
    // バニラの溜めの印を隠したときの元の設定
    private static AttackIndicatorStatus hidden;

    private ChargeRing() {
    }

    // 近接で殴る手か (預かりの武器は振りのある物、そのほかは剣・斧・槌・三叉槍)
    public static boolean melee(ItemStack stack) {
        MoveSet ms = WeaponStrikes.setOfAny(stack);
        if (ms != null) {
            return ms.strike > 0f;
        }
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem || stack.getItem() instanceof MaceItem
                || stack.getItem() instanceof TridentItem;
    }

    public static void receiveMiss() {
        Minecraft mc = Minecraft.getInstance();
        missAge = 0;
        active = true;
        if (Config.CHARGE_SOUND.get()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SHIELD_BLOCK, 1.7f, 0.22f));
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !melee(p.getMainHandItem())) {
            active = false;
            flashAge = -1;
            missAge = -1;
            last = 1f;
            return;
        }
        float f = p.getAttackStrengthScale(0f);
        // 溜めが戻った = 振った (持ち替えでも戻る)
        if (f < last - 0.05f) {
            active = true;
            flashAge = -1;
        }
        if (active && f >= 1f && last < 1f && flashAge < 0) {
            flashAge = 0;
            if (Config.CHARGE_SOUND.get()) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.9f, 0.3f));
            }
        } else if (flashAge >= 0 && ++flashAge > FLASH) {
            flashAge = -1;
            active = false;
        }
        if (missAge >= 0 && ++missAge > MISS) {
            missAge = -1;
        }
        // 満ちたまま空振りの揺れだけ出た
        if (active && f >= 1f && flashAge < 0 && missAge < 0) {
            active = false;
        }
        last = f;
    }

    static boolean visible(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (!Config.CHARGE_RING.get() || p == null || p.isSpectator() || mc.options.hideGui || mc.screen != null
                || mc.getDebugOverlay().showDebugScreen() || !melee(p.getMainHandItem())) {
            return false;
        }
        if (!mc.options.getCameraType().isFirstPerson() && !jp.revati.skyabyss.client.camera.ShoulderCam.active()) {
            return false;
        }
        return !jp.revati.skyabyss.client.cinematic.CinematicClient.active() && !jp.revati.skyabyss.party.client.DownedClient.aimingRevive();
    }

    // バニラの照準の下の溜めの印を、輪を出せる手のあいだだけ隠す (照準の層を描く間だけ設定を「なし」に)
    @SubscribeEvent
    static void onCrosshairPre(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (Config.CHARGE_RING.get() && mc.player != null && melee(mc.player.getMainHandItem())
                && mc.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR) {
            hidden = AttackIndicatorStatus.CROSSHAIR;
            mc.options.attackIndicator().set(AttackIndicatorStatus.OFF);
        }
    }

    @SubscribeEvent
    static void onCrosshairPost(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            return;
        }
        restore();
        Minecraft mc = Minecraft.getInstance();
        if (!visible(mc) || !(active || missAge >= 0)) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        GuiGraphics g = event.getGuiGraphics();
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        if (flashAge >= 0 && missAge < 0) {
            float t = Math.min(1f, (flashAge + partial) / FLASH);
            int a = Math.round(255 * (1f - t));
            ring(g, cx, cy, RADIUS + Math.round(t * 3), 1f, (a << 24) | GOLD, 0, (Math.round(0x60 * (1f - t)) << 24));
            return;
        }
        float f = mc.player.getAttackStrengthScale(partial);
        if (missAge >= 0) {
            int dx = missAge >= MISS - 2 ? 0 : (missAge % 2 == 0 ? 1 : -1);
            ring(g, cx + dx, cy, RADIUS, f, GREY, TRACK, SHADOW);
            return;
        }
        ring(g, cx, cy, RADIUS, f, LIT, TRACK, SHADOW);
    }

    // 照準の層の Post が来なかったとき (ほかの係が取り消した) も、設定は必ず戻す
    @SubscribeEvent
    static void onGuiPost(RenderGuiEvent.Post event) {
        restore();
    }

    private static void restore() {
        if (hidden != null) {
            Minecraft.getInstance().options.attackIndicator().set(hidden);
            hidden = null;
        }
    }

    // 点の輪: 半径 r の円の画素を上から時計回りに並べ、fill (0..1) の分を lit、残りを track で塗る。各点の外側に一画素の影
    static void ring(GuiGraphics g, int cx, int cy, int r, float fill, int lit, int track, int shadow) {
        int n = r * 8;
        int px = Integer.MIN_VALUE;
        int py = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            int x = cx + (int) Math.round(Math.sin(a) * r);
            int y = cy - (int) Math.round(Math.cos(a) * r);
            if (x == px && y == py) {
                continue;
            }
            px = x;
            py = y;
            int color = (i + 0.5f) / n <= fill ? lit : track;
            if ((shadow >>> 24) != 0) {
                int sx = cx + (int) Math.round(Math.sin(a) * (r + 1));
                int sy = cy - (int) Math.round(Math.cos(a) * (r + 1));
                g.fill(sx, sy, sx + 1, sy + 1, shadow);
            }
            if ((color >>> 24) != 0) {
                g.fill(x, y, x + 1, y + 1, color);
            }
        }
    }
}
