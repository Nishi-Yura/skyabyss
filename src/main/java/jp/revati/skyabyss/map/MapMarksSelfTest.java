package jp.revati.skyabyss.map;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.KeyDefaults;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.OpeningBeat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.util.FakePlayer;

// 旅の地図の印 (docs/design/33 の 4 章) の自己テスト: 序盤の記録から「噂/見た」を決める・噂は本当の位置を出さない (方角は 16 方位の内、距離は 100 刻み)・
// すでに遊んでいた人は全部見た・積み石は町を見たあと・一人ぶんの集めがこわれない・パケットの往復・キー M
public final class MapMarksSelfTest {
    private MapMarksSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            run(server, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST travel map crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST travel map PASSED");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST travel map FAILED: {}", m));
        }
    }

    private static final BlockPos CAMP = new BlockPos(0, 70, 0);
    private static final BlockPos TOWN = new BlockPos(130, 70, -120);
    private static final BlockPos RIBS = new BlockPos(-260, 80, 120);

    private static List<MapMarks.Spot> spots() {
        List<MapMarks.Spot> s = new ArrayList<>();
        s.add(new MapMarks.Spot("camp", 0, CAMP));
        s.add(new MapMarks.Spot("shelter", 0, TOWN));
        for (int i = 0; i < 3; i++) {
            s.add(new MapMarks.Spot("footprint", i, new BlockPos(30 + i * 20, 70, -40 - i * 15)));
        }
        s.add(new MapMarks.Spot("ribs", 0, RIBS));
        for (int i = 0; i < 4; i++) {
            s.add(new MapMarks.Spot("cairn", i, new BlockPos(26 * (i + 1), 70, -24 * (i + 1))));
        }
        return s;
    }

    private static List<MapMarks.Mark> marks(boolean legacy, Set<OpeningBeat> done, BlockPos here) {
        List<MapMarks.Mark> out = new ArrayList<>();
        MapMarks.openingMarks(spots(), new MapMarks.Knowledge(legacy, done, here), out);
        return out;
    }

    private static List<MapMarks.Mark> of(List<MapMarks.Mark> marks, MapMarks.Kind kind) {
        return marks.stream().filter(m -> m.kind() == kind).toList();
    }

    private static void run(MinecraftServer server, List<String> f) {
        // 1. 目覚めたばかり: 野営地は見た、町は野営地からの噂 (本当の位置ではない)、足跡・骨・積み石はまだ
        List<MapMarks.Mark> fresh = marks(false, EnumSet.of(OpeningBeat.AWAKE), CAMP);
        if (of(fresh, MapMarks.Kind.CAMP).size() != 1 || of(fresh, MapMarks.Kind.CAMP).get(0).rumor()) {
            f.add("a fresh player should see the camp");
        }
        List<MapMarks.Mark> town = of(fresh, MapMarks.Kind.TOWN);
        if (town.size() != 1 || !town.get(0).rumor()) {
            f.add("a fresh player should only have a rumor of the town: " + town);
        } else {
            MapMarks.Mark r = town.get(0);
            if (r.x() == TOWN.getX() && r.z() == TOWN.getZ()) {
                f.add("the town rumor leaks the true position");
            }
            double trueA = Math.atan2(TOWN.getZ() - CAMP.getZ(), TOWN.getX() - CAMP.getX());
            double rumorA = Math.atan2(r.z() - CAMP.getZ(), r.x() - CAMP.getX());
            double diff = Math.abs(Math.atan2(Math.sin(trueA - rumorA), Math.cos(trueA - rumorA)));
            if (diff > Math.PI / 8 + 1e-6) {
                f.add("the town rumor points the wrong way (" + Math.toDegrees(diff) + " degrees off)");
            }
            double d = Math.hypot(r.x() - CAMP.getX(), r.z() - CAMP.getZ());
            if (Math.abs(d - 200) > 1.5) {
                f.add("the town rumor distance should be rounded to 200, got " + d);
            }
        }
        if (!of(fresh, MapMarks.Kind.FOOTPRINT).isEmpty() || !of(fresh, MapMarks.Kind.RIBS).isEmpty() || !of(fresh, MapMarks.Kind.CAIRN).isEmpty()) {
            f.add("a fresh player should not see footprints, ribs or cairns");
        }
        // 噂は何度でも同じ点 (開き直すたびに動かない)
        if (!MapMarks.rumor(CAMP, TOWN, 5).equals(MapMarks.rumor(CAMP, TOWN, 5))) {
            f.add("rumor position is not stable");
        }
        // 2. 町の近くに来た: 町は見た (本当の位置)、積み石の道しるべが出る
        List<MapMarks.Mark> near = marks(false, EnumSet.of(OpeningBeat.AWAKE, OpeningBeat.CAMP), TOWN.offset(20, 0, 10));
        List<MapMarks.Mark> nearTown = of(near, MapMarks.Kind.TOWN);
        if (nearTown.size() != 1 || nearTown.get(0).rumor() || nearTown.get(0).x() != TOWN.getX() || nearTown.get(0).z() != TOWN.getZ()) {
            f.add("being near the town should make it seen at its true position: " + nearTown);
        }
        if (of(near, MapMarks.Kind.CAIRN).size() != 4) {
            f.add("cairns should show once the town is seen: " + of(near, MapMarks.Kind.CAIRN).size());
        }
        // 3. 足跡の拍子・イサナのあと: 足跡 3 つ、骨は町からの噂
        List<MapMarks.Mark> mid = marks(false, EnumSet.of(OpeningBeat.FOOTPRINTS, OpeningBeat.STORYTELLER, OpeningBeat.ISANA), null);
        if (of(mid, MapMarks.Kind.FOOTPRINT).size() != 3) {
            f.add("three footprints expected after the footprint beat");
        }
        List<MapMarks.Mark> ribs = of(mid, MapMarks.Kind.RIBS);
        if (ribs.size() != 1 || !ribs.get(0).rumor()) {
            f.add("after Isana the bone hill should be a rumor: " + ribs);
        }
        // 4. 骨の拍子のあと: 骨は見た (巨神の色の縁取り)
        List<MapMarks.Mark> bones = marks(false, EnumSet.of(OpeningBeat.STORYTELLER, OpeningBeat.BONES), null);
        List<MapMarks.Mark> rb = of(bones, MapMarks.Kind.RIBS);
        if (rb.size() != 1 || rb.get(0).rumor() || rb.get(0).x() != RIBS.getX() || rb.get(0).color() == 0) {
            f.add("after the bones beat the bone hill should be seen with the whale rim: " + rb);
        }
        // 5. すでに遊んでいた人: 全部見た
        List<MapMarks.Mark> legacy = marks(true, EnumSet.noneOf(OpeningBeat.class), null);
        if (legacy.stream().anyMatch(MapMarks.Mark::rumor) || of(legacy, MapMarks.Kind.FOOTPRINT).size() != 3 || of(legacy, MapMarks.Kind.RIBS).size() != 1) {
            f.add("a legacy player should see every opening site");
        }
        // 6. 場所が決まっていない世界: 何も出さず、町もない
        List<MapMarks.Mark> none = new ArrayList<>();
        if (MapMarks.openingMarks(List.of(), new MapMarks.Knowledge(true, Set.of(), null), none) != null || !none.isEmpty()) {
            f.add("an unplanned world should give no opening marks");
        }
        // 7. 一人ぶんの集め (本物のサーバーの記録で) とパケットの往復
        FakePlayer p = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "map_probe"));
        p.setPos(0, 100, 0);
        MapMarks.Sheet sheet = MapMarks.collect(p);
        if (sheet.marks().size() > MapMarks.MAX) {
            f.add("too many marks: " + sheet.marks().size());
        }
        List<MapMarks.Mark> sample = new ArrayList<>(fresh);
        sample.add(new MapMarks.Mark(MapMarks.Kind.TITAN, "t:stone_whale", "stone_whale", "", -1234, 5678, false, 0x7FF4FF));
        sample.add(new MapMarks.Mark(MapMarks.Kind.WAYMARK, "w1", "灯台の丘", "minecraft:plains", 10, -10, false, 0));
        MapNetwork.SheetPayload sent = new MapNetwork.SheetPayload(true, new MapMarks.Sheet(130, -120, true, sample));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            MapNetwork.SheetPayload.STREAM_CODEC.encode(buf, sent);
            MapNetwork.SheetPayload back = MapNetwork.SheetPayload.STREAM_CODEC.decode(buf);
            if (!back.equals(sent)) {
                f.add("sheet packet does not round-trip");
            }
        } finally {
            buf.release();
        }
        // 8b. 巨神の祠: 建っていて、その巨神の心臓を止めた人にだけ、見たで出る (名前の材料は巨神)
        java.util.Map<String, BlockPos> built = java.util.Map.of(jp.revati.skyabyss.progress.Titans.STONE_WHALE, new BlockPos(40, 80, -420));
        List<MapMarks.Mark> shrines = new ArrayList<>();
        MapMarks.shrineMarks(built::get, t -> true, shrines);
        if (shrines.size() != 1 || shrines.get(0).kind() != MapMarks.Kind.SHRINE || shrines.get(0).rumor() || shrines.get(0).x() != 40
                || !shrines.get(0).extra().equals(jp.revati.skyabyss.progress.Titans.STONE_WHALE) || shrines.get(0).color() == 0) {
            f.add("a built shrine should be seen by those who stopped the heart: " + shrines);
        }
        shrines.clear();
        MapMarks.shrineMarks(built::get, t -> false, shrines);
        if (!shrines.isEmpty()) {
            f.add("the shrine should not show before its heart is stopped: " + shrines);
        }
        // 8. キー: 地図は M、合図は別のキー
        if (KeyDefaults.MAP != 77 || KeyDefaults.PING == KeyDefaults.MAP) {
            f.add("the travel map should be on M and the ping elsewhere");
        }
        SkyAbyss.LOGGER.info("SELFTEST travel map: probe sheet has {} marks, center {} {}", sheet.marks().size(), sheet.centerX(), sheet.centerZ());
    }
}
