package jp.revati.skyabyss.story.ch6.rift;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import jp.revati.skyabyss.story.ch6.mob.RiftArmor;
import jp.revati.skyabyss.story.ch6.mob.RiftMob;
import jp.revati.skyabyss.story.ch6.mob.RiftSerpent;
import jp.revati.skyabyss.story.ch6.mob.RiftWing;
import jp.revati.skyabyss.story.ch6.valley.StarLamps;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// 敵の残像 (docs/design/46 の 4.6「残像」)。淵の敵が倒れると、3 秒前にいた所に「残り影」(青白い人形の輪郭の粒) が 1.5 秒出て、
// そこから一度だけ残像として立ち上がる。残り影が星灯りの中にあれば立ち上がらない。灯りの回廊の残り影の溜まりも同じ形で立たせる (RiftTicker)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RiftRemnants {
    public static final int MARK = 30;
    private static final List<Remnant> PENDING = new ArrayList<>();

    static final class Remnant {
        final EntityType<? extends RiftMob> type;
        final Vec3 at;
        final String tag;
        int left = MARK;

        Remnant(EntityType<? extends RiftMob> type, Vec3 at, String tag) {
            this.type = type;
            this.at = at;
            this.tag = tag;
        }
    }

    private RiftRemnants() {
    }

    public static int pending() {
        return PENDING.size();
    }

    // 残り影を置く (tag はできた残像に付ける札。溜まりの残像の数を数える用。null なら付けない)
    public static void mark(ServerLevel level, EntityType<? extends RiftMob> type, Vec3 at, String tag) {
        PENDING.add(new Remnant(type, at, tag));
        level.playSound(null, at.x, at.y, at.z, RiftBlocks.REMNANT_MARK.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof RiftMob mob) || mob.echo() || !(mob.level() instanceof ServerLevel level)
                || level.dimension() != MoonWorld.LEVEL) {
            return;
        }
        @SuppressWarnings("unchecked")
        EntityType<? extends RiftMob> type = (EntityType<? extends RiftMob>) mob.getType();
        mark(level, type, mob.pastPosition(), mob.getTags().contains(RiftArena.TAG) ? RiftArena.TAG : null);
    }

    // 毎 tick (RiftTicker): 残り影の輪郭を描き、時が来たら立ち上がらせる
    static void tick(ServerLevel level) {
        for (Iterator<Remnant> it = PENDING.iterator(); it.hasNext();) {
            Remnant r = it.next();
            r.left--;
            if (r.left % 3 == 0) {
                outline(level, r.at);
            }
            if (r.left > 0) {
                continue;
            }
            it.remove();
            if (StarLamps.inLight(level, r.at.add(0, 0.5, 0))) {
                // 光の中では残像が形を結ばない
                level.sendParticles(ParticleTypes.END_ROD, r.at.x, r.at.y + 1, r.at.z, 16, 0.3, 0.6, 0.3, 0.06);
                continue;
            }
            rise(level, r);
        }
    }

    static RiftMob rise(ServerLevel level, Remnant r) {
        RiftMob m = r.type.create(level);
        if (m == null) {
            return null;
        }
        m.moveTo(r.at.x, r.at.y, r.at.z, level.random.nextFloat() * 360f, 0f);
        boolean matched = MoonMob.waningMoon(level);
        if (m instanceof RiftWing) {
            m.tuneMoon(3, matched, false, RiftWing.HP, RiftWing.DMG);
        } else if (m instanceof RiftArmor) {
            m.tuneMoon(3, matched, false, RiftArmor.HP, RiftArmor.DMG);
        } else if (m instanceof RiftSerpent) {
            m.tuneMoon(3, matched, false, RiftSerpent.HP, RiftSerpent.DMG);
        }
        m.makeEcho();
        if (r.tag != null) {
            m.addTag(r.tag);
        }
        level.addFreshEntity(m);
        level.playSound(null, r.at.x, r.at.y, r.at.z, RiftBlocks.REMNANT_RISE.get(), SoundSource.HOSTILE, 1.2f, 1.0f);
        level.sendParticles(ParticleTypes.SOUL, r.at.x, r.at.y + 1, r.at.z, 20, 0.3, 0.8, 0.3, 0.03);
        return m;
    }

    // 人形の輪郭 (頭の輪と肩から足まで)
    private static void outline(ServerLevel level, Vec3 at) {
        for (int i = 0; i < 8; i++) {
            double a = i / 8.0 * Math.PI * 2;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x + Math.cos(a) * 0.25, at.y + 1.75, at.z + Math.sin(a) * 0.25, 1, 0, 0, 0, 0);
        }
        for (int i = 0; i <= 6; i++) {
            double y = at.y + 1.4 - i * 0.22;
            double w = i < 3 ? 0.4 : 0.22;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x + w, y, at.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x - w, y, at.z, 1, 0, 0, 0, 0);
        }
    }

    public static void clear() {
        PENDING.clear();
    }
}
