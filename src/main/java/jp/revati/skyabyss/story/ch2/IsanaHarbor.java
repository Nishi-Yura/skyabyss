package jp.revati.skyabyss.story.ch2;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.npc.NpcEntity;
import jp.revati.skyabyss.npc.NpcSetup;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.quest.QuestLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 二章のあいだ、イサナ (鯨追い) は汐守の港に立つ (2026-11-04 の決めごと c2_isana = harbor。docs/design/07 の 3.2)。
// 居場所は人ごと: 港のイサナと鯨見の町のイサナを二人立て、その人の進みで見える方を一人に決める (NpcEntity.broadcastToPlayer)。
//   港にいる   段階 1 (岩鯨を止めた) で、噂の話を聞いた (印 c2_harbor) か、頼み main_tide が噂の段を越えた・終えた人。
//              段階 2 (潮殻を止めた) でも、港で心臓のあとの話 (印 c2_harbor_done) をするまで。ただし三章の頼み main_thunder が支度の段を越えたら町へ戻る
//   立つ所     汐守の町の中心から海へいちばん近い岸の、水際から 2〜8 手前の立てる所 (町の記録 SiteData に印 isana_harbor として一度だけ書く)。顔は町の方へ (海に背を向ける)
//   道しるべ   港にいる人には、話す相手の目印 (SiteTargets の npc:whale_chaser) も港を指す (SiteNetwork.payload)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class IsanaHarbor {
    public static final String TAG = "skyabyss_harbor_isana";
    public static final String MARK = "isana_harbor";
    public static final String ROLE = "skyabyss:whale_chaser";
    // 噂の話で「先に港へ行く」と言った
    public static final String FLAG_TOLD = "c2_harbor";
    // 港で心臓のあとの話をした (町へ戻る)
    public static final String FLAG_DONE = "c2_harbor_done";
    static final String TIDE_TOWN = "tide";
    static final int SEARCH_FROM = 16;
    static final int SEARCH_TO = 160;

    private IsanaHarbor() {
    }

    // この人にとって、イサナが汐守の港にいるか
    public static boolean atHarbor(ServerPlayer p) {
        int stage = Progression.stage(p);
        if (stage != 1 && stage != 2) {
            return false;
        }
        java.util.Set<String> flags = NpcBonds.of(p).bond(ROLE).flags();
        QuestLog log = QuestLog.of(p);
        if (stage == 1) {
            QuestLog.Entry tide = log.entry("main_tide");
            return flags.contains(FLAG_TOLD) || tide != null && (tide.done || tide.stage >= 1);
        }
        if (flags.contains(FLAG_DONE)) {
            return false;
        }
        QuestLog.Entry thunder = log.entry("main_thunder");
        return thunder == null || !thunder.done && thunder.stage <= 1;
    }

    // この人に、この NPC が見えるか (鯨追いだけを分ける。ほかの NPC はいつも見える)
    public static boolean visibleTo(NpcEntity npc, ServerPlayer p) {
        if (npc.getType() != NpcSetup.WHALE_CHASER.get()) {
            return true;
        }
        return npc.getTags().contains(TAG) == atHarbor(p);
    }

    // ---------------------------------------------------------------- 立つ所
    // 汐守の町の記録 (港の立つ所の印があれば、それ)
    @Nullable
    static SiteData.Entry tideTown(ServerLevel level) {
        for (SiteData.Entry e : SiteData.get(level).entries(jp.revati.skyabyss.world.continent.ContinentTowns.KIND)) {
            if (e.built && TIDE_TOWN.equals(jp.revati.skyabyss.world.continent.ContinentTowns.region(e.index))) {
                return e;
            }
        }
        return null;
    }

    // 港の立つ所 (分かっていなければ null。道しるべと見た目の確かめ用)
    @Nullable
    public static BlockPos spot(net.minecraft.server.MinecraftServer server) {
        ServerLevel level = server.overworld();
        SiteData.Entry e = level == null ? null : tideTown(level);
        return e == null ? null : e.extra(MARK);
    }

    // 町の中心から 16 方向に海を探し、いちばん近い水際の手前の立てる所。周りが読み込まれていなければ null (あとでもう一度)
    @Nullable
    static BlockPos findSpot(ServerLevel level, BlockPos center) {
        int sea = level.getSeaLevel();
        BlockPos best = null;
        int bestR = Integer.MAX_VALUE;
        for (int k = 0; k < 16; k++) {
            double a = Math.PI * 2 * k / 16;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            for (int r = SEARCH_FROM; r <= SEARCH_TO && r < bestR; r += 2) {
                int x = center.getX() + (int) Math.round(dx * r);
                int z = center.getZ() + (int) Math.round(dz * r);
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    break;
                }
                BlockPos w = new BlockPos(x, sea - 1, z);
                if (!level.getFluidState(w).is(FluidTags.WATER) || level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) >= sea - 1) {
                    continue;
                }
                // 水際から町の方へ 2〜8 戻った、立てる所 (足もとが固く、水でなく、頭の上が空いている)
                for (int back = 2; back <= 8; back++) {
                    int bx = center.getX() + (int) Math.round(dx * (r - back));
                    int bz = center.getZ() + (int) Math.round(dz * (r - back));
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                    BlockPos feet = new BlockPos(bx, y, bz);
                    BlockPos below = feet.below();
                    if (y >= sea && y <= sea + 6 && level.getFluidState(below).isEmpty() && level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)
                            && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()) {
                        best = feet;
                        bestR = r;
                        break;
                    }
                }
                break;
            }
        }
        return best;
    }

    // 港の立つ所 (印が無ければ探して書く)
    @Nullable
    static BlockPos ensureSpot(ServerLevel level, SiteData.Entry town) {
        BlockPos at = town.extra(MARK);
        if (at != null) {
            return at;
        }
        at = findSpot(level, town.pos);
        if (at != null) {
            town.extra(MARK, at);
            SiteData.get(level).setDirty();
            SkyAbyss.LOGGER.info("[isana] harbor spot of the tide town: {}", at.toShortString());
        }
        return at;
    }

    // 港のイサナ: 港にいるはずの人が町の近くにいて、立っていなければ立たせる。離れていたら戻す
    public static void keep(ServerLevel level, SiteData.Entry town, ServerPlayer player) {
        if (!atHarbor(player)) {
            return;
        }
        BlockPos spot = ensureSpot(level, town);
        if (spot == null || !level.hasChunk(spot.getX() >> 4, spot.getZ() >> 4) || !level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(spot))) {
            return;
        }
        java.util.List<NpcEntity> found = level.getEntities(NpcSetup.WHALE_CHASER.get(), new AABB(spot).inflate(32), n -> n.isAlive() && n.getTags().contains(TAG));
        for (int i = 1; i < found.size(); i++) {
            found.get(i).discard();
        }
        float yaw = (float) (Mth.atan2(town.pos.getZ() - spot.getZ(), town.pos.getX() - spot.getX()) * (180 / Math.PI)) - 90f;
        if (!found.isEmpty()) {
            NpcEntity live = found.get(0);
            if (live.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) > 64) {
                live.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                live.setYRot(yaw);
                live.setYHeadRot(yaw);
            }
            return;
        }
        NpcEntity npc = NpcSetup.WHALE_CHASER.get().create(level);
        if (npc == null) {
            return;
        }
        npc.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0f);
        npc.setYHeadRot(yaw);
        npc.setHome(spot);
        npc.nameByRole();
        npc.addTag(TAG);
        npc.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), net.minecraft.world.entity.MobSpawnType.STRUCTURE, null);
        level.addFreshEntity(npc);
        SkyAbyss.LOGGER.info("[isana] stands at the harbor {}", spot.toShortString());
    }

    // ---------------------------------------------------------------- 道しるべ
    // 港にいるかどうかが変わった人に、話す相手の目印を送り直す (2 秒ごと)
    private static final Map<UUID, Boolean> LAST = new ConcurrentHashMap<>();

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 40 != 17 || p.level().dimension() != Level.OVERWORLD) {
            return;
        }
        boolean now = atHarbor(p);
        Boolean before = LAST.put(p.getUUID(), now);
        if (before != null && before != now) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, jp.revati.skyabyss.opening.site.SiteNetwork.payload(p.server, p));
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }
}
