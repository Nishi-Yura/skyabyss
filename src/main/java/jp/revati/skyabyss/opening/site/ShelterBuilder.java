package jp.revati.skyabyss.opening.site;

import jp.revati.skyabyss.SkyAbyss;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

// 鐘の宿り (17 の 4.2、語り部の家)。小屋 1 つ、柵の庭、バニラの鐘 (POI "meeting" になるので、NpcSpawner が語り部を立たせる)、
// わら束の上の煙の出る焚き火 (遠くからの目印)。庭の入口 (南向きの部品) は野営地の方へ向く
public final class ShelterBuilder {
    private ShelterBuilder() {
    }

    public static SiteJob create(ServerLevel level, SiteData data, SiteData.Entry entry) {
        // 鐘の宿りは「絶えかけの国の都」(docs/design/33 の 2)。小屋の設計図 (shelter) は SKYABYSS_SMALL_SHELTER=1 のときだけ (古い台本の撮影向け)
        String name = System.getenv("SKYABYSS_SMALL_SHELTER") != null ? "shelter" : "capital";
        return new SiteJob(level, data, entry, Blueprint.get(name), "shelter", ShelterBuilder::finish);
    }

    static void finish(SiteJob job) {
        BlockPos bell = job.mark("bell");
        if (bell == null || !job.level.getBlockState(bell).is(Blocks.BELL)) {
            SkyAbyss.LOGGER.error("Opening shelter: no bell at {}", bell);
        } else {
            job.entry.extra("bell", bell);
        }
        BlockPos smoke = job.mark("smoke");
        if (smoke != null) {
            job.entry.extra("smoke", smoke);
        }
        BlockPos stand = job.mark("stand");
        if (stand != null) {
            job.entry.extra("stand", stand);
        }
        BlockPos door = job.mark("door");
        if (door != null) {
            job.entry.extra("door", door);
        }
        // 都: 門・閉じた施設・空き区画の碑と、市と住まいの村人 (docs/design/33 の 2.2)
        // 施設の碑 (docs/design/35 の 2.1): 道具屋・薬師・預かり所・宿・止まり木・訓練場・闇市・地図屋・酒場・古井戸
        for (String key : List.of("gate_n", "gate_e", "gate_s", "gate_w", "keep", "hall", "guild", "shrine", "smithy", "lot_1", "lot_2", "lot_3", "lot_4", "lot_5", "graveyard", "guild_roll",
                "tool_shop", "apothecary", "storage", "inn", "perch", "training", "black_market", "mapmaker", "tavern", "well")) {
            BlockPos at = job.mark("sign_" + key);
            if (at != null && job.level.getBlockState(at).is(jp.revati.skyabyss.registry.ModBlocks.TABLET.get())) {
                SiteKit.writeSign(job.level, at, "sign.skyabyss.capital." + (key.startsWith("lot_") ? "lot" : key));
            }
        }
        // 章で開く施設の戸の印と碑の印 (CapitalDoors が読む)
        for (String key : List.of("open_guild", "open_shrine", "open_keep", "open_keep2", "open_hall", "open_training",
                "sign_guild", "sign_shrine", "sign_keep", "sign_hall", "sign_training", "chaser", "altar",
                "stone_stone_whale", "stone_tide_shell", "stone_thunder_wing", "stone_ember",
                // あとで町のクエスト・裏通りの仕組みが読む置き場 (35 の 3.3・3.5): 止まり木の台、古井戸の格子、酒場の地下の奥の壁
                "perch", "old_well", "cellar_wall")) {
            BlockPos at = job.mark(key);
            if (at != null) {
                job.entry.extra(key, at);
            }
        }
        // 町のクエストの家 (大通りの 1 列目の入れる家) quest_house_<n>
        for (String key : job.bp.marks.keySet()) {
            if (key.startsWith("quest_house_")) {
                BlockPos at = job.mark(key);
                if (at != null) {
                    job.entry.extra(key, at);
                }
            }
        }
        // 都の言伝ての板は組合の中 (設計図が置く)。鐘のそばに自動で立てる板は出さない
        if (job.mark("altar") != null && job.mark("bell") != null) {
            jp.revati.skyabyss.quest.free.FreeQuestData.get(job.level.getServer().overworld()).markBoard(job.mark("bell").asLong());
        }
        // 都の施設の係の人 (npc_<役>) と宿の寝台 (inn_bed*) の印を写し、係の人を立たせる (docs/design/35 の 6 章。印がなければ何もしない)
        java.util.Map<String, BlockPos> facility = new java.util.LinkedHashMap<>();
        for (String key : job.bp.marks.keySet()) {
            if (jp.revati.skyabyss.facility.Attendants.ours(key)) {
                BlockPos at = job.mark(key);
                if (at != null) {
                    job.entry.extra(key, at);
                    facility.put(key, at);
                }
            }
        }
        if (!facility.isEmpty()) {
            int placed = jp.revati.skyabyss.facility.Attendants.ensureAll(job.level, facility);
            SkyAbyss.LOGGER.info("Opening capital: {} facility mark(s), {} attendant(s) placed", facility.size(), placed);
        }
        int villagers = 0;
        for (int i = 1; i <= 10; i++) {
            BlockPos at = job.mark("villager_" + i);
            if (at == null) {
                continue;
            }
            net.minecraft.world.entity.npc.Villager v = net.minecraft.world.entity.EntityType.VILLAGER.create(job.level);
            if (v == null) {
                continue;
            }
            v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, job.level.random.nextFloat() * 360f, 0f);
            v.setPersistenceRequired();
            job.level.addFreshEntity(v);
            villagers++;
        }
        SkyAbyss.LOGGER.info("Opening capital: {} villager(s) placed", villagers);
    }
}
