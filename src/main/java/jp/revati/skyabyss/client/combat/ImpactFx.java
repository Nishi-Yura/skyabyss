package jp.revati.skyabyss.client.combat;

import java.util.Locale;

import org.joml.Vector3f;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// 予告した技が当たった瞬間の見た目と音だけ (ダメージ・判定には触れない)。
// 床の印の範囲に、その技の主の属性 (石・水・雷・熱・深淵) の mod の粒を一層だけ出し (docs/design/59)、大きな技 (BIG_RADIUS) だけ画面を揺らす。
// 揺れは screenShake 設定と weaponShake の強さに従う。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class ImpactFx {
    enum Element {
        STONE, WATER, STORM, EMBER, ABYSS
    }

    private static final int SHAKE_TICKS = 10;
    // これより大きな範囲の技だけ爆発 (音・粒・揺れ)。半径 4.5 = 幅 9 の円や叩きつけ (名のある強敵・巨神・大技)
    static final double BIG_RADIUS = 4.5;
    private static float shakeAmp;
    private static int shakeAge;

    private ImpactFx() {
    }

    // 当たった瞬間に 1 回だけ呼ぶ
    static void burst(ClientLevel level, TelegraphClient.Decal d) {
        burst(level, d, false);
    }

    // bespoke: 技の見た目 (AttackFx) が当たりの粒を描くので、ここは音と揺れだけ
    static void burst(ClientLevel level, TelegraphClient.Decal d, boolean bespoke) {
        if (d.style == Telegraph.Style.SAFE || d.style == Telegraph.Style.GOAL) {
            return;
        }
        TelegraphShape s = d.shape;
        double[] b = s.bounds();
        double cx = (b[0] + b[2]) / 2;
        double cz = (b[1] + b[3]) / 2;
        double radius = Math.max(1.0, Math.min(14.0, Math.max(b[2] - b[0], b[3] - b[1]) / 2));
        double y0 = d.ground(cx, cz);
        if (Double.isNaN(y0)) {
            y0 = d.floor;
        }
        boolean light = d.style == Telegraph.Style.HARMLESS;
        Element el = elementNear(level, cx, y0, cz);
        float size = (float) Math.min(1.0, radius / 8.0);
        // 爆発 (音・爆発の粒・画面の揺れ) は大きな技だけ。小さな技 (雑魚の一撃) は重い打撃の音と破片だけ
        // (2026-10-27 の声「なんでもかんでも爆発つければいいんじゃない」)
        boolean big = radius >= BIG_RADIUS;
        SoundEvent snd;
        float pitch;
        switch (el) {
            case WATER -> {
                snd = SoundEvents.GENERIC_SPLASH;
                pitch = 0.7f;
            }
            case STORM -> {
                snd = SoundEvents.LIGHTNING_BOLT_IMPACT;
                pitch = 1.3f;
            }
            case EMBER -> {
                snd = big ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.FIRECHARGE_USE;
                pitch = big ? 1.4f : 1.1f;
            }
            case ABYSS -> {
                snd = SoundEvents.WARDEN_SONIC_BOOM;
                pitch = 1.5f;
            }
            default -> {
                snd = big ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.PLAYER_ATTACK_STRONG;
                pitch = big ? 0.8f : 0.7f;
            }
        }
        level.playLocalSound(cx, y0, cz, snd, SoundSource.HOSTILE, light ? 0.5f : 0.9f + 0.5f * size, pitch + level.random.nextFloat() * 0.1f, false);
        if (light) {
            return;
        }
        // 当たった瞬間の粒は一層だけ (docs/design/59): 心臓の後の主は主の粒 (BossImpactFx)、技の見た目があればそちら (AttackFx)、
        // ほかは属性の mod の粒を AttackFx.burst で (多くて IMPACT_MAX)。爆発の煙は出さない。揺れは大きな技だけ
        boolean boss = !bespoke && BossImpactFx.burst(level, d, cx, y0, cz, radius);
        if (!bespoke && !boss) {
            AttackFx.burst(level, d, fxElement(el), 1f);
        }
        if (big) {
            shake(cx, cz, radius, size);
        }
    }

    static jp.revati.skyabyss.fx.FxParticles.Element fxElement(Element el) {
        return switch (el) {
            case WATER -> jp.revati.skyabyss.fx.FxParticles.Element.WATER;
            case STORM -> jp.revati.skyabyss.fx.FxParticles.Element.STORM;
            case EMBER -> jp.revati.skyabyss.fx.FxParticles.Element.EMBER;
            case ABYSS -> jp.revati.skyabyss.fx.FxParticles.Element.ABYSS;
            default -> jp.revati.skyabyss.fx.FxParticles.Element.STONE;
        };
    }

    private static void shake(double cx, double cz, double radius, float size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && Config.SCREEN_SHAKE.get()) {
            double dist = Math.hypot(mc.player.getX() - cx, mc.player.getZ() - cz);
            double reach = radius + 10;
            if (dist < reach) {
                float k = (float) (1.0 - dist / reach);
                float strength = Config.WEAPON_SHAKE.get().floatValue();
                float amp = (0.5f + 0.9f * size) * k * strength;
                if (amp > 0.02f) {
                    shakeAmp = Math.max(shakeAmp * 0.5f, amp);
                    shakeAge = 0;
                }
            }
        }
    }

    // 主 = 範囲の近くにいる mod の生き物のうち最も近いもの。登録名から属性を決める (サーバーの通信は変えない)
    private static Element elementNear(ClientLevel level, double cx, double y, double cz) {
        AABB box = new AABB(cx - 40, y - 20, cz - 40, cx + 40, y + 30, cz + 40);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(SkyAbyss.MODID)) {
                continue;
            }
            double dd = e.distanceToSqr(cx, y, cz);
            if (dd < bestD) {
                bestD = dd;
                best = e;
            }
        }
        if (best == null) {
            return Element.STONE;
        }
        String n = BuiltInRegistries.ENTITY_TYPE.getKey(best.getType()).getPath().toLowerCase(Locale.ROOT);
        if (n.contains("abyss") || n.contains("heart_warden") || n.contains("void")) {
            return Element.ABYSS;
        }
        if (n.contains("storm") || n.contains("raiju") || n.contains("thunder") || n.contains("lightning")) {
            return Element.STORM;
        }
        if (n.contains("ember") || n.contains("ash") || n.contains("scorch") || n.contains("fire") || n.contains("flame")) {
            return Element.EMBER;
        }
        if (n.contains("tide") || n.contains("pearl") || n.contains("flood") || n.contains("clam") || n.contains("maw") || n.contains("water")) {
            return Element.WATER;
        }
        return Element.STONE;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (shakeAmp > 0f && ++shakeAge >= SHAKE_TICKS) {
            shakeAmp = 0f;
        }
    }

    @SubscribeEvent
    static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (shakeAmp <= 0f) {
            return;
        }
        float age = shakeAge + (float) event.getPartialTick();
        float k = Math.max(0f, 1f - age / SHAKE_TICKS);
        k *= k;
        event.setRoll(event.getRoll() + (float) Math.sin(age * 3.3) * shakeAmp * k);
        event.setPitch(event.getPitch() + (float) Math.cos(age * 4.1) * shakeAmp * 0.7f * k);
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        shakeAmp = 0f;
    }
}
