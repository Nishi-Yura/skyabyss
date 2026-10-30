package jp.revati.skyabyss.story.finale;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.finale.mob.CloudMobsSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 巡礼者の座 (docs/design/50 の 4.3)。地形は data/skyabyss/finale/seat.json (tools/gen/world/build_cloud_seat.py。FinaleWorld の版 6)。
//   光の橋  約束の島の主の間の北の縁から上る橋 (印 bridge_gate)。約束の島の頼みを終えていない人は月見の岩の上の入口へ戻す
//   座の床  内の固い床と鐘雲の輪 (印 ring のまわり)。輪の鐘雲は島 1 と同じ決まり (BellIsle に足す)。内の床の四隅の鐘 (印 seat_bell_<k>) を右クリックで鳴らす
//   座      北の台の巡礼者の座 (印 throne) に触れると戦いが始まる (SeatArena)。台の帰りの台で月見の岩の上へ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PilgrimSeat {
    public static final String NAME = "seat";
    public static final String QUEST = "main_finale_seat";
    static final String[] BELLS = {"ne", "se", "sw", "nw"};
    static final double ARRIVE_NEAR = 8;
    private static List<BlockPos> clouds;

    private PilgrimSeat() {
    }

    public static FinaleTerrain terrain() {
        return FinaleTerrain.of(NAME);
    }

    public static BlockPos mark(String key) {
        return terrain().mark(key);
    }

    static synchronized List<BlockPos> clouds() {
        if (clouds == null) {
            clouds = List.copyOf(terrain().positionsOf("skyabyss:bell_cloud"));
        }
        return clouds;
    }

    public static List<BlockPos> bells() {
        List<BlockPos> out = new ArrayList<>();
        for (String k : BELLS) {
            out.add(mark("seat_bell_" + k));
        }
        return out;
    }

    // 輪の鐘をみな鳴らす (目と月の形態の間は輪を固めておく)
    public static void ringAll(ServerLevel level) {
        for (BlockPos b : bells()) {
            BellIsle.ring(level, b);
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != FinaleWorld.LEVEL
                || !FinaleWorld.built(level.getServer()) || FinaleWorld.version(level.getServer()) < 6) {
            return;
        }
        long now = level.getGameTime();
        if (now % 10 == 0) {
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator()) {
                    gate(p);
                }
            }
        }
        if (now % 20 == 0) {
            // 輪の鐘雲は鐘の島と同じ見張りで固める (島 1 の主の間の片付けで消されても、ここで足し直す)
            BellIsle.addExtra(clouds());
            for (ServerPlayer p : level.players()) {
                BlockPos a = mark("arrive");
                if (!p.isSpectator() && p.distanceToSqr(a.getX() + 0.5, a.getY(), a.getZ() + 0.5) < ARRIVE_NEAR * ARRIVE_NEAR) {
                    Quests.signal(p, "finale_seat_arrive");
                }
            }
            SeatArena.tick(level);
        }
        SeatArena.everyTick(level);
    }

    // ---------------------------------------------------------------- 光の橋の関所 (月見の岩の上 → 座)
    static boolean inGate(Vec3 p) {
        BlockPos g = mark("bridge_gate");
        return Math.abs(p.x - (g.getX() + 0.5)) <= 2.5 && p.z <= g.getZ() + 2 && p.z >= g.getZ() - 12 && Math.abs(p.y - g.getY()) <= 4;
    }

    public static boolean bridgeOpen(ServerPlayer p) {
        QuestLog.Entry e = QuestLog.of(p).entry(PromiseIsle.QUEST);
        return e != null && e.done;
    }

    static void gate(ServerPlayer p) {
        if (!inGate(p.position()) || bridgeOpen(p)) {
            return;
        }
        BlockPos to = PromiseIsle.mark("arena_entry");
        p.teleportTo(p.serverLevel(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 180f, 0f);
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            p.moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 180f, 0f);
        }
        p.resetFallDistance();
        Notice.narrate(p, "message.skyabyss.cloudsea.bridge_shut");
    }

    // 座に触れてよいか: 頼みの段 throne 以降・頼みを終えた人 (挑み直し)
    public static boolean mayChallenge(ServerPlayer p) {
        QuestLog.Entry e = QuestLog.of(p).entry(QUEST);
        if (e == null) {
            return false;
        }
        return e.done || e.stage >= PromiseIsle.stageIndex(QuestManager.quest(QUEST), "throne");
    }

    // ---------------------------------------------------------------- 右クリック (鐘・座・帰りの台)
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand() != InteractionHand.MAIN_HAND || !FinaleWorld.in(p)
                || FinaleWorld.version(p.server) < 6) {
            return;
        }
        BlockPos pos = event.getPos();
        ServerLevel level = p.serverLevel();
        if (bells().contains(pos)) {
            BellIsle.ring(level, pos);
            return;
        }
        if (pos.equals(mark("throne")) || pos.equals(mark("throne").below())) {
            event.setCanceled(true);
            if (!mayChallenge(p)) {
                Notice.narrate(p, "message.skyabyss.seat.throne_shut");
                return;
            }
            if (SeatArena.form() != 0) {
                Notice.bar(p, net.minecraft.network.chat.Component.translatable("message.skyabyss.seat.throne_busy"));
                return;
            }
            level.playSound(null, pos, CloudMobsSetup.SEAT_TOUCH.get(), SoundSource.BLOCKS, 2.0f, 1.0f);
            List<ServerPlayer> near = new ArrayList<>();
            for (ServerPlayer o : level.players()) {
                if (o.distanceToSqr(p) < 40 * 40) {
                    near.add(o);
                }
            }
            Quests.signalWithParty(level, near, "finale_seat_throne");
            SeatArena.start(level, 1);
        } else if (pos.equals(mark("arena_back"))) {
            event.setCanceled(true);
            BlockPos to = PromiseIsle.mark("arena_entry");
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0f, 0f);
        }
    }
}
