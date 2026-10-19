package jp.revati.skyabyss.abyss;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.combat.BossCue;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.entity.HeartBoss;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

// 深淵の心臓の「深淵の技」(docs/design/31 の B04)。普通の技の合間に、心臓ごとに一つ形の違う技が割り込む。
// 守ること: 全部 Telegraph (床の予告 34 tick 以上)。予告のあとに当たりが出る。必ず安全な筋 (輪の内外・光線の間・円の外) がある。
// 一人でも避けられる数 (人数で少し増える。上限あり)。傷は Abyss.ATTACK_DAMAGE × 心臓の深淵の傷倍率 (28% の帯の中)。
//   岩鯨 「骨の放射」  : 核から 8 本の骨の光線 (間が安全)。憤怒では半歩ずらした二巡目が 24 tick おくれて来る
//   潮殻 「渦の輪」    : 内と外の二重の輪が 18 tick おくれて立つ。輪の間・中心・外が安全。跳べば越えられる
//   雷翼 「雷の柱」    : 立っている場所に雷が三度。14 tick おきに、そのときの足もとへ。止まらず動けば当たらない
//   余燼 「熾の雨」    : 部屋のあちこちに熾が落ちる。落ちる円は 2 tick ずつずれて予告され、足もとの二つは必ず含まれる
public final class AbyssAttacks {
    private AbyssAttacks() {
    }

    // 試験用: 最後に始めた技の名前と、その技が作った傷の出る予告の数
    private static String lastKind = "";
    private static int lastShapes;

    public static String lastKind() {
        return lastKind;
    }

    public static int lastShapes() {
        return lastShapes;
    }

    public static void launch(HeartBoss heart, ServerLevel server, List<ServerPlayer> players) {
        HeartBoss.AbyssCtx c = heart.abyssCtx();
        String theme = heart.abyssThemeId();
        lastKind = theme;
        lastShapes = 0;
        BossCue.send(heart, theme, cueOf(theme), Abyss.ATTACK_WARN);
        switch (theme) {
            case "tide" -> undertow(heart, server, c);
            case "storm" -> pillars(heart, server, c, players);
            case "ember" -> rain(heart, server, c, players);
            default -> spines(heart, server, c);
        }
    }

    public static String cueOf(String theme) {
        return switch (theme) {
            case "tide" -> "abyss_undertow";
            case "storm" -> "abyss_pillars";
            case "ember" -> "abyss_rain";
            default -> "abyss_spines";
        };
    }

    private static float damage(HeartBoss heart) {
        return Abyss.ATTACK_DAMAGE * heart.abyssDamage();
    }

    private static SoundEvent soundOf(String theme) {
        return switch (theme) {
            case "tide" -> SoundEvents.GUARDIAN_ATTACK;
            case "storm" -> SoundEvents.TRIDENT_THUNDER.value();
            case "ember" -> SoundEvents.FIRECHARGE_USE;
            default -> SoundEvents.WARDEN_SONIC_CHARGE;
        };
    }

    // 共通: 危険の予告を一つ立てる
    private static void shape(HeartBoss heart, ServerLevel server, HeartBoss.AbyssCtx c, TelegraphShape shape, int warn, double jumpClear, double hitX, double hitZ,
            boolean sound) {
        lastShapes++;
        Telegraph.Builder b = Telegraph.in(server, shape).floor(c.floorY()).warn(warn).danger().owner(heart).victims(c.victims()).jumpClear(jumpClear)
                .onImpact(s -> AbyssFx.burst(s, hitX, c.floorY(), hitZ))
                .onHit((s, p) -> p.hurt(heart.attackSource(), damage(heart)));
        if (sound) {
            b.startSound(soundOf(heart.abyssThemeId()), 2.2f, 0.6f);
        }
        b.start();
    }

    // ---- 岩鯨: 骨の放射 ----
    private static void spines(HeartBoss heart, ServerLevel server, HeartBoss.AbyssCtx c) {
        RandomSource r = server.random;
        double base = r.nextDouble() * Math.PI * 2;
        volley(heart, server, c, base, true);
        if (c.fury()) {
            heart.abyssLater(24, () -> volley(heart, server, c, base + Math.PI / 8, false));
        }
    }

    static final int RAYS = 8;
    static final double RAY_START = 3.0;
    static final double RAY_HALF_WIDTH = 1.0;

