package jp.revati.skyabyss.client.pouch;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.client.PartyMarkers;
import jp.revati.skyabyss.pouch.Pouch;
import jp.revati.skyabyss.pouch.PouchEntries;
import jp.revati.skyabyss.pouch.PouchUsable;
import jp.revati.skyabyss.pouch.QuickPick;
import jp.revati.skyabyss.titan.StoneWhaleEntity;
import jp.revati.skyabyss.titan.storm.StormWingEntity;
import jp.revati.skyabyss.titan.tide.TideShellEntity;
import jp.revati.skyabyss.titan.tide.chapter.TideConchItem;
import jp.revati.skyabyss.titan.storm.WeathercockItem;
import jp.revati.skyabyss.titan.travel.TitanCallItem;
import jp.revati.skyabyss.titan.travel.TitanKind;
import jp.revati.skyabyss.titan.travel.TitanWaymarkBlock;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// 一枚札 (docs/design/22 の 10.5): ホットバーの左 (左手の枠のある側) に、旅嚢の短押し (Y) で使う道具を 1 枚だけ出す。
// 場面に合わせて自動で入れ替わる (QuickPick): 巨神の乗り場・巨神のそば = 呼び笛・呼び貝、海の上 = 潮見の貝、嵐 = 風見鶏、そのほか = 前回の道具。
// 旅嚢の画面で右クリックして置いた道具 (pin) と、輪で選んだ道具は、はずすまで自動より優先する。
// 場面の最中・画面を開いている間・F1・デバッグ画面では出さない。設定 pouchQuickCard で消せる (消すと短押しは前回の道具)
public final class QuickCard {
    private static final ResourceLocation SPRITE_LEFT = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_left");
    private static final ResourceLocation SPRITE_RIGHT = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_right");
    private static final int SPRITE_W = 29;
    private static final int SPRITE_H = 24;
    // 左手の枠が出ているときの、札との間
    private static final int GAP = 3;
    // 入れ替わりの強調 (ms) と、名前を出している間
    private static final long HIGHLIGHT_MS = 1600;
    private static final long NAME_MS = 2600;
    private static final int GOLD = 0xF2C14E;
    // 見回りの間隔 (tick) と、巨神・乗り場・嵐を探す範囲
    private static final int SCAN_INTERVAL = 10;
    private static final double TITAN_RANGE = 128;
    private static final int WAYMARK_RADIUS = 6;

    private static QuickPick.Ctx ctx = QuickPick.Ctx.NONE;
    private static int scanIn;
    // 今の札 (表示用。前回と違えば強調を始める)
    @Nullable
    private static ResourceLocation shown;
    private static long changedAt;

