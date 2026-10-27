package jp.revati.skyabyss.titan.storm.chapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.guide.SiteTreasure;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteJob;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.tablet.Tablets;
import jp.revati.skyabyss.titan.storm.back.StormBackSetup;
import jp.revati.skyabyss.titan.storm.back.StormBlocks;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 避雷の柱 (docs/design/34 の 4.4、支度の段 ch3/lightning_pillars)。舞台は避雷の塔の跡 (StormSites.RUIN、部品 ch3_rod_tower)。
//
//   台     塔のまわりの斜めの 4 か所 (印 pedestal0..3 = 針を立てる所。銅の飾り台の上)。バニラの避雷針を立てると、そこへ演出の雷が落ちる
//   戸     四つの台すべてに針が立つと、塔の根の銅の戸 (印 door0..8) が開く (世界に一つ。一度開けば残る)。
//          奥の石の箱 (印 vault_pack。樽) に風の民の書 (伝承 ch3/stayer_returns。碑 sign.skyabyss.ch3_vault を読んでも記される) と、人ごとに一度の銅 4
//   数え方 段 raise にいる人が塔の跡から 24 以内にいる間、針の立っている台一つにつき一度 (人ごと。Growth の発見 ch3_pillar:<k>) 合図 ch3_pillar。
//          四つそろうと伝承 ch3/pillars。先に誰かが立てていれば、着いた人にもそのまま数える (立て直さなくてよい。銅を集める理由は台より先に段の文が持つ)
//   見本   東の隣の見本の柱 (印 sample_bulb・sample_tip。見かけは背中の避雷の柱) に、人が 32 以内にいる間 60 秒に一度、雷が落ちて電球が光る
//   旧い跡 部品が ch3_ruin (台も戸もない) の世界: 塔の跡のまわり 8 以内に立っている避雷針を、針の立つ台の数として数え (柱の 1 本は除く)、
//          四本で戸の代わりに箱の中身 (書と銅) をその場の人へ渡す
public final class LightningPillars {
    public static final String SITE = StormSites.RUIN;
    public static final String BLUEPRINT = "ch3_rod_tower";
    public static final int STANDS = 4;
    public static final int DOOR_CELLS = 9;
    public static final String VAULT_TREASURE = "ch3_vault_pack";
    public static final String VAULT_SIGN = "sign.skyabyss.ch3_vault";
    public static final double NEAR = 24;
    public static final double SAMPLE_NEAR = 32;
    public static final int SAMPLE_EVERY = 1200;
    static final int EVERY = 20;
    // 旧い跡で針を探す範囲
    static final int LEGACY_REACH = 8;

    // 塔の跡 (基点) → 前の見回りで針の立っていた台 (新しく立った台に雷を落とすため。保存しない)
    private static final Map<Long, Integer> LAST_MASK = new HashMap<>();
    private static final Map<Long, Long> LAST_SAMPLE = new HashMap<>();

    private LightningPillars() {
    }

    static void register() {
        SiteTreasure.register(VAULT_TREASURE, SiteTreasure.Table.of(List.of(new SiteTreasure.Entry("skyabyss:copper_lump", 4),
                new SiteTreasure.Entry(Ch3Mobs.COPPER_WIRE, 4))));
    }

    // ---------------------------------------------------------------- 建て終わり (StormSites の RUIN の post)
    static void finish(SiteJob job) {
        List<String> names = new ArrayList<>(List.of("rod", "vault_pack", "vault_tablet", "sample_bulb", "sample_tip"));
        for (int k = 0; k < STANDS; k++) {
            names.add("pedestal" + k);
        }
        for (int k = 0; k < DOOR_CELLS; k++) {
            names.add("door" + k);
        }
        for (String name : names) {
            BlockPos at = job.mark(name);
            if (at != null) {
                job.entry.extra(name, at);
            }
        }
        BlockPos tablet = job.mark("vault_tablet");
        if (tablet != null) {
            job.set(tablet, Tablets.standingState().setValue(jp.revati.skyabyss.relic.RelicStyle.PROP, jp.revati.skyabyss.relic.RelicStyle.STORM));
            Tablets.write(job.level, tablet, VAULT_SIGN);
        }
    }

