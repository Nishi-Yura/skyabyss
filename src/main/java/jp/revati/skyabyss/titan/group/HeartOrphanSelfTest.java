package jp.revati.skyabyss.titan.group;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.HeartBoss;
import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 「死んでも巨神のボス (心臓) が消えないときがある」(2026-10-23 の試遊) の自己テスト (SKYABYSS_SELFTEST_ONLY="heart orphan")。
//  1. 全滅: 部屋の中の参加者が全員倒れる → ABANDON_DELAY のうちに戦いが終わり、心臓が消える
//  2. 全員が出る: 倒れずに部屋から出る → 同じく消える
//  3. サーバーの再起動 (部屋の状態はメモリにだけある): 部屋を clear() したあと、残った心臓は ORPHAN_TICKS のうちに自分を消す
//  4. 保存から戻った心臓 (印 RoomBound つき) も、戦わせている部屋がなければ自分を消す
//  5. 部屋と関係なく置いた心臓 (/summon・テスト) は消えない
//  6. 本物の潮殻の心臓の間: 背中の次元に人が一人もいない (全員が地上へ戻った) と、前は戦いが閉じたまま心臓が残った (再現)。直したあとは放棄して消える
public final class HeartOrphanSelfTest {
    private static final int X = 2600;
    private static final int Y = 230;
    private static final int Z = 2600;