    private QuickCard() {
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        static void onLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "pouch_quick_card"), QuickCard::render);
        }
    }

    // ---------------------------------------------------------------- 選ぶ
    // 毎 tick (PouchClient から)。見回りは SCAN_INTERVAL ごと
    static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            shown = null;
            return;
        }
        if (--scanIn <= 0) {
            scanIn = SCAN_INTERVAL;
            ctx = scan(mc);
        }
        ResourceLocation now = pick(mc);
        if (!java.util.Objects.equals(now, shown)) {
            // 初めて出るとき (shown が null) は強調しない
            if (shown != null && now != null) {
                changedAt = Util.getMillis();
            }
            shown = now;
        }
    }

    // 短押しが今使う道具 (なければ null)。サーバーに送る前の最後の選び直し
    @Nullable
    static ResourceLocation pick(Minecraft mc) {
        if (mc.player == null) {
            return null;
        }
        return QuickPick.pick(candidates(mc), ctx, pinned(), PouchClient.last());
    }

    private static List<QuickPick.Cand> candidates(Minecraft mc) {
        List<QuickPick.Cand> out = new ArrayList<>();
        for (PouchEntries.Entry e : PouchUsable.usable(mc.player)) {
            out.add(cand(e));
        }
        return out;
    }

    static QuickPick.Cand cand(PouchEntries.Entry e) {
        if (e.item() instanceof TitanCallItem call) {
            return new QuickPick.Cand(e.id(), QuickPick.Role.CALL, call.titanId());
        }
        if (e.item() instanceof TideConchItem) {
            return new QuickPick.Cand(e.id(), QuickPick.Role.SEA, null);
        }
        if (e.item() instanceof WeathercockItem) {
            return new QuickPick.Cand(e.id(), QuickPick.Role.STORM, null);
        }
        return new QuickPick.Cand(e.id(), QuickPick.Role.OTHER, null);
    }

    // 今の場面 (巨神・乗り場・海・嵐)
    private static QuickPick.Ctx scan(Minecraft mc) {
        net.minecraft.client.multiplayer.ClientLevel level = mc.level;
        boolean overworld = level.dimension() == Level.OVERWORLD;
        String titan = null;
        boolean wing = false;
        double best = TITAN_RANGE * TITAN_RANGE;
        for (Entity e : level.entitiesForRendering()) {
            String kind = null;
            if (e instanceof StoneWhaleEntity) {
                kind = TitanKind.STONE_WHALE.id();
            } else if (e instanceof TideShellEntity) {
                kind = TitanKind.TIDE_SHELL.id();
            } else if (e instanceof StormWingEntity) {
                wing = true;
                kind = jp.revati.skyabyss.progress.Titans.THUNDER_WING;
            } else if (e instanceof jp.revati.skyabyss.titan.ember.EmberTitanEntity ember && ember.isSurfaced()) {
                kind = jp.revati.skyabyss.progress.Titans.EMBER;
            }
            if (kind != null) {
                double d = e.distanceToSqr(mc.player);
                if (d < best) {
                    best = d;
                    titan = kind;
                }
            }
        }
        boolean waymark = false;
        BlockPos p = mc.player.blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-WAYMARK_RADIUS, -3, -WAYMARK_RADIUS), p.offset(WAYMARK_RADIUS, 3, WAYMARK_RADIUS))) {
            if (level.getBlockState(q).getBlock() instanceof TitanWaymarkBlock) {
                waymark = true;
                break;
            }
        }
        boolean sea = overworld && (mc.player.isInWater() || level.getFluidState(p.below()).is(FluidTags.WATER)
                || mc.player.getVehicle() instanceof TideShellEntity || mc.player.getVehicle() instanceof Boat);
        boolean storm = overworld && (level.getRainLevel(1f) > 0.3f || wing);
        return new QuickPick.Ctx(titan, waymark, sea, storm);
    }

    // ---------------------------------------------------------------- 置く (pin)
    @Nullable
    static ResourceLocation pinned() {
        String s = Config.POUCH_QUICK_PIN.get();
        return s == null || s.isEmpty() ? null : ResourceLocation.tryParse(s);
    }

    static boolean isPinned(ResourceLocation id) {
        return id.equals(pinned());
    }

    static void pin(@Nullable ResourceLocation id) {
        Config.POUCH_QUICK_PIN.set(id == null ? "" : id.toString());
        Config.POUCH_QUICK_PIN.save();
    }

    // 旅嚢の画面の「この札に置く」。置いていれば外す。置いたら true
    static boolean togglePin(ResourceLocation id) {
        if (isPinned(id)) {
            pin(null);
            return false;
        }
        pin(id);
        return true;
    }

    // ---------------------------------------------------------------- 描く
    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!Config.POUCH_QUICK_CARD.get() || !PartyMarkers.hudAllowed(mc) || mc.player.isSpectator() || shown == null) {
            return;
        }
        PouchEntries.Entry e = PouchEntries.byId(shown);
        if (e == null) {
            return;
        }
        int count = Pouch.count(mc.player, e.item());
        if (count <= 0) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        boolean leftSide = mc.options.mainHand().get() == HumanoidArm.RIGHT;
        boolean offhand = !mc.player.getOffhandItem().isEmpty();
        int cx = w / 2;
        int spriteX;
        int itemX;
        if (leftSide) {
            int base = cx - 91 - (offhand ? SPRITE_W + GAP : 0);
            spriteX = base - SPRITE_W - GAP;
            itemX = spriteX + 3;
        } else {
            int base = cx + 91 + (offhand ? SPRITE_W + GAP : 0);
            spriteX = base + GAP;
            itemX = spriteX + 10;
        }
        int spriteY = h - 23;
        long age = Util.getMillis() - changedAt;
        float glow = age < HIGHLIGHT_MS ? 1f - age / (float) HIGHLIGHT_MS : 0f;

        g.blitSprite(leftSide ? SPRITE_LEFT : SPRITE_RIGHT, spriteX, spriteY, SPRITE_W, SPRITE_H);
        if (glow > 0f) {
            int a = (int) (glow * 150) << 24;
            // 縁にそった金の線 (ホットバーの絵に合わせた細い角ばった枠)
            int x0 = leftSide ? spriteX : spriteX + 7;
            int x1 = leftSide ? spriteX + 22 : spriteX + SPRITE_W;
            g.renderOutline(x0, spriteY, x1 - x0, SPRITE_H - 1, a | GOLD);
        }
        ItemStack stack = new ItemStack(e.item(), count);
        g.renderItem(stack, itemX, h - 19);
        g.renderItemDecorations(mc.font, stack, itemX, h - 19);
        // 置いた札の印 (角の小さな金の点)
        if (isPinned(shown)) {
            int px = itemX - 1;
            g.fill(px, h - 20, px + 3, h - 19, 0xFF000000 | GOLD);
            g.fill(px, h - 20, px + 1, h - 17, 0xFF000000 | GOLD);
        }
        // キー名 (上に小さく)。入れ替わった直後は道具の名前も
        Component key = PouchClient.USE_KEY.getTranslatedKeyMessage();
        Component label = age < NAME_MS
                ? Component.literal("[").append(key).append("] ").append(e.item().getDescription())
                : Component.literal("[").append(key).append("]");
        float scale = 0.75f;
        int tw = mc.font.width(label);
        float lx = (leftSide ? spriteX + 11.5f : spriteX + 17.5f) - tw * scale / 2f;
        float ly = spriteY - 8;
        float alpha = age < NAME_MS ? 1f : 0.7f;
        int color = ((int) (Mth.clamp(alpha, 0f, 1f) * 255) << 24) | 0xD8DCE6;
        g.pose().pushPose();
        g.pose().translate(lx, ly, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(mc.font, label, 0, 0, color, true);
        g.pose().popPose();
    }
}
