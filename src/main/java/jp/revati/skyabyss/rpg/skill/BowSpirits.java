package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.combat.prop.AttackProp;
import jp.revati.skyabyss.combat.prop.AttackProps;
import jp.revati.skyabyss.rpg.skill.tree.NodeEffects;
import jp.revati.skyabyss.util.PlayerMaps;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 番の弓霊 (弓使いの 4 つ目の技。数値は SkillRules の SPIRIT_*。2026-11-13 に射手の跳び退きと入れ替え)。
// 呼ぶと持ち主の肩のまわりに弓の霊 (prop_bow_spirit。AttackProp の FOLLOW) が浮かび、SPIRIT_SHOT_GAP ごとに一射ずつ、勝手に近くの敵を射る。
//   狙い: 持ち主が最後に傷つけた敵 (見えていて SPIRIT_RANGE 以内) → いなければ霊からいちばん近い、見えている敵
//   数: 1 + 木の節 spirit_count (罠の型の「霊の群れ」「霊の宿り」) で SPIRIT_MAX まで。呼び直すと前の霊は消える
//   傷: 技の基準の傷 (弓の満月の一矢。SkillActions.attackDamage) × spiritShot(段)。技の力・型の溜まるもの・波紋は SkillActions.hit を通して乗る
public final class BowSpirits {
    private static final class Spirit {
        final AttackProp prop;
        final int slot;
        final int count;
        long nextShot;

        Spirit(AttackProp prop, int slot, int count, long nextShot) {
            this.prop = prop;
            this.slot = slot;
            this.count = count;
            this.nextShot = nextShot;
        }
    }

    private static final class Flock {
        final ServerLevel level;
        final int rank;
        final long until;
        final List<Spirit> spirits = new ArrayList<>();

        Flock(ServerLevel level, int rank, long until) {
            this.level = level;
            this.rank = rank;
            this.until = until;
        }
    }

    private static final Map<UUID, Flock> FLOCKS = PlayerMaps.track(new HashMap<>());

    private BowSpirits() {
    }

    // 呼べる霊の数
    public static int count(ServerPlayer player) {
        return Math.min(SkillRules.SPIRIT_MAX, 1 + (int) Math.round(NodeEffects.sum(player, "spirit_count")));
    }

    static String summon(ServerPlayer player, int rank) {
        if (SkillActions.heldBow(player).isEmpty()) {
            return "message.skyabyss.skill.need_bow";
        }
        dismiss(player.getUUID());
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        int n = count(player);
        Flock f = new Flock(level, rank, now + SkillRules.SPIRIT_TICKS);
        for (int i = 0; i < n; i++) {
            AttackProp p = AttackProps.follow(level, "bow_spirit", player, i, n, SkillRules.SPIRIT_TICKS, 0.75f);
            if (p != null) {
                // 一体目はすぐ、あとは少しずつずらして射る
                f.spirits.add(new Spirit(p, i, n, now + 6 + i * (SkillRules.SPIRIT_SHOT_GAP / Math.max(1, n))));
                Vec3 at = p.position();
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.03);
            }
        }
        FLOCKS.put(player.getUUID(), f);
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.PLAYERS, 0.9f, 0.7f);
        return null;
    }

    // 今いる霊の数 (自己テスト・画面)
    public static int alive(UUID player) {
        Flock f = FLOCKS.get(player);
        return f == null ? 0 : f.spirits.size();
    }

    static void dismiss(UUID player) {
        Flock f = FLOCKS.remove(player);
        if (f != null) {
            for (Spirit s : f.spirits) {
                s.prop.discard();
            }
        }
    }

    public static void tick(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Flock>> it = FLOCKS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Flock> en = it.next();
            Flock f = en.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(en.getKey());
            long now = f.level.getGameTime();
            if (owner == null || !owner.isAlive() || owner.level() != f.level || now >= f.until) {
                for (Spirit s : f.spirits) {
                    Vec3 at = s.prop.position();
                    f.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 5, 0.15, 0.15, 0.15, 0.02);
                    s.prop.discard();
                }
                it.remove();
                continue;
            }
            for (Spirit s : f.spirits) {
                if (now < s.nextShot) {
                    continue;
                }
                Vec3 from = AttackProp.follow(owner, s.slot, s.count, s.prop.tickCount);
                LivingEntity target = target(owner, from);
                if (target == null) {
                    s.nextShot = now + 5;
                    continue;
                }
                s.nextShot = now + SkillRules.SPIRIT_SHOT_GAP;
                shoot(owner, f, from, target);
            }
        }
    }

    private static void shoot(ServerPlayer owner, Flock f, Vec3 from, LivingEntity target) {
        ServerLevel level = f.level;
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
        int flight = Math.max(2, (int) Math.round(from.distanceTo(to) / 1.6));
        AttackProps.fly(level, "bow_spirit_fly", owner, from, to, flight, 0.8f);
        float dmg = SkillActions.attackDamage(owner) * (float) SkillRules.spiritShot(f.rank);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.5f, 1.6f);
        SkillEffects.later(level, flight, () -> {
            if (target.isAlive() && owner.isAlive() && target.level() == level) {
                SkillActions.hit(owner, target, dmg, Skill.BOW_SPIRIT);
                level.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 5, 0.15, 0.15, 0.15, 0.1);
            }
        });
    }

    @Nullable
    private static LivingEntity target(ServerPlayer owner, Vec3 from) {
        double r2 = SkillRules.SPIRIT_RANGE * SkillRules.SPIRIT_RANGE;
        LivingEntity last = owner.getLastHurtMob();
        if (last != null && last.isAlive() && last.level() == owner.level() && SkillActions.isFoe(owner, last) && last.distanceToSqr(from) <= r2
                && seen(owner, from, last)) {
            return last;
        }
        LivingEntity best = null;
        double bestD = r2;
        for (LivingEntity e : owner.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(SkillRules.SPIRIT_RANGE),
                e -> e.isAlive() && SkillActions.isFoe(owner, e))) {
            double d = e.distanceToSqr(from);
            if (d < bestD && seen(owner, from, e)) {
                best = e;
                bestD = d;
            }
        }
        return best;
    }

    private static boolean seen(ServerPlayer owner, Vec3 from, LivingEntity e) {
        Vec3 to = e.position().add(0, e.getBbHeight() * 0.6, 0);
        return owner.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getType() == HitResult.Type.MISS;
    }

    public static void clear() {
        FLOCKS.clear();
    }
}
