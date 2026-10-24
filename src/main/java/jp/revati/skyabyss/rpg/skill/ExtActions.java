package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 橋・淵・前世の技の能動 9 本と、技の変異 8 本の中身 (サーバーだけ)。傷はすべて SkillActions.hit を通る
// (タグ・紋・熟練・連鎖の力が掛かる)。大きさは同格の技に合わせる (鯨尾薙ぎ 段 1 ×0.9・段 2 ×1.2 が目安)。docs/design/31
final class ExtActions {
    private ExtActions() {
    }

    // ================================================================ 入口
    static String perform(ServerPlayer player, Skill skill, int rank) {
        return switch (skill) {
            case DUST_CHARGE -> dustCharge(player, rank);
            case STEAM_BELL -> steamBell(player, rank);
            case CINDER_VOLLEY -> cinderVolley(player, rank);
            case DEEP_PULL -> deepPull(player, rank);
            case DEEP_ECHO -> deepEcho(player, rank);
            case DEEP_MAW -> deepMaw(player, rank);
            case ECHO_STRIKE -> echoStrike(player, rank);
            case GRAVE_BELL -> graveBell(player, rank);
            case FINAL_LIGHT -> finalLight(player, rank);
            default -> "message.skyabyss.skill.not_active";
        };
    }

    // ================================================================ 道具
    private static boolean alive(ServerPlayer p, ServerLevel level) {
        return p.isAlive() && !p.hasDisconnected() && p.level() == level;
    }

    private static float atk(ServerPlayer p, double mult) {
        return SkillActions.attackDamage(p) * (float) mult;
    }

