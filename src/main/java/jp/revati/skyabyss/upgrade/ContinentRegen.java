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
//   2. 世界を開いたとき (ServerStarted。場所の建て直しの予約より先): 建て物の「建った」印を消し、今の設計図と置き場で建て直させる
//      (野営地・都・町・船着き場・島・見どころ (置き場から決め直す)・章の小要素・跡地の均し・祠 (今の場所に建て直す)・沖の難破船)。
//      巨神は居場所へ出し直す。地上の墓は、チャンクが読み込まれたら印のブロックを置き直す
//   3. 入った人: 前の版の地上で、地面や壁の中にいる人は、上の空いた所へ出す
// 版の記録は世界のフォルダの skyabyss/continent_version.txt (地上を読む前に読むので、保存のデータではなくファイル)。
// 新しい世界 (地上のチャンクがまだない) は、今の版を書くだけ。大陸でない世界 (人が建てた物がありうる) は何もしない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ContinentRegen {
    // 大陸の中身の版。1 = 2026-11-03 (地形の起伏・段丘・岩の露頭・池、祠を道から離す、鯨見の道を門で止める)、
    // 2 = 2026-11-03 夜 (祠の参道を丘の中のトンネルにしない。切り通しにする)、
    // 3 = 2026-11-04 (見どころの作り替え: 道しるべ 6・頭目の兄弟の野営・旅人の野営・場所ごとの部品。docs/design/40 の 3.3)
    public static final int VERSION = 3;
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
        int entries = 0;
        // 見どころは置き場から決め直す (道から離す決まり・地面の高さが変わる)
        for (SiteData.Entry e : new ArrayList<>(data.entries(jp.revati.skyabyss.world.continent.ContinentPois.KIND))) {
            data.remove(e);
        }
        for (SiteData.Entry e : data.allEntries()) {
            e.built = false;
            e.started = false;
            entries++;
        }
        int grounds = 0;
        for (SiteData.Ground g : data.grounds()) {
            if (g.shaped && !"NONE".equals(g.shape)) {
                g.shaped = false;
                grounds++;
            }
        }
        data.setDirty();
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
        // 地上の墓は、印のブロックを置き直す
        for (jp.revati.skyabyss.grave.GraveData.Grave g : jp.revati.skyabyss.grave.GraveData.get(server).all()) {
            if (g.dimension == Level.OVERWORLD) {
                GRAVES.add(g.id);
            }
        }
        SkyAbyss.LOGGER.info("Continent regen: {} site(s) and {} ground(s) will be built again, {} shrine(s) rebuilt, {} grave mark(s) restored",
                entries, grounds, SHRINES.size(), GRAVES.size());
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
        if (!SHRINES.isEmpty()) {
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
        boolean first = tag.getInt(PLAYER_KEY) < VERSION;
        // 版が上がってから初めて入った人と、この起動で作り直した世界に初めて入った人: まわりの地形と建物が揃うまで読み込み画面 (RegenLoading)
        if (continent(p.server) && (first || regenerated() && LOADED.add(p.getUUID()))) {
            RegenLoading.begin(p);
        }
        if (!first) {
            return;
        }
        tag.putInt(PLAYER_KEY, VERSION);
        // 地上の外にいる人 (背中・塔など) は、戻ったときの場所が作り直した地上の上なのでそのまま
        if (p.level().dimension() == Level.OVERWORLD) {
            unbury(p);
        }
    }

    // この起動で読み込み画面を出した人 (作り直した起動のあいだに、二度は出さない)
    private static final Set<UUID> LOADED = new HashSet<>();

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
}
