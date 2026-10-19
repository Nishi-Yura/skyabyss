package jp.revati.skyabyss.story.ch1;

import jp.revati.skyabyss.util.Players;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.SiteSpawner;
import jp.revati.skyabyss.guide.SiteTreasure;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteJob;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 割れ殻の洞 (docs/design/21 の 3.1。備えの頼み 2・3 の舞台。10〜18 分の小さなダンジョン)。部品は data/skyabyss/opening/ch1_cleft_shell_cave.json (tools/gen_ch1_sites.py)。
//
//   場所   ChapterSites の要素 (拠点から 60〜150)。地表に石レンガの口、階段で地下 15 ブロックへ。地下 3 部屋 (入口の間・棘の間・奥の間)。
//          部屋は石レンガの殻で包んでいるので、自然の洞や水とつながらない
//   敵     SiteSpawner (guide/)。人が 48 ブロック以内に来たときだけ、その人のグループの分だけ湧く。二つのグループが同時に入れる
//            入口の間  岩殻虫 3 (盾持ちの予習。P7 まで苔這い)     棘の間  棘うさぎ 3 (射手の予習。P7 まで骨の射手 2) と石柱の遮蔽
//            奥の間    老いた歩哨 1 (番兵長の読み方の予習。P7 まで骨の番兵 1 と射手 1)
//   仕掛け 棘の間の東の壁ぎわに「鳴り石」3 つ (石の台。上のろうそくの数 1・2・3)。低い順に叩くと、奥の間へ続く回廊の鉄格子が開く
//          (歌石と同じ決まり: 順を違えると鳴りかけて黙り、1 から数え直す。進みはグループごと。格子は世界に一つ = 一度開けば、そのあとの人も通れる)。
//          格子の向こうに奥の間の敵が見える (壁をすり抜けない)。叩く順は洞の外で歌石が教えている
//   クリア 湧かせた敵がすべて倒れる → 近くの人に記録 site_clear.ch1_cleft_shell_cave。初めての人に経験値 40・宝 (護り糸・素材・固焼き)・合図 site_clear:ch1_cleft_shell_cave・伝承。
//          再訪は 1 日ごとに最後の部屋だけ敵が戻り、素材が出る (経験値なし)。洞は周回の長さに数えない (一章の地上の小さな場所)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class CleftCave {
    public static final String SITE = Ch1Prep.CLEFT_CAVE;
    public static final String TREASURE = "ch1_cleft_shell_cave";
    public static final String REPEAT_TREASURE = "ch1_cleft_shell_cave_repeat";
    public static final int XP = 40;
    public static final int PLATES = 3;
    public static final int GATE_CELLS = 9;
    // 部屋の名前 (SiteSpawner.Pad の room)
    public static final String ROOM_ENTRY = "entry";
    public static final String ROOM_SPINES = "spines";
    public static final String ROOM_ELDER = "elder";

    // 向きの写し (基点 → 符号化された向き)。origins の呼び出しのたびに SiteData から更新する (SiteSpawner の pads を部品の向きに回すため)
    private static final Map<Long, Integer> YAW = new HashMap<>();
    // 鳴り石の進み (グループ + 場所ごと。保存しない。格子は開けば世界に残る)
    private static final Map<String, Integer> PROGRESS = new HashMap<>();

    private CleftCave() {
    }

    // ---------------------------------------------------------------- 登録 (Sites.register から)
    static void register() {
        ChapterSites.element(SITE, Chapters.WHALE, "ch1_cleft_shell_cave").dist(60, 150).arriveRadius(14)
                .post(CleftCave::finish)
                .onArrive((p, s) -> Notice.narrate(p, "message.skyabyss.ch1.arrive." + SITE)).register();
        SiteTreasure.register(TREASURE, SiteTreasure.Table.of(
                List.of(new SiteTreasure.Entry("skyabyss:rock_shell_thread", 1), new SiteTreasure.Entry("skyabyss:rock_shell_scrap", 4),
                        new SiteTreasure.Entry("skyabyss:bone_chip", 3),
                        new SiteTreasure.Entry("skyabyss:road_hardtack", 3)),
                1, 2, List.of(new SiteTreasure.Entry("skyabyss:rock_shell_scrap", 2, 4, 3), new SiteTreasure.Entry("skyabyss:bone_chip", 2, 3, 3),
                        new SiteTreasure.Entry("skyabyss:hare_quill", 2, 4, 2), new SiteTreasure.Entry("minecraft:iron_ingot", 1, 2, 2))));
        SiteTreasure.register(REPEAT_TREASURE, SiteTreasure.Table.of(List.of(), 1, 2, List.of(new SiteTreasure.Entry("skyabyss:rock_shell_scrap", 2, 4, 3),
                new SiteTreasure.Entry("skyabyss:bone_chip", 1, 3, 3), new SiteTreasure.Entry("skyabyss:hare_quill", 1, 3, 2))));
        SiteSpawner.register(new SiteSpawner.Def(SITE, Chapters.WHALE, pads(), XP, TREASURE, REPEAT_TREASURE, SiteSpawner.MAX_MOBS, CleftCave::origins, CleftCave::yawAt));
    }

    // 湧き台 (部屋の中の位置。部品の座標 = 基点からのずれ。部品の向きに合わせて SiteSpawner が回す。床は y = -16、敵が立つのは -15)。
    // 最後の部屋 (奥の間) が、クリア後の再訪で戻る
    static List<SiteSpawner.Pad> pads() {
        String beetle = Ch1Mobs.pick(Ch1Mobs.ROCK_SHELL_BEETLE, Ch1Mobs.STAND_IN_BEETLE);
        String hare = Ch1Mobs.pick(Ch1Mobs.SPINE_HARE, Ch1Mobs.STAND_IN_HARE);
        String elder = Ch1Mobs.pick(Ch1Mobs.ELDER_SENTRY, Ch1Mobs.STAND_IN_ELDER);
        int hareCount = hare.equals(Ch1Mobs.SPINE_HARE) ? 3 : 2;
        List<SiteSpawner.Pad> pads = new ArrayList<>();
        pads.add(new SiteSpawner.Pad(ROOM_ENTRY, -3, -15, 19, List.of(new SiteSpawner.Spawn(beetle, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_ENTRY, 3, -15, 19, List.of(new SiteSpawner.Spawn(beetle, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_ENTRY, 0, -15, 23, List.of(new SiteSpawner.Spawn(beetle, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_SPINES, 13, -15, 16, List.of(new SiteSpawner.Spawn(hare, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_SPINES, 16, -15, 26, List.of(new SiteSpawner.Spawn(hare, 1))));
        if (hareCount > 2) {
            pads.add(new SiteSpawner.Pad(ROOM_SPINES, 20, -15, 22, List.of(new SiteSpawner.Spawn(hare, hareCount - 2))));
        }
        pads.add(new SiteSpawner.Pad(ROOM_ELDER, 18, -15, 1, List.of(new SiteSpawner.Spawn(elder, 1))));
        if (!elder.equals(Ch1Mobs.ELDER_SENTRY)) {
            // 代用のとき: 老いた歩哨の代わりに骨の番兵と射手
            pads.add(new SiteSpawner.Pad(ROOM_ELDER, 15, -15, -3, List.of(new SiteSpawner.Spawn(Ch1Mobs.STAND_IN_HARE, 1))));
        }
        return pads;
    }

    // ---------------------------------------------------------------- 場所と向き
    // 建て終わった洞の基点 (SiteSpawner の origins)。向きも写す
    static List<BlockPos> origins(MinecraftServer server) {
        List<BlockPos> list = new ArrayList<>();
        for (SiteData.ChapterPlan plan : SiteData.get(server.overworld()).plans()) {
            for (SiteData.Entry e : plan.sites) {
                if (e.siteId.equals(SITE) && e.built) {
                    list.add(e.pos);
                    YAW.put(e.pos.asLong(), e.yaw);
                }
            }
        }
        return list;
    }

    static int yawAt(BlockPos origin) {
        return YAW.getOrDefault(origin.asLong(), 0);
    }

    // 洞の記録 (建て終わりのもの)
    static List<SiteData.Entry> entries(MinecraftServer server) {
        List<SiteData.Entry> list = new ArrayList<>();
        for (SiteData.ChapterPlan plan : SiteData.get(server.overworld()).plans()) {
            for (SiteData.Entry e : plan.sites) {
                if (e.siteId.equals(SITE) && e.built) {
                    list.add(e);
                }
            }
        }
        return list;
    }

    // 建て終わり: 鳴り石と格子の位置を記録に残す (向きを回したあとの世界の座標)
    static void finish(SiteJob job) {
        for (int n = 1; n <= PLATES; n++) {
            BlockPos at = job.mark("plate" + n);
            if (at != null) {
                job.entry.extra("plate" + n, at);
            }
        }
        for (int k = 0; k < GATE_CELLS; k++) {
            BlockPos at = job.mark("gate" + k);
            if (at != null) {
                job.entry.extra("gate" + k, at);
            }
        }
        YAW.put(job.entry.pos.asLong(), job.entry.yaw);
    }

    // ---------------------------------------------------------------- 鳴り石
    @Nullable
    static SiteData.Entry cave(MinecraftServer server, BlockPos pos) {
        for (SiteData.Entry e : entries(server)) {
            for (int n = 1; n <= PLATES; n++) {
                if (pos.equals(e.extra("plate" + n))) {
                    return e;
                }
            }
        }
        return null;
    }

    static int plateNumber(SiteData.Entry cave, BlockPos pos) {
        for (int n = 1; n <= PLATES; n++) {
            if (pos.equals(cave.extra("plate" + n))) {
                return n;
            }
        }
        return 0;
    }

    public static boolean gateOpen(ServerLevel level, SiteData.Entry cave) {
        for (int k = 0; k < GATE_CELLS; k++) {
            BlockPos at = cave.extra("gate" + k);
            if (at != null && level.hasChunkAt(at) && level.getBlockState(at).is(Blocks.IRON_BARS)) {
                return false;
            }
        }
        return true;
    }

    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level) || !level.getBlockState(event.getPos()).is(Blocks.CHISELED_STONE_BRICKS)) {
            return;
        }
        SiteData.Entry cave = cave(level.getServer(), event.getPos());
        if (cave == null) {
            return;
        }
        event.setCanceled(true);
        tap(player, level, cave, plateNumber(cave, event.getPos()));
    }

    // 鳴り石 n を叩いた (自己テストからも)。進みは SongStones.step と同じ決まり
    public static SongStones.Step tap(ServerPlayer player, ServerLevel level, SiteData.Entry cave, int n) {
        String key = Groups.keyOf(player) + "@" + cave.pos.asLong();
        BlockPos plate = cave.extra("plate" + n);
        boolean done = gateOpen(level, cave);
        SongStones.Step step = SongStones.step(PROGRESS.getOrDefault(key, 0), n, done);
        if (plate != null) {
            SongStones.chimes(level, plate, n);
        }
        switch (step.result()) {
            case SILENT -> Notice.narrate(player, "message.skyabyss.ch1.plate.done");
            case WRONG, RESTART -> {
                PROGRESS.put(key, step.progress());
                Notice.narrate(player, step.result() == SongStones.Result.WRONG ? "message.skyabyss.ch1.plate.wrong" : "message.skyabyss.ch1.plate.n1");
            }
            case ADVANCE -> {
                PROGRESS.put(key, step.progress());
                Notice.narrate(player, "message.skyabyss.ch1.plate.n" + n);
                if (n == PLATES) {
                    PROGRESS.remove(key);
                    Tasks.later(40, () -> openGate(level, cave));
                }
            }
        }
        return step;
    }

    // 回廊の鉄格子を開ける (世界に一つ)
    public static void openGate(ServerLevel level, SiteData.Entry cave) {
        BlockPos centre = cave.extra("gate4");
        for (int k = 0; k < GATE_CELLS; k++) {
            BlockPos at = cave.extra("gate" + k);
            if (at != null && level.hasChunkAt(at)) {
                BlockState s = level.getBlockState(at);
                if (s.is(Blocks.IRON_BARS)) {
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        if (centre != null) {
            level.playSound(null, centre, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.4f, 0.7f);
            level.playSound(null, centre, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0f, 0.6f);
            level.sendParticles(ParticleTypes.POOF, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, 12, 1.0, 1.0, 1.0, 0.02);
            for (ServerPlayer p : Players.snapshot(level.players())) {
                if (p.distanceToSqr(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5) < 40 * 40) {
                    Notice.narrate(p, "message.skyabyss.ch1.plate.open");
                }
            }
        }
    }

    // 自己テスト用: 進みを消す
    static void resetProgress() {
        PROGRESS.clear();
    }

    // 自己テスト用: 鳴り石のグループごとの進み
    static int progress(UUID group, SiteData.Entry cave) {
        return PROGRESS.getOrDefault(group + "@" + cave.pos.asLong(), 0);
    }
}