    // 足もと (水平) の円の中の敵
    private static List<LivingEntity> within(ServerPlayer p, Vec3 center, double radius) {
        ServerLevel level = p.serverLevel();
        AABB box = new AABB(center, center).inflate(radius + 1, 1.5, radius + 1).expandTowards(0, 2.0, 0);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, f -> SkillActions.isFoe(p, f))) {
            if (Math.hypot(e.getX() - center.x, e.getZ() - center.z) - e.getBbWidth() / 2 <= radius) {
                out.add(e);
            }
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        return out;
    }

    // 線 (始点 → 終点、幅) の上の敵。始点に近い順
    private static List<LivingEntity> along(ServerPlayer p, Vec3 from, Vec3 to, double width) {
        ServerLevel level = p.serverLevel();
        AABB box = new AABB(from, to).inflate(width + 1, 2.0, width + 1);
        List<LivingEntity> out = new ArrayList<>();
        Vec3 seg = to.subtract(from);
        double len2 = Math.max(1e-6, seg.lengthSqr());
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, f -> SkillActions.isFoe(p, f))) {
            Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
            double t = Mth.clamp(c.subtract(from).dot(seg) / len2, 0, 1);
            if (c.distanceTo(from.add(seg.scale(t))) <= width + e.getBbWidth() / 2) {
                out.add(e);
            }
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
        return out;
    }

    // 敵を点へ寄せる (大きな相手はあまり動かない)
    private static void pull(LivingEntity e, Vec3 center, double strength) {
        double k = SkillActions.isLarge(e) ? strength * 0.3 : strength;
        Vec3 d = new Vec3(center.x - e.getX(), 0, center.z - e.getZ());
        double len = Math.max(0.5, d.length());
        Vec3 v = d.scale(Math.min(1.0, len) / len * k);
        e.setDeltaMovement(v.x, Math.max(e.getDeltaMovement().y, 0.12), v.z);
        e.hurtMarked = true;
    }

    // 敵を点から押し出す
    private static void push(LivingEntity e, Vec3 center, double strength) {
        double k = SkillActions.isLarge(e) ? strength * 0.3 : strength;
        Vec3 d = new Vec3(e.getX() - center.x, 0, e.getZ() - center.z);
        double len = Math.max(0.3, d.length());
        e.setDeltaMovement(d.x / len * k, 0.25, d.z / len * k);
        e.hurtMarked = true;
    }

    private static void line(ServerLevel level, ParticleOptions p, Vec3 from, Vec3 to, double step, double spread) {
        int n = Math.max(1, (int) (from.distanceTo(to) / step));
        for (int i = 0; i <= n; i++) {
            Vec3 at = from.lerp(to, i / (double) n);
            level.sendParticles(p, at.x, at.y, at.z, 1, spread, spread, spread, 0.0);
        }
    }

    private static BlockParticleOption dust(net.minecraft.world.level.block.Block block) {
        return new BlockParticleOption(ParticleTypes.BLOCK, block.defaultBlockState());
    }

    private static void teleport(ServerPlayer player, Vec3 to) {
        if (player.connection != null) {
            player.connection.teleport(to.x, to.y, to.z, player.getYRot(), player.getXRot());
        } else {
            player.setPos(to.x, to.y, to.z);
        }
    }

    // 向きに進んだ先 (0.5 ずつ。ぶつかるところで止まる)。足場がなくても通れる
    private static Vec3 dashEnd(ServerPlayer player, Vec3 dir, double length) {
        ServerLevel level = player.serverLevel();
        Vec3 start = player.position();
        Vec3 reached = start;
        for (double t = 0.5; t <= length + 1e-6; t += 0.5) {
            Vec3 p = start.add(dir.scale(t));
            AABB box = player.getBoundingBox().move(p.subtract(player.position()));
            if (!level.noCollision(player, box) || !SkillActions.hasFooting(level, p)) {
                break;
            }
            reached = p;
        }
        return reached;
    }

    // 見ている先 (ブロックにぶつかるか reach まで)。途中の敵の足もとを優先
    private static Vec3 aim(ServerPlayer player, double reach) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(reach));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 center = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        var entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, center,
                new AABB(eye, center).inflate(1.0), e -> SkillActions.isFoe(player, e), eye.distanceToSqr(center));
        if (entityHit != null) {
            center = entityHit.getEntity().position();
        } else if (block.getType() != HitResult.Type.MISS) {
            center = center.add(0, 0.1, 0);
        }
        return center;
    }

    // ================================================================ 橋 (A09)
    // 砂塵の盾突き (岩 + 風): 前へ 6 ブロック駆け抜け、通り道の敵を押しのける (×0.9 / ×1.2)
    static String dustCharge(ServerPlayer player, int rank) {
        if (!player.onGround() || player.isPassenger()) {
            return "message.skyabyss.skill.need_ground";
        }
        Vec3 dir = SkillActions.flatLook(player);
        Vec3 from = player.position();
        Vec3 end = dashEnd(player, dir, SkillRules.DUST_LENGTH);
        if (end.distanceToSqr(from) > 0.01) {
            teleport(player, end);
        }
        float amount = atk(player, SkillRules.dustMultiplier(rank));
        for (LivingEntity e : along(player, from, end.add(dir.scale(1.2)), SkillRules.DUST_WIDTH / 2 + 0.3)) {
            if (SkillActions.hit(player, e, amount, Skill.DUST_CHARGE)) {
                Vec3 side = new Vec3(-dir.z, 0, dir.x);
                double sgn = Math.signum(e.position().subtract(from).dot(side));
                e.setDeltaMovement(side.scale(0.7 * (sgn == 0 ? 1 : sgn)).add(dir.scale(0.3)).add(0, 0.2, 0));
                e.hurtMarked = true;
            }
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillActions.sound(player, Skills.SOUND_LUNGE.get(), 0.9f, 0.7f);
        return null;
    }

    // 蒸気の鐘 (潮 + 灰): 足もとから熱い蒸気を吹き出し、周り (半径 4) の敵を打って足を鈍らせる
    static String steamBell(ServerPlayer player, int rank) {
        Vec3 c = player.position();
        List<LivingEntity> foes = within(player, c, SkillRules.STEAM_RADIUS);
        float amount = atk(player, SkillRules.steamMultiplier(rank));
        for (LivingEntity e : foes) {
            if (SkillActions.hit(player, e, amount, Skill.STEAM_BELL)) {
                SkillEffects.slow(e, SkillRules.STEAM_SLOW * (SkillActions.isLarge(e) ? 0.5 : 1.0), SkillRules.STEAM_TICKS);
            }
        }
        SkillActions.sound(player, SoundEvents.BELL_BLOCK, 0.9f, 0.6f);
        SkillActions.sound(player, SoundEvents.FIRE_EXTINGUISH, 0.7f, 1.2f);
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    // 熾の矢雨 (灰 + 骨): 前方の扇へ、火の矢を 5 本放つ。矢ごとに手前の 2 体までを射抜き、3 秒燃える
    static String cinderVolley(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 flat = SkillActions.flatLook(player);
        float amount = atk(player, SkillRules.volleyMultiplier(rank));
        boolean any = false;
        for (int i = 0; i < SkillRules.VOLLEY_BOLTS; i++) {
            double yaw = Math.toRadians((i - (SkillRules.VOLLEY_BOLTS - 1) / 2.0) * 9.0);
            Vec3 dir = new Vec3(look.x * Math.cos(yaw) - look.z * Math.sin(yaw), look.y, look.x * Math.sin(yaw) + look.z * Math.cos(yaw)).normalize();
            Vec3 end = eye.add(dir.scale(SkillRules.VOLLEY_RANGE));
            BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (block.getType() != HitResult.Type.MISS) {
                end = block.getLocation();
            }
            List<LivingEntity> foes = along(player, eye, end, 0.6);
            for (int k = 0; k < Math.min(2, foes.size()); k++) {
                LivingEntity e = foes.get(k);
                if (SkillActions.hit(player, e, amount, Skill.CINDER_VOLLEY)) {
                    e.igniteForSeconds(SkillRules.VOLLEY_BURN / SkillRules.SECOND);
                    any = true;
                }
            }
        }
        if (flat.lengthSqr() < 0.0001) {
            any = false;
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillActions.sound(player, Skills.SOUND_PIERCE.get(), 0.8f, 1.3f);
        SkillActions.sound(player, SoundEvents.FIRECHARGE_USE, 0.6f, 1.4f);
        return null;
    }

    // ================================================================ 淵 (B09)
    // 淵の引き: 足もとの前 3 ブロックへ周り (半径 7) の敵を寄せ、少し傷つけて足を鈍らせる (混ぜるための下ごしらえ)
    static String deepPull(ServerPlayer player, int rank) {
        Vec3 point = player.position().add(SkillActions.flatLook(player).scale(3.0));
        List<LivingEntity> foes = within(player, player.position(), SkillRules.PULL_RADIUS);
        if (foes.isEmpty()) {
            return "message.skyabyss.skill.no_target";
        }
        float amount = atk(player, SkillRules.pullMultiplier(rank));
        for (LivingEntity e : foes) {
            pull(e, point, 0.55);
            if (SkillActions.hit(player, e, amount, Skill.DEEP_PULL)) {
                SkillEffects.slow(e, SkillRules.PULL_SLOW * (SkillActions.isLarge(e) ? 0.5 : 1.0), SkillRules.PULL_SLOW_TICKS);
            }
        }
        SkillActions.sound(player, SoundEvents.ENDERMAN_TELEPORT, 0.7f, 0.5f);
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    private static void spiral(ServerLevel level, Vec3 center, double radius, ParticleOptions p) {
        for (int i = 0; i < 40; i++) {
            double a = i * 0.55;
            double r = radius * (1 - i / 42.0);
            level.sendParticles(p, center.x + Math.cos(a) * r, center.y + 0.2 + i * 0.03, center.z + Math.sin(a) * r, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    // 淵の残響: 5 秒のあいだ、すべての技の力が +12% / +18% (SkillExt)。傷そのものは与えない
    static String deepEcho(ServerPlayer player, int rank) {
        SkillData d = SkillManager.get(player);
        d.echoUntil = player.level().getGameTime() + SkillRules.ECHO_TICKS;
        SkillActions.sound(player, Skills.SOUND_MARK.get(), 0.9f, 0.5f);
        return null;
    }

    // 淵の顎: 見ている先 (10 ブロック) の半径 4 へ、敵を引きずり込んでから 0.4 秒後に噛み砕く
    static String deepMaw(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        Vec3 center = aim(player, SkillRules.MAW_REACH);
        float amount = atk(player, SkillRules.mawMultiplier(rank));
        for (LivingEntity e : within(player, center, SkillRules.MAW_RADIUS)) {
            pull(e, center, 0.45);
        }
        SkillActions.sound(player, SoundEvents.WARDEN_SONIC_CHARGE, 0.6f, 1.6f);
        SkillEffects.later(level, 8, () -> {
            if (!alive(player, level)) {
                return;
            }
            for (LivingEntity e : within(player, center, SkillRules.MAW_RADIUS)) {
                float a = SkillActions.isLarge(e) ? amount * 1.2f : amount;
                SkillActions.hit(player, e, a, Skill.DEEP_MAW);
            }
            level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 0.6f);
        });
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    // ================================================================ 前世の技 (B08)
    // 遺響の一撃 (転生 1): 前の人生の最後の一撃の残響。前方の 1 体を、0.65 の力ですぐ、0.35 の力で 0.5 秒後にもう一度
    static String echoStrike(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : SkillActions.foesAround(player, SkillRules.ECHO_STRIKE_RANGE + 1, 2.0, 3.0)) {
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            double dist = to.length();
            if (dist > SkillRules.ECHO_STRIKE_RANGE + e.getBbWidth() / 2) {
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(look), -1, 1)));
            if (angle > 60) {
                continue;
            }
            double score = angle * 0.2 + dist;
            if (score < best) {
                best = score;
                target = e;
            }
        }
        if (target == null) {
            return "message.skyabyss.skill.no_target";
        }
        LivingEntity foe = target;
        double mult = SkillRules.echoStrikeMultiplier(rank);
        ghostSlash(level, foe, false);
        SkillActions.hit(player, foe, atk(player, mult * 0.65), Skill.ECHO_STRIKE);
        SkillEffects.later(level, 10, () -> {
            if (alive(player, level) && foe.isAlive() && foe.level() == level) {
                ghostSlash(level, foe, true);
                SkillActions.hit(player, foe, atk(player, mult * 0.35), Skill.ECHO_STRIKE);
            }
        });
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillActions.sound(player, Skills.SOUND_RIPOSTE.get(), 0.9f, 0.7f);
        return null;
    }

    private static void ghostSlash(ServerLevel level, LivingEntity foe, boolean reverse) {
        // 斬りの筋はクライアントの SkillFx が描く。ここは当たりの小さな火花だけ
        Vec3 c = foe.position().add(0, foe.getBbHeight() * 0.6, 0);
        level.sendParticles(Skills.RUNE_SPARK.get(), c.x, c.y, c.z, 1, 0.1, 0.1, 0.1, 0.02);
    }

    // 墓鐘 (転生 2): 鐘を 1 つ打つ。周り (半径 5) の敵を少し打って、3 秒、足を重くする
    static String graveBell(ServerPlayer player, int rank) {
        Vec3 c = player.position();
        float amount = atk(player, SkillRules.bellMultiplier(rank));
        List<LivingEntity> foes = within(player, c, SkillRules.BELL_RADIUS);
        for (LivingEntity e : foes) {
            if (SkillActions.hit(player, e, amount, Skill.GRAVE_BELL)) {
                SkillEffects.slow(e, SkillRules.BELL_SLOW * (SkillActions.isLarge(e) ? 0.5 : 1.0), SkillRules.BELL_TICKS);
            }
        }
        SkillActions.sound(player, SoundEvents.BELL_RESONATE, 1.0f, 0.5f);
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    // 最期の光 (転生 4): 前の人生の最後の瞬間。足もとから光の輪が三度ひろがり、周り (半径 5.5) の敵を打って押し出す (×1.5 / ×2.0)
    static String finalLight(ServerPlayer player, int rank) {
        Vec3 c = player.position();
        float amount = atk(player, SkillRules.lightMultiplier(rank));
        for (LivingEntity e : within(player, c, SkillRules.LIGHT_RADIUS)) {
            if (SkillActions.hit(player, e, amount, Skill.FINAL_LIGHT)) {
                push(e, c, 0.9);
            }
        }
        SkillActions.sound(player, SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.7f);
        SkillActions.sound(player, Skills.SOUND_CLEAVE.get(), 0.8f, 0.5f);
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    // ================================================================ 変異 (A08)
    // 形だけを変える。力・待ち・マナは元の技のまま (SkillManager.cast が払う)。元の技の「倍率」の表を使い、同じ傷の量に近づける
    static String mutated(ServerPlayer player, Mutation m, int rank) {
        return switch (m) {
            case CLEAVE_CLIFF -> cliff(player, rank);
            case SWEEP_TAIL -> tail(player, rank);
            case REND_BLADE -> bladeThrow(player, rank);
            case RAIN_COMET -> comet(player, rank);
            case EBB_WHIRL -> whirl(player, rank);
            case LUNGE_SURGE -> surge(player, rank);
            case HAMMER_METEOR -> meteor(player, rank);
            case TRAP_MINE -> mine(player, rank);
        };
    }

    // 断崖 (巨神斬り): 扇ではなく、前へ 10 ブロック・幅 3 の岩の列を 0.05 秒おきに突き上げる。×0.8 (大きな相手はさらに ×1.5)
    private static String cliff(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        Vec3 dir = SkillActions.flatLook(player);
        Vec3 origin = player.position();
        Vec3 end = origin.add(dir.scale(10));
        float base = atk(player, SkillRules.cleaveMultiplier(rank) * 0.8);
        List<LivingEntity> foes = along(player, origin.add(dir.scale(0.8)), end, 1.5);
        boolean big = false;
        for (int i = 0; i < Math.min(SkillRules.CLEAVE_MAX_TARGETS, foes.size()); i++) {
            LivingEntity e = foes.get(i);
            boolean large = SkillActions.isLarge(e);
            if (SkillActions.hit(player, e, large ? base * (float) SkillRules.CLEAVE_LARGE_BONUS : base, Skill.TITAN_CLEAVE)) {
                e.setDeltaMovement(e.getDeltaMovement().add(0, large ? 0.2 : 0.55, 0));
                e.hurtMarked = true;
                big |= large;
            }
        }
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        for (int i = 1; i <= 10; i++) {
            Vec3 at = origin.add(dir.scale(i));
            SkillEffects.later(level, i, () -> {
                for (int s = -1; s <= 1; s++) {
                    Vec3 p = at.add(side.scale(s * 0.9));
                    level.sendParticles(dust(Blocks.STONE), p.x, p.y + 0.4, p.z, 14, 0.3, 0.6, 0.3, 0.1);
                    level.sendParticles(dust(Blocks.DEEPSLATE), p.x, p.y + 1.4, p.z, 6, 0.25, 0.5, 0.25, 0.08);
                    level.sendParticles(ParticleTypes.POOF, p.x, p.y + 0.2, p.z, 3, 0.3, 0.1, 0.3, 0.02);
                    level.sendParticles(ParticleTypes.CRIT, p.x, p.y + 1.2, p.z, 4, 0.15, 0.7, 0.15, 0.15);
                }
            });
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillActions.sound(player, Skills.SOUND_CLEAVE.get(), 1.0f, big ? 0.6f : 0.75f);
        return null;
    }

    // 鯨尾の地鳴らし (鯨尾薙ぎ): 1 度の薙ぎを、広がる 2 つの輪にする。内の輪 (半径 3.5) ×0.6、0.4 秒後の外の輪 (半径 5.5) ×0.6 と押し出し
    private static String tail(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        Vec3 c = player.position();
        float amount = atk(player, SkillRules.sweepMultiplier(rank) * 0.6);
        for (LivingEntity e : within(player, c, SkillRules.SWEEP_RADIUS)) {
            SkillActions.hit(player, e, amount, Skill.WHALE_SWEEP);
        }
        SkillActions.ring(level, dust(Blocks.STONE), c, 2.0, 16, 0.2);
        SkillActions.ring(level, ParticleTypes.CLOUD, c, SkillRules.SWEEP_RADIUS, 24, 0.3);
        SkillEffects.later(level, 8, () -> {
            if (!alive(player, level)) {
                return;
            }
            for (LivingEntity e : within(player, c, SkillRules.SWEEP_RADIUS + 2)) {
                if (SkillActions.hit(player, e, amount, Skill.WHALE_SWEEP)) {
                    push(e, c, SkillRules.SWEEP_PUSH * 1.4);
                }
            }
            SkillActions.ring(level, dust(Blocks.STONE), c, 4.0, 22, 0.2);
            SkillActions.ring(level, ParticleTypes.CLOUD, c, SkillRules.SWEEP_RADIUS + 2, 34, 0.3);
            SkillActions.ring(level, Skills.SKY_MOTE.get(), c, SkillRules.SWEEP_RADIUS + 2, 20, 0.6);
        });
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillActions.sound(player, Skills.SOUND_SWEEP.get(), 1.0f, 0.6f);
        return null;
    }

    // 飛ぶ刃 (風裂き): 近づかず、前へ 12 ブロック飛ぶ三日月の刃。行きに ×1.0 (手前の 3 体まで)、0.4 秒後の戻りに ×0.8
    private static String bladeThrow(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition().add(0, -0.4, 0);
        Vec3 dir = player.getLookAngle();
        Vec3 end = eye.add(dir.scale(12));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        Vec3 to = end;
        double per = SkillRules.rendFront(rank);
        List<LivingEntity> out = along(player, eye, to, 0.8);
        for (int i = 0; i < Math.min(3, out.size()); i++) {
            SkillActions.hit(player, out.get(i), atk(player, per), Skill.WIND_REND);
        }
        crescent(level, eye, to);
        SkillEffects.later(level, 8, () -> {
            if (!alive(player, level)) {
                return;
            }
            List<LivingEntity> back = along(player, to, eye, 0.8);
            for (int i = 0; i < Math.min(3, back.size()); i++) {
                SkillActions.hit(player, back.get(i), atk(player, per * 0.8), Skill.WIND_REND);
            }
            crescent(level, to, eye);
        });
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillActions.sound(player, Skills.SOUND_LUNGE.get(), 0.9f, 1.6f);
        return null;
    }

    private static void crescent(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if (len < 0.1) {
            return;
        }
        Vec3 unit = dir.scale(1 / len);
        Vec3 side = new Vec3(-unit.z, 0, unit.x).normalize();
        for (double t = 0; t <= len; t += 0.6) {
            Vec3 at = from.add(unit.scale(t));
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 0, 1.2, 0, 0, 1.0);
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.01);
            level.sendParticles(Skills.SKY_MOTE.get(), at.x + side.x * 0.4, at.y, at.z + side.z * 0.4, 1, 0.03, 0.03, 0.03, 0.0);
            level.sendParticles(Skills.SKY_MOTE.get(), at.x - side.x * 0.4, at.y, at.z - side.z * 0.4, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    // 雷星 (天の驟雨): 6 回の雨を、1 つの大きな星にする。0.6 秒後に半径 3.5 へ一度に落ち、雨 6 回ぶんの ×0.85 を与える
    private static String comet(ServerPlayer player, int rank) {
        if (SkillActions.heldBow(player).isEmpty()) {
            return "message.skyabyss.skill.need_bow";
        }
        ServerLevel level = player.serverLevel();
        Vec3 center = aim(player, SkillRules.RAIN_REACH);
        float amount = (float) (SkillRules.rainDamage(rank) * SkillRules.RAIN_PULSES * 0.85);
        SkillActions.ring(level, ParticleTypes.END_ROD, center, 3.5, 24, 0.1);
        for (int i = 0; i < 6; i++) {
            int h = i;
            SkillEffects.later(level, h * 2, () -> {
                level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 14 - h * 2.2, center.z, 4, 0.15, 0.4, 0.15, 0.01);
            });
        }
        SkillEffects.later(level, 12, () -> {
            if (!alive(player, level)) {
                return;
            }
            for (LivingEntity e : within(player, center, 3.5)) {
                if (SkillActions.hit(player, e, amount, Skill.SKY_RAIN)) {
                    push(e, center, 0.5);
                }
            }
            level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 0.5, center.z, 1, 0, 0, 0, 0);
            level.sendParticles(Skills.RUNE_SPARK.get(), center.x, center.y + 0.6, center.z, 40, 1.2, 0.8, 1.2, 0.15);
            SkillActions.ring(level, ParticleTypes.ELECTRIC_SPARK, center, 3.0, 28, 0.2);
            level.playSound(null, center.x, center.y, center.z, Skills.SOUND_RAIN.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 0.6f);
        });
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    // 渦潮 (引き潮): 前だけでなく、周り (範囲 + 1) の敵をみな足もとの 1 点へ巻き込み、足を鈍らせる。渦はしばらく残る
    private static String whirl(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        Vec3 c = player.position();
        double range = SkillRules.ebbRange(rank) + 1;
        List<LivingEntity> foes = within(player, c, range);
        if (foes.isEmpty()) {
            return "message.skyabyss.skill.no_target";
        }
        Vec3 point = c.add(SkillActions.flatLook(player).scale(2.2));
        for (LivingEntity e : foes) {
            pull(e, point, 0.6);
            SkillEffects.slow(e, 0.5 * (SkillActions.isLarge(e) ? 0.5 : 1.0), SkillRules.ebbSlowTicks(rank));
        }
        SkillEffects.zone(SkillEffects.ZoneKind.WHIRL, player, point, SkillRules.WHIRL_RADIUS, SkillRules.WHIRL_TICKS, rank);
        spiral(level, point, range, ParticleTypes.SPLASH);
        spiral(level, point, range * 0.7, ParticleTypes.BUBBLE_POP);
        SkillActions.sound(player, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.9f, 0.6f);
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    // 潮走り (踏み込み): 狙いがいなくても、前へ 8 ブロック水のように駆け、通り道の全員を ×0.8 で斬る
    private static String surge(ServerPlayer player, int rank) {
        if (!player.onGround() || player.isPassenger()) {
            return "message.skyabyss.skill.need_ground";
        }
        ServerLevel level = player.serverLevel();
        Vec3 dir = SkillActions.flatLook(player);
        Vec3 from = player.position();
        Vec3 end = dashEnd(player, dir, SkillRules.LUNGE_RANGE);
        if (end.distanceToSqr(from) > 0.01) {
            teleport(player, end);
        }
        float amount = atk(player, SkillRules.LUNGE_MULTIPLIER);
        for (LivingEntity e : along(player, from, end.add(dir.scale(1.0)), 1.3)) {
            SkillActions.hit(player, e, amount, Skill.LUNGE);
        }
        line(level, ParticleTypes.SPLASH, from.add(0, 0.6, 0), end.add(0, 0.6, 0), 0.3, 0.3);
        line(level, ParticleTypes.BUBBLE_POP, from.add(0, 1.2, 0), end.add(0, 1.2, 0), 0.5, 0.2);
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillActions.sound(player, SoundEvents.PLAYER_SPLASH, 0.9f, 1.4f);
        return null;
    }

    // 熾の墜落 (熾火の槌): 振り下ろす代わりに、前 6 ブロックへ火の塊を落とす。0.5 秒後に ×(槌の倍率) を与え、熾火の床を残す
    private static String meteor(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position().add(SkillActions.flatLook(player).scale(6.0));
        float amount = atk(player, SkillRules.hammerMultiplier(rank));
        double radius = SkillRules.HAMMER_RADIUS + 0.5;
        SkillActions.ring(level, ParticleTypes.FLAME, center, radius, 22, 0.1);
        for (int i = 0; i < 10; i++) {
            int h = i;
            SkillEffects.later(level, h, () -> {
                level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 10 - h, center.z, 14, 0.4, 0.5, 0.4, 0.03);
                level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 10 - h, center.z, 2, 0.3, 0.3, 0.3, 0.0);
                level.sendParticles(ParticleTypes.SMOKE, center.x, center.y + 10 - h, center.z, 4, 0.3, 0.3, 0.3, 0.02);
            });
        }
        SkillEffects.later(level, 10, () -> {
            if (!alive(player, level)) {
                return;
            }
            for (LivingEntity e : within(player, center, radius)) {
                if (SkillActions.hit(player, e, amount, Skill.EMBER_HAMMER)) {
                    push(e, center, 0.4);
                }
            }
            SkillEffects.zone(SkillEffects.ZoneKind.EMBER, player, center, radius, SkillRules.EMBER_TICKS, rank);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 0.6, center.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.5, center.z, 40, radius * 0.4, 0.3, radius * 0.4, 0.12);
            level.sendParticles(dust(Blocks.MAGMA_BLOCK), center.x, center.y + 0.3, center.z, 30, radius * 0.5, 0.3, radius * 0.5, 0.1);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.8f);
        });
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillActions.sound(player, Skills.SOUND_CLEAVE.get(), 1.0f, 0.5f);
        return null;
    }

    // 熾火の罠 (骨の罠): 縛る罠の代わりに、見ている先 (8 ブロック) へ火の地雷を埋める。踏まれると (6 秒で自爆) 半径 3 を ×(罠の倍率) で吹き飛ばして燃やす
    private static String mine(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        Vec3 at = aim(player, 8);
        float amount = atk(player, SkillRules.trapMultiplier(rank) * 1.1);
        SkillActions.ring(level, ParticleTypes.FLAME, at, 1.0, 12, 0.1);
        Runnable[] tick = new Runnable[1];
        int[] age = {0};
        tick[0] = () -> {
            if (!alive(player, level)) {
                return;
            }
            age[0] += 4;
            boolean triggered = age[0] >= 24 && !within(player, at, 1.8).isEmpty();
            if (triggered || age[0] >= 6 * SkillRules.SECOND) {
                for (LivingEntity e : within(player, at, 3.0)) {
                    if (SkillActions.hit(player, e, amount, Skill.BONE_TRAP)) {
                        e.igniteForSeconds(3);
                        push(e, at, 0.5);
                    }
                }
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.4, at.z, 1, 0, 0, 0, 0);
                SkillActions.ring(level, ParticleTypes.FLAME, at, 2.5, 24, 0.2);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.2f);
                return;
            }
            if (age[0] % 12 == 0) {
                level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y + 0.2, at.z, 3, 0.2, 0.05, 0.2, 0.01);
            }
            SkillEffects.later(level, 4, tick[0]);
        };
        SkillEffects.later(level, 4, tick[0]);
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillActions.sound(player, Skills.SOUND_BACKSTEP.get(), 0.7f, 0.5f);
        return null;
    }
}