    private static void volley(HeartBoss heart, ServerLevel server, HeartBoss.AbyssCtx c, double base, boolean sound) {
        double len = Math.max(6, c.radius() - RAY_START + 2);
        for (int i = 0; i < RAYS; i++) {
            double a = base + i * Math.PI * 2 / RAYS;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            shape(heart, server, c, TelegraphShape.line(c.cx() + dx * RAY_START, c.cz() + dz * RAY_START, a, len, RAY_HALF_WIDTH), Abyss.ATTACK_WARN, 0,
                    c.cx() + dx * (RAY_START + len / 2), c.cz() + dz * (RAY_START + len / 2), sound && i == 0);
        }
    }

    // ---- 潮殻: 渦の輪 ----
    private static void undertow(HeartBoss heart, ServerLevel server, HeartBoss.AbyssCtx c) {
        double r = c.radius();
        ring(heart, server, c, r * 0.30, r * 0.50, true);
        heart.abyssLater(18, () -> ring(heart, server, c, r * 0.62, r * 0.82, false));
    }

    private static void ring(HeartBoss heart, ServerLevel server, HeartBoss.AbyssCtx c, double inner, double outer, boolean sound) {
        shape(heart, server, c, TelegraphShape.ring(c.cx(), c.cz(), inner, outer), Abyss.ATTACK_WARN, 0.9, c.cx() + (inner + outer) / 2, c.cz(), sound);
    }

    // ---- 雷翼: 雷の柱 ----
    static final double PILLAR_RADIUS = 2.3;

    private static void pillars(HeartBoss heart, ServerLevel server, HeartBoss.AbyssCtx c, List<ServerPlayer> players) {
        for (int wave = 0; wave < 3; wave++) {
            boolean first = wave == 0;
            heart.abyssLater(wave * 14, () -> {
                List<Vec3> spots = new ArrayList<>();
                for (ServerPlayer p : server.getEntitiesOfClass(ServerPlayer.class, heart.getBoundingBox().inflate(40, 14, 40), c.victims())) {
                    Vec3 v = p.getDeltaMovement();
                    spots.add(new Vec3(p.getX() + v.x * 6, 0, p.getZ() + v.z * 6));
                }
                int extras = 1 + players.size() / 2;
                for (int i = 0; i < extras; i++) {
                    double a = server.random.nextDouble() * Math.PI * 2;
                    double d = Math.sqrt(server.random.nextDouble()) * c.radius() * 0.85;
                    spots.add(new Vec3(c.cx() + Math.cos(a) * d, 0, c.cz() + Math.sin(a) * d));
                }
                boolean sound = first;
                for (Vec3 s : spots) {
                    shape(heart, server, c, TelegraphShape.circle(s.x, s.z, PILLAR_RADIUS), Abyss.ATTACK_WARN, 0, s.x, s.z, sound);
                    sound = false;
                }
            });
        }
    }

    // ---- 余燼: 熾の雨 ----
    static final double EMBER_RADIUS = 1.9;

    private static void rain(HeartBoss heart, ServerLevel server, HeartBoss.AbyssCtx c, List<ServerPlayer> players) {
        int count = Math.min(12, 8 + players.size());
        List<double[]> spots = new ArrayList<>();
        // 足もとの二つ (動いている先ではなく、いま立っている所)
        for (ServerPlayer p : players) {
            if (spots.size() < 2) {
                spots.add(new double[] {p.getX(), p.getZ()});
            }
        }
        int guard = 0;
        while (spots.size() < count && guard++ < 200) {
            double a = server.random.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(server.random.nextDouble()) * c.radius() * 0.85;
            double x = c.cx() + Math.cos(a) * d;
            double z = c.cz() + Math.sin(a) * d;
            boolean ok = true;
            for (double[] s : spots) {
                if (Math.hypot(s[0] - x, s[1] - z) < 3.6) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                spots.add(new double[] {x, z});
            }
        }
        for (int i = 0; i < spots.size(); i++) {
            double[] s = spots.get(i);
            boolean sound = i == 0;
            heart.abyssLater(i * 2, () -> shape(heart, server, c, TelegraphShape.circle(s[0], s[1], EMBER_RADIUS), Abyss.ATTACK_WARN, 0, s[0], s[1], sound));
        }
    }
}