    private HeartOrphanSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        List<SelfTestRunner.Step> steps = new ArrayList<>();
        steps.add(SelfTestRunner.Step.once("heart orphan (load)", HeartOrphanSelfTest::load));
        steps.add(SelfTestRunner.Step.until("heart orphan", HeartOrphanSelfTest::run));
        return steps;
    }

    private static final List<String> F = new ArrayList<>();
    private static int stage;
    private static int wait;
    private static GroupBossRoom room;
    private static Host host;
    private static FakePlayer a;
    private static FakePlayer b;
    private static Mob restartHeart;
    private static Mob reloaded;
    private static Mob loose;
    private static UUID tideGroup;

    static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.setChunkForced(X >> 4, Z >> 4, true);
        jp.revati.skyabyss.titan.tide.heart.TideHeartSelfTest.load(server);
        F.clear();
        stage = 0;
        wait = 0;
    }

    // 部屋の中にいる人を手で決める部屋 (偽のプレイヤーはレベルの人の一覧に入らないので)
    private static final class Host implements GroupBossRoom.Host {
        final List<ServerPlayer> inside = new ArrayList<>();

        @Override
        public List<ServerPlayer> playersInside(ServerLevel level) {
            return inside;
        }

        @Override
        public void seal(ServerLevel level, UUID group) {
        }

        @Override
        public void open(ServerLevel level, UUID group) {
        }

        @Override
        public void pullIn(ServerLevel level, UUID group) {
        }

        @Override
        public void letBack(ServerLevel level, UUID group, Set<UUID> participants) {
        }

        @Override
        public Mob spawnBoss(ServerLevel level, UUID group, List<ServerPlayer> challengers) {
            StoneWhaleHeart heart = ModEntities.STONE_WHALE_HEART.get().create(level);
            if (heart == null) {
                return null;
            }
            heart.moveTo(X + 0.5, Y, Z + 0.5, 0f, 0f);
            heart.setGroupId(group);
            level.addFreshEntity(heart);
            return heart;
        }

        @Override
        public Vec3 home() {
            return new Vec3(X + 0.5, Y, Z + 0.5);
        }

        @Override
        public double leash() {
            return 64;
        }

        @Override
        public void broadcast(ServerLevel level, UUID group, Component message) {
        }

        @Override
        public void countdown(ServerLevel level, UUID group, int secondsLeft) {
        }

        @Override
        public void cleanup(ServerLevel level, UUID group) {
        }

        @Override
        public boolean asleep(ServerLevel level) {
            return false;
        }
    }

    private static boolean gone(ServerLevel level, @javax.annotation.Nullable Entity e) {
        return e == null || e.isRemoved() || level.getEntity(e.getUUID()) == null;
    }

    // 部屋を 10 tick ぶん n 回動かす (部屋の tick は 10 tick ごとに呼ぶ決まり)
    private static void steps(ServerLevel level, int n) {
        for (int i = 0; i < n; i++) {
            room.tick(level, host);
        }
    }

    static boolean run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        try {
            return advance(server, level);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST heart orphan crashed", e);
            F.add("crashed: " + e);
            return finish();
        }
    }

    private static boolean advance(MinecraftServer server, ServerLevel level) {
        switch (stage) {
            case 0 -> {
                a = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "orphan_a"));
                b = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "orphan_b"));
                host = new Host();
                room = new GroupBossRoom(Groups.keyOf(a));
                // 1. 全滅 (一人パーティの鍵で、二人とも同じ鍵に見えるよう a だけで数える)
                host.inside.add(a);
                if (!room.start(level, host, host.inside)) {
                    F.add("wipe: the room did not start");
                    return finish();
                }
                Entity heart = level.getEntity(room.bossId());
                if (!(heart instanceof HeartBoss hb) || !hb.roomBound()) {
                    F.add("wipe: the spawned heart is not marked as bound to its room");
                }
                steps(level, 1);
                GroupBossRoom.noteDeath(a.getUUID());
                host.inside.clear();
                steps(level, GroupBossRoom.ABANDON_DELAY / 10);
                if (room.active() || !gone(level, heart)) {
                    F.add("wipe: the heart stayed after the whole party fell");
                }
                // 2. 全員が出る (倒れずに)
                host.inside.add(a);
                room = new GroupBossRoom(Groups.keyOf(a));
                room.start(level, host, host.inside);
                heart = level.getEntity(room.bossId());
                steps(level, 1);
                host.inside.clear();
                steps(level, GroupBossRoom.ABANDON_DELAY / 10 - 1);
                if (!room.active() || gone(level, heart)) {
                    F.add("leave: the fight ended before the abandon delay");
                }
                steps(level, 1);
                if (room.active() || !gone(level, heart)) {
                    F.add("leave: the heart stayed after everyone walked out");
                }
                // 3. 再起動: 部屋の状態だけが消える
                host.inside.add(b);
                room = new GroupBossRoom(Groups.keyOf(b));
                room.start(level, host, host.inside);
                restartHeart = (Mob) level.getEntity(room.bossId());
                // 4. 保存から戻った心臓 (印つき)。同じ UUID は使えないので新しい UUID で置き直す
                Host second = new Host();
                second.inside.add(a);
                GroupBossRoom other = new GroupBossRoom(Groups.keyOf(a));
                other.start(level, second, second.inside);
                Entity saved = level.getEntity(other.bossId());
                CompoundTag tag = saved.saveWithoutId(new CompoundTag());
                tag.putString("id", EntityType.getKey(saved.getType()).toString());
                other.reset(level, second, false);
                Entity copy = EntityType.create(tag, level).orElse(null);
                if (!(copy instanceof Mob m) || !(copy instanceof HeartBoss hb2) || !hb2.roomBound()) {
                    F.add("reload: the saved heart lost its room mark");
                } else {
                    copy.setUUID(UUID.randomUUID());
                    level.addFreshEntity(copy);
                    reloaded = m;
                }
                room.clear();
                // 5. 部屋と関係なく置いた心臓
                loose = ModEntities.STONE_WHALE_HEART.get().create(level);
                if (loose != null) {
                    loose.moveTo(X + 6.5, Y, Z + 0.5, 0f, 0f);
                    level.addFreshEntity(loose);
                }
                stage = 1;
                wait = 0;
                return false;
            }
            case 1 -> {
                wait++;
                if (wait == HeartBoss.ORPHAN_TICKS - 30 && gone(level, restartHeart)) {
                    F.add("restart: the orphan heart vanished too early");
                }
                if (wait < HeartBoss.ORPHAN_TICKS + 30) {
                    return false;
                }
                if (!gone(level, restartHeart)) {
                    F.add("restart: the heart stayed after the room state was lost (server restart mid-fight)");
                }
                if (reloaded != null && !gone(level, reloaded)) {
                    F.add("reload: a heart loaded back from disk with no room stayed");
                }
                if (loose == null || gone(level, loose)) {
                    F.add("a heart placed without a room (/summon) was removed");
                }
                if (loose != null) {
                    loose.discard();
                }
                for (Mob m : new Mob[]{restartHeart, reloaded}) {
                    if (m != null) {
                        m.discard();
                    }
                }
                stage = 2;
                wait = 0;
                return false;
            }
            case 2 -> {
                // 6. 本物の潮殻の心臓の間。まず前の動き (人のいない次元では放棄を数えない) で残ることを確かめる
                ServerLevel back = server.getLevel(jp.revati.skyabyss.titan.tide.TideTravel.TIDE_BACK);
                if (back == null) {
                    F.add("tide: no tide back dimension");
                    return finish();
                }
                tideGroup = UUID.randomUUID();
                GroupBossRoom.abandonWhenLevelEmpty = false;
                if (!jp.revati.skyabyss.titan.tide.heart.TideHeartBattle.startNow(back, tideGroup)) {
                    F.add("tide: the fight did not start");
                    return finish();
                }
                stage = 3;
                wait = 0;
                return false;
            }
            case 3 -> {
                ServerLevel back = server.getLevel(jp.revati.skyabyss.titan.tide.TideTravel.TIDE_BACK);
                if (++wait < 100) {
                    return false;
                }
                boolean before = jp.revati.skyabyss.titan.tide.heart.TideHeartBattle.current(back, tideGroup) != null;
                SkyAbyss.LOGGER.info("SELFTEST heart orphan: with nobody on the back, the old rule kept the heart = {}", before);
                GroupBossRoom.abandonWhenLevelEmpty = true;
                stage = 4;
                wait = 0;
                return false;
            }
            case 4 -> {
                ServerLevel back = server.getLevel(jp.revati.skyabyss.titan.tide.TideTravel.TIDE_BACK);
                if (++wait < GroupBossRoom.ABANDON_DELAY + 40) {
                    return false;
                }
                if (jp.revati.skyabyss.titan.tide.heart.TideHeartBattle.current(back, tideGroup) != null
                        || jp.revati.skyabyss.titan.tide.heart.TideHeartBattle.fighting(tideGroup)) {
                    F.add("tide: with nobody left on the back the fight stayed sealed and the heart stayed");
                }
                jp.revati.skyabyss.titan.tide.heart.TideHeartBattle.abandon(back, tideGroup);
                jp.revati.skyabyss.titan.tide.heart.TideHeartBattle.releaseAll(back);
                return finish();
            }
            default -> {
                return finish();
            }
        }
    }

    private static boolean finish() {
        GroupBossRoom.abandonWhenLevelEmpty = !SelfTestRunner.enabled();
        if (F.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST heart orphan PASSED");
        } else {
            F.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST heart orphan FAILED: {}", m));
        }
        stage = 99;
        return true;
    }
}
