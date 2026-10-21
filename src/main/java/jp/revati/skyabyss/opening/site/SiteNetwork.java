package jp.revati.skyabyss.opening.site;

import java.util.LinkedHashMap;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.opening.SiteTargets;
import jp.revati.skyabyss.opening.OpeningSites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 構造物の場所をクライアントへ送る (目的の帯 ObjectiveStrip.targets のため)。入ったとき、と、建て終わるたびに全員へ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SiteNetwork {
    private static final String PROTOCOL_VERSION = "opening-sites-1";
    // 目的の帯の行き先として送る章の要素 (クライアントの SiteTargets.bindChapterTargets が段に結ぶ)
    static final java.util.List<String> CHAPTER_TARGETS = java.util.List.of("ch1_bone_pit", "ch1_cleft_shell_cave", "ch2_salt_vault", "ch2_drift_raft", "storm_ruin", "ch3_scorched_watch");
    // 章の担当が足す行き先 (場所の表にないもの。二章の沖の難破船など)。名前 → 場所 (なければ null)
    public static final java.util.Map<String, java.util.function.Function<MinecraftServer, BlockPos>> EXTRA_TARGETS = new java.util.concurrent.ConcurrentHashMap<>();

    private SiteNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(SiteTargetsPayload.TYPE, SiteTargetsPayload.STREAM_CODEC, (payload, context) -> SiteTargets.receive(payload));
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            PacketDistributor.sendToPlayer(p, payload(p.server));
        }
    }

    public static void broadcast(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(payload(server));
    }

    public static SiteTargetsPayload payload(MinecraftServer server) {
        Map<String, BlockPos> m = new LinkedHashMap<>();
        OpeningSites.camp(server).ifPresent(p -> m.put("camp", p));
        OpeningSites.notebook(server).ifPresent(p -> m.put("notebook", p));
        OpeningSites.shelter(server).ifPresent(p -> m.put("shelter", p));
        OpeningSites.bell(server).ifPresent(p -> m.put("bell", p));
        // 宿りの煙の焚き火 (クライアントが遠くから見える煙の柱を描く。client/opening/SmokeColumnRenderer)
        OpeningSites.smoke(server).or(() -> OpeningSites.shelter(server)).ifPresent(p -> m.put("smoke", p));
        OpeningSites.ribs(server).ifPresent(p -> m.put("ribs", p));
        int i = 0;
        for (BlockPos p : OpeningSites.footprints(server)) {
            m.put(i == 0 ? "footprint" : "footprint" + i, p);
            i++;
        }
        i = 0;
        for (BlockPos p : OpeningSites.cairns(server)) {
            m.put("cairn" + i++, p);
        }
        // 話す相手の居場所 (道しるべの目印): 語り部は宿りの鐘のそば、鯨追いは外れの焚き火
        OpeningSites.bell(server).or(() -> OpeningSites.shelter(server)).ifPresent(bell -> {
            m.put(SiteTargets.NPC_PREFIX + "storyteller", bell);
            if (server.overworld() != null) {
                BlockPos chaser = jp.revati.skyabyss.npc.NpcSpawner.chaserSpot(server.overworld(), bell);
                if (chaser != null) {
                    m.put(SiteTargets.NPC_PREFIX + "whale_chaser", chaser);
                }
            }
        });
        // 旅の職人: 章の立つ所 (ArtisanSpawner.Spot) の印の最初の場所 (拠点が複数あれば先頭。話す目的の道しるべ)
        if (server.overworld() != null) {
            for (var spot : jp.revati.skyabyss.npc.ArtisanSpawner.spots()) {
                var marks = jp.revati.skyabyss.npc.ArtisanSpawner.marks(server.overworld(), spot);
                if (!marks.isEmpty()) {
                    m.put(SiteTargets.NPC_PREFIX + "artisan", marks.get(0));
                    break;
                }
            }
        }
        // 章の小さなダンジョン (一章の支度の段の行き先: 骨の坑・割れ殻の洞)。拠点が複数あれば、建て終わった最初の場所
        for (String id : CHAPTER_TARGETS) {
            for (ChapterSites.Site site : ChapterSites.sites(server, id)) {
                if (site.built()) {
                    m.put(id, site.pos());
                    break;
                }
            }
        }
        EXTRA_TARGETS.forEach((name, f) -> {
            BlockPos p = f.apply(server);
            if (p != null) {
                m.put(name, p);
            }
        });
        // 巨神の最後の位置 (章のクエストの帯の方角と距離。動くので、下の onTick が変わるたびに送り直す)
        net.minecraft.server.level.ServerLevel overworld = server.overworld();
        if (overworld != null) {
            var data = jp.revati.skyabyss.titan.TitanWorldData.get(overworld);
            for (String titan : jp.revati.skyabyss.progress.Titans.all()) {
                var slot = data.slot(titan);
                BlockPos spot = titanSpot(overworld, titan, slot);
                if (spot != null) {
                    m.put(SiteTargets.TITAN_PREFIX + titan, spot);
                }
            }
        }
        return new SiteTargetsPayload(m);
    }

    // 道しるべの巨神の場所。生きている実体があればその「今」の位置 (記録の位置は 1 秒おきで、遅れる)。
    // 余燼は、体の原点 (地の中) ではなく見える目印 (浮かんでいれば背の尾根、泳いでいる間は真上の地表の高さ) を指す。
    // 余燼の目印が体の原点だと、距離も矢印も実際に見える山からずれ、上下の矢印も地の下を指していた
    @org.jetbrains.annotations.Nullable
    static BlockPos titanSpot(net.minecraft.server.level.ServerLevel level, String titan, jp.revati.skyabyss.titan.TitanWorldData.Slot slot) {
        net.minecraft.world.entity.Entity live = switch (titan) {
            case jp.revati.skyabyss.progress.Titans.STONE_WHALE -> jp.revati.skyabyss.titan.WhaleKeeper.current(level);
            case jp.revati.skyabyss.progress.Titans.TIDE_SHELL -> jp.revati.skyabyss.titan.tide.TideKeeper.KEEPER.current(level);
            case jp.revati.skyabyss.progress.Titans.THUNDER_WING -> jp.revati.skyabyss.titan.storm.StormKeeper.KEEPER.current(level);
            case jp.revati.skyabyss.progress.Titans.EMBER -> jp.revati.skyabyss.titan.ember.EmberKeeper.KEEPER.current(level);
            default -> null;
        };
        if (live instanceof jp.revati.skyabyss.titan.ember.EmberTitanEntity ember) {
            net.minecraft.world.phys.Vec3 ridge = ember.ridgePoint();
            int y = (int) ridge.y;
            if (!ember.isSurfaced() && level.hasChunk(BlockPos.containing(ridge).getX() >> 4, BlockPos.containing(ridge).getZ() >> 4)) {
                y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(ridge.x), (int) Math.floor(ridge.z));
            }
            return new BlockPos((int) Math.floor(ridge.x), y, (int) Math.floor(ridge.z));
        }
        if (live != null && live.isAlive()) {
            return live.blockPosition();
        }
        return slot != null && slot.hasLast ? BlockPos.containing(slot.lastX, slot.lastY, slot.lastZ) : null;
    }

    private static Map<String, BlockPos> lastSent = Map.of();

    // 60 tick (3 秒) ごとに、巨神の位置が 8 ブロック以上動いていたら全員へ送り直す
    @SubscribeEvent
    static void onTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 60 != 0 || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        SiteTargetsPayload p = payload(server);
        if (moved(lastSent, p.sites())) {
            lastSent = Map.copyOf(p.sites());
            PacketDistributor.sendToAllPlayers(p);
        }
    }

    public static boolean moved(Map<String, BlockPos> before, Map<String, BlockPos> now) {
        if (before.size() != now.size() || !before.keySet().equals(now.keySet())) {
            return true;
        }
        for (var e : now.entrySet()) {
            if (e.getValue().distSqr(before.get(e.getKey())) >= 64) {
                return true;
            }
        }
        return false;
    }
}
