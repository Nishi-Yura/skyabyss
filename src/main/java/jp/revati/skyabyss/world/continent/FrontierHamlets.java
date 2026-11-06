package jp.revati.skyabyss.world.continent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.AttendantRole;
import jp.revati.skyabyss.facility.Attendants;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.SiteBuilds;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteJob;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 外れの集落 (2026-11-06。docs/design/40 の 3 章の 4。案の頁「大陸の外れ」の m_hamlets = a・「外れの集落の形」の h_shape = d)。
// layout.json の frontier (kind hamlet) の 8 か所に、宿・道具屋・家・井戸・依頼の板の小さな集落を建てる。部品は opening/hamlet_<鍵>.json
// (tools/capital/frontier_hamlet.py。土地ごとに、奥のダンジョンに近いほうが柵の砦村、もう一つが目印の里)。SiteData の種類は "hamlet"、番号 = 置き場の並び。
//  - 人が近く (BUILD_RADIUS) に来たら場所を決め (正面 = 一番近い道)、見どころと同じく少しずつ建てる。大陸の作り直しは要らない
//  - 建てたら印を場所の付属の点に写し (rest・board・inn・shop・stand・sign・folk_a・folk_b・door・inn_bed・npc_<役>・label_<施設>)、
//    札に集落の名前と目安のレベル、口 (door) から一番近い道まで小道を敷く
//  - 近く (KEEP_RADIUS) に人がいれば、宿の主人と道具屋を立たせ続ける。町と同じ守り (SiteGuard.inTown: 壊せない・置けない・敵が湧かない)
//  - 入ると名前の札・訪れた集落を覚える (ContinentAreas。名前は map.skyabyss.town.hamlet_<鍵>)。倒れたら、訪れた集落の宿の前でも起きる (ContinentRespawn)。
//    宿の主人の「休む」は、いちばん近い宿の寝台 (ContinentTowns.innBeds が集落の寝台も数える)。地図にはいつも町の印で出す (MapMarks)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class FrontierHamlets {
    public static final String KIND = "hamlet";
    static final int BUILD_RADIUS = 240;
    static final int KEEP_RADIUS = 96;
    static final int CHECK = 40;
    static final String[] MARKS = {"rest", "board", "inn", "shop", "stand", "folk_a", "folk_b", "door", Attendants.INN_BED};
    // 口から道までの小道の半幅
    static final int PATH_HALF = 1;

    private FrontierHamlets() {
    }

    public static List<ContinentLayout.Frontier> spots() {
        return ContinentLayout.get().hamlets();
    }

    @Nullable
    public static ContinentLayout.Frontier spot(int index) {
        List<ContinentLayout.Frontier> all = spots();
        return index >= 0 && index < all.size() ? all.get(index) : null;
    }

    // 町と同じ名前の鍵の名前 (map.skyabyss.town.<これ>・訪れた所の記録・地図の印)
    public static String townId(int index) {
        ContinentLayout.Frontier f = spot(index);
        return f == null ? "hamlet" : "hamlet_" + f.key();
    }

    public static String blueprint(int index) {
        ContinentLayout.Frontier f = spot(index);
        return f == null ? "" : "hamlet_" + f.key();
    }

    @Nullable
    static SiteData.Entry entry(SiteData data, int index) {
        for (SiteData.Entry e : data.entries(KIND)) {
            if (e.index == index) {
                return e;
            }
        }
        return null;
    }

    static boolean continent(ServerLevel over) {
        return over.getChunkSource().getGenerator() instanceof ContinentChunkGenerator;
    }

    // ---------------------------------------------------------------- 建てる
    public static SiteJob create(ServerLevel level, SiteData data, SiteData.Entry entry) {
        ContinentLayout.Frontier f = spot(entry.index);
        String name = blueprint(entry.index);
        Blueprint bp = Blueprint.get(name);
        return new SiteJob(level, data, entry, bp, "town " + name, job -> {
            for (String m : MARKS) {
                BlockPos at = job.mark(m);
                if (at != null) {
                    job.entry.extra(m, at);
                }
            }
            if (bp != null) {
                for (String m : bp.marks.keySet()) {
                    if ((m.startsWith(AttendantRole.MARK_PREFIX) && !m.equals(ContinentTowns.CHASER_MARK)) || m.startsWith(Attendants.INN_BED) || m.startsWith("label_")) {
                        BlockPos at = job.mark(m);
                        if (at != null) {
                            job.entry.extra(m, at);
                        }
                    }
                }
            }
            BlockPos sign = job.mark("sign");
            if (sign != null && f != null && level.getBlockEntity(sign) instanceof net.minecraft.world.level.block.entity.SignBlockEntity be) {
                ContinentLayout.Level lv = ContinentLayout.get().levels().get(f.region());
                be.setText(new net.minecraft.world.level.block.entity.SignText()
                        .setMessage(1, Component.translatable("map.skyabyss.town." + townId(entry.index)))
                        .setMessage(2, Component.literal(lv == null ? "" : "Lv " + lv.text())), true);
                be.setWaxed(true);
                be.setChanged();
                level.sendBlockUpdated(sign, be.getBlockState(), be.getBlockState(), 3);
            }
            for (String kind : new String[] {"inn", "shop"}) {
                BlockPos at = job.mark("sign_" + kind);
                if (at != null && level.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.SignBlockEntity be) {
                    be.setText(new net.minecraft.world.level.block.entity.SignText().setMessage(1, Component.translatable("sign.skyabyss.town_" + kind)), true);
                    be.setWaxed(true);
                    be.setChanged();
                    level.sendBlockUpdated(at, be.getBlockState(), be.getBlockState(), 3);
                }
            }
            BlockPos door = job.mark("door");
            if (door != null) {
                int n = pathToRoad(level, door);
                SkyAbyss.LOGGER.info("Frontier hamlet {}: {} block(s) of path from the door to the road", name, n);
            }
        });
    }

    // 口から一番近い道の点まで、地面に土の小道を敷く (幅 PATH_HALF × 2 + 1)。草・土・砂・砂利・雪の上だけ。敷いた数
    static int pathToRoad(ServerLevel level, BlockPos door) {
        BlockPos road = nearestRoad(door.getX(), door.getZ());
        if (road == null) {
            return 0;
        }
        double dx = road.getX() - door.getX();
        double dz = road.getZ() - door.getZ();
        int steps = (int) Math.ceil(Math.hypot(dx, dz));
        if (steps > 40) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i <= steps; i++) {
            int cx = (int) Math.round(door.getX() + dx * i / Math.max(1, steps));
            int cz = (int) Math.round(door.getZ() + dz * i / Math.max(1, steps));
            for (int ox = -PATH_HALF; ox <= PATH_HALF; ox++) {
                for (int oz = -PATH_HALF; oz <= PATH_HALF; oz++) {
                    int x = cx + ox;
                    int z = cz + oz;
                    level.getChunk(x >> 4, z >> 4);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    BlockPos at = new BlockPos(x, y, z);
                    BlockState s = level.getBlockState(at);
                    if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.SAND) || s.is(Blocks.RED_SAND)
                            || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.MYCELIUM) || s.is(Blocks.ROOTED_DIRT)) {
                        if (!level.getBlockState(at.above()).isAir()) {
                            BlockState up = level.getBlockState(at.above());
                            if (up.canBeReplaced()) {
                                level.setBlock(at.above(), Blocks.AIR.defaultBlockState(), 2);
                            } else {
                                continue;
                            }
                        }
                        level.setBlock(at, (x + z) % 5 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState(), 2);
                        n++;
                    }
                }
            }
        }
        return n;
    }

    // 図の道の上で、(x, z) に一番近い点 (線分の上)。道がなければ null
    @Nullable
    static BlockPos nearestRoad(int x, int z) {
        double best = Double.MAX_VALUE;
        BlockPos out = null;
        for (List<BlockPos> road : ContinentLayout.get().roads()) {
            for (int k = 0; k + 1 < road.size(); k++) {
                BlockPos a = road.get(k);
                BlockPos b = road.get(k + 1);
                double dx = b.getX() - a.getX();
                double dz = b.getZ() - a.getZ();
                double len2 = Math.max(1e-6, dx * dx + dz * dz);
                double t = Math.max(0, Math.min(1, ((x - a.getX()) * dx + (z - a.getZ()) * dz) / len2));
                double px = a.getX() + dx * t;
                double pz = a.getZ() + dz * t;
                double d = (px - x) * (px - x) + (pz - z) * (pz - z);
                if (d < best) {
                    best = d;
                    out = new BlockPos((int) Math.round(px), 0, (int) Math.round(pz));
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 見回り: 近づいたら建てる・係の人を立たせる
    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        ServerLevel over = event.getServer().overworld();
        if (over.getGameTime() % CHECK != 13 || over.players().isEmpty() || !continent(over)) {
            return;
        }
        SiteData data = SiteData.get(over);
        if (!data.planned()) {
            return;
        }
        List<ContinentLayout.Frontier> all = spots();
        for (ServerPlayer p : over.players()) {
            if (p.isSpectator()) {
                continue;
            }
            for (int i = 0; i < all.size(); i++) {
                ContinentLayout.Frontier f = all.get(i);
                double d2 = p.distanceToSqr(f.pos().getX() + 0.5, p.getY(), f.pos().getZ() + 0.5);
                if (d2 > (double) BUILD_RADIUS * BUILD_RADIUS) {
                    continue;
                }
                SiteData.Entry e = entry(data, i);
                if (e == null) {
                    int x = f.pos().getX();
                    int z = f.pos().getZ();
                    int y = ContinentMap.get().surface(x, z) + 1;
                    e = data.add(KIND, i, new BlockPos(x, y, z), ContinentPois.faceRoad(x, z));
                    e.blueprint = blueprint(i);
                    data.setDirty();
                }
                if (!e.built) {
                    if (!SiteBuilds.queued(e)) {
                        SiteBuilds.enqueue(create(over, data, e));
                    }
                    continue;
                }
                if (d2 <= (double) KEEP_RADIUS * KEEP_RADIUS) {
                    Map<String, BlockPos> marks = new LinkedHashMap<>();
                    for (int k = 0; k < e.extraNames.size(); k++) {
                        if (e.extraNames.get(k).startsWith(AttendantRole.MARK_PREFIX)) {
                            marks.put(e.extraNames.get(k), e.extraPos.get(k));
                        }
                    }
                    Attendants.ensureAll(over, marks);
                    jp.revati.skyabyss.opening.site.FacilityLabels.keep(over, e, true);
                }
            }
        }
    }

    // ---------------------------------------------------------------- ほかの仕組みから
    // 建った集落の宿の寝台
    public static List<BlockPos> innBeds(MinecraftServer server) {
        List<BlockPos> out = new ArrayList<>();
        for (SiteData.Entry e : SiteData.get(server.overworld()).entries(KIND)) {
            BlockPos b = e.extra(Attendants.INN_BED);
            if (e.built && b != null) {
                out.add(b);
            }
        }
        return out;
    }

    // 建った集落の休み場 (起き上がる所)。who が訪れた集落だけ (null なら全部)
    public static List<BlockPos> rests(ServerLevel over, @Nullable ServerPlayer who) {
        List<BlockPos> out = new ArrayList<>();
        for (SiteData.Entry e : SiteData.get(over).entries(KIND)) {
            BlockPos r = e.extra("rest");
            if (e.built && r != null && (who == null || ContinentAreas.visited(who, townId(e.index)))) {
                out.add(r);
            }
        }
        return out;
    }

    // (x, z) が集落の中 (中心から r 以内) なら、その置き場の番号。なければ -1
    public static int at(int x, int z, int r) {
        List<ContinentLayout.Frontier> all = spots();
        for (int i = 0; i < all.size(); i++) {
            BlockPos c = all.get(i).pos();
            if (Math.hypot(x - c.getX(), z - c.getZ()) <= r) {
                return i;
            }
        }
        return -1;
    }
}
