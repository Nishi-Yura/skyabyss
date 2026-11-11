package jp.revati.skyabyss.upgrade;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.world.continent.ContinentChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 大陸 (地上) の作り直し (2026-11-03 の声「アップデートに対応できるように。かえられないものは無いようにしたい」)。
// 大陸の世界の地上は、Mod が作る物だけでできている (冒険の決まりで人はブロックを置けず壊せない。docs/design/40 の 2 章) ので、
// 地上のブロックと生き物はいつでも作り直せる。進み (クエスト・持ち物・見つけた見どころ・伝承・巨神の進み・墓の中身) は保存のデータにある。
// 地形・道・建物の置き場所の決まりを変えたら VERSION を一つ上げる。前の版で作った大陸の世界を開くと:
//   1. 地上を読む前 (ServerAboutToStart): 地上のチャンクの保存 (region・entities・poi) を skyabyss_backup/overworld_v<前の版> へ移す
//      (前の作り直しの控えは消し、一つだけ残す)。地形・木・道は、新しい世界と同じに作られる
//   2. 世界を開いたとき (ServerStarted。場所の建て直しの予約より先): 建て物の置き場をすべて捨て (野営地の場所だけ残す)、新しい世界と同じに
//      置き場から決め直して建てさせる (都・町・船着き場・島・見どころ・外れの集落・章の跡地と小要素・祠 (今の場所に建て直す)・沖の難破船)。
//      跡地を見た人・章の小要素に着いた人の記録は引き継ぐ (SiteData.clearForRegen)。
//      巨神は居場所へ出し直す。地上の墓は、チャンクが読み込まれたら印のブロックを置き直す
//   3. 入った人: 前の版の地上で、地面や壁の中にいる人は、上の空いた所へ出す
// 版の記録は世界のフォルダの skyabyss/continent_version.txt (地上を読む前に読むので、保存のデータではなくファイル)。
// 新しい世界 (地上のチャンクがまだない) は、今の版を書くだけ。大陸でない世界 (人が建てた物がありうる) は何もしない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ContinentRegen {
    // 大陸の中身の版。1 = 2026-11-03 (地形の起伏・段丘・岩の露頭・池、祠を道から離す、鯨見の道を門で止める)、
    // 2 = 2026-11-03 夜 (祠の参道を丘の中のトンネルにしない。切り通しにする)、
    // 3 = 2026-11-04 (見どころの作り替え: 道しるべ 6・頭目の兄弟の野営・旅人の野営・場所ごとの部品。docs/design/40 の 3.3)、
    // 4 = 2026-11-07 (半ブロックが面で接するように: 町の岩を巻く段・灯の笠・風車・門のまぐさ・僧院の屑。tools/audit/check_slabs.py)、
    // 5 = 2026-11-08 (潮殻と岩鯨の祠を町の外へ。町の広場と縁が祠の台地に重なっていた。「祠の位置と建物がかぶってる」)
    // 6 = 2026-11-08 夜 (岩のアーチ・岩山の張り出しの下に、くぐれない 1〜2 段のすき間を作らない。「火のバイオームのところ、高さが無く通れない」)
    // 7 = 2026-11-08 夜 (四つの大陸: 内海から外の海へ四本の広い海を引いて、西・北・東・南の島に分ける。海を渡る道は岸で切って渡し船。docs/design/40 の 3 章)
    // 8 = 2026-11-08 夜遅く (上がれない足場を直した: 大樹の床の輪・野盗の物見の梯子・柵の砦村の物見の梯子、都の城壁の歩廊へ門のそばの段と塔の通り抜け。
    //     別の道どうしの出会いを丸く、見どころを道から対角の幅で離す。「上らせる気の無い建築」「道と建造物重なってる」「道が上手く曲がってない」)
    // 9 = 2026-11-09 (章の小さな部品 (荷車の傍らの轍と荷・旅の職人の立つ所など、SiteJob.placeSmall) を道から遠ざける。「ここ道に旅の職人がかぶってる」)
    // 10 = 2026-11-09 (外れの帯の見せ場の地形: 骨の谷の肋骨・干潟と岩礁・氷河と浮き岩・冷えた熔岩の川と灰の砂丘。world/continent/FrontierTerrain。issue #19)
    // 11 = 2026-11-09 (奥のダンジョン四つの入口を外れの置き場に。dungeon/Dungeon の DEEP_*、tools/gen/world/dungeons_deep.py)
    // 12 = 2026-11-09 (木をバニラの飾りから、土地ごとのオリジナルの形の木に。間を空け、砂丘のまわりは緑の木なし。world/continent/ContinentTrees)
    // 13 = 2026-11-09 夜 (作り直しで建て物の置き場もすべて決め直す。前の版の置き場が残り、四つの大陸の渡し船などが途中からの世界に出なかった。
    //      「途中からのところはおかしくなるのかも。全部新しくなるようにしてほしい」)
    // 14 = 2026-11-11 (雷の土地の中ダンジョン (風穴) の口を、雷翼の祠のすぐ脇から西の平らな所へ (-1308,-266)。枝道も引き直し。
    //      「雷のところ、中ダンジョンの入り口の場所がおかしい」。ContinentLairsSelfTest が祠と見どころの間をたしかめる)
    public static final int VERSION = 14;
    static final String FILE = "skyabyss/continent_version.txt";
    static final String BACKUP = "skyabyss_backup";
    // 人の永続データ: その人が入ったときの大陸の版 (倒れて消えても、埋まっていなければ何もしないので害はない)
    public static final String PLAYER_KEY = "skyabyss_continent_version";
    private static final String[] DIRS = {"region", "entities", "poi"};

    private ContinentRegen() {
    }

    // この起動で作り直したか (前の版)。-1 = 作り直していない
    private static int regenFrom = -1;
    // 祠を建て直す巨神と、印を置き直す墓
    private static final Set<String> SHRINES = new HashSet<>();
    private static final Set<UUID> GRAVES = new HashSet<>();

    public static boolean regenerated() {
        return regenFrom >= 0;
    }

    static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT);
    }

    // 世界のフォルダの版 (ファイルがなければ 0)
    public static int savedVersion(Path root) {
        Path f = root.resolve(FILE);
        try {
            return Files.exists(f) ? Integer.parseInt(Files.readString(f, StandardCharsets.UTF_8).trim()) : 0;
        } catch (IOException | NumberFormatException e) {
            SkyAbyss.LOGGER.warn("Continent regen: could not read {} ({}); treating the world as version 0", f, e.toString());
            return 0;
        }
    }

    static void writeVersion(Path root, int version) {
        Path f = root.resolve(FILE);
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, Integer.toString(version), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SkyAbyss.LOGGER.error("Continent regen: could not write {}", f, e);
        }
    }

    // 地上のチャンクの保存があるか (region に .mca が一つでもある)
    static boolean hasChunks(Path root) {
        Path region = root.resolve("region");
        if (!Files.isDirectory(region)) {
            return false;
        }
        try (Stream<Path> s = Files.list(region)) {
            return s.anyMatch(p -> p.getFileName().toString().endsWith(".mca"));
        } catch (IOException e) {
            return false;
        }
    }

    // 地上が大陸の生成か (世界の設定から。地上を読む前に分かる)
    static boolean continent(MinecraftServer server) {
        LevelStem stem = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).get(LevelStem.OVERWORLD);
        return stem != null && stem.generator() instanceof ContinentChunkGenerator;
    }

    // ---------------------------------------------------------------- 1. 地上を読む前
    @SubscribeEvent
    static void onAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        regenFrom = -1;
        LOADED.clear();
        SETTLE.clear();
        SHRINES.clear();
        GRAVES.clear();
        if (!continent(server)) {
            return;
        }
        Path root = root(server);
        // 新しい世界と、自己テストの世界 (雛形の写し。作り直すと段の前提の建て物がなくなる) は、今の版を書くだけ
        if (!hasChunks(root) || System.getenv("SKYABYSS_SELFTEST") != null) {
            writeVersion(root, VERSION);
            return;
        }
        int saved = savedVersion(root);
        if (saved >= VERSION) {
            return;
        }
        // 開発用: 作り直す前の姿を見る (撮影の見比べ)。版も書かない
        if (System.getenv("SKYABYSS_NO_REGEN") != null) {
            SkyAbyss.LOGGER.info("Continent regen: skipped (SKYABYSS_NO_REGEN); the world stays at continent version {}", saved);
            return;
        }
        Path backup = root.resolve(BACKUP).resolve("overworld_v" + saved);
        try {
            // 前の作り直しの控えは消す (一つだけ残す)
            Path all = root.resolve(BACKUP);
            if (Files.isDirectory(all)) {
                try (Stream<Path> s = Files.list(all)) {
                    for (Path old : s.filter(p -> p.getFileName().toString().startsWith("overworld_v")).toList()) {
                        deleteTree(old);
                    }
                }
            }
            Files.createDirectories(backup);
            for (String d : DIRS) {
                Path src = root.resolve(d);
                if (Files.isDirectory(src)) {
                    Files.move(src, backup.resolve(d));
                }
            }
        } catch (IOException e) {
            // 移せなかったら作り直さない (前の地上のまま開く。次の起動でもう一度)
            SkyAbyss.LOGGER.error("Continent regen: could not move the old overworld chunks to {}; the world opens unchanged", backup, e);
            return;
        }
        regenFrom = saved;
        SkyAbyss.LOGGER.info("Continent regen: the overworld (continent version {} -> {}) will be generated again; the old chunks are kept in {}",
                saved, VERSION, backup);
    }

    static void deleteTree(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    // ---------------------------------------------------------------- 2. 世界を開いたとき
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onStarted(ServerStartedEvent event) {
        if (regenFrom < 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        try {
            resetBuilt(server);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("Continent regen: resetting the built sites failed", e);
        }
        writeVersion(root(server), VERSION);
    }

    // 建て物の「建った」印を消す (建て直しは各仕組みの見回りが、人が近づいたときにする)
    static void resetBuilt(MinecraftServer server) {
        ServerLevel over = server.overworld();
        SiteData data = SiteData.get(over);
        // 置き場をすべて捨てて、新しい世界と同じに決め直す (このあと SiteBuilds.onServerStarted が計画からやり直す)。
        // 前は「建った」印だけ消して前の版の置き場に建て直していたので、あとから足した物 (四つの大陸の渡し船など) が途中からの世界に出なかった
        int entries = data.clearForRegen();
        // 祠: 建っていた巨神の分を、今の場所に建て直す (onTick。チャンクを読むので起動の直後に一つずつ)
        for (String titan : jp.revati.skyabyss.progress.Titans.all()) {
            if (jp.revati.skyabyss.titan.shrine.TitanShrine.forget(server, titan)) {
                SHRINES.add(titan);
            }
        }
        // 地上の巨神は居場所へ出し直す (前の体の生き物はもうない)
        jp.revati.skyabyss.titan.TitanWorldData titans = jp.revati.skyabyss.titan.TitanWorldData.get(over);
        for (String titan : jp.revati.skyabyss.progress.Titans.all()) {
            jp.revati.skyabyss.titan.TitanWorldData.Slot slot = titans.slot(titan);
            slot.spawned = false;
            slot.id = null;
            slot.hasLast = false;
        }
        titans.setDirty();
        // 沖の難破船は、もう一度置く
        jp.revati.skyabyss.story.ch2.Wreck.forget(server);
        // 語り部・鯨追い・職人を立たせた時刻: 地上の生き物ごと消えたので、待たずに立て直す (2026-11-05 の声「アプデしたら、語り部がいない」)
        jp.revati.skyabyss.npc.NpcWorldData.get(over).forgetAll();
        // 地上の墓は、印のブロックを置き直す
        for (jp.revati.skyabyss.grave.GraveData.Grave g : jp.revati.skyabyss.grave.GraveData.get(server).all()) {
            if (g.dimension == Level.OVERWORLD) {
                GRAVES.add(g.id);
            }
        }
        SkyAbyss.LOGGER.info("Continent regen: {} old site(s) dropped; every place is planned and built again like a new world, {} shrine(s) rebuilt, {} grave mark(s) restored",
                entries, SHRINES.size(), GRAVES.size());
    }

    // 祠の建て直しと墓の印 (1 秒ごと)
    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (SHRINES.isEmpty() && GRAVES.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 7) {
            return;
        }
        // 祠は、町などの建て物が建ち終わってから (先に建てると、あとの建て物が空けた所が祠を削る)
        if (!SHRINES.isEmpty() && !jp.revati.skyabyss.opening.site.SiteBuilds.busy()) {
            String titan = SHRINES.iterator().next();
            SHRINES.remove(titan);
            try {
                jp.revati.skyabyss.titan.shrine.TitanShrine.ensure(server, titan);
            } catch (RuntimeException e) {
                SkyAbyss.LOGGER.error("Continent regen: rebuilding the shrine of the {} failed", titan, e);
            }
        }
        ServerLevel over = server.overworld();
        jp.revati.skyabyss.grave.GraveData graves = jp.revati.skyabyss.grave.GraveData.get(server);
        for (var it = GRAVES.iterator(); it.hasNext(); ) {
            jp.revati.skyabyss.grave.GraveData.Grave g = graves.get(it.next());
            if (g == null) {
                it.remove();
                continue;
            }
            if (!over.hasChunkAt(g.pos)) {
                continue;
            }
            it.remove();
            Block grave = jp.revati.skyabyss.registry.ModBlocks.GRAVE.get();
            if (!over.getBlockState(g.pos).is(grave)) {
                over.setBlock(g.pos, grave.defaultBlockState(), Block.UPDATE_ALL);
            }
            // 地形が上がって埋まった墓は、上の二つを空ける (近づいて触れるように)
            for (int up = 1; up <= 2; up++) {
                BlockPos a = g.pos.above(up);
                if (!over.getBlockState(a).isAir() && over.getBlockState(a).getFluidState().isEmpty()) {
                    over.setBlock(a, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 3. 入った人
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) {
            return;
        }
        var tag = p.getPersistentData();
        // 印の無い人は、この世界に初めて入った人 (新しい世界・新しい仲間)。作り直した起動でなければ、今の版を書くだけ
        // (2026-11-09 の声「新しいワールド作ったら、大陸の新しい版を作り直していますと初めに出た」「職選べる画面出なかった」:
        //  前は印が無いのを古い版と見て読み込み画面を出し、終わると立ち位置を動かして野営地から離していた)
        if (!tag.contains(PLAYER_KEY) && !regenerated()) {
            tag.putInt(PLAYER_KEY, VERSION);
            return;
        }
        boolean first = tag.getInt(PLAYER_KEY) < VERSION;
        // 版が上がってから初めて入った人と、この起動で作り直した世界に初めて入った人: まわりの地形と建物が揃うまで読み込み画面 (RegenLoading)
        if (continent(p.server) && (first || regenerated() && LOADED.add(p.getUUID()))) {
            RegenLoading.begin(p);
            // 地面に立たせるのは、まわりが読めて建物が建ったあと (読み込み画面が終わったとき。released)。版の印もそのとき書く
            // (読み込みの途中で抜けた人は、次に入ったときにもう一度)
            if (first) {
                SETTLE.add(p.getUUID());
            }
            return;
        }
        if (first) {
            tag.putInt(PLAYER_KEY, VERSION);
        }
    }

    // この起動で読み込み画面を出した人 (作り直した起動のあいだに、二度は出さない)
    private static final Set<UUID> LOADED = new HashSet<>();
    // 読み込み画面が終わったら地面に立たせる人 (版が上がってから初めて入った人)
    private static final Set<UUID> SETTLE = new HashSet<>();

    // 読み込み画面が終わった (RegenLoading)。版が上がってから初めての人は、地面に立たせて版の印を書く
    static void released(ServerPlayer p) {
        if (!SETTLE.remove(p.getUUID())) {
            return;
        }
        p.getPersistentData().putInt(PLAYER_KEY, VERSION);
        // 地上の外にいる人 (背中・塔など) は、戻ったときの場所が作り直した地上の上なのでそのまま
        if (p.level().dimension() == Level.OVERWORLD) {
            settle(p);
        }
    }

    // 前の版の立ち位置が、作り直した地上では海の上・宙・地面の中になっていることがある (2026-11-05 の声「自分は海に落ちた」)。
    //   地面や壁の中 → 上の空いた所 (unbury)。足の下 3 までに地面がある → そのまま。
    //   下が陸 → その陸の上。下が水 (海・湖) → 近くの陸 (まわり 96 まで)。どれも無ければ起き上がる場所
    static void settle(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        unbury(p);
        BlockPos from = p.blockPosition();
        BlockPos to = settleTarget(level, from);
        if (to == null) {
            return;
        }
        if (to.equals(NO_LAND)) {
            BlockPos home = p.getRespawnPosition() != null && p.getRespawnDimension() == Level.OVERWORLD ? p.getRespawnPosition() : level.getSharedSpawnPos();
            level.getChunk(home.getX() >> 4, home.getZ() >> 4);
            // 起き上がる場所そのもの (か、そのそばの立てる所)。列のいちばん上にすると屋根の上に出る
            to = standNear(level, home);
            if (to == null) {
                to = new BlockPos(home.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, home.getX(), home.getZ()), home.getZ());
            }
        }
        p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.resetFallDistance();
        SkyAbyss.LOGGER.info("Continent regen: moved {} from {} to {} (the old spot was over water or in the air)", p.getGameProfile().getName(), from, to);
    }

    // at のそば (横 2・下 1・上 2) で、足と頭が空いて足の下に地面がある所。近い順。無ければ null
    static BlockPos standNear(ServerLevel level, BlockPos at) {
        BlockPos best = null;
        int bestD = Integer.MAX_VALUE;
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = at.offset(dx, dy, dz);
                    int d = dx * dx + dz * dz + dy * dy;
                    if (d < bestD && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                            && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                            && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()
                            && level.getFluidState(p).isEmpty()) {
                        best = p;
                        bestD = d;
                    }
                }
            }
        }
        return best;
    }

    // 近くに陸が無い (起き上がる場所へ)
    static final BlockPos NO_LAND = new BlockPos(0, Integer.MIN_VALUE / 2, 0);
    static final int LAND_SEARCH = 96;

    // 足の位置 feet の人をどこへ移すか。null = 動かさない、NO_LAND = 起き上がる場所へ
    static BlockPos settleTarget(ServerLevel level, BlockPos feet) {
        boolean wet = !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty();
        if (!wet) {
            for (int i = 1; i <= 3; i++) {
                BlockPos b = feet.below(i);
                if (!level.getBlockState(b).getCollisionShape(level, b).isEmpty()) {
                    return null;
                }
            }
        }
        BlockPos here = wet ? null : dryTop(level, feet.getX(), feet.getZ());
        if (here != null) {
            return here;
        }
        BlockPos best = null;
        for (int r = 1; r <= LAND_SEARCH && best == null; r += r < 8 ? 1 : 4) {
            int step = Math.max(1, r / 8);
            double bestD = Double.MAX_VALUE;
            for (int a = -r; a <= r; a += step) {
                for (int[] d : new int[][] {{a, -r}, {a, r}, {-r, a}, {r, a}}) {
                    BlockPos land = dryTop(level, feet.getX() + d[0], feet.getZ() + d[1]);
                    if (land != null) {
                        double dist = (double) d[0] * d[0] + (double) d[1] * d[1];
                        if (dist < bestD) {
                            bestD = dist;
                            best = land;
                        }
                    }
                }
            }
        }
        return best != null ? best : NO_LAND;
    }

    // 列 (x, z) のいちばん上が水でない地面なら、その上 (足の高さ)。水・溶岩が上にある列、読めていない列は null
    static BlockPos dryTop(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return null;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
        if (top > floor || top <= level.getMinBuildHeight() + 1) {
            return null;
        }
        BlockPos at = new BlockPos(x, top, z);
        if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty() || !level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
                || !level.getFluidState(at.below()).isEmpty()) {
            return null;
        }
        return at;
    }

    // 地面や壁の中にいたら、上の、足と頭が空いた所へ出す (最大 64 上まで)
    static void unbury(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        if (level.noCollision(p, p.getBoundingBox())) {
            return;
        }
        BlockPos.MutableBlockPos m = p.blockPosition().mutable();
        for (int i = 0; i < 64; i++) {
            m.move(0, 1, 0);
            if (level.getBlockState(m).getCollisionShape(level, m).isEmpty()
                    && level.getBlockState(m.above()).getCollisionShape(level, m.above()).isEmpty()) {
                p.teleportTo(level, p.getX(), m.getY(), p.getZ(), p.getYRot(), p.getXRot());
                SkyAbyss.LOGGER.info("Continent regen: moved {} out of the ground to Y {}", p.getGameProfile().getName(), m.getY());
                return;
            }
        }
    }

    // OP の確かめ: /skyabyss dev regen settle (作り直したあとの立ち位置の直しを、今いる所で流す。海の上・宙で試す)
    @SubscribeEvent
    static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal(SkyAbyss.MODID).requires(src -> src.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("dev").then(net.minecraft.commands.Commands.literal("regen")
                        .then(net.minecraft.commands.Commands.literal("settle").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            BlockPos to = settleTarget(p.serverLevel(), p.blockPosition());
                            String msg = "settle from " + p.blockPosition().toShortString() + " -> " + (to == null ? "stay" : to.equals(NO_LAND) ? "respawn point" : to.toShortString());
                            SkyAbyss.LOGGER.info("[regen] {}", msg);
                            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(msg), false);
                            settle(p);
                            return 1;
                        })))));
    }
}
