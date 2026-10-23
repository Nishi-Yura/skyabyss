package jp.revati.skyabyss.story.ch1;

import java.util.List;

import org.joml.Vector3f;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 初日のお使い (E3+E8) と失せ物の旅人 (E2) の下働き (17 の 1.5)。1 秒に 1 回、人ごとに。
//   旅の跡の見回り: 旅の跡 (序章 3・一章 2。Sites.trace) の近くに初めて着いたら「見て回った」印を付ける (頼みを受ける前の分も数える。印は Sites が付ける)。
//                  頼みが進んでいる間、まだ送っていない印のぶんだけ合図 ch1_trace_round を送る。頼みが済んだら送った印は忘れる (次の週にまた数える)
//   足跡のくぼみ:  失せ物の旅人の頼みの「たどる」段の間、荷車から日記の場所へ、粒だけの足跡を近くに描く (建築は触らない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Errands {
    // 足跡を描く間隔と、本人からの範囲
    static final double TRAIL_STEP = 3.0;
    static final double TRAIL_RANGE = 30.0;
    private static final DustParticleOptions HOLLOW = new DustParticleOptions(new Vector3f(0.16f, 0.12f, 0.09f), 0.9f);

    private Errands() {
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || player.isSpectator()) {
            return;
        }
        int phase = (player.getUUID().hashCode() & 0x7fffffff) % 20;
        if (player.tickCount % 20 != phase) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        round(player, level);
        trail(player, level);
    }

    // ---------------------------------------------------------------- 旅の跡の見回り
    static void round(ServerPlayer player, ServerLevel level) {
        Ch1Data data = Ch1Data.get(level.getServer().overworld());
        Ch1Data.Player st = data.player(player.getUUID());
        syncSignals(player, st, data);
    }

    // 頼みが進んでいるなら、まだ送っていない見回りの合図を送る。進んでいなければ送った印を忘れる (自己テストからも呼ぶ)
    static int syncSignals(ServerPlayer player, Ch1Data.Player st, Ch1Data data) {
        QuestLog.Entry entry = QuestLog.of(player).entry(Ch1.Q_ROUNDS);
        if (entry == null || entry.done) {
            if (!st.signalled.isEmpty()) {
                st.signalled.clear();
                data.setDirty();
            }
            return 0;
        }
        int sent = 0;
        for (String key : List.copyOf(st.rounds)) {
            if (key.startsWith("s:") && st.signalled.add(key)) {
                data.setDirty();
                Quests.signal(player, Ch1.SIGNAL_ROUND);
                sent++;
            }
        }
        return sent;
    }

    // ---------------------------------------------------------------- 足跡のくぼみ
    static void trail(ServerPlayer player, ServerLevel level) {
        QuestLog.Entry entry = QuestLog.of(player).entry(Ch1.Q_TRAVELLER);
        QuestDef def = QuestManager.quest(Ch1.Q_TRAVELLER);
        if (entry == null || entry.done || def == null || entry.stage != 0) {
            return;
        }
        BlockPos here = player.blockPosition();
        ChapterSites.Site cart = ChapterSites.nearest(level.getServer(), player.getUUID(), Sites.LOST_CART, here);
        ChapterSites.Site diary = ChapterSites.nearest(level.getServer(), player.getUUID(), Sites.DIARY_SPOT, here);
        if (cart == null || diary == null) {
            return;
        }
        draw(player, level, cart.pos(), diary.pos());
    }

    // from → to の線に沿って、本人から範囲内の足跡 (左右交互の 2 つの粒の束) を描く。描いた数を返す
    static int draw(ServerPlayer player, ServerLevel level, BlockPos from, BlockPos to) {
        Vec3 a = Vec3.atCenterOf(from);
        Vec3 b = Vec3.atCenterOf(to);
        double len = Math.hypot(b.x - a.x, b.z - a.z);
        if (len < 4) {
            return 0;
        }
        double dx = (b.x - a.x) / len;
        double dz = (b.z - a.z) / len;
        int drawn = 0;
        int steps = (int) (len / TRAIL_STEP);
        for (int i = 1; i < steps; i++) {
            double x = a.x + dx * i * TRAIL_STEP;
            double z = a.z + dz * i * TRAIL_STEP;
            if (Math.hypot(x - player.getX(), z - player.getZ()) > TRAIL_RANGE) {
                continue;
            }
            BlockPos column = BlockPos.containing(x, player.getY(), z);
            if (!level.hasChunkAt(column)) {
                continue;
            }
            double side = (i % 2 == 0 ? 1 : -1) * 0.28;
            double px = x - dz * side;
            double pz = z + dx * side;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(px), (int) Math.floor(pz));
            level.sendParticles(player, HOLLOW, true, px, y + 0.06, pz, 4, 0.12, 0.0, 0.18, 0.0);
            drawn++;
        }
        return drawn;
    }
}