    public static List<SiteData.Entry> entries(MinecraftServer server) {
        List<SiteData.Entry> list = new ArrayList<>();
        for (SiteData.Entry e : SiteData.get(server.overworld()).allEntries()) {
            if (e.siteId.equals(SITE) && e.built) {
                list.add(e);
            }
        }
        return list;
    }

    // 台と戸のある (ch3_rod_tower で建てた) 跡か
    public static boolean modern(SiteData.Entry e) {
        return e.extra("pedestal0") != null;
    }

    @Nullable
    public static BlockPos stand(SiteData.Entry e, int k) {
        return e.extra("pedestal" + k);
    }

    // 針の立っている台 (ビット k = 台 k)。旧い跡は、立っている針の数だけ下から埋める
    public static int mask(ServerLevel level, SiteData.Entry e) {
        if (modern(e)) {
            int m = 0;
            for (int k = 0; k < STANDS; k++) {
                BlockPos at = stand(e, k);
                if (at != null && level.isLoaded(at) && level.getBlockState(at).is(Blocks.LIGHTNING_ROD)) {
                    m |= 1 << k;
                }
            }
            return m;
        }
        int n = legacyRods(level, e).size();
        return (1 << Math.min(STANDS, n)) - 1;
    }

    static List<BlockPos> legacyRods(ServerLevel level, SiteData.Entry e) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos o = e.pos;
        for (BlockPos q : BlockPos.betweenClosed(o.offset(-LEGACY_REACH, -3, -LEGACY_REACH), o.offset(LEGACY_REACH, 8, LEGACY_REACH))) {
            if (q.getX() == o.getX() && q.getZ() == o.getZ()) {
                continue; // 跡に残っている柱の針
            }
            if (level.isLoaded(q) && level.getBlockState(q).is(Blocks.LIGHTNING_ROD)) {
                out.add(q.immutable());
            }
        }
        return out;
    }

    public static boolean doorOpen(ServerLevel level, SiteData.Entry e) {
        if (!modern(e)) {
            return true;
        }
        for (int k = 0; k < DOOR_CELLS; k++) {
            BlockPos at = e.extra("door" + k);
            if (at != null && level.isLoaded(at) && level.getBlockState(at).is(Blocks.WAXED_OXIDIZED_CUT_COPPER)) {
                return false;
            }
        }
        return true;
    }

    // 戸を開ける (開いたら true)。雷と銅の軋み
    public static boolean openDoor(ServerLevel level, SiteData.Entry e) {
        if (doorOpen(level, e)) {
            return false;
        }
        for (int k = 0; k < DOOR_CELLS; k++) {
            BlockPos at = e.extra("door" + k);
            if (at != null && level.getBlockState(at).is(Blocks.WAXED_OXIDIZED_CUT_COPPER)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        BlockPos c = e.extra("door4");
        if (c != null) {
            level.playSound(null, c, SoundEvents.COPPER_DOOR_OPEN, SoundSource.BLOCKS, 1.2f, 0.5f);
            level.playSound(null, c, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0f, 0.6f);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 40, 1.0, 1.2, 0.3, 0.05);
        }
        return true;
    }

    // 演出の雷 (燃やさない・傷つけない・避雷針を動かさない)
    public static void strike(ServerLevel level, BlockPos at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5, 24, 0.3, 0.6, 0.3, 0.08);
    }

    // ---------------------------------------------------------------- 見回り (StormChapter の tick から EVERY ごとに)
    static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        for (SiteData.Entry e : entries(server)) {
            if (!level.isLoaded(e.pos)) {
                continue;
            }
            List<ServerPlayer> near = near(level, e.pos, SAMPLE_NEAR);
            if (near.isEmpty()) {
                continue;
            }
            sample(level, e, now);
            int m = mask(level, e);
            long key = e.pos.asLong();
            int before = LAST_MASK.getOrDefault(key, m);
            LAST_MASK.put(key, m);
            int fresh = m & ~before;
            List<ServerPlayer> close = near(level, e.pos, NEAR);
            for (int k = 0; k < STANDS; k++) {
                if ((fresh & (1 << k)) != 0) {
                    BlockPos at = modern(e) ? stand(e, k) : null;
                    strike(level, at != null ? at : e.pos);
                    close.forEach(p -> Notice.narrate(p, "message.skyabyss.ch3g.ruin.strike"));
                }
            }
            if (m == (1 << STANDS) - 1 && modern(e) && openDoor(level, e)) {
                close.forEach(p -> Notice.narrate(p, "message.skyabyss.ch3g.ruin.open"));
            }
            for (ServerPlayer p : close) {
                credit(p, level, e, m);
            }
        }
    }

    // 人ごとの数え (段 raise の間だけ)。旧い跡は四本で書と銅を渡す
    public static void credit(ServerPlayer p, ServerLevel level, SiteData.Entry e, int m) {
        String stage = stageOf(p, Ch3Ground.Q_PILLARS);
        if ("raise".equals(stage)) {
            for (int k = 0; k < STANDS; k++) {
                if ((m & (1 << k)) != 0 && Growth.discover(p, "ch3_pillar:" + k)) {
                    Quests.signal(p, Ch3Ground.SIGNAL_PILLAR);
                }
            }
        }
        if (m == (1 << STANDS) - 1) {
            Lore.unlock(p, Ch3Ground.PAGE_PILLARS, true);
            if (!modern(e) && ("raise".equals(stage) || "vault".equals(stage)) && !Lore.state(p).has(Ch3Ground.PAGE_STAYER)) {
                openVault(p, level, null);
            }
        }
    }

    // その人のクエストの今の段の名前 (受けていない・終えたなら null)
    @Nullable
    static String stageOf(ServerPlayer p, String questId) {
        QuestLog.Entry entry = QuestLog.of(p).entry(questId);
        QuestDef def = QuestManager.quest(questId);
        if (entry == null || entry.done || def == null || entry.stage >= def.stages().size()) {
            return null;
        }
        return def.stages().get(entry.stage).id();
    }

    // 石の箱: 人ごとに一度の銅 4。書 (伝承) は何度でも記される
    public static boolean openVault(ServerPlayer player, ServerLevel level, @Nullable BlockPos at) {
        boolean first = !SiteTreasure.claimed(player, VAULT_TREASURE);
        if (first) {
            SiteTreasure.claim(player, VAULT_TREASURE);
            Notice.narrate(player, "message.skyabyss.ch3g.ruin.vault");
        } else {
            Notice.narrate(player, "message.skyabyss.ch3g.ruin.vault_empty");
        }
        if (at != null) {
            level.playSound(null, at, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.8f, 0.8f);
        }
        Lore.unlock(player, Ch3Ground.PAGE_STAYER, true);
        return first;
    }

    @Nullable
    static SiteData.Entry vaultAt(MinecraftServer server, BlockPos pos) {
        for (SiteData.Entry e : entries(server)) {
            if (pos.equals(e.extra("vault_pack"))) {
                return e;
            }
        }
        return null;
    }

    // 見本の柱: 60 秒に一度、雷が落ちて電球が 4 秒光る
    static void sample(ServerLevel level, SiteData.Entry e, long now) {
        BlockPos tip = e.extra("sample_tip");
        BlockPos bulb = e.extra("sample_bulb");
        if (tip == null || bulb == null) {
            return;
        }
        long key = e.pos.asLong();
        Long last = LAST_SAMPLE.get(key);
        if (last == null) {
            LAST_SAMPLE.put(key, now - SAMPLE_EVERY + 200);
            return;
        }
        if (now - last < SAMPLE_EVERY) {
            return;
        }
        LAST_SAMPLE.put(key, now);
        strike(level, tip);
        bulb(level, bulb, true);
        StormChapter.later(level.getServer(), 80, () -> bulb(level, bulb, false));
    }

    static void bulb(ServerLevel level, BlockPos at, boolean lit) {
        BlockState s = level.getBlockState(at);
        if (s.is(StormBackSetup.ROD_BULB.get()) && s.getValue(StormBlocks.RodBulb.LIT) != lit) {
            level.setBlock(at, s.setValue(StormBlocks.RodBulb.LIT, lit), 3);
        }
    }

    static List<ServerPlayer> near(ServerLevel level, BlockPos at, double r) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (!p.isSpectator() && p.isAlive() && p.distanceToSqr(at.getX() + 0.5, p.getY(), at.getZ() + 0.5) <= r * r) {
                out.add(p);
            }
        }
        return out;
    }

    static void forget() {
        LAST_MASK.clear();
        LAST_SAMPLE.clear();
    }
}
