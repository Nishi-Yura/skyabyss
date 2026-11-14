package jp.revati.skyabyss.client.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.FoeStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

// 敵の状態のアイコン (combat/FoeStatus が送る)。2026-11-12 の声「敵の現状の効果はhp上にアイコン表示して今どんな状態かを表すようにして。
// ボスバーだと名前の右」「ボスバーあるやつは、頭の上のhpバーいらない」。
//   - 頭の上: 体力の帯のすぐ上に横一列 (EnemyHealthBars が帯と一緒に描く。ここは並びと絵の場所を返す)
//   - ボスの帯: 名前の行の右の端に右寄せで一列。帯の持ち主は名前 (BossOwners.find) か、体力の割合 (PhasedBossBar.byRatio) で探し、
//     見つけた持ち主は BossOwners.claim で「帯の持ち主」と覚える (頭の上の帯を出さない。どの描き手の帯でも同じに効く)
//   - 残りが ENDING tick を切ったアイコンは点滅する
// 燃え方: バニラの体を包む炎は敵には描かない (mixin/EntityFlameMixin。作り込んだ模型が炎で隠れていた)。代わりに
//   体が熱で橙に脈打ち (LowPolyGeoRenderer.getRenderColor の heat)、体の表面から小さな火の粉が少し昇る (ここの onTick)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class FoeStatusClient {
    public static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/foe_status.png");
    public static final int ICON = 9;
    static final int SHEET_W = ICON * FoeStatus.Kind.ALL.length;
    static final int ENDING = 40;
    // ボスの帯の幅 (PhasedBossBar.WIDTH と同じ)
    static final int BAR_W = 182;

    private record State(byte[] kinds, int[] ticks, long at) {
    }

    private static Map<Integer, State> states = new HashMap<>();

    private FoeStatusClient() {
    }

    public static void receive(FoeStatus.Payload p) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        Map<Integer, State> next = new HashMap<>();
        for (FoeStatus.Entry e : p.entries()) {
            next.put(e.entityId(), new State(e.kinds(), e.ticks(), now));
        }
        states = next;
    }

    // 一つのアイコン: 種類の番号と、見せる濃さ (点滅中は薄くなる)
    public record Shown(int kind, float alpha) {
    }

    // この体のいまのアイコン (無ければ空)
    public static List<Shown> of(LivingEntity e, float partial) {
        State s = states.get(e.getId());
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.level == null) {
            return List.of();
        }
        long now = mc.level.getGameTime();
        List<Shown> out = new ArrayList<>(s.kinds().length);
        for (int i = 0; i < s.kinds().length; i++) {
            int kind = s.kinds()[i];
            if (kind < 0 || kind >= FoeStatus.Kind.ALL.length) {
                continue;
            }
            float alpha = 1f;
            if (s.ticks()[i] >= 0) {
                float left = s.ticks()[i] - (now - s.at()) - partial;
                if (left <= 0) {
                    continue;
                }
                if (left < ENDING) {
                    // 終わり際は点滅 (だんだん速く)
                    float speed = 0.35f + 0.5f * (1f - left / ENDING);
                    alpha = 0.35f + 0.65f * (0.5f + 0.5f * Mth.cos((now + partial) * speed));
                }
            }
            out.add(new Shown(kind, alpha));
        }
        return out;
    }

    // 燃えているか (アイコンが出ている。火の粉と熱の色に使う)
    public static boolean burning(LivingEntity e) {
        State s = states.get(e.getId());
        if (s == null) {
            return e.isOnFire();
        }
        for (byte k : s.kinds()) {
            if (k == FoeStatus.Kind.BURN.ordinal()) {
                return true;
            }
        }
        return e.isOnFire();
    }

    // 敵の体か (炎の描き替えの対象)
    public static boolean isFoe(net.minecraft.world.entity.Entity e) {
        return e instanceof Enemy && e instanceof LivingEntity && !(e instanceof Player);
    }

    // 熱の色 (掛け算の色。1 = そのまま)。燃えている敵は橙に脈打つ
    public static int heat(LivingEntity e, float partial) {
        if (!burning(e)) {
            return 0xFFFFFFFF;
        }
        float t = 0.5f + 0.5f * Mth.sin((e.tickCount + partial) * 0.3f + e.getId());
        float k = 0.45f + 0.30f * t;
        int g = Math.round(255 * (1f - 0.45f * k));
        int b = Math.round(255 * (1f - 0.85f * k));
        return 0xFF000000 | 0xFF << 16 | g << 8 | b;
    }

    // ---------------------------------------------------------------- 火の粉
    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        var r = mc.level.random;
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, new AABB(mc.player.blockPosition()).inflate(32),
                FoeStatusClient::isFoe)) {
            if (!e.isAlive() || e.isInvisible() || !burning(e)) {
                continue;
            }
            // 大きな体ほど多く (それでも体を隠さない程度)
            float size = e.getBbWidth() * e.getBbHeight();
            int n = size > 6 ? 2 : r.nextInt(2);
            for (int i = 0; i < n; i++) {
                AABB box = e.getBoundingBox();
                // 体の表面 (横の面) の一点から、少し外へ、上へ。頭より上では出さない (帯とアイコンを隠さない。溶岩の粒と煙は大きくて邪魔だった)
                double a = r.nextDouble() * Math.PI * 2;
                double hw = e.getBbWidth() * 0.5;
                double x = e.getX() + Math.cos(a) * hw;
                double z = e.getZ() + Math.sin(a) * hw;
                double y = box.minY + r.nextDouble() * e.getBbHeight() * 0.8;
                mc.level.addParticle(ParticleTypes.SMALL_FLAME, x, y, z, Math.cos(a) * 0.01, 0.01 + r.nextDouble() * 0.02, Math.sin(a) * 0.01);
            }
        }
    }

    // ---------------------------------------------------------------- ボスの帯
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        BossEvent boss = event.getBossEvent();
        LivingEntity owner = jp.revati.skyabyss.client.BossOwners.find(mc, boss, LivingEntity.class, 160);
        if (owner == null) {
            owner = jp.revati.skyabyss.client.titan.PhasedBossBar.byRatio(mc, boss);
        }
        if (owner == null) {
            return;
        }
        jp.revati.skyabyss.client.BossOwners.claim(owner);
        List<Shown> icons = of(owner, event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (icons.isEmpty()) {
            return;
        }
        int x = event.getX();
        int top = event.getY() - jp.revati.skyabyss.client.titan.BossBarShift.UP;
        // 描き換えた帯は名前が上の行 (y + 4)。バニラの帯は帯の上 (y - 9)。Mod の名の帯は、同じ順位の描き手 (BossBarLooks の
        // 型なしの帯) がまだ描いていなくても描き換えられる
        boolean custom = event.isCanceled() || jp.revati.skyabyss.client.titan.BossBarLooks.entityKey(boss.getName(), 0) != null;
        int y = custom ? top + 3 : top - 10;
        int nameEnd = x + (custom ? 20 : (BAR_W - mc.font.width(boss.getName())) / 2) + mc.font.width(boss.getName());
        int w = icons.size() * (ICON + 1) - 1;
        int left = Math.max(x + BAR_W - w, nameEnd + 4);
        draw(event.getGuiGraphics(), icons, left, y);
    }

    static void draw(GuiGraphics g, List<Shown> icons, int x, int y) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        for (int i = 0; i < icons.size(); i++) {
            Shown s = icons.get(i);
            g.setColor(1f, 1f, 1f, s.alpha());
            g.blit(ICONS, x + i * (ICON + 1), y, s.kind() * ICON, 0, ICON, ICON, SHEET_W, ICON);
        }
        g.setColor(1f, 1f, 1f, 1f);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    // 頭の上: 体の id → 帯の位置で描く。EnemyHealthBars から (帯の真上、中央寄せ)
    static float uOf(int kind) {
        return kind * ICON / (float) SHEET_W;
    }

    static float uEnd(int kind) {
        return (kind + 1) * ICON / (float) SHEET_W;
    }
}
