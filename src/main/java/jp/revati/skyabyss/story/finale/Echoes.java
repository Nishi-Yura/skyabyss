package jp.revati.skyabyss.story.finale;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.finale.mob.CloudMobsSetup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 巻き戻りの打ち (docs/design/50 の 4.2.3)。炉の島の敵と主の技は、同じ所でもう一度起こる。
//   circle / cone で「delay tick あとにここがもう一度打たれる」を置く。待つあいだは残り火の印 (炎の粒の輪・扇) が見え、時が来ると燃えて、
//   中にいる人に傷 (打った者が生きていればその者の傷、いなければ炎の傷)。打った所から離れれば当たらない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Echoes {
    private static final List<Echo> PENDING = new ArrayList<>();

    private Echoes() {
    }

    record Echo(ServerLevel level, Vec3 c, double r, Vec3 dir, double cosHalf, long at, float dmg, LivingEntity owner) {
        boolean inside(Vec3 p) {
            Vec3 to = new Vec3(p.x - c.x, 0, p.z - c.z);
            if (to.length() > r + 0.3 || Math.abs(p.y - c.y) > 2.2) {
                return false;
            }
            return dir == null || to.length() < 0.6 || to.normalize().dot(dir) >= cosHalf;
        }
    }

    public static void circle(ServerLevel level, Vec3 c, double r, int delay, float dmg, LivingEntity owner) {
        PENDING.add(new Echo(level, c, r, null, -1, level.getGameTime() + delay, dmg, owner));
        level.playSound(null, c.x, c.y, c.z, CloudMobsSetup.ECHO_MARK.get(), SoundSource.HOSTILE, 0.8f, 1.0f);
    }

    public static void cone(ServerLevel level, Vec3 c, Vec3 dir, double r, double halfDeg, int delay, float dmg, LivingEntity owner) {
        Vec3 d = new Vec3(dir.x, 0, dir.z).normalize();
        PENDING.add(new Echo(level, c, r, d, Math.cos(Math.toRadians(halfDeg)), level.getGameTime() + delay, dmg, owner));
        level.playSound(null, c.x, c.y, c.z, CloudMobsSetup.ECHO_MARK.get(), SoundSource.HOSTILE, 0.8f, 1.0f);
    }

    public static int pending(ServerLevel level) {
        int n = 0;
        for (Echo e : PENDING) {
            n += e.level == level ? 1 : 0;
        }
        return n;
    }

    // 待っている打ちのうち、at を含むものの数 (自己テスト向け)
    static int covering(ServerLevel level, Vec3 at) {
        int n = 0;
        for (Echo e : PENDING) {
            n += e.level == level && e.inside(at) ? 1 : 0;
        }
        return n;
    }

    // 時が来たものを今すぐ燃やす (自己テスト向け)
    static void fireAll(ServerLevel level) {
        for (Echo e : new ArrayList<>(PENDING)) {
            if (e.level == level) {
                fire(e);
            }
        }
        PENDING.removeIf(e -> e.level == level);
    }

    public static void clear() {
        PENDING.clear();
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || PENDING.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (Iterator<Echo> it = PENDING.iterator(); it.hasNext();) {
            Echo e = it.next();
            if (e.level != level) {
                continue;
            }
            if (now >= e.at) {
                it.remove();
                fire(e);
            } else if ((e.at - now) % 3 == 0) {
                draw(e, e.at - now <= 20);
            }
        }
    }

    static void draw(Echo e, boolean soon) {
        int n = (int) (10 + e.r * 6);
        for (int i = 0; i < n; i++) {
            double a = i / (double) n * Math.PI * 2;
            Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
            if (e.dir != null && d.dot(e.dir) < e.cosHalf) {
                continue;
            }
            e.level.sendParticles(soon ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, e.c.x + d.x * e.r, e.c.y + 0.15, e.c.z + d.z * e.r, 1, 0, 0, 0, 0);
        }
        if (e.dir != null) {
            for (double t = 0.5; t <= e.r; t += 0.8) {
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 side = e.dir.yRot((float) (s * Math.acos(e.cosHalf)));
                    e.level.sendParticles(ParticleTypes.SMALL_FLAME, e.c.x + side.x * t, e.c.y + 0.15, e.c.z + side.z * t, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    static void fire(Echo e) {
        ServerLevel l = e.level;
        l.playSound(null, e.c.x, e.c.y, e.c.z, CloudMobsSetup.ECHO_BURST.get(), SoundSource.HOSTILE, 1.2f, 0.9f);
        l.sendParticles(ParticleTypes.FLAME, e.c.x, e.c.y + 0.4, e.c.z, (int) (14 + e.r * 8), e.r * 0.5, 0.3, e.r * 0.5, 0.04);
        l.sendParticles(ParticleTypes.LAVA, e.c.x, e.c.y + 0.3, e.c.z, 4, e.r * 0.4, 0.1, e.r * 0.4, 0);
        for (ServerPlayer p : l.getEntitiesOfClass(ServerPlayer.class, new AABB(e.c, e.c).inflate(e.r + 1, 3, e.r + 1), x -> x.isAlive() && !x.isSpectator())) {
            if (e.inside(p.position())) {
                boolean owned = e.owner != null && e.owner.isAlive();
                p.hurt(owned ? l.damageSources().mobAttack(e.owner) : l.damageSources().inFire(), e.dmg);
            }
        }
    }
}
