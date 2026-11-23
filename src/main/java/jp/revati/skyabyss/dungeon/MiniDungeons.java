package jp.revati.skyabyss.dungeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.SiteSpawner;
import jp.revati.skyabyss.guide.SiteTreasure;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteFinder;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 章の跡地の小ダンジョン (2026-11-07 の声「メインクエストのほんとに小さいダンジョン、もっと作り込んで。誰でもいつでも行けるので、壊れる物が最初から壊れている。
// 敵も少なく主も不定期。中ダンジョンのように tp させる方がいい。外へ出るときの梯子と戸の問題も消える」。頁の選択: 中ダンジョン式・部屋 3 + 主の間・謎解きはなくして全滅で開く)。
//   地上   章の場所の要素 (ChapterSites) はそのまま (建物・碑・着いたときの一言・職人の立つ所)。前の版の地下の部屋 (部品の y -1 より下の空き・階段・梯子・樽・戸) は
//          その土地の石で埋め、口に門の石 (天然磁石。名札「〜の門」) を置く。塔の二つ (避雷の塔の跡・焼けた見張り塔) は地下がないので、前の立つ所に門の石だけ
//   入る   門の石を右クリック → 組ごとの写し (dungeon/Dungeons、次元 skyabyss:dungeon。近くの同じ組の人もいっしょ)。層は部屋の層と主の間
//   中     部屋 3 つ。人が寄ると敵が出て、その部屋の敵がみな倒れると次の戸 (鉄格子) が開く。主は毎回出る。碑 (印 tablet:<碑の鍵>) と前の私の荷 (印 pack) は中にある
//   越える 主が倒れたとき写しにいた人に、章の場所のクリアを数える (SiteSpawner.credit: 初回の経験値・宝・合図 site_clear:<id>・頁。1 日ごとの周回の素材)。
//          主の間の奥の帰りの光か、帰り石で、入った門の前へ戻る
//   地上の湧き台・謎解き (塩樽・鳴り石・冷えの戸・避雷の柱)・出口の縦穴の戸はもう使わない (SiteSpawner.INSTANCED、各場所の RETIRED)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MiniDungeons {
    public static final String BOSS_TAG = "skyabyss_mini_boss";
    // 前の版の地下を埋めた印 (SiteData の要素の extra)
    static final String SEALED = "mini_sealed_v1";
    // 埋めた地下を地形へ戻した印 (2026-11-12 の声「跡地の門の元々のやつ、いらない」。斜面では埋めた部屋と階段が石レンガの箱として地上へ突き出ていた)
    static final String GROUND = "mini_ground_v1";
    static final int CHECK_EVERY = 100;
    static final double NEAR = 64;

    // 入った人が戻る門 (人 → 門の位置)
    // 人 → 入った門 (どの小ダンジョンのものか)。前は門だけを覚えて消さず、仲間の分は「なければ足す」だったので、前に別の小ダンジョン
    // (潮殻の塩の蔵など) へ入った仲間が、そこの門へ戻されていた (2026-11-08 の声「最初のミニダンジョン、クリアしたら亀の方のミニダンジョンtpさせられた」)
    private record Home(Dungeon dungeon, BlockPos gate) {
    }

    private static final Map<UUID, Home> HOME = new HashMap<>();

    private MiniDungeons() {
    }

    @SubscribeEvent
    static void onSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (Dungeon d : Dungeon.minis()) {
                SiteSpawner.INSTANCED.add(d.id);
            }
            // 避雷の塔の跡は地上の湧き台がなかったので、クリアの口をここで持つ (避雷の柱の頁と風の民の書、銅。支度の段 ch3/lightning_pillars)
            if (SiteSpawner.def(Dungeon.MINI_ROD_TOWER.id) == null) {
                SiteSpawner.register(new SiteSpawner.Def(Dungeon.MINI_ROD_TOWER.id, Chapters.STORM, List.of(), 60,
                        jp.revati.skyabyss.titan.storm.chapter.LightningPillars.VAULT_TREASURE, jp.revati.skyabyss.titan.storm.chapter.ScorchedWatch.REPEAT_TREASURE,
                        SiteSpawner.MAX_MOBS, server -> List.of()));
                SiteSpawner.onCleared(Dungeon.MINI_ROD_TOWER.id, (p, first) -> {
                    Lore.unlock(p, jp.revati.skyabyss.titan.storm.chapter.Ch3Ground.PAGE_PILLARS, true);
                    jp.revati.skyabyss.story.ch1.Tasks.later(40, () -> Lore.unlock(p, jp.revati.skyabyss.titan.storm.chapter.Ch3Ground.PAGE_STAYER, true));
                });
            }
        });
    }

    // ---------------------------------------------------------------- 地上の門
    // 塔の二つは前の立つ所 (stand)、ほかは口 (mouth)
    static boolean tower(Dungeon d) {
        return d == Dungeon.MINI_ROD_TOWER || d == Dungeon.MINI_SCORCHED_WATCH;
    }

    // その小ダンジョンの建った要素 (章の場所)
    public static List<SiteData.Entry> entries(MinecraftServer server, Dungeon d) {
        List<SiteData.Entry> out = new ArrayList<>();
        for (SiteData.Entry e : SiteData.get(server.overworld()).allEntries()) {
            if (e.built && e.siteId.equals(d.id)) {
                out.add(e);
            }
        }
        return out;
    }

    // 要素の門の石の位置。部品に印がなければ null
    @Nullable
    public static BlockPos gateOf(SiteData.Entry e, Dungeon d) {
        Blueprint bp = Blueprint.get(e.blueprint.isEmpty() ? d.id : e.blueprint);
        if (bp == null) {
            return null;
        }
        int[] m = bp.marks.get(tower(d) ? "stand" : "mouth");
        if (m == null && d == Dungeon.MINI_FORGE_PIT) {
            // 消えた炉の坑の部品には口の印がない。坑口の階段の上 (地表の高さの穴の真ん中)
            m = new int[] {0, 0, 1};
        }
        if (m == null) {
            m = bp.marks.get("stand");
        }
        if (m == null) {
            return null;
        }
        return Blueprint.transform(m[0], m[1], m[2], SiteFinder.rot(e.yaw), SiteFinder.mirror(e.yaw), e.pos);
    }

    // その位置の門の小ダンジョン (地上。門の石から 1 以内)
    @Nullable
    public static Dungeon gateAt(MinecraftServer server, BlockPos pos) {
        for (Dungeon d : Dungeon.minis()) {
            for (SiteData.Entry e : entries(server, d)) {
                BlockPos g = gateOf(e, d);
                if (g != null && g.distManhattan(pos) <= 1) {
                    return d;
                }
            }
        }
        return null;
    }

    // 門の石を使った
    public static void use(ServerPlayer p, Dungeon d) {
        BlockPos best = null;
        for (SiteData.Entry e : entries(p.server, d)) {
            BlockPos g = gateOf(e, d);
            if (g != null && (best == null || g.distSqr(p.blockPosition()) < best.distSqr(p.blockPosition()))) {
                best = g;
            }
        }
        if (best != null) {
            HOME.put(p.getUUID(), new Home(d, best));
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 0.8f, 1.4f);
        DungeonRun run = Dungeons.enter(p, d);
        if (run != null) {
            // 一緒に入った人も同じ門へ戻る
            for (UUID id : run.members) {
                if (best != null) {
                    HOME.put(id, new Home(d, best));
                }
            }
        }
    }

    // 入った門の前へ戻す (門の石の前、石を向いて)
    public static void sendHome(ServerPlayer p, Dungeon d) {
        ServerLevel over = p.server.overworld();
        Home home = HOME.remove(p.getUUID());
        BlockPos gate = home != null && home.dungeon() == d ? home.gate() : null;
        if (gate == null) {
            List<SiteData.Entry> list = entries(p.server, d);
            gate = list.isEmpty() ? null : gateOf(list.get(0), d);
        }
        if (gate == null) {
            BlockPos s = over.getSharedSpawnPos();
            p.teleportTo(over, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, p.getYRot(), 0f);
            return;
        }
        Vec3 at = standBeside(over, gate);
        float yaw = (float) Math.toDegrees(Math.atan2(-(gate.getX() + 0.5 - at.x), gate.getZ() + 0.5 - at.z));
        p.teleportTo(over, at.x, at.y, at.z, yaw, 0f);
        p.resetFallDistance();
        p.setDeltaMovement(Vec3.ZERO);
    }

    // 門の石のそばの立てる所 (横 1〜3。なければ石の上)
    static Vec3 standBeside(ServerLevel over, BlockPos gate) {
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos q = gate.offset(dx, dy, dz);
                        if (over.getBlockState(q).getCollisionShape(over, q).isEmpty() && over.getBlockState(q.above()).getCollisionShape(over, q.above()).isEmpty()
                                && !over.getBlockState(q.below()).getCollisionShape(over, q.below()).isEmpty()) {
                            return new Vec3(q.getX() + 0.5, q.getY(), q.getZ() + 0.5);
                        }
                    }
                }
            }
        }
        return new Vec3(gate.getX() + 0.5, gate.getY() + 1, gate.getZ() + 0.5);
    }

    // ---------------------------------------------------------------- 越えた
    static void cleared(DungeonRun run, ServerLevel level) {
        List<ServerPlayer> who = new ArrayList<>();
        for (ServerPlayer p : run.online()) {
            if (DungeonWorld.in(p) && run.floorOf(p) >= 0) {
                who.add(p);
            }
        }
        reward(run, level, who);
    }

    // 越えた人へ (知らせ・合図・章の場所のクリア)。自己テストは偽の人を直に渡す
    static void reward(DungeonRun run, ServerLevel level, List<ServerPlayer> who) {
        for (ServerPlayer p : who) {
            if (Dungeons.real(p)) {
                Notice.place(p, Component.translatable("message.skyabyss.dungeon.clear_title"), Component.translatable(run.dungeon.nameKey()));
                p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1f, 1f);
                Notice.hint(p, "message.skyabyss.dungeon.exit_hint");
            }
            jp.revati.skyabyss.quest.Quests.signal(p, "mini:" + run.dungeon.id);
        }
        SiteSpawner.credit(level, run.dungeon.id, who);
    }

    // ---------------------------------------------------------------- 中の碑と荷 (流し込んだあと)
    // 印 tablet:<碑の鍵> の碑に文を刻む
    static void afterPour(DungeonFloor f, ServerLevel level) {
        for (String k : f.terrain.marksStarting("tablet:")) {
            jp.revati.skyabyss.tablet.Tablets.write(level, f.at(k), k.substring("tablet:".length()));
        }
    }

    // 印 pack の樽 (前の私の荷) と、印 warm_stand の温石の台 (消えた炉の坑。温石を持って触れると台が温まり、熾り蟲が台へ寄る。五章の熾も移る)。触れたら true
    static boolean pack(ServerPlayer p, DungeonRun run, int floor, BlockPos pos) {
        ServerLevel level = run.level();
        if (run.dungeon == Dungeon.MINI_FORGE_PIT && run.floor(floor).terrain.has("warm_stand") && pos.equals(run.mark(floor, "warm_stand"))) {
            jp.revati.skyabyss.story.ch4.DeadForgePit.touchStand(p, level, pos);
            return true;
        }
        if (!run.floor(floor).terrain.has("pack") || !pos.equals(run.mark(floor, "pack"))) {
            return false;
        }
        switch (run.dungeon) {
            case MINI_BONE_PIT -> jp.revati.skyabyss.story.ch1.BonePit.openPack(p, level, pos);
            case MINI_FORGE_PIT -> jp.revati.skyabyss.story.ch4.DeadForgePit.openPack(p, level, pos);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- 前の版の地下を埋めて、門の石を置く
    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_EVERY != 57) {
            return;
        }
        ServerLevel over = server.overworld();
        List<ServerPlayer> players = Players.snapshot(over.players());
        if (players.isEmpty()) {
            return;
        }
        boolean dirty = false;
        for (Dungeon d : Dungeon.minis()) {
            for (SiteData.Entry e : entries(server, d)) {
                BlockPos gate = gateOf(e, d);
                if (gate == null || !over.isLoaded(gate) || players.stream().noneMatch(p -> p.distanceToSqr(Vec3.atCenterOf(gate)) < NEAR * NEAR)) {
                    continue;
                }
                if (!tower(d) && jp.revati.skyabyss.world.continent.ContinentLairs.continent(over)) {
                    if (e.extra(GROUND) == null && restore(over, e, d) >= 0) {
                        e.extra(GROUND, BlockPos.ZERO);
                        e.extra(SEALED, BlockPos.ZERO);
                        dirty = true;
                    }
                } else if (e.extra(SEALED) == null && !tower(d)) {
                    seal(over, e, d);
                    e.extra(SEALED, BlockPos.ZERO);
                    dirty = true;
                }
                if (!over.getBlockState(gate).is(Blocks.LODESTONE)) {
                    over.setBlock(gate, Blocks.LODESTONE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        if (dirty) {
            SiteData.get(over).setDirty();
        }
    }

    // 塞ぐ石 (その土地の石。口の上から見える面)
    static BlockState plug(Dungeon d) {
        return switch (d) {
            case MINI_BONE_PIT -> Blocks.COBBLESTONE.defaultBlockState();
            case MINI_CLEFT_CAVE -> Blocks.STONE_BRICKS.defaultBlockState();
            case MINI_SALT_VAULT -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
            case MINI_FORGE_PIT -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }

    // 部品の地面より下 (y <= -1) の、固くない所 (空き・階段・梯子・樽・戸・水・灯り) を塞ぐ石で埋める。出口の縦穴の戸と梯子 (地表まで延ばした分) も
    public static int seal(ServerLevel over, SiteData.Entry e, Dungeon d) {
        Blueprint bp = Blueprint.get(e.blueprint.isEmpty() ? d.id : e.blueprint);
        if (bp == null) {
            return 0;
        }
        BlockState fill = plug(d);
        int rot = SiteFinder.rot(e.yaw);
        boolean mirror = SiteFinder.mirror(e.yaw);
        int n = 0;
        for (Blueprint.Cell c : bp.cells) {
            if (c.y() > -1) {
                continue;
            }
            BlockPos at = Blueprint.transform(c.x(), c.y(), c.z(), rot, mirror, e.pos);
            BlockState s = over.getBlockState(at);
            if (!s.isCollisionShapeFullBlock(over, at) || s.hasBlockEntity() || !s.getFluidState().isEmpty()) {
                over.setBlock(at, fill, 2 | 16);
                n++;
            }
        }
        int[] exit = bp.marks.get("exit");
        if (exit != null) {
            BlockPos base = Blueprint.transform(exit[0], exit[1], exit[2], rot, mirror, e.pos).below();
            for (int i = 0; i < 18; i++) {
                BlockPos q = base.above(i);
                BlockState s = over.getBlockState(q);
                if (s.is(Blocks.LADDER) || s.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock) {
                    over.setBlock(q, fill, 2 | 16);
                    n++;
                } else if (i > 1 && s.isAir()) {
                    break;
                }
            }
        }
        SkyAbyss.LOGGER.info("Mini dungeon {}: sealed the old underground of the site at {} ({} cells)", d.id, e.pos.toShortString(), n);
        return n;
    }

    // 大陸の世界: 前の版の地下 (部品の y <= -1 の全部。部屋・階段・壁・埋めた石) を、生まれたままの地形に置き直す。
    // 均した台 (部品の範囲 + 輪) の下は台の高さを上面にした土と石、外は大陸の地面 (地面より上は空気か水)。出口の縦穴の埋め石も。
    // 置き直した数。辺りのチャンクがまだなければ -1 (何もしない)
    public static int restore(ServerLevel over, SiteData.Entry e, Dungeon d) {
        Blueprint bp = Blueprint.get(e.blueprint.isEmpty() ? d.id : e.blueprint);
        if (bp == null) {
            return 0;
        }
        int rot = SiteFinder.rot(e.yaw);
        boolean mirror = SiteFinder.mirror(e.yaw);
        int pad = e.pos.getY() - 1;
        Map<BlockPos, Integer> tops = new HashMap<>();
        for (Blueprint.Cell c : bp.cells) {
            if (c.y() > -1) {
                continue;
            }
            BlockPos at = Blueprint.transform(c.x(), c.y(), c.z(), rot, mirror, e.pos);
            if (!over.isLoaded(at)) {
                return -1;
            }
            boolean inPad = c.x() >= bp.rx0 - bp.ring && c.x() <= bp.rx1 + bp.ring && c.z() >= bp.rz0 - bp.ring && c.z() <= bp.rz1 + bp.ring;
            tops.put(at, inPad ? Math.max(pad, jp.revati.skyabyss.world.continent.ContinentMap.get().surface(at.getX(), at.getZ())) : Integer.MIN_VALUE);
        }
        BlockState fill = plug(d);
        int[] exit = bp.marks.get("exit");
        if (exit != null) {
            BlockPos base = Blueprint.transform(exit[0], exit[1], exit[2], rot, mirror, e.pos);
            for (int i = 0; i < 18; i++) {
                BlockPos q = base.above(i);
                if (over.isLoaded(q) && over.getBlockState(q).is(fill.getBlock())) {
                    tops.putIfAbsent(q, Integer.MIN_VALUE);
                }
            }
        }
        int n = 0;
        for (Map.Entry<BlockPos, Integer> t : tops.entrySet()) {
            BlockPos at = t.getKey();
            int top = t.getValue() == Integer.MIN_VALUE ? jp.revati.skyabyss.world.continent.ContinentMap.get().surface(at.getX(), at.getZ()) : t.getValue();
            BlockState want = jp.revati.skyabyss.world.continent.ContinentChunkGenerator.naturalAt(at.getX(), at.getY(), at.getZ(), top);
            BlockState now = over.getBlockState(at);
            if (now.equals(want)) {
                continue;
            }
            // 地上の部品 (y >= 0) の下の空き (地面より上でも) は残す: 建物が浮かないよう、台の外でも上に部品のある所は土で支える
            if (want.isAir() || !want.getFluidState().isEmpty()) {
                BlockState above = over.getBlockState(at.above());
                if (!above.isAir() && above.getFluidState().isEmpty() && !tops.containsKey(at.above()) && at.getY() == e.pos.getY() - 1) {
                    want = jp.revati.skyabyss.world.continent.ContinentChunkGenerator.naturalAt(at.getX(), at.getY(), at.getZ(), at.getY());
                }
            }
            if (now.hasBlockEntity()) {
                over.removeBlockEntity(at);
            }
            over.setBlock(at, want, 2 | 16);
            n++;
        }
        SkyAbyss.LOGGER.info("Mini dungeon {}: put the old underground of the site at {} back to the land ({} of {} cells)", d.id, e.pos.toShortString(), n, tops.size());
        return n;
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        HOME.clear();
    }
}
