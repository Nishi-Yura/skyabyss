package jp.revati.skyabyss.story.ch5.warden;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssEntrance;
import jp.revati.skyabyss.story.ch5.AbyssGate;
import jp.revati.skyabyss.story.ch5.AbyssPressure;
import jp.revati.skyabyss.story.ch5.AbyssRespawn;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import jp.revati.skyabyss.town.TownGuests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.neoforged.neoforge.common.util.FakePlayer;

// SELFTEST abyss warden: 心臓の番人 (docs/design/36 の 6)。
//   印 (heart と灯籠 8 つ)、出したときの体力、闇 25% / 光 100% の傷、灯籠の点く・消える (段で持ちが短くなる)、
//   二段目の写し二つ、三段目の闇の波、間の中の闇のたまり 2 倍、倒した印と後片付け
public final class WardenSelfTest {
    private WardenSelfTest() {
    }

    // 心臓の間の塊を先に読み込み、間を建てておく (番人と写しが床の上に出られるように)
    public static void load(MinecraftServer server) {
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        BlockPos heart = AbyssBuild.mark("heart");
        if (level != null && heart != null) {
            hold(level, heart, true);
            if (!AbyssBuild.built(server)) {
                AbyssBuild.finishNow(server);
            }
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        BlockPos heart = AbyssBuild.mark("heart");
        boolean clearedWas = WardenArena.cleared(server);
        try {
            check(f, level != null, "the abyss dimension is missing");
            check(f, heart != null, "the heart mark is missing");
            for (int i = 0; i < AbyssBeacons.COUNT; i++) {
                BlockPos b = AbyssBeacons.pos(i);
                check(f, b != null, "beacon_" + i + " is missing");
                if (b != null && heart != null) {
                    double d = Math.sqrt(b.distSqr(heart));
                    check(f, d > 10 && d < WardenArena.ROOM_RADIUS, "beacon_" + i + " is not on the ring: " + d);
                }
            }
            if (level == null || heart == null) {
                report(f);
                return;
            }
            HeartWarden w = WardenArena.wake(level);
            check(f, w != null && w.isAlive(), "the warden did not wake");
            check(f, w != null && level.getEntity(w.getUUID()) == w, "the warden did not join the level (removed " + (w == null ? null : w.getRemovalReason()) + ")");
            if (w == null) {
                report(f);
                return;
            }
            check(f, Math.abs(w.getMaxHealth() - HeartWarden.BASE_HP) < 1, "one player should face 600 HP: " + w.getMaxHealth());
            check(f, AbyssBeacons.litCount() == 0, "waking should put every lantern out");
            BlockPos b0 = AbyssBeacons.pos(0);
            check(f, level.getBlockState(b0).is(Blocks.SOUL_CAMPFIRE) && !level.getBlockState(b0).getValue(CampfireBlock.LIT), "the lantern is not an unlit soul campfire");
            // 闇の中の傷は 25%
            w.invulnerableTime = 0;
            float hp = w.getHealth();
            w.hurt(level.damageSources().generic(), 100);
            check(f, Math.abs(hp - w.getHealth() - 25) < 0.5, "a dark hit should deal 25: " + (hp - w.getHealth()));
            // 光の中は 100%
            w.getEntityData().set(HeartWarden.LIT, true);
            w.invulnerableTime = 0;
            hp = w.getHealth();
            w.hurt(level.damageSources().generic(), 40);
            check(f, Math.abs(hp - w.getHealth() - 40) < 0.5, "a lit hit should deal 40: " + (hp - w.getHealth()));
            w.getEntityData().set(HeartWarden.LIT, false);
            // 灯籠: 一段目は 40 秒
            long now = level.getGameTime();
            AbyssBeacons.light(level, 0, now);
            check(f, AbyssBeacons.lit(0) && level.getBlockState(b0).getValue(CampfireBlock.LIT), "lighting did not light the campfire");
            check(f, AbyssBeacons.litNear(b0.offset(5, 0, 0)) && !AbyssBeacons.litNear(b0.offset(9, 0, 0)), "the lantern sight should reach 7");
            AbyssBeacons.tickAt(level, now + 40 * 20 - 1);
            check(f, AbyssBeacons.lit(0), "the lantern went out before 40 s");
            AbyssBeacons.tickAt(level, now + 40 * 20);
            check(f, !AbyssBeacons.lit(0) && !level.getBlockState(b0).getValue(CampfireBlock.LIT), "the lantern did not go out at 40 s");
            // 間の中で灯籠が全部消えていると闇のたまりは 2 倍
            FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "warden_test"));
            p.moveTo(heart.getX() + 0.5, heart.getY() + 1, heart.getZ() + 0.5);
            check(f, AbyssPressure.voiceScale.applyAsDouble(p) == 2.0, "the dark should gather twice as fast in the heart room");
            AbyssBeacons.light(level, 1, now);
            check(f, AbyssPressure.voiceScale.applyAsDouble(p) == 1.0, "a lit lantern should bring the gathering back to normal");
            AbyssBeacons.snuff(level, 1);
            // 二段目 (60% 以下): 写しが二つ、灯籠は 30 秒
            w.setHealth(w.getMaxHealth() * 0.5f);
            w.updatePhase(level);
            check(f, w.phase() == 2 && AbyssBeacons.phase == 2, "half health should be phase 2");
            int decoys = level.getEntitiesOfClass(HeartWarden.class, w.getBoundingBox().inflate(48), HeartWarden::decoy).size();
            check(f, decoys == 1, "phase 2 should bring one shade: " + decoys);
            AbyssBeacons.light(level, 2, now);
            AbyssBeacons.tickAt(level, now + 30 * 20);
            check(f, !AbyssBeacons.lit(2), "in phase 2 a lantern should last 30 s");
            // 写しは一撃で消える
            HeartWarden shade = level.getEntitiesOfClass(HeartWarden.class, w.getBoundingBox().inflate(48), HeartWarden::decoy).get(0);
            shade.hurt(level.damageSources().generic(), 1);
            check(f, shade.isRemoved(), "a shade should vanish in one hit");
            // 三段目 (30% 以下): 闇の波で全部消える
            AbyssBeacons.light(level, 3, now);
            AbyssBeacons.light(level, 4, now);
            w.setHealth(w.getMaxHealth() * 0.2f);
            w.updatePhase(level);
            check(f, w.phase() == 3 && AbyssBeacons.litCount() == 0, "the dark wave should put every lantern out");
            // 倒す
            w.getEntityData().set(HeartWarden.LIT, true);
            w.invulnerableTime = 0;
            w.hurt(level.damageSources().generic(), 10000);
            check(f, w.isDeadOrDying(), "the warden did not fall");
            check(f, WardenArena.cleared(server), "the fall was not recorded");
            int left = level.getEntitiesOfClass(HeartWarden.class, w.getBoundingBox().inflate(48), HeartWarden::decoy).size();
            check(f, left == 0, "shades stayed after the fall: " + left);
            w.discard();
            // 休み場: 立つ所に足場と頭の空き、近くに来ると覚える
            for (int i = 0; i <= 3; i++) {
                BlockPos at = AbyssRespawn.spot(i);
                if (at == null) {
                    f.add("no spot for rest " + i);
                    continue;
                }
                level.getChunk(at);
                check(f, !level.getBlockState(at.below()).isAir() && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir(),
                        "rest " + i + " spot is not standable: " + at + " " + level.getBlockState(at.below()) + "/" + level.getBlockState(at) + "/" + level.getBlockState(at.above()));
            }
            FakePlayer r = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_rest"));
            BlockPos s2 = AbyssRespawn.spot(2);
            r.moveTo(s2.getX() + 0.5, s2.getY(), s2.getZ() + 0.5);
            AbyssRespawn.touch(r);
            check(f, AbyssRespawn.rest(r) == 2, "standing at rest 2 was not remembered: " + AbyssRespawn.rest(r));
            // 心臓に触れる: 段 heart の人だけ
            FakePlayer q = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "warden_heart"));
            Quests.forgetAll(q);
            check(f, !AbyssGate.touch(q), "someone without the quest could touch the heart");
            Quests.start(q, AbyssEntrance.QUEST);
            for (int i = 0; i < 24 && !"heart".equals(TownGuests.stageOf(q, AbyssEntrance.QUEST)); i++) {
                Quests.forceStage(q, AbyssEntrance.QUEST);
            }
            check(f, "heart".equals(TownGuests.stageOf(q, AbyssEntrance.QUEST)) && AbyssGate.touch(q), "the heart stage could not touch the heart");
            Quests.forgetAll(q);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST abyss warden crashed", e);
            f.add("crashed: " + e);
        } finally {
            if (level != null) {
                for (HeartWarden h : level.getEntitiesOfClass(HeartWarden.class, new net.minecraft.world.phys.AABB(heart).inflate(64), e -> true)) {
                    h.discard();
                }
                AbyssBeacons.reset(level);
                if (heart != null) {
                    hold(level, heart, false);
                }
            }
            WardenArena.Data d = WardenArena.Data.get(server);
            d.cleared = clearedWas;
            d.setDirty();
        }
        report(f);
    }

    static void hold(ServerLevel level, BlockPos heart, boolean on) {
        int cx = heart.getX() >> 4;
        int cz = heart.getZ() >> 4;
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                level.setChunkForced(x, z, on);
            }
        }
    }

    private static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss warden PASSED (marks, wake, 25/100 shares, lantern 40/30 s and sight, dark gathers x2, shades, dark wave, fall, heart touch by stage, rest spots and memory)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST abyss warden FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}
