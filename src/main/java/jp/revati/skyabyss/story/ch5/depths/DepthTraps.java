package jp.revati.skyabyss.story.ch5.depths;

import java.util.Random;

import org.joml.Vector3f;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.depths.DepthFloor.Room;
import jp.revati.skyabyss.story.ch5.depths.DepthFloor.RoomType;
import jp.revati.skyabyss.story.ch5.depths.DepthFloor.Trap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.block.Blocks;

// 階の中の仕掛け (docs/design/36 の 9)。人が階の中にいる間、DepthRun から 2 tick ごとに呼ばれる。
//   罠 5 種 (踏むと一度だけ働く。床の割れた板が目印で、近づくと赤い粒が見える)
//     SPIKE 棘 (傷)・POISON 毒の霧 (毒)・SUMMON 呼び笛 (敵が三体湧く)・BLIND 眩み (闇)・HEAVY 重石 (足が重い)
//   敵の巣: 巣の部屋の真ん中に踏み入ると、部屋に一度に湧く
//   王の広間: 広間に踏み入ると王が目を覚ます
//   行商: 行商のいる部屋では、鐘銭か深淵の素材で買い物ができる (村人の取引画面)
public final class DepthTraps {
    private static final DustParticleOptions HINT = new DustParticleOptions(new Vector3f(0.9f, 0.08f, 0.08f), 0.7f);
    public static final String MERCHANT_TAG = "skyabyss_depth_merchant";
    static final Random RAND = new Random();

    private DepthTraps() {
    }

    static void tick(ServerLevel level, DepthBuilder.Inst i, ServerPlayer p) {
        int tx = (int) Math.floor(p.getX()) - i.ox;
        int tz = (int) Math.floor(p.getZ()) - i.oz;
        boolean low = p.getY() < DepthPlan.FLOOR_Y + 1.7 && p.getY() > DepthPlan.FLOOR_Y - 0.5;
        if (p.tickCount % 20 == 0) {
            for (Trap t : i.plan.traps()) {
                if (!i.trapsUsed.contains(key(t)) && Math.abs(t.x() - tx) + Math.abs(t.z() - tz) <= 8) {
                    level.sendParticles(HINT, i.ox + t.x() + 0.5, DepthPlan.FLOOR_Y + 1.2, i.oz + t.z() + 0.5, 2, 0.25, 0.1, 0.25, 0);
                }
            }
        }
        if (low) {
            for (Trap t : i.plan.traps()) {
                if (!i.trapsUsed.contains(key(t)) && t.x() == tx && t.z() == tz && p.getY() < DepthPlan.FLOOR_Y + 1.4) {
                    trigger(level, i, p, t);
                }
            }
        }
        if (i.plan.houseRoom() >= 0 && !i.houseFired) {
            Room h = i.plan.rooms().get(i.plan.houseRoom());
            if (Math.abs(tx - h.cx()) <= 3 && Math.abs(tz - h.cz()) <= 3 && low) {
                i.houseFired = true;
                int n = DepthMobs.spawnHouse(level, i, h);
                for (ServerPlayer q : DepthBoss.party(level, i)) {
                    Notice.alert(q, "message.skyabyss.depths.house");
                    level.playSound(null, q.blockPosition(), SoundEvents.WARDEN_NEARBY_CLOSEST, SoundSource.HOSTILE, 0.9f, 0.7f);
                }
                jp.revati.skyabyss.SkyAbyss.LOGGER.info("[abyss depth] monster house on floor {}: {} foes", i.floor, n);
            }
        }
        if (i.plan.boss() && i.boss == null && i.bossDeadAt < 0 && p.isAlive()) {
            Room arena = i.plan.first(RoomType.BOSS);
            if (arena != null && tx >= arena.x0() + 3 && tx < arena.x0() + arena.w() && tz >= arena.z0() && tz < arena.z0() + arena.d()) {
                DepthBoss.start(level, i, DepthBoss.party(level, i));
            }
        }
    }

    static long key(Trap t) {
        return t.x() * 1000L + t.z();
    }

    static void trigger(ServerLevel level, DepthBuilder.Inst i, ServerPlayer p, Trap t) {
        i.trapsUsed.add(key(t));
        BlockPos floor = i.pos(t.x(), DepthFloorBuild.FLOOR_YI, t.z());
        level.setBlock(floor, Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
        level.sendParticles(HINT, floor.getX() + 0.5, floor.getY() + 1.2, floor.getZ() + 0.5, 24, 0.4, 0.3, 0.4, 0.02);
        float dmg = (float) Math.min(10, 3 + 0.15 * i.floor);
        String key = "message.skyabyss.depths.trap." + t.kind().name().toLowerCase(java.util.Locale.ROOT);
        switch (t.kind()) {
            case SPIKE -> {
                p.hurt(level.damageSources().generic(), dmg);
                level.playSound(null, floor, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0f, 0.6f);
            }
            case POISON -> p.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
            case SUMMON -> {
                int made = 0;
                for (int k = 0; k < 3; k++) {
                    int dx = RAND.nextInt(7) - 3;
                    int dz = RAND.nextInt(7) - 3;
                    int tx = t.x() + dx;
                    int tz = t.z() + dz;
                    if (tx > 0 && tz > 0 && tx < DepthPlan.SIZE && tz < DepthPlan.SIZE && i.plan.open()[tx][tz]) {
                        BlockPos feet = i.feet(tx, tz);
                        if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()) {
                            DepthMobs.spawn(level, i.floor, feet, RAND);
                            made++;
                        }
                    }
                }
                level.playSound(null, floor, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), SoundSource.HOSTILE, 0.6f, 0.8f);
                if (made == 0) {
                    key = "message.skyabyss.depths.trap.dud";
                }
            }
            case BLIND -> p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
            case HEAVY -> p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
        }
        Notice.warn(p, key);
    }

    // 行商を置く (階が整って最初の人が着いたとき、一度だけ)
    static void spawnMerchant(ServerLevel level, DepthBuilder.Inst i) {
        if (i.shopped || i.plan.merchant() == null) {
            return;
        }
        i.shopped = true;
        BlockPos at = i.feet(i.plan.merchant().x(), i.plan.merchant().z());
        Villager v = EntityType.VILLAGER.create(level);
        if (v == null) {
            return;
        }
        v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        v.setNoAi(true);
        v.setInvulnerable(true);
        v.setPersistenceRequired();
        v.setVillagerData(new VillagerData(VillagerType.SWAMP, VillagerProfession.CLERIC, 1));
        v.setCustomName(Component.translatable("message.skyabyss.depths.merchant"));
        v.setCustomNameVisible(true);
        v.setOffers(DepthLoot.offers(i.floor));
        v.addTag(MERCHANT_TAG);
        level.addFreshEntity(v);
    }
}
