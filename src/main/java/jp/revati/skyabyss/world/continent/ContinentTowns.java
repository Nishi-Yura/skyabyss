package jp.revati.skyabyss.world.continent;

import java.util.LinkedHashMap;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.AttendantRole;
import jp.revati.skyabyss.facility.Attendants;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteJob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 天と淵の大陸の、土地ごとの小さな町 (docs/design/40 の 3 章「作る順」の 4)。広場 (井戸・依頼の板・街灯)・宿・店・家二つ。
// 場所は layout.json の towns。大陸の世界でだけ、場所を決めるとき (SiteFinder.planRestAll) に足し、野営地や都と同じく少しずつ建てる。
// 部品は data/skyabyss/opening/town_<土地>.json (tools/gen/world/gen_region_towns.py)。SiteData の種類は "town"、番号は REGIONS の並び。
// 建てたら部品の印 (rest・board・npc_<役>) を場所の付属の点に写し、札に町の名前と目安のレベルを書く。近く (KEEP_RADIUS) に人がいれば、宿の主人と道具屋を立たせ続ける
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ContinentTowns {
    public static final String KIND = "town";
    public static final String[] REGIONS = {"whale", "tide", "storm", "ember"};
    // 鯨追いの立つ所 (鯨見の町だけ。00 の 278)
    public static final String CHASER_MARK = "npc_chaser";
    static final String[] MARKS = {"rest", "board", "inn", "shop", "stand", CHASER_MARK, "folk_a", "folk_b", Attendants.INN_BED, AttendantRole.INNKEEPER.mark(), AttendantRole.TOOL_MERCHANT.mark(),
            AttendantRole.SMITH.mark(), AttendantRole.APOTHECARY.mark()};
    // 町の作りの版。2 = 都と同じ作りの壁のある町 (tools/capital/region_town.py。2026-10-26 の声「始まりの町とそれ以外の町はクオリティ違いすぎる」)。
    // 前の版で建った町は、建て直す (建てたら付属の点 VERSION_MARK を書く)
    public static final String VERSION_MARK = "town_v2";
    static final int KEEP_RADIUS = 96;
    static final int CHECK_INTERVAL = 100;

    private ContinentTowns() {
    }

    public static String region(int index) {
        return REGIONS[Math.max(0, Math.min(REGIONS.length - 1, index))];
    }

    public static SiteJob create(ServerLevel level, SiteData data, SiteData.Entry entry) {
        String region = region(entry.index);
        Blueprint bp = Blueprint.get("town_" + region);
        return new SiteJob(level, data, entry, bp, "town " + region, job -> {
            for (String m : MARKS) {
                BlockPos at = job.mark(m);
                if (at != null) {
                    job.entry.extra(m, at);
                }
            }
            // 施設の浮く名札 (都と同じ。opening/site/ShelterBuilder.keepLabels が近くの人のために置き直す)
            if (bp != null) {
                for (String m : bp.marks.keySet()) {
                    if (m.startsWith("label_")) {
                        BlockPos at = job.mark(m);
                        if (at != null) {
                            job.entry.extra(m, at);
                        }
                    }
                }
            }
            job.entry.extra(VERSION_MARK, job.entry.pos);
            BlockPos sign = job.mark("sign");
            if (sign != null && level.getBlockEntity(sign) instanceof net.minecraft.world.level.block.entity.SignBlockEntity be) {
                ContinentLayout.Level lv = ContinentLayout.get().levels().get(region);
                net.minecraft.world.level.block.entity.SignText text = new net.minecraft.world.level.block.entity.SignText()
                        .setMessage(1, net.minecraft.network.chat.Component.translatable("map.skyabyss.town." + region))
                        .setMessage(2, net.minecraft.network.chat.Component.literal(lv == null ? "" : "Lv " + lv.text()));
                be.setText(text, true);
                be.setWaxed(true);
                be.setChanged();
                level.sendBlockUpdated(sign, be.getBlockState(), be.getBlockState(), 3);
            }
            // 町の気配: 広場のまわりに猫二匹と鶏三羽 (建てたときに一度だけ。消えない)
            BlockPos stand = job.mark("stand");
            if (stand != null) {
                net.minecraft.world.entity.EntityType<?>[] kinds = {net.minecraft.world.entity.EntityType.CAT, net.minecraft.world.entity.EntityType.CAT,
                        net.minecraft.world.entity.EntityType.CHICKEN, net.minecraft.world.entity.EntityType.CHICKEN, net.minecraft.world.entity.EntityType.CHICKEN};
                for (int k = 0; k < kinds.length; k++) {
                    double a = k * 1.3 + 0.4;
                    int x = stand.getX() + (int) Math.round(Math.cos(a) * 7);
                    int z = stand.getZ() + (int) Math.round(Math.sin(a) * 7);
                    int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (kinds[k].create(level) instanceof net.minecraft.world.entity.Mob m) {
                        m.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360f, 0f);
                        m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), net.minecraft.world.entity.MobSpawnType.STRUCTURE, null);
                        m.setPersistenceRequired();
                        level.addFreshEntity(m);
                    }
                }
            }
            // 宿と道具屋の戸の上の看板
            for (String kind : new String[] {"inn", "shop"}) {
                BlockPos at = job.mark("sign_" + kind);
                if (at != null && level.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.SignBlockEntity be) {
                    be.setText(new net.minecraft.world.level.block.entity.SignText()
                            .setMessage(1, net.minecraft.network.chat.Component.translatable("sign.skyabyss.town_" + kind)), true);
                    be.setWaxed(true);
                    be.setChanged();
                    level.sendBlockUpdated(at, be.getBlockState(), be.getBlockState(), 3);
                }
            }
        });
    }

    // 前の版 (広場と宿と店と家二つの小さな町) で建った町を、建て直しの列に戻す。建て直しは SiteBuilds が近くに人が来たときに進める
    @SubscribeEvent
    static void onStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        ServerLevel over = event.getServer().overworld();
        SiteData data = SiteData.get(over);
        int n = 0;
        for (SiteData.Entry e : data.entries(KIND)) {
            if (e.built && e.extra(VERSION_MARK) == null) {
                e.built = false;
                e.started = false;
                e.extraNames.clear();
                e.extraPos.clear();
                n++;
            }
        }
        if (n > 0) {
            data.setDirty();
            SkyAbyss.LOGGER.info("Region towns: {} town(s) from the old plan will be rebuilt", n);
        }
    }

    // 土地の町の宿の寝台 (建ってあるもの)。宿の主人に「休む」と言うと、いちばん近い宿の寝台で目覚めるようになる (FacilityActions.innBed)
    public static java.util.List<BlockPos> innBeds(net.minecraft.server.MinecraftServer server) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (SiteData.Entry e : SiteData.get(server.overworld()).entries(KIND)) {
            BlockPos b = e.extra(Attendants.INN_BED);
            if (e.built && b != null) {
                out.add(b);
            }
        }
        return out;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD || (level.getGameTime() + (player.getId() & 0x3F) + 37) % CHECK_INTERVAL != 0) {
            return;
        }
        for (SiteData.Entry e : SiteData.get(level).entries(KIND)) {
            if (!e.built || e.pos.distSqr(player.blockPosition()) > (double) KEEP_RADIUS * KEEP_RADIUS) {
                continue;
            }
            Map<String, BlockPos> marks = new LinkedHashMap<>();
            for (int i = 0; i < e.extraNames.size(); i++) {
                if (e.extraNames.get(i).startsWith(AttendantRole.MARK_PREFIX)) {
                    marks.put(e.extraNames.get(i), e.extraPos.get(i));
                }
            }
            Attendants.ensureAll(level, marks);
            jp.revati.skyabyss.opening.site.ShelterBuilder.keepLabels(level, e);
            // 土地の町の人 (town/RegionFolk。二〜四章の町に二人ずつ)
            for (String fm : new String[] {"folk_a", "folk_b"}) {
                BlockPos at = e.extra(fm);
                if (at != null) {
                    jp.revati.skyabyss.town.RegionFolk.ensure(level, region(e.index), fm, at);
                }
            }
            BlockPos chaser = e.extra(CHASER_MARK);
            if (chaser != null) {
                jp.revati.skyabyss.npc.NpcSpawner.keepTownChaser(level, chaser, player);
            }
        }
    }

    // 鯨見の町の鯨追いの立つ所 (建っていなければ null)
    @org.jetbrains.annotations.Nullable
    public static BlockPos chaserSpot(net.minecraft.server.MinecraftServer server) {
        for (SiteData.Entry e : SiteData.get(server.overworld()).entries(KIND)) {
            BlockPos at = e.extra(CHASER_MARK);
            if (e.built && at != null) {
                return at;
            }
        }
        return null;
    }
}
