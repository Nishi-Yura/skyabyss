package jp.revati.skyabyss.titan.shrine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.relic.RelicStyle;
import jp.revati.skyabyss.titan.end.ReturnBeacons;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.TrialRoute;
import jp.revati.skyabyss.util.Players;

// 巨神の祠 (docs/design/32 の 3.1)。周回の順路の入口。巨神ごとに世界に一つ、地上に建つ。壊せない (台座 9×9 を守る)。
//   置き場 (仮): はじまりの都 (鐘) から、その巨神の跡地の方位 岩鯨 北・潮殻 東・雷翼 西・余燼 南) へ、距離の帯の近い端。
//     本当の置き場は、地図と地形の生成 が決まったら差し替える。心臓を初めて止めたとき (誰かが) に建てる (ensure)
//   形: 巨神ごとに別の建物 (ShrineBuild。部品 shrine_<巨神>。岩鯨 = 鯨骨の聖堂・潮殻 = 潮の神殿・雷翼 = 雷の歌の環・余燼 = 炉の門)。
//     記録する位置 (Data.pos) は碑。広場の真ん中の集合台 (縁が光る円い壇)・共鳴の炉。まわりに順位の石と飾り板 (RebirthBoards。集合台のまわり)
//   使い方 (Wynncraft のレイドの入口と同じ。2026-11-03 の声「始まるときに集まる場所」): 集合台に
//     パーティのオンラインの全員 (一人なら一人) が立つと、GATHER_TICKS の数え下ろしのあと、台の上の誰かの歌片が一つ減り (旅嚢か持ち物。代表が払う)、
//     雲の帳のあいだに台の上の人をその巨神の背へ運び、順路の段 1 へ (TrialRoute.start)。数えるあいだに誰かが台を降りると止まる
//   断るとき: 心臓を止めていない人が台にいる / 台の誰も歌片を持っていない / そのグループの順路がもう動いている (台の上の人の帯に理由)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanShrine {
    private TitanShrine() {
    }

    // 全員がそろってから始まるまで (5 秒)
    public static final int GATHER_TICKS = 100;
    // 集まる所: 集合台の真ん中から横にこの内 (上下 8)。パーティのオンラインの全員がここにいないと始まらない (2026-11-07 の声「Wynncraft のように」)
    public static final double GATHER_RANGE = 14.0;
    // 祭司に話しかけられる祠の近さ (集合台の真ん中から)
    public static final double PRIEST_REACH = 28.0;
    // 祭司に「仲間と渡る」と頼んだグループ → 巨神 (数え下ろしの間だけ)
    private static final Map<UUID, String> ASKED = new java.util.HashMap<>();
    // 雲の帳を上げるまで
    private static final int VEIL_TICKS = 30;
    // 誓いを選べる距離
    public static final double VOW_REACH = 8.0;

    // 数え下ろし中のグループ → {始めた tick, 最後に鳴らした秒}
    private static final Map<UUID, long[]> GATHER = new HashMap<>();

    // 心臓を止めたか (自己テストは差し替える)
    public interface Stopped {
        boolean test(MinecraftServer server, UUID player, String titan);
    }

    public static Stopped STOPPED = Progression::stopped;
    private static final Map<UUID, long[]> VEILS = new HashMap<>();

    // ---------------------------------------------------------------- 置き場
    // 跡地の方位 (x, z) と距離 (帯の近い端)
    static int[] bearing(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> new int[] {1, 0, 800};
            case Titans.THUNDER_WING -> new int[] {-1, 0, 1000};
            case Titans.EMBER -> new int[] {0, 1, 1200};
            default -> new int[] {0, -1, 280};
        };
    }

    // はじまりの都の鐘 (なければ宿り、それもなければ世界の出発点)
    static BlockPos town(MinecraftServer server) {
        return jp.revati.skyabyss.opening.OpeningSites.bell(server)
                .or(() -> jp.revati.skyabyss.opening.OpeningSites.shelter(server))
                .orElse(server.overworld().getSharedSpawnPos());
    }

    // 祠の中央の碑の位置 (まだ建っていなければ null)
    @Nullable
    public static BlockPos posOf(MinecraftServer server, String titan) {
        return Data.get(server).pos.get(titan);
    }

    // 建てた記録を消す (地上を作り直したとき。upgrade/ContinentRegen が、建っていた巨神の分を ensure で今の場所に建て直す)。記録があったか
    public static boolean forget(MinecraftServer server, String titan) {
        Data data = Data.get(server);
        if (data.pos.remove(titan) == null) {
            return false;
        }
        data.ver.remove(titan);
        data.setDirty();
        return true;
    }

    // 祠を建てる (もう建っていれば、その位置)。チャンクを読み込む (心臓を止めたときに一度)。前の版 (拝殿の祠) なら、同じ場所に建て直す
    public static BlockPos ensure(MinecraftServer server, String titan) {
        Data data = Data.get(server);
        BlockPos have = data.pos.get(titan);
        ServerLevel level = server.overworld();
        if (have != null) {
            if (data.ver.getOrDefault(titan, 1) < ShrineBuild.VERSION) {
                return rebuildOld(server, titan, have, data.ver.getOrDefault(titan, 1));
            }
            return have;
        }
        // 跡地 (ChapterGrounds) が取っておいた台座の場所。跡地がまだ決まっていない世界だけ、方位と距離の帯の近い端
        // 大陸: 決まった道ばたの広場 (world.continent.ContinentLairs.shrine。2026-10-28 の試遊「祠が地形になじまない、どうやって行けばいい」)
        int[] fixed = jp.revati.skyabyss.world.continent.ContinentLairs.shrine(server.overworld(), titan);
        BlockPos spot = fixed != null ? new BlockPos(fixed[0], 0, fixed[1])
                : jp.revati.skyabyss.opening.site.ChapterGrounds.shrineSpot(server, titan).orElse(null);
        int x;
        int z;
        if (spot != null) {
            x = spot.getX();
            z = spot.getZ();
        } else {
            int[] b = bearing(titan);
            BlockPos t = town(server);
            x = t.getX() + b[0] * b[2];
            z = t.getZ() + b[1] * b[2];
        }
        level.getChunk(x >> 4, z >> 4);
        int y = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), level.getSeaLevel() + 1);
        return place(server, titan, new BlockPos(x, y, z));
    }

    // 広場の真ん中 (地面の一つ上) に建てて記録する。碑の位置を返す
    static BlockPos place(MinecraftServer server, String titan, BlockPos origin) {
        ShrineBuild.build(server.overworld(), origin, titan);
        BlockPos tablet = ShrineBuild.tabletOf(origin, titan);
        Data data = Data.get(server);
        data.pos.put(titan, tablet);
        data.ver.put(titan, ShrineBuild.VERSION);
        data.setDirty();
        jp.revati.skyabyss.rebirth.RebirthBoards.clearCache();
        SkyAbyss.LOGGER.info("Built the shrine of the {} (tablet {})", titan, tablet);
        return tablet;
    }

    // 前の版の祠を、今の作りで建て直す。版 1 (拝殿の祠) は碑 = 広場の真ん中の地面の一つ上。版 2 から碑は部品の印 tablet の所
    static BlockPos rebuildOld(MinecraftServer server, String titan, BlockPos oldTablet, int oldVer) {
        SkyAbyss.LOGGER.info("Rebuilding the old shrine of the {} at {} (version {} -> {})", titan, oldTablet, oldVer, ShrineBuild.VERSION);
        return place(server, titan, oldVer >= 2 ? ShrineBuild.originOf(oldTablet, titan) : oldTablet);
    }

    // 地上の建て物 (町・見どころなど。SiteBuilds) が建ち終わった。そばの祠は建て直す (建て物が空けた所が祠を削っていることがある。
    // 2026-11-07 の声「鯨の祠の建築、変」: 大陸の作り直しで祠が先に建ち、あとから建った鯨見の町が祠の西を削って土を残した)。
    // 祠の建て直しは何度しても同じ形
    public static void afterSite(MinecraftServer server, BlockPos site, int reach) {
        Data data = Data.get(server);
        for (Map.Entry<String, BlockPos> e : new java.util.ArrayList<>(data.pos.entrySet())) {
            BlockPos t = e.getValue();
            double dx = t.getX() - site.getX();
            double dz = t.getZ() - site.getZ();
            if (dx * dx + dz * dz <= (double) reach * reach) {
                SkyAbyss.LOGGER.info("A site was built near the shrine of the {}; building the shrine again", e.getKey());
                place(server, e.getKey(), ShrineBuild.originOf(t, e.getKey()));
            }
        }
    }

    // 集合台の真ん中の立つ所 (順位の石と飾り板の真ん中・あきらめた順路の出口)
    @Nullable
    public static BlockPos hubOf(MinecraftServer server, String titan) {
        BlockPos c = posOf(server, titan);
        return c == null ? null : ShrineBuild.padOf(c, titan);
    }

    // 守る箱 (広場の円を囲む箱)
    static AABB guard(BlockPos tablet, String titan) {
        BlockPos o = ShrineBuild.originOf(tablet, titan);
        int r = ShrineBuild.guardRadius(titan);
        return new AABB(o.getX() - r, o.getY() - 3, o.getZ() - r, o.getX() + r + 1, o.getY() + 34, o.getZ() + r + 1);
    }

    // 集合台の上か
    public static boolean onPad(ServerPlayer p, BlockPos tablet, String titan) {
        return p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD && !p.isSpectator() && p.isAlive()
                && ShrineBuild.onPad(p.getX(), p.getY(), p.getZ(), tablet, titan);
    }

    // 集合台のまわり (横に GATHER_RANGE、上下 8) にいるか
    public static boolean nearPad(ServerPlayer p, BlockPos tablet, String titan) {
        if (p.level().dimension() != net.minecraft.world.level.Level.OVERWORLD || p.isSpectator() || !p.isAlive()) {
            return false;
        }
        BlockPos pad = ShrineBuild.padOf(tablet, titan);
        double dx = p.getX() - (pad.getX() + 0.5);
        double dz = p.getZ() - (pad.getZ() + 0.5);
        return dx * dx + dz * dz <= GATHER_RANGE * GATHER_RANGE && Math.abs(p.getY() - pad.getY()) <= 8;
    }

    // 集まっていない仲間 (オンラインで、見物でない人) の名前
    public static List<String> missing(ServerPlayer by, BlockPos tablet, String titan) {
        List<String> out = new ArrayList<>();
        UUID group = Groups.keyOf(by);
        for (ServerPlayer p : Players.snapshot(by.server.getPlayerList().getPlayers())) {
            if (!p.isSpectator() && Groups.keyOf(p).equals(group) && !nearPad(p, tablet, titan)) {
                out.add(p.getGameProfile().getName());
            }
        }
        return out;
    }

    // その人のそばの祠 (集合台の真ん中から reach の内)。なければ null
    @Nullable
    public static String shrineNear(ServerPlayer p, double reach) {
        if (p.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return null;
        }
        for (Map.Entry<String, BlockPos> e : Data.get(p.server).pos.entrySet()) {
            BlockPos pad = ShrineBuild.padOf(e.getValue(), e.getKey());
            if (p.distanceToSqr(pad.getX() + 0.5, pad.getY(), pad.getZ() + 0.5) <= reach * reach) {
                return e.getKey();
            }
        }
        return null;
    }

    // 祭司に「仲間と渡る」と頼む (FacilityActions "shrine_go")。全員がそろっていれば数え下ろしを始める。始めたか
    public static boolean ask(ServerPlayer by) {
        String titan = shrineNear(by, PRIEST_REACH);
        if (titan == null) {
            Notice.bar(by, ModText.deny("message.skyabyss.shrine.not_here"));
            return false;
        }
        BlockPos c = posOf(by.server, titan);
        UUID group = Groups.keyOf(by);
        if (ASKED.containsKey(group)) {
            return true;
        }
        List<String> away = missing(by, c, titan);
        if (!away.isEmpty()) {
            Notice.bar(by, ModText.deny("message.skyabyss.shrine.missing", String.join("・", away)));
            return false;
        }
        ServerLevel back = Titans.hasBack(titan) ? by.server.getLevel(Titans.backOf(titan)) : null;
        List<ServerPlayer> ring = ring(by, c, titan);
        ServerPlayer payer = by;
        for (ServerPlayer p : ring) {
            if (hasShard(p, titan)) {
                payer = p;
                break;
            }
        }
        Component why = back == null ? ModText.deny("message.skyabyss.shrine.no_back")
                : refusal(by.server, payer, titan, ring, hasShard(payer, titan), TrialRoute.active(back, group));
        if (why != null) {
            Notice.bar(by, why);
            return false;
        }
        ASKED.put(group, titan);
        GATHER.put(group, new long[] {by.server.getTickCount(), -1});
        for (ServerPlayer p : ring) {
            Notice.hint(p, "message.skyabyss.shrine.asked", by.getGameProfile().getName(), GATHER_TICKS / 20);
        }
        return true;
    }

    // 祠の祭司 (社の祭司と同じ役。話しかけて「仲間と渡る」)。集合台の縁の外の、立てる床に一人
    static void ensurePriest(ServerLevel level, String titan, BlockPos tablet) {
        BlockPos spot = priestSpot(level, titan, tablet);
        if (spot != null) {
            jp.revati.skyabyss.facility.Attendants.ensure(level, jp.revati.skyabyss.facility.AttendantRole.SHRINE_PRIEST, spot);
        }
    }

    // 祭司の立つ所: 集合台の真ん中から padR + 2〜6 の輪 (碑の側を先に)。足もとが空き・頭が空き・下が固い所
    @Nullable
    public static BlockPos priestSpot(ServerLevel level, String titan, BlockPos tablet) {
        BlockPos pad = ShrineBuild.padOf(tablet, titan);
        int padR = ShrineBuild.padRadius(titan);
        double toward = Math.atan2(tablet.getZ() - pad.getZ(), tablet.getX() - pad.getX());
        for (int r = padR + 2; r <= padR + 6; r++) {
            for (int k = 0; k < 16; k++) {
                double a = toward + Math.PI / 2 + (k % 2 == 0 ? 1 : -1) * (k / 2) * (Math.PI / 8);
                for (int dy = 1; dy >= -2; dy--) {
                    BlockPos q = new BlockPos(pad.getX() + (int) Math.round(Math.cos(a) * r), pad.getY() + dy, pad.getZ() + (int) Math.round(Math.sin(a) * r));
                    if (!level.hasChunkAt(q)) {
                        return null;
                    }
                    if (level.getBlockState(q).isAir() && level.getBlockState(q.above()).isAir()
                            && level.getBlockState(q.below()).isFaceSturdy(level, q.below(), net.minecraft.core.Direction.UP)) {
                        return q;
                    }
                }
            }
        }
        return null;
    }

    // その祠に来ているか (祭司のまわり = 集合台から PRIEST_REACH、または碑のそば)。祠の画面の「仲間と渡る」・誓い・歌片はこれで見る
    // (2026-11-08 の声「話しかけて、始めるボタンない」。前は碑のそばだけを見ていて、台の縁に立つ祭司のそばでは出なかった)
    public static boolean atShrine(ServerPlayer p, String titan) {
        return titan.equals(shrineNear(p, PRIEST_REACH)) || near(p, titan);
    }

    // 碑のそばか
    public static boolean near(ServerPlayer p, String titan) {
        BlockPos c = posOf(p.server, titan);
        return c != null && p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD
                && p.distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5) <= VOW_REACH * VOW_REACH;
    }

    // 祠の集合台へ送る (あきらめた周回の順路の出口)。送れたか
    public static boolean sendTo(ServerPlayer p, String titan) {
        BlockPos c = hubOf(p.server, titan);
        if (c == null) {
            return false;
        }
        p.stopRiding();
        p.teleportTo(p.server.overworld(), c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 180f, 0f);
        p.fallDistance = 0;
        return true;
    }

    // ---------------------------------------------------------------- 使う
    // 碑の右クリック: 祭司へ案内するだけ (2026-11-08 の声「祠の祭祀に要素を集めて。話しかけると ui 出るようにして」)。
    // 試練・図鑑・誓い・心得・歌片・深淵・仲間と渡る は、祭司に話しかけて開く祠の画面 (TrialBoard) に一つにまとめた
    static void open(ServerPlayer sp, String titan, boolean sneak) {
        Notice.hint(sp, "message.skyabyss.shrine.ask_priest");
    }

    // 歌片を持って碑に触れた: 同じく祭司へ
    static void shardTouch(ServerPlayer sp, String titan) {
        open(sp, titan, false);
    }

    // 祭司に話しかけた (AttendantEntity): 祠のそばなら祠の画面を開く。開いたか (開かなければふつうの会話)
    public static boolean talkToPriest(ServerPlayer sp) {
        String titan = shrineNear(sp, PRIEST_REACH);
        if (titan == null) {
            return false;
        }
        jp.revati.skyabyss.trial.TrialBoard.open(sp, titan);
        return true;
    }

    // 断る理由 (null なら使える)。純粋に近い部分 (自己テストが直接呼ぶ)
    @Nullable
    public static Component refusal(MinecraftServer server, ServerPlayer by, String titan, List<ServerPlayer> ring, boolean hasShard, boolean routeActive) {
        if (!STOPPED.test(server, by.getUUID(), titan)) {
            return ModText.deny("message.skyabyss.shrine.unknown");
        }
        for (ServerPlayer p : ring) {
            if (!STOPPED.test(server, p.getUUID(), titan)) {
                return ModText.deny("message.skyabyss.shrine.member_unknown", p.getGameProfile().getName());
            }
        }
        if (!hasShard) {
            return ModText.deny("message.skyabyss.shrine.no_shard", ModItems.songShard(titan).getDescription());
        }
        if (routeActive) {
            return ModText.deny("message.skyabyss.shrine.busy");
        }
        return null;
    }

    // 集合台のまわり (GATHER_RANGE) の、その人のグループのオンラインの人 (本人を先に)
    public static List<ServerPlayer> ring(ServerPlayer by, BlockPos center, String titan) {
        List<ServerPlayer> out = new ArrayList<>();
        out.add(by);
        UUID group = Groups.keyOf(by);
        for (ServerPlayer p : Players.snapshot(by.server.getPlayerList().getPlayers())) {
            if (p != by && Groups.keyOf(p).equals(group) && nearPad(p, center, titan)) {
                out.add(p);
            }
        }
        return out;
    }

    // 歌片を払える人 (旅嚢か持ち物に一つでもある。クリエイティブは払わない)
    static boolean hasShard(ServerPlayer p, String titan) {
        return p.isCreative() || jp.revati.skyabyss.pouch.Pouch.countAll(p, ModItems.songShard(titan)) > 0;
    }

    public static void activate(ServerPlayer by, String titan, BlockPos pos) {
        MinecraftServer server = by.server;
        ServerLevel back = Titans.hasBack(titan) ? server.getLevel(Titans.backOf(titan)) : null;
        if (back == null) {
            Notice.bar(by, ModText.deny("message.skyabyss.shrine.no_back"));
            return;
        }
        UUID group = Groups.keyOf(by);
        List<String> away = missing(by, pos, titan);
        if (!away.isEmpty()) {
            Notice.bar(by, ModText.deny("message.skyabyss.shrine.missing", String.join("・", away)));
            return;
        }
        List<ServerPlayer> ring = ring(by, pos, titan);
        Component why = refusal(server, by, titan, ring, hasShard(by, titan), TrialRoute.active(back, group));
        if (why != null) {
            Notice.bar(by, why);
            return;
        }
        if (!by.isCreative()) {
            jp.revati.skyabyss.pouch.Pouch.takeAny(by, ModItems.songShard(titan), 1);
        }
        // 輪の外の仲間には、名前つきの一行
        List<String> names = new ArrayList<>();
        for (ServerPlayer p : ring) {
            names.add(p.getGameProfile().getName());
        }
        for (ServerPlayer p : Players.snapshot(server.getPlayerList().getPlayers())) {
            if (Groups.keyOf(p).equals(group) && !ring.contains(p)) {
                Notice.hint(p, "message.skyabyss.shrine.left_behind", String.join("・", names));
            }
        }
        int style = Titans.TIDE_SHELL.equals(titan) ? 1 : 0;
        BlockPos arrival = ReturnBeacons.arrivalOf(titan);
        long now = server.getTickCount();
        for (ServerPlayer p : ring) {
            Notice.narrate(p, "message.skyabyss.shrine.song");
            PacketDistributor.sendToPlayer(p, new jp.revati.skyabyss.titan.travel.TravelNetwork.Veil(true, style));
            VEILS.put(p.getUUID(), new long[] {now + VEIL_TICKS, style});
            p.stopRiding();
            p.teleportTo(back, arrival.getX() + 0.5, arrival.getY() + 1, arrival.getZ() + 0.5, p.getYRot(), 0f);
            p.fallDistance = 0;
            jp.revati.skyabyss.lore.Lore.unlock(p, loreId(titan), true);
        }
        // 区画の写しは帳のあいだに tick ごとに少しずつ (一 tick に止まらない)。段 1 の写しが済んだら運ばれる
        TrialRoute.startSoon(back, group, titan, ring);
        SkyAbyss.LOGGER.info("{} used the shrine of the {} with {} in the ring", by.getGameProfile().getName(), titan, names);
    }

    public static String loreId(String titan) {
        return "chapter1/shrine_" + jp.revati.skyabyss.trial.TrialRegions.signalTitan(titan);
    }

    // ---------------------------------------------------------------- 毎 tick: 雲の帳を上げる・祠のまわり (順位の石と飾り板)
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (!VEILS.isEmpty()) {
            for (Map.Entry<UUID, long[]> e : new ArrayList<>(VEILS.entrySet())) {
                if (now < e.getValue()[0]) {
                    continue;
                }
                VEILS.remove(e.getKey());
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p != null) {
                    PacketDistributor.sendToPlayer(p, new jp.revati.skyabyss.titan.travel.TravelNetwork.Veil(false, (int) e.getValue()[1]));
                }
            }
        }
        if (now % 10 == 3) {
            gather(server, now);
        }
        if (now % 200 != 37) {
            return;
        }
        ServerLevel level = server.overworld();
        for (Map.Entry<String, BlockPos> e : new ArrayList<>(Data.get(server).pos.entrySet())) {
            BlockPos c = e.getValue();
            if (!level.hasChunkAt(c)) {
                continue;
            }
            boolean someone = false;
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(c.getX(), c.getY(), c.getZ()) < 48 * 48) {
                    someone = true;
                    break;
                }
            }
            if (someone) {
                // 前の版の祠・碑が無くなった祠 (古い世界の書き換え・コマンド) は建て直す
                int ver = Data.get(server).ver.getOrDefault(e.getKey(), 1);
                if (ver < ShrineBuild.VERSION) {
                    // 町などの建て物が建つのを待ってから (建て物の空けた所が祠を削らないように)
                    if (jp.revati.skyabyss.opening.site.SiteBuilds.busy()) {
                        continue;
                    }
                    rebuildOld(server, e.getKey(), c, ver);
                    continue;
                }
                if (!level.getBlockState(c).is(ModBlocks.TITAN_SHRINE.get())) {
                    place(server, e.getKey(), ShrineBuild.originOf(c, e.getKey()));
                    continue;
                }
                jp.revati.skyabyss.rebirth.RebirthBoards.install(level, e.getKey(), ShrineBuild.padOf(c, e.getKey()));
                ensurePriest(level, e.getKey(), c);
            }
        }
    }

    // 数え下ろし (0.5 秒ごと): 祭司に頼んだグループ (ask) だけ。全員が集まる所にいるあいだ数え、0 で渡る。
    // 誰かが離れた・断る理由ができたら止める。集合台に立つだけでは始まらない (祭司の案内を出す)
    static void gather(MinecraftServer server, long now) {
        ServerLevel level = server.overworld();
        if (now % 60 == 3) {
            for (Map.Entry<String, BlockPos> e : Data.get(server).pos.entrySet()) {
                BlockPos c = e.getValue();
                if (!level.hasChunkAt(c)) {
                    continue;
                }
                for (ServerPlayer p : Players.snapshot(level.players())) {
                    if (onPad(p, c, e.getKey()) && !ASKED.containsKey(Groups.keyOf(p))) {
                        Notice.bar(p, ModText.progress("message.skyabyss.shrine.talk_priest"));
                    }
                }
            }
        }
        for (Map.Entry<UUID, String> a : new ArrayList<>(ASKED.entrySet())) {
            UUID group = a.getKey();
            String titan = a.getValue();
            BlockPos c = posOf(server, titan);
            List<ServerPlayer> members = new ArrayList<>();
            for (ServerPlayer p : Players.snapshot(server.getPlayerList().getPlayers())) {
                if (!p.isSpectator() && Groups.keyOf(p).equals(group)) {
                    members.add(p);
                }
            }
            if (c == null || members.isEmpty()) {
                ASKED.remove(group);
                GATHER.remove(group);
                continue;
            }
            List<String> away = missing(members.get(0), c, titan);
            if (!away.isEmpty()) {
                for (ServerPlayer p : members) {
                    Notice.bar(p, ModText.deny("message.skyabyss.shrine.gather_broken", String.join("・", away)));
                }
                ASKED.remove(group);
                GATHER.remove(group);
                continue;
            }
            ServerPlayer payer = members.get(0);
            for (ServerPlayer p : members) {
                if (hasShard(p, titan)) {
                    payer = p;
                    break;
                }
            }
            ServerLevel back = Titans.hasBack(titan) ? server.getLevel(Titans.backOf(titan)) : null;
            Component why = back == null ? ModText.deny("message.skyabyss.shrine.no_back")
                    : refusal(server, payer, titan, members, hasShard(payer, titan), TrialRoute.active(back, group));
            if (why != null) {
                for (ServerPlayer p : members) {
                    Notice.bar(p, why);
                }
                ASKED.remove(group);
                GATHER.remove(group);
                continue;
            }
            long[] st = GATHER.computeIfAbsent(group, k -> new long[] {now, -1});
            long left = GATHER_TICKS - (now - st[0]);
            if (left <= 0) {
                ASKED.remove(group);
                GATHER.remove(group);
                activate(payer, titan, c);
                continue;
            }
            long sec = (left + 19) / 20;
            for (ServerPlayer p : members) {
                Notice.bar(p, ModText.progress("message.skyabyss.shrine.pad_count", sec));
            }
            if (st[1] != sec) {
                st[1] = sec;
                level.playSound(null, ShrineBuild.padOf(c, titan), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.8f, sec == 1 ? 1.4f : 0.9f);
            }
        }
    }

    // 祠は壊せない (クリエイティブは除く。広場の円を囲む箱)
    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != net.minecraft.world.level.Level.OVERWORLD
                || event.getPlayer() == null || event.getPlayer().isCreative()) {
            return;
        }
        for (Map.Entry<String, BlockPos> e : Data.get(level.getServer()).pos.entrySet()) {
            if (guard(e.getValue(), e.getKey()).contains(event.getPos().getCenter())) {
                event.setCanceled(true);
                Notice.bar(event.getPlayer(), ModText.deny("message.skyabyss.shrine.guarded"));
                return;
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        GATHER.clear();
        ASKED.clear();
        VEILS.clear();
        STOPPED = Progression::stopped;
    }

    // ---------------------------------------------------------------- 記録 (オーバーワールド)
    public static final class Data extends SavedData {
        private static final String NAME = "skyabyss_titan_shrines";
        final Map<String, BlockPos> pos = new HashMap<>();
        // 祠の作りの版 (ShrineBuild.VERSION。前の版の祠は 1)
        final Map<String, Integer> ver = new HashMap<>();

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            CompoundTag vers = tag.getCompound("ver");
            for (String titan : tag.getAllKeys()) {
                if (titan.equals("ver")) {
                    continue;
                }
                d.pos.put(titan, BlockPos.of(tag.getLong(titan)));
                d.ver.put(titan, vers.contains(titan) ? vers.getInt(titan) : 1);
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            pos.forEach((t, p) -> tag.putLong(t, p.asLong()));
            CompoundTag vers = new CompoundTag();
            ver.forEach(vers::putInt);
            tag.put("ver", vers);
            return tag;
        }

        // 自己テスト用
        public void forget(String titan) {
            pos.remove(titan);
            ver.remove(titan);
            setDirty();
        }
    }
}
