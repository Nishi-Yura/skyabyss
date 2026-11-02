package jp.revati.skyabyss.client.combat;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.DamageNumbers;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// 与えた傷の数字を、当てた体の上に浮かべる (combat/DamageNumbers が送る)。
// 数字は体力の帯 (EnemyHealthBars) より上に出て、跳ねるように少し上がり、1.3 秒で消える。ふつうは白、技は金、とどめは大きく赤みの金。太字で大きく
// (2026-10-27 の声「もっとダメージ値見やすく」)。同じ体に続けて出た数字は少しずつ上にずらして重ねない。
// 同じ体に 0.15 秒の内に続けて当てた数字は一つにまとめる (多段の技で数字が重ならない)。設定 damageNumbers で消せる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class DamageNumbersClient {
    static final int LIFE = 26;
    static final int MERGE_TICKS = 3;
    static final int MAX = 48;

    private static final class Num {
        final int entity;
        Vec3 pos;
        Vec3 prev;
        double vy;
        final double vx;
        final double vz;
        float amount;
        int kind;
        int age;

        Num(int entity, Vec3 pos, float amount, int kind, double vx, double vz) {
            this.entity = entity;
            this.pos = pos;
            this.prev = pos;
            this.amount = amount;
            this.kind = kind;
            this.vx = vx;
            this.vz = vz;
            this.vy = 0.12;
        }
    }

    private static final List<Num> NUMS = new ArrayList<>();

    private DamageNumbersClient() {
    }

    public static void receive(DamageNumbers.Payload p) {
        if (!Config.DAMAGE_NUMBERS.get()) {
            return;
        }
        for (Num n : NUMS) {
            if (n.entity == p.entity() && n.age <= MERGE_TICKS) {
                n.amount += p.amount();
                n.kind = Math.max(n.kind, p.kind());
                n.age = 0;
                return;
            }
        }
        if (NUMS.size() >= MAX) {
            NUMS.remove(0);
        }
        double a = Math.random() * Math.PI * 2;
        // 同じ体の、まだ出ている数字の数だけ上へずらす
        int stack = 0;
        for (Num n : NUMS) {
            if (n.entity == p.entity()) {
                stack++;
            }
        }
        NUMS.add(new Num(p.entity(), new Vec3(p.x(), p.y() + 0.95 + Math.min(stack, 3) * 0.32, p.z()), p.amount(), p.kind(),
                Math.cos(a) * 0.008, Math.sin(a) * 0.008));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            NUMS.clear();
            return;
        }
        if (mc.isPaused() || NUMS.isEmpty()) {
            return;
        }
        for (Num n : NUMS) {
            n.prev = n.pos;
            n.pos = n.pos.add(n.vx, n.vy, n.vz);
            n.vy = Math.max(-0.02, n.vy * 0.78 - 0.006);
            n.age++;
        }
        NUMS.removeIf(n -> n.age > LIFE);
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || NUMS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Font font = mc.font;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack ps = event.getPoseStack();
        for (Num n : NUMS) {
            Vec3 at = n.prev.lerp(n.pos, partial);
            double dist = at.distanceTo(cam);
            if (dist > 48) {
                continue;
            }
            float t = (n.age + partial) / LIFE;
            // 中の傷の 10 倍 (Hp.SHOWN) を整数で。1 に満たない傷も「1」と出す
            int shown = Math.max(1, jp.revati.skyabyss.Hp.whole(n.amount));
            // 出た瞬間に大きく弾んで、すぐ落ち着く (2026-11-02 の声「攻撃力の迫力がない」)。大きい傷ほど大きく (35 で 1 倍、300 で 1.25 倍、1000 で 1.4 倍)。
            // 遠くても読めるよう、距離で少し大きく
            float age = n.age + partial;
            float pop = age < 4 ? 1.7f - 0.175f * age : 1f;
            float big = Mth.clamp(0.66f + 0.215f * (float) Math.log10(shown), 0.85f, 1.6f);
            float size = (n.kind == DamageNumbers.KILL ? 1.35f : n.kind == DamageNumbers.SKILL ? 1.15f : 1f) * pop * big
                    * (float) Mth.clamp(0.8 + dist * 0.035, 0.8, 2.2) * 0.05f;
            int alpha = (int) (255 * Mth.clamp(t < 0.75f ? 1f : (1f - t) / 0.25f, 0f, 1f));
            if (alpha < 8) {
                continue;
            }
            // 出た瞬間は白く光り、2 tick で種類の色 (ふつう 白、技 金、とどめ 赤みの金) へ
            int rgb = n.kind == DamageNumbers.KILL ? 0xFFB347 : n.kind == DamageNumbers.SKILL ? 0xFFD966 : 0xFFFFFF;
            if (age < 2) {
                rgb = mix(0xFFFFFF, rgb, age / 2f);
            }
            String text = Integer.toString(shown);
            FormattedCharSequence seq = Component.literal(text).withStyle(net.minecraft.ChatFormatting.BOLD).getVisualOrderText();
            ps.pushPose();
            ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            ps.mulPose(camera.rotation());
            // 出た 3 tick は小さく震える (とどめは強く)
            if (age < 3) {
                float shake = (n.kind == DamageNumbers.KILL ? 0.05f : 0.025f) * (3 - age);
                ps.translate((float) Math.sin(age * 9.1f + n.entity) * shake, (float) Math.cos(age * 7.3f + n.entity) * shake, 0);
            }
            ps.scale(size, -size, size);
            Matrix4f m = ps.last().pose();
            float x = -font.width(seq) / 2f;
            font.drawInBatch8xOutline(seq, x, 0, alpha << 24 | rgb, alpha << 24 | 0x000000, m, buffers, LightTexture.FULL_BRIGHT);
            ps.popPose();
        }
        buffers.endBatch();
    }

    private static int mix(int a, int b, float k) {
        k = Mth.clamp(k, 0f, 1f);
        int r = (int) Mth.lerp(k, a >> 16 & 255, b >> 16 & 255);
        int g = (int) Mth.lerp(k, a >> 8 & 255, b >> 8 & 255);
        int bl = (int) Mth.lerp(k, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }
}
