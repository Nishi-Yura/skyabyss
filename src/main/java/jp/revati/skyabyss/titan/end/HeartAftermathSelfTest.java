package jp.revati.skyabyss.titan.end;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.HeartBoss;
import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.ember.heart.EmberHeartBattle;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupBossRoom;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.storm.heart.StormHeartBattle;
import jp.revati.skyabyss.titan.tide.heart.TideHeartBattle;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// 心臓を止めたあとの自己テスト (SKYABYSS_SELFTEST。結果は SELFTEST titan end hearts PASSED / FAILED)。docs/design/10 の 10.5。
// 「心臓を倒したあと、また心臓が出て、ボスゲージが残った。巨神も去らなかった」の再現と、直したことの確認。
//  - 岩鯨の心臓の間で、パーティの二人 (リーダーでない人が間接の傷で) が心臓を倒す。倒れたあと人が部屋に残っていても (運び出しの前) 心臓は湧き直さず、
//    ボスゲージは誰にも残らず、巨神は眠りに入り、運び出しで二人とも地上へ出る。別のディメンションへ移った人のボスゲージは外れる
//  - 四体の心臓の部屋の仕組みそれぞれで、倒したグループには (人が残っていても) 集合も湧かせ直しもしない。試練が戻って記録が消えれば、また始められる
//  - 停泊 (TitanStay) 中の岩鯨が眠りに入ると、沈まず居座るのでなく、昇って去る
public final class HeartAftermathSelfTest {
    private HeartAftermathSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(
                SelfTestRunner.Step.once("titan end hearts (load)", HeartAftermathSelfTest::load),
                SelfTestRunner.Step.once("titan end hearts", 40, HeartAftermathSelfTest::runSelfTest));
    }

    private static void forceArena(ServerLevel back, boolean on) {
        net.minecraft.world.phys.AABB a = TitanBackTrials.heartArena();
        for (int cx = ((int) a.minX >> 4) - 1; cx <= ((int) a.maxX >> 4) + 1; cx++) {
            for (int cz = ((int) a.minZ >> 4) - 1; cz <= ((int) a.maxZ >> 4) + 1; cz++) {
                back.setChunkForced(cx, cz, on);
            }
        }
    }

    static void load(MinecraftServer server) {
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        if (back != null) {
            forceArena(back, true);
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        try {
            if (back == null) {
                f.add("the whale back dimension is missing");
            } else {
                whaleHeartKilledByPartyMate(server, back, f);
                everyRoomKeepsQuietAfterDefeat(server, f);
                stayingWhaleLeaves(server, f);
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST titan end hearts crashed", e);
            f.add("crashed: " + e);
        } finally {
            for (String t : Titans.all()) {
                TitanDormancy.clear(server, t);
            }
            TitanEnd.clearHolding();
            Tasks.flush();
            if (back != null) {
                forceArena(back, false);
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST titan end hearts PASSED (no second heart after the kill, boss bar cleared, carry-out and sleep, all four rooms stay quiet after a defeat, a staying whale leaves)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST titan end hearts FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static FakePlayer fake(ServerLevel level, String tail, String name) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7e5a0e0d-0000-4000-8000-0000000f" + tail), name));
    }

    // 心臓の部屋の仕組みを包み、人の一覧だけを差し替える (フェイクの人は、世界の人の一覧に入らないため)
    private static final class Wrapped implements GroupBossRoom.Host {
        final GroupBossRoom.Host real;
        final List<ServerPlayer> inside = new ArrayList<>();

        Wrapped(GroupBossRoom.Host real) {
            this.real = real;
        }

        @Override
        public List<ServerPlayer> playersInside(ServerLevel level) {
            return List.copyOf(inside);
        }

        @Override
        public void seal(ServerLevel level, UUID group) {
            real.seal(level, group);
        }

        @Override
        public void open(ServerLevel level, UUID group) {
            real.open(level, group);
        }

        @Override
        public void pullIn(ServerLevel level, UUID group) {
        }

        @Override
        public void letBack(ServerLevel level, UUID group, Set<UUID> participants) {
        }

        @Override
        @Nullable
        public Mob spawnBoss(ServerLevel level, UUID group, List<ServerPlayer> challengers) {
            return real.spawnBoss(level, group, challengers);
        }

        @Override
        public Vec3 home() {
            return real.home();
        }

        @Override
        public double leash() {
            return real.leash();
        }

        @Override
        public void broadcast(ServerLevel level, UUID group, Component message) {
        }

        @Override
        public void countdown(ServerLevel level, UUID group, int secondsLeft) {
        }

        @Override
        public void cleanup(ServerLevel level, UUID group) {
            real.cleanup(level, group);
        }

        @Override
        public boolean asleep(ServerLevel level) {
            return false;
        }

        @Override
        public boolean defeated(ServerLevel level, UUID group) {
            return real.defeated(level, group);
        }
    }

    // ---------------------------------------------------------------- 岩鯨の心臓を、パーティの二人目が間接の傷で倒す
    private static void whaleHeartKilledByPartyMate(MinecraftServer server, ServerLevel back, List<String> f) {
        String titan = Titans.STONE_WHALE;
        ServerLevel overworld = server.overworld();
        TitanDormancy.clear(server, titan);
        TitanEnd.clearHolding();
        Tasks.flush();
        FakePlayer leader = fake(back, "0001", "[hearts_leader]");
        FakePlayer mate = fake(back, "0002", "[hearts_mate]");
        PartyData parties = PartyData.get(server);
        var snapshot = parties.snapshot(server.registryAccess());
        UUID group = null;
        int liveBefore = HeartBoss.liveCount();
        Wrapped host = new Wrapped(TitanBackTrials.heartHost());
        try {
            parties.clearAll();
            PartyData.Party party = parties.create(leader.getUUID(), "hearts_leader");
            parties.add(party, mate.getUUID(), "hearts_mate");
            group = party.id;
            check(f, Groups.keyOf(leader).equals(group) && Groups.keyOf(mate).equals(group), "the two players do not share the party's group");
            GroupProgress.reset(back, group);
            Vec3 home = host.home();
            for (FakePlayer p : List.of(leader, mate)) {
                p.setPos(home.x + 3, home.y, home.z + 3);
                host.inside.add(p);
            }
            GroupBossRoom room = TitanBackTrials.heartRooms().of(group);
            check(f, room.start(back, host, List.of(leader, mate)), "the heart did not start for the party");
            StoneWhaleHeart heart = room.bossId() != null && back.getEntity(room.bossId()) instanceof StoneWhaleHeart h ? h : null;
            if (heart == null) {
                f.add("the heart was not spawned");
                return;
            }
            heart.skipIntro();
            heart.showBarTo(leader);
            heart.showBarTo(mate);
            check(f, heart.barViewers().size() == 2, "the boss bar did not show to both party members");
            // 倒れる: リーダーでない人の間接の傷 (魔法・矢のたぐい)
            heart.setHealth(1f);
            heart.invulnerableTime = 0;
            heart.hurt(back.damageSources().indirectMagic(mate, mate), Float.MAX_VALUE);
            check(f, !heart.isAlive(), "the heart survived an indirect hit from a party member");
            check(f, heart.barViewers().isEmpty(), "the boss bar stayed on while the heart was dying");
            // 倒れる演出のあいだ、部屋は閉じたまま待つ。演出が終われば、撃破の報告
            for (int i = 0; i < HeartBoss.DEATH_TICKS + 5 && !heart.isRemoved(); i++) {
                heart.tick();
                if (i % 10 == 0) {
                    room.tick(back, host);
                }
            }
            check(f, heart.isRemoved(), "the heart was not removed after its death animation");
            BackGroupData.Record record = GroupProgress.of(back, group);
            check(f, record.isHeartDefeated(), "the kill was not recorded for the party's group");
            check(f, !room.active(), "the room stayed active after the kill");
            check(f, TitanDormancy.isResting(server, titan), "the whale did not fall asleep after its heart stopped");
            TitanDormancy.Rest rest = TitanDormancy.rest(server, titan);
            check(f, rest != null && rest.since - overworld.getGameTime() >= TitanEnd.CARRY_AT - 2, "the sleep is not timed to the carry-out");
            // 人が部屋に残ったまま、運び出しの前に 10 秒たっても (集合 5 秒 + 湧き)、心臓は湧き直さない
            for (int i = 0; i < 60; i++) {
                room.tick(back, host);
                if (room.active() || room.gathering()) {
                    f.add("a new gathering began after the heart was defeated (step " + i + ")");
                    break;
                }
            }
            long again = back.getEntitiesOfClass(Entity.class, TitanBackTrials.heartArena().inflate(8),
                    e -> e.getTags().contains(TitanBackTrials.HEART_TAG)).size();
            check(f, again == 0, "a second heart is standing in the room (" + again + ")");
            check(f, HeartBoss.liveCount() == liveBefore, "a heart is still registered after the kill (" + (HeartBoss.liveCount() - liveBefore) + ")");
            // 運び出し: 戦ったのはリーダーでない人だけ、でもパーティの二人とも出る
            List<ServerPlayer> picked = TitanEnd.pick(server, back, List.of(leader, mate), List.of(mate.getUUID()));
            check(f, picked.size() == 2, "the carry-out did not pick both party members (" + picked.size() + ")");
            // (フェイクの人は別のディメンションへ移せないので、降ろす先が決まることまで)
            check(f, TitanEnd.landing(server, titan).pos() != null, "no landing for the carry-out");
            host.inside.clear();
            room.tick(back, host);
            check(f, !room.active() && !room.gathering(), "the room woke after everyone left");
            // 別のディメンションへ運ばれた人のボスゲージは、心臓が動いていなくても外れる
            StoneWhaleHeart other = ModEntities.STONE_WHALE_HEART.get().create(back);
            other.moveTo(home.x, home.y, home.z, 0f, 0f);
            other.addTag(TitanBackTrials.HEART_TAG);
            other.setGroupId(group);
            other.bindArena(TitanBackTrials.heartArena(), 1);
            back.addFreshEntity(other);
            other.tick();
            other.showBarTo(leader);
            check(f, other.barViewers().contains(leader), "could not show the boss bar for the dimension check");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(leader, back.dimension(), Level.OVERWORLD));
            check(f, !other.barViewers().contains(leader), "the boss bar stayed on a player who left the dimension");
            other.discard();
            check(f, other.barViewers().isEmpty() && HeartBoss.liveCount() == liveBefore, "a discarded heart kept its boss bar or its registration");
            // 試練が戻って記録が消えれば、また始められる
            GroupProgress.reset(back, group);
            host.inside.add(leader);
            boolean began = false;
            for (int i = 0; i < 20 && !began; i++) {
                room.tick(back, host);
                began = room.gathering() || room.active();
            }
            check(f, began, "the heart could not be challenged again after the group's trials were renewed");
        } finally {
            Tasks.flush();
            if (group != null) {
                TitanBackTrials.heartRooms().of(group).reset(back, host, false);
                GroupProgress.reset(back, group);
                BackGroupData.get(back).remove(group);
            }
            for (Entity e : back.getEntitiesOfClass(Entity.class, TitanBackTrials.heartArena().inflate(8), e -> e.getTags().contains(TitanBackTrials.HEART_TAG))) {
                e.discard();
            }
            parties.restore(snapshot, server.registryAccess());
            TitanDormancy.clear(server, titan);
            TitanEnd.clearHolding();
        }
    }

    // ---------------------------------------------------------------- 四体の心臓の部屋: 倒したグループには、人が残っていても集合も湧かせ直しもしない
    private static final class Probe implements GroupBossRoom.Host {
        final GroupBossRoom.Host real;
        final List<ServerPlayer> inside;
        int spawns;

        Probe(GroupBossRoom.Host real, List<ServerPlayer> inside) {
            this.real = real;
            this.inside = inside;
        }

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
        @Nullable
        public Mob spawnBoss(ServerLevel level, UUID group, List<ServerPlayer> challengers) {
            spawns++;
            return null;
        }

        @Override
        public Vec3 home() {
            return Vec3.ZERO;
        }

        @Override
        public double leash() {
            return 20;
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

        @Override
        public boolean defeated(ServerLevel level, UUID group) {
            return real.defeated(level, group);
        }
    }

    private static void everyRoomKeepsQuietAfterDefeat(MinecraftServer server, List<String> f) {
        String[] titans = {Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER};
        GroupBossRoom.Host[] hosts = {TitanBackTrials.heartHost(), TideHeartBattle.host(), StormHeartBattle.host(), EmberHeartBattle.host()};
        for (int k = 0; k < titans.length; k++) {
            var dim = Titans.backOf(titans[k]);
            ServerLevel back = dim == null ? null : server.getLevel(dim);
            if (back == null) {
                f.add(titans[k] + ": the back dimension is missing");
                continue;
            }
            FakePlayer p = fake(back, "01" + k + "0", "[hearts_probe" + k + "]");
            UUID group = Groups.keyOf(p);
            BackGroupData data = BackGroupData.get(back);
            try {
                data.of(group).clearHeartDefeated();
                // 倒していなければ、人がいれば集合して湧かせようとする (確認の仕掛けが動いていること)
                Probe open = new Probe(hosts[k], List.of(p));
                GroupBossRoom live = new GroupBossRoom(group);
                for (int i = 0; i < 20 && open.spawns == 0; i++) {
                    live.tick(back, open);
                }
                check(f, open.spawns > 0, titans[k] + ": a room with a player and no defeat never tried to spawn the heart");
                // 倒したら、人が残っていても、集合も湧かせ直しもしない
                data.of(group).markHeartDefeated();
                check(f, hosts[k].defeated(back, group), titans[k] + ": the room does not know the group has defeated its heart");
                Probe quiet = new Probe(hosts[k], List.of(p));
                GroupBossRoom room = new GroupBossRoom(group);
                for (int i = 0; i < 60; i++) {
                    room.tick(back, quiet);
                    if (room.gathering() || room.active()) {
                        break;
                    }
                }
                check(f, quiet.spawns == 0 && !room.gathering() && !room.active(), titans[k] + ": the heart came back for a group that had defeated it");
                // 別のグループの記録は関係ない
                check(f, !hosts[k].defeated(back, UUID.randomUUID()), titans[k] + ": another group counted as having defeated the heart");
                // 試練が戻って記録が消えれば、また始められる
                data.of(group).clearHeartDefeated();
                Probe again = new Probe(hosts[k], List.of(p));
                GroupBossRoom renewed = new GroupBossRoom(group);
                for (int i = 0; i < 20 && again.spawns == 0; i++) {
                    renewed.tick(back, again);
                }
                check(f, again.spawns > 0, titans[k] + ": the heart could not be challenged again after renewal");
            } finally {
                data.remove(group);
            }
        }
    }

    // ---------------------------------------------------------------- 停泊 (TitanStay) 中の岩鯨が眠る: 沈む余地がなく居座るのでなく、昇って去る
    private static void stayingWhaleLeaves(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        int px = 6000;
        int pz = -6000;
        jp.revati.skyabyss.titan.StoneWhaleEntity whale = ModEntities.STONE_WHALE.get().create(level);
        whale.moveTo(px + 32, 134, pz + 32, 0f, 0f);
        whale.restHere();
        for (int i = 0; i < 40; i++) {
            whale.tickCount++;
            whale.tick();
        }
        double y0 = whale.getY();
        check(f, whale.isResting(), "the whale was not staying before the heart stopped");
        TitanDormancy.begin(server, Titans.STONE_WHALE, 0);
        for (int i = 0; i < 600; i++) {
            whale.tickCount++;
            whale.tick();
        }
        check(f, whale.getY() > y0 + 10, "a staying whale did not leave when its heart stopped (y " + y0 + " -> " + whale.getY() + ")");
        check(f, whale.chainLength() == 0f && !whale.isChainDown(), "the departing whale kept its chain down");
        TitanDormancy.clear(server, Titans.STONE_WHALE);
        for (int i = 0; i < 3; i++) {
            whale.tickCount++;
            whale.tick();
        }
        whale.discard();
        for (var chain : level.getEntitiesOfClass(jp.revati.skyabyss.titan.WhaleChainEntity.class, new net.minecraft.world.phys.AABB(px - 80, 0, pz - 80, px + 144, 320, pz + 144))) {
            chain.discard();
        }
    }
}
