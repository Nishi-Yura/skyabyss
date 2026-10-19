package jp.revati.skyabyss.titan.group;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.grave.GraveData;
import jp.revati.skyabyss.grave.GraveMarkers;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.titan.storm.back.StormBackState;
import jp.revati.skyabyss.titan.storm.back.StormProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 使い心地の磨き (rpg_brushup A3 / A4 / A8) の自己テスト:
//  - 背中の復活の守り: 光る・傷を受けない・狙われない、半径 8 ブロックには敵が湧かない (その外は湧く)、時間が過ぎれば解ける
//  - 石の「誰が鳴らしたか」: 自分 / 仲間 / まだ、巨神ごとの数、グループが石を戻せば「まだ」に戻る
//  - 墓の標: 持ち主にだけ送る・他人の墓は入らない・変わったときだけ・拾い終えたら空を一度だけ
public final class UxPolishSelfTest {
    private UxPolishSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            respawnGuard(server, f);
            whoRang(server, f);
            graveMarker(server, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ux polish crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ux polish PASSED");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST ux polish FAILED: {}", m));
        }
    }

    private static FakePlayer player(ServerLevel level, String name) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        p.setPos(0, 100, 0);
        return p;
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    // ---------------------------------------------------------------- A3
    private static void respawnGuard(MinecraftServer server, List<String> f) {
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        if (back == null) {
            f.add("titan_back dimension is missing");
            return;
        }
        check(f, RespawnGuard.TICKS >= 40 && RespawnGuard.TICKS <= 60, "the guard is not 2-3 seconds (" + RespawnGuard.TICKS + " ticks)");
        check(f, RespawnGuard.RADIUS >= 8.0, "the quiet radius is below 8 blocks");
        RespawnGuard.clear();
        FakePlayer p = player(back, "ux_respawn");
        p.setPos(6040.5, 100, 6040.5);
        back.getChunk(new BlockPos(6040, 100, 6040));
        // 床のない所には敵が湧かない決まり (SpawnSafety) なので、敵を置く所に床を敷く (建物のない背の外れ。(40, 100, 40) は建てた背の体の中)
        jp.revati.skyabyss.util.SpawnSafety.testFloor(back, new BlockPos(6040, 100, 6040), 5, 1);
        jp.revati.skyabyss.util.SpawnSafety.testFloor(back, new BlockPos(6060, 100, 6040), 1, 1);
        Zombie chaser = EntityType.ZOMBIE.create(back);
        Zombie near = null;
        Zombie far = null;
        try {
            check(f, !ReentryRules.guarded(p), "guarded before any respawn");
            chaser.moveTo(6044.5, 100, 6040.5);
            check(f, back.addFreshEntity(chaser), "the test mob could not be added to the back");
            chaser.setTarget(p);
            check(f, chaser.getTarget() == p, "the test could not set a target to begin with (guard active already?)");
            long now = back.getGameTime();
            RespawnGuard.begin(p);
            // 狙っていた敵の狙いを外す (世界の検索は、チャンクが動いているときだけ見つけるので、ここでは部品を直接)
            RespawnGuard.release(List.of(chaser), p);
            check(f, ReentryRules.guarded(p), "a respawn did not start the guard");
            check(f, p.hasEffect(MobEffects.GLOWING), "a respawn did not make the player glow");
            check(f, chaser.getTarget() == null, "a mob kept targeting the player after the respawn (in level " + chaser.isAddedToLevel() + ")");
            // 傷を受けない
            float hp = p.getHealth();
            p.hurt(back.damageSources().generic(), 6f);
            check(f, p.getHealth() >= hp, "the player took damage during the guard");
            // 守りの間は狙えない
            var change = new net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent(chaser, p,
                    net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent.LivingTargetType.MOB_TARGET);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(change);
            check(f, change.isCanceled(), "a mob could target a guarded player");
            // 半径 8 ブロックの静かな所
            check(f, RespawnGuard.quiet(back.dimension(), p.position().add(7, 0, 0), now), "7 blocks away is not quiet");
            check(f, !RespawnGuard.quiet(back.dimension(), p.position().add(9.5, 0, 0), now), "9.5 blocks away is quiet");
            check(f, !RespawnGuard.quiet(back.dimension(), p.position().add(3, 0, 0), now + RespawnGuard.ZONE_TICKS + 1), "the quiet zone never expires");
            check(f, !RespawnGuard.quiet(net.minecraft.world.level.Level.OVERWORLD, p.position(), now), "the quiet zone leaked into another dimension");
            // 実際の湧き: 近くは止まる・遠くは湧く
            near = EntityType.ZOMBIE.create(back);
            near.moveTo(6043.5, 100, 6040.5);
            check(f, !back.addFreshEntity(near), "a mob spawned inside the quiet radius");
            far = EntityType.ZOMBIE.create(back);
            far.moveTo(6060.5, 100, 6040.5);
            check(f, back.addFreshEntity(far), "a mob outside the quiet radius was blocked");
        } finally {
            RespawnGuard.clear();
            chaser.discard();
            if (far != null) {
                far.discard();
            }
            if (near != null) {
                near.discard();
            }
            p.removeAllEffects();
        }
    }

    // ---------------------------------------------------------------- A4
    private static void whoRang(MinecraftServer server, List<String> f) {
        ServerLevel storm = server.getLevel(StormTravel.STORM_BACK);
        if (storm == null) {
            f.add("storm_back dimension is missing");
            return;
        }
        PartyData parties = PartyData.get(server);
        var snapshot = parties.snapshot(server.registryAccess());
        UUID group = null;
        UUID soloGroup = null;
        try {
            parties.clearAll();
            FakePlayer a = player(storm, "ux_ring_a");
            FakePlayer b = player(storm, "ux_ring_b");
            FakePlayer solo = player(storm, "ux_ring_solo");
            PartyData.Party party = parties.create(a.getUUID(), "ux_ring_a");
            parties.add(party, b.getUUID(), "ux_ring_b");
            group = party.id;
            soloGroup = solo.getUUID();
            SteleRingers.get(server).forget(group);

            String west = TrialSteles.signal(TrialSteles.STORM, "west");
            String east = TrialSteles.signal(TrialSteles.STORM, "east");
            check(f, TrialSteles.who(server, group, a.getUUID(), west).who() == TrialSteles.Who.NONE, "an unrung stone did not read as not yet");
            // A が西の石を鳴らす
            StormProgress.of(storm, group).setRung(StormBackState.Trial.WEST, true);
            TrialSteles.onRung(storm, group, TrialSteles.STORM, "west", null, a);
            TrialSteles.WhoRang wa = TrialSteles.who(server, group, a.getUUID(), west);
            TrialSteles.WhoRang wb = TrialSteles.who(server, group, b.getUUID(), west);
            check(f, wa.who() == TrialSteles.Who.SELF, "the one who rang did not read as self (" + wa.who() + ")");
            check(f, wb.who() == TrialSteles.Who.OTHER && wb.name().equals("ux_ring_a"), "a companion did not see who rang (" + wb + ")");
            check(f, TrialSteles.who(server, group, a.getUUID(), east).who() == TrialSteles.Who.NONE, "another stone read as rung");
            // 巨神ごとの数
            int[] ca = TrialSteles.counts(server, group, a.getUUID(), "thunder_wing");
            int[] cb = TrialSteles.counts(server, group, b.getUUID(), "thunder_wing");
            check(f, ca[0] == 1 && ca[1] == 0 && ca[2] == 2, "ringer counts wrong: " + ca[0] + "/" + ca[1] + "/" + ca[2]);
            check(f, cb[0] == 0 && cb[1] == 1 && cb[2] == 2, "companion counts wrong: " + cb[0] + "/" + cb[1] + "/" + cb[2]);
            check(f, TrialSteles.counts(server, group, a.getUUID(), "no_such_titan")[2] == 0, "a titan without stones had stones");
            // B が東の石を鳴らす: 控えは石ごと
            StormProgress.of(storm, group).setRung(StormBackState.Trial.EAST, true);
            TrialSteles.onRung(storm, group, TrialSteles.STORM, "east", null, b);
            check(f, TrialSteles.who(server, group, a.getUUID(), east).who() == TrialSteles.Who.OTHER
                    && TrialSteles.who(server, group, b.getUUID(), east).who() == TrialSteles.Who.SELF, "the second stone's ringer was not kept separately");
            // 別のグループには見えない
            check(f, TrialSteles.who(server, soloGroup, solo.getUUID(), west).who() == TrialSteles.Who.NONE, "another group saw the party's ringer");
            // 控えのない鳴った石 (この機能より前・コマンド): 仲間が鳴らした扱いで名前が空
            StormProgress.of(storm, group).setRung(StormBackState.Trial.FRONT, true);
            TrialSteles.WhoRang unknown = TrialSteles.who(server, group, a.getUUID(), TrialSteles.signal(TrialSteles.STORM, "front"));
            check(f, unknown.who() == TrialSteles.Who.OTHER && unknown.name().isEmpty(), "a stone with no record did not read as an unnamed companion");
            // グループが石を戻せば「まだ」に戻る (古い控えは見えない)
            StormProgress.of(storm, group).setRung(StormBackState.Trial.WEST, false);
            check(f, TrialSteles.who(server, group, a.getUUID(), west).who() == TrialSteles.Who.NONE, "a reset stone still read as rung");
        } finally {
            if (group != null) {
                SteleRingers.get(server).forget(group);
                BackGroupDataCleanup.remove(server, group);
            }
            if (soloGroup != null) {
                BackGroupDataCleanup.remove(server, soloGroup);
            }
            parties.restore(snapshot, server.registryAccess());
        }
    }

    private static final class BackGroupDataCleanup {
        static void remove(MinecraftServer server, UUID group) {
            for (var key : List.of(StormTravel.STORM_BACK)) {
                ServerLevel l = server.getLevel(key);
                if (l != null) {
                    BackGroupData.get(l).remove(group);
                }
            }
        }
    }

    // ---------------------------------------------------------------- A8
    private static void graveMarker(MinecraftServer server, List<String> f) {
        ServerLevel over = server.overworld();
        GraveData data = GraveData.get(server);
        FakePlayer a = player(over, "ux_grave_a");
        FakePlayer b = player(over, "ux_grave_b");
        FakePlayer c = player(over, "ux_grave_c");
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        try {
            GraveMarkers.clearForTest();
            data.add(new GraveData.Grave(idA, a.getUUID(), "ux_grave_a", over.dimension(), new BlockPos(500, 70, 500), 1000L, 3, new ArrayList<>(List.of(new ItemStack(Items.STONE, 4)))));
            data.add(new GraveData.Grave(idB, b.getUUID(), "ux_grave_b", over.dimension(), new BlockPos(-700, 70, 900), 1000L, 0, new ArrayList<>(List.of(new ItemStack(Items.DIRT, 2)))));
            Map<UUID, List<GraveMarkers.Pos>> got = new HashMap<>();
            java.util.function.BiConsumer<ServerPlayer, GraveMarkers.Pos> sink = (p, pos) -> got.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(pos);
            List<ServerPlayer> all = List.of(a, b, c);
            GraveMarkers.tick(server, all, sink);
            check(f, got.getOrDefault(a.getUUID(), List.of()).size() == 1, "the owner did not get a marker");
            check(f, got.getOrDefault(b.getUUID(), List.of()).size() == 1, "the second owner did not get a marker");
            check(f, !got.containsKey(c.getUUID()), "a player without a grave got a packet");
            GraveMarkers.Pos pa = got.get(a.getUUID()).get(0);
            check(f, pa.marks().size() == 1 && pa.marks().get(0).x() == 500 && pa.marks().get(0).z() == 500, "the owner's marker has the wrong place");
            for (GraveMarkers.Mark m : pa.marks()) {
                check(f, m.x() != -700, "another player's grave was sent to the owner");
            }
            // 変わらなければ送り直さない
            GraveMarkers.tick(server, all, sink);
            check(f, got.get(a.getUUID()).size() == 1 && got.get(b.getUUID()).size() == 1, "an unchanged marker was sent again");
            // 拾い終えて墓がなくなれば、空を一度だけ
            data.remove(idA);
            GraveMarkers.tick(server, all, sink);
            GraveMarkers.tick(server, all, sink);
            List<GraveMarkers.Pos> after = got.get(a.getUUID());
            check(f, after.size() == 2 && after.get(1).marks().isEmpty(), "a collected grave did not send a single empty marker (" + after.size() + " packets)");
            check(f, got.get(b.getUUID()).size() == 1, "an unrelated grave change resent the other owner's marker");
            // 中身が空の墓は標にしない
            GraveData.Grave empty = new GraveData.Grave(idA, a.getUUID(), "ux_grave_a", over.dimension(), new BlockPos(1, 70, 1), 1000L, 0, new ArrayList<>());
            data.add(empty);
            check(f, GraveMarkers.marksFor(data, a.getUUID()).isEmpty(), "an emptied grave still shows a marker");
            // 一度に送る数の上限
            data.remove(idA);
            List<UUID> extra = new ArrayList<>();
            for (int i = 0; i < GraveMarkers.MAX + 2; i++) {
                UUID id = UUID.randomUUID();
                extra.add(id);
                data.add(new GraveData.Grave(id, c.getUUID(), "ux_grave_c", over.dimension(), new BlockPos(i, 70, 0), 2000L + i, 1, new ArrayList<>()));
            }
            check(f, GraveMarkers.marksFor(data, c.getUUID()).size() == GraveMarkers.MAX, "the marker count is not capped");
            for (UUID id : extra) {
                data.remove(id);
            }
        } finally {
            data.remove(idA);
            data.remove(idB);
            GraveMarkers.clearForTest();
        }
        check(f, new Vec3(0, 0, 0).distanceTo(new Vec3(GraveMarkers.NEAR_BLOCKS, 0, 0)) == 8.0, "the near distance is not 8 blocks");
    }
}
