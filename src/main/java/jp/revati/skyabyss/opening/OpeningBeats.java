package jp.revati.skyabyss.opening;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.npc.NpcSetup;
import jp.revati.skyabyss.npc.NpcSpawner;
import jp.revati.skyabyss.npc.NpcWorldData;
import jp.revati.skyabyss.sky.SkyEventData;
import jp.revati.skyabyss.sky.SkyEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 序盤の拍子の保証 (17 の担当 3 が OpeningState へ移す前の、最小の形)。語り部に必ず会える (07 の 3.1節、06 の 2.7節)。最初の空の影のあと、まだ語り部と話していない人を、次の順で導く。
//  1. 影が去って 1 分たったら、いちばん近い村 (村の構造。まだ見ていない村も) の方角と距離を知らせる。近くに村が無ければ、すぐ 3 へ。
//  2. 村へ向かわないまま半日 (村の知らせから 1 日) たったら、知らせ直す (最大 3 回)。それでも会えなければ 3 へ
//  3. 本人の近く (18〜34 ブロック) に、旅の語り部の野営 (焚き火と語り部) を作る。会うまで、1 日ごとに作り直す
// 「鐘のない村には出ない」(07 の判断 15) は、村の NPC を鐘にだけ立たせるという意味で、今も守っている。野営は村ではなく、
// 「村へ行けない人を詰まらせないための旅の語り部」なので、判断 15 とは別に立てる。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class OpeningBeats {
    // 影が去ってから、村の方角を知らせるまで
    static final long HINT_AFTER = 1200;
    // 知らせ直す間隔と、最大の回数
    static final long REHINT = 12000;
    static final int MAX_HINTS = 3;
    // 最初に村を知らせてから、それでも会えなければ野営を作るまで。野営を作ったあと、作り直すまでも同じ
    static final long CAMP_AFTER = 24000;
    // この距離 (水平) より遠い村は、「近くに村が無い」とみなす。探す範囲 (チャンク数) もこれに合わせる
    static final int NEAR_VILLAGE = 640;
    static final int SEARCH_CHUNKS = 40;
    // 村の鐘か野営がこの近さにあれば、そちらに任せる (NpcSpawner が語り部を立たせる)
    private static final int BASE_NEAR = 64;
    private static final int CHECK_INTERVAL = 100;

    public enum Move { DONE, WAIT, SEARCH, HINT, CAMP }

    private OpeningBeats() {
    }

    // 次にすること (副作用なし)。テストできるよう、状態の値だけで決める
    public static Move decide(boolean met, boolean baseNear, int villageState, long now, long since, long hintAt, int hints, long campAt) {
        if (met) {
            return Move.DONE;
        }
        if (baseNear) {
            return Move.WAIT;
        }
        if (villageState == 0) {
            return now - since >= HINT_AFTER ? Move.SEARCH : Move.WAIT;
        }
        if (campAt >= 0) {
            return now - campAt >= CAMP_AFTER ? Move.CAMP : Move.WAIT;
        }
        if (villageState == 2 || now - since >= HINT_AFTER + CAMP_AFTER) {
            return Move.CAMP;
        }
        if (hints < MAX_HINTS && now - hintAt >= REHINT) {
            return Move.HINT;
        }
        return Move.WAIT;
    }

    // 語り部と話したことがあるか (記録は人ごと。月のループで印が「覚え」に移っても、会った人は会った人)
    static boolean metStoryteller(ServerPlayer player) {
        NpcBonds.Bond bond = NpcBonds.of(player).bonds().get(NpcSetup.STORYTELLER_ID);
        return bond != null && (bond.talks() > 0 || bond.flags().contains("met") || bond.echoes().contains("met"));
    }

    // オーバーワールドにいる誰かが語り部と話したことがあるか (岩鯨の出現の条件)
    public static boolean anyoneMetStoryteller(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && metStoryteller(p)) {
                return true;
            }
        }
        return false;
    }

    // 案内が始まっていてよいか: 影を起こす設定なら最初の影が済んで (進行中でなく)、影を起こさない設定なら最初から
    public static boolean started(SkyEventData sky, boolean passActive, double frequency) {
        return frequency <= 0 || (sky.passCount() > 0 && !passActive);
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD || (level.getGameTime() + (player.getId() & 0xFF)) % CHECK_INTERVAL != 0) {
            return;
        }
        run(level, player);
    }

    static void run(ServerLevel level, ServerPlayer player) {
        if (!started(SkyEventData.get(level), SkyEvents.activePass() != null, Config.HORROR_EVENT_FREQUENCY.get())) {
            return;
        }
        NpcWorldData data = NpcWorldData.get(level.getServer().overworld());
        long now = level.getGameTime();
        NpcWorldData.Guide g = data.guide(player.getUUID(), now);
        if (g.done) {
            return;
        }
        boolean met = metStoryteller(player);
        boolean baseNear = baseNear(level, data, player);
        switch (decide(met, baseNear, g.villageState, now, g.since, g.hintAt, g.hints, g.campAt)) {
            case DONE -> {
                g.done = true;
                data.setDirty();
            }
            case SEARCH -> search(level, data, g, player, now);
            case HINT -> hint(data, g, player, now);
            case CAMP -> camp(level, data, g, player, now);
            default -> {
            }
        }
    }

    private static boolean baseNear(ServerLevel level, NpcWorldData data, ServerPlayer player) {
        return level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), player.blockPosition(), BASE_NEAR, PoiManager.Occupancy.ANY).isPresent()
                || NpcSpawner.nearestCamp(data, player.blockPosition(), BASE_NEAR) != null;
    }

    // いちばん近い村を探す (まだ歩いていない村も、地図屋の地図と同じ方法で分かる)
    private static void search(ServerLevel level, NpcWorldData data, NpcWorldData.Guide g, ServerPlayer player, long now) {
        BlockPos village = level.findNearestMapStructure(StructureTags.VILLAGE, player.blockPosition(), SEARCH_CHUNKS, false);
        data.setDirty();
        if (village == null || horizontal(village, player.blockPosition()) > NEAR_VILLAGE) {
            g.villageState = 2;
            SkyAbyss.LOGGER.info("[npc] no village within {} blocks of {}; a storyteller's camp comes next", NEAR_VILLAGE,
                    player.getGameProfile().getName());
            return;
        }
        g.villageState = 1;
        g.villageX = village.getX();
        g.villageZ = village.getZ();
        g.hintAt = now;
        g.hints = 1;
        tellVillage(g, player);
    }

    private static void hint(NpcWorldData data, NpcWorldData.Guide g, ServerPlayer player, long now) {
        g.hintAt = now;
        g.hints++;
        data.setDirty();
        tellVillage(g, player);
    }

    private static void tellVillage(NpcWorldData.Guide g, ServerPlayer player) {
        BlockPos at = new BlockPos(g.villageX, player.getBlockY(), g.villageZ);
        int distance = (int) Math.round(horizontal(at, player.blockPosition()) / 10.0) * 10;
        Notice.narrate(player, "message.skyabyss.guide.village", directionName(player.blockPosition(), at), Math.max(distance, 10));
    }

    private static void camp(ServerLevel level, NpcWorldData data, NpcWorldData.Guide g, ServerPlayer player, long now) {
        BlockPos fire = NpcSpawner.buildCamp(level, player.blockPosition(), level.random);
        if (fire == null) {
            // 置ける地面が近くに無い (海の上・洞窟の中など)。次の見回りでやり直す
            return;
        }
        g.campAt = now;
        data.setDirty();
        NpcSpawner.visitVillage(level, fire, player);
        Notice.narrate(player, "message.skyabyss.guide.camp");
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // 北を 0 として 8 方位 (0 北 1 北東 2 東 …)
    public static int compass(BlockPos from, BlockPos to) {
        double degrees = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
        return Math.floorMod(Math.round((float) (degrees / 45.0)), 8);
    }

    private static Component directionName(BlockPos from, BlockPos to) {
        return Component.translatable("message.skyabyss.guide.dir." + compass(from, to));
    }
}
