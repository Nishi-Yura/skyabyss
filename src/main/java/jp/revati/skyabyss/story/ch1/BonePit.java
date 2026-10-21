package jp.revati.skyabyss.story.ch1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.SiteSpawner;
import jp.revati.skyabyss.guide.SiteTreasure;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.ChapterSites.Prefer;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteJob;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.tablet.Tablets;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 骨の坑 (docs/design/34 の 4.2.1。一章の支度「鉄の跡」の舞台。8〜12 分の小さなダンジョン)。部品は data/skyabyss/opening/ch1_bone_pit.json (tools/gen_ch1_sites.py)。
//
//   場所   ChapterSites の要素 (拠点から 70〜150、高台を選ぶ = 北の丘のつもり。33 の跡地 W2 ができたら一章の跡地の中へ)。地表に骨の肋骨の弧と坑口の碑
//          (sign.skyabyss.ch1_bone_pit)、木の枠の坑口から階段で地下 13 ブロックへ。部屋は石レンガの殻で包む (自然の洞や水とつながらない)
//   部屋   坑口の間   苔這い 3。壁に鉄鉱石の筋 18 (掘ってよい)。坑木・こわれた線路・松明の残り              予習: 暗がりの足もと
//          水の底     床の井戸から水没の坑道 (長さ 15、息継ぎ 2 か所) を抜けて巣の間へ。苔這い 2            予習: 闇の水路の息継ぎ
//          荷の間     岩殻虫 2。前の私の荷 (樽。触れると人ごとに一度: 鉄 2・松明 8・頁 ch1/trail_iron)。     予習: 盾持ちの背を取る
//                     西の壁に冷えの戸 (石の 3x3。鉄の防具を一つでも着た人が触れると開く。演出。世界に一つ = 一度開けば残る)。
//                     奥は氷の小部屋で、冷えの戸の碑文 (sign.skyabyss.ch1_cold_door。読むと伝承 ch1/cold_door)。東の壁の外に地上へ出る梯子の縦穴
//   クリア 湧かせた敵がすべて倒れる → 初めての人に経験値 40・宝 (粗鉄 4・骨片 2)・合図 site_clear:ch1_bone_pit (SiteSpawner)。
//          鉄の数は「鉄を 24」(頁) に合わせた: 鉱石 18 + 荷 2 + クリアの粗鉄 4。再訪は 1 日ごとに荷の間の敵が戻り、粗鉄と骨片が出る (経験値なし)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BonePit {
    public static final String SITE = Ch1Prep.BONE_PIT;
    public static final String TREASURE = "ch1_bone_pit";
    public static final String REPEAT_TREASURE = "ch1_bone_pit_repeat";
    // 前の私の荷 (人ごとに一度)
    public static final String PACK = "ch1_bone_pit_pack";
    public static final int XP = 40;
    public static final int COLD_CELLS = 9;
    public static final int IRON_VEINS = 18;
    public static final String SIGN = "sign.skyabyss.ch1_bone_pit";
    public static final String COLD_SIGN = "sign.skyabyss.ch1_cold_door";
    public static final String ROOM_MOUTH = "mouth";
    public static final String ROOM_NEST = "nest";
    public static final String ROOM_PACK = "pack";

    private static final Map<Long, Integer> YAW = new HashMap<>();

    private BonePit() {
    }

    // ---------------------------------------------------------------- 登録 (Sites.registerP6 から)
    static void register() {
        ChapterSites.element(SITE, Chapters.WHALE, SITE).atGround().dist(70, 150).prefer(Prefer.HIGH).arriveRadius(14)
                .post(BonePit::finish)
                .onArrive((p, s) -> {
                    Notice.narrate(p, "message.skyabyss.ch1.arrive." + SITE);
                    if (onTrail(p)) {
                        Tasks.later(80, () -> Notice.narrate(p, "message.skyabyss.ch1.pit.trail"));
                    }
                }).register();
        SiteTreasure.register(PACK, SiteTreasure.Table.of(List.of(new SiteTreasure.Entry("minecraft:iron_ingot", 2), new SiteTreasure.Entry("minecraft:torch", 8))));
        SiteTreasure.register(TREASURE, SiteTreasure.Table.of(List.of(new SiteTreasure.Entry("minecraft:raw_iron", 4), new SiteTreasure.Entry("skyabyss:bone_chip", 2))));
        SiteTreasure.register(REPEAT_TREASURE, SiteTreasure.Table.of(List.of(), 1, 2, List.of(new SiteTreasure.Entry("minecraft:raw_iron", 1, 3, 3),
                new SiteTreasure.Entry("skyabyss:bone_chip", 1, 2, 2), new SiteTreasure.Entry("skyabyss:rock_shell_scrap", 1, 2, 2))));
        SiteSpawner.register(new SiteSpawner.Def(SITE, Chapters.WHALE, pads(), XP, TREASURE, REPEAT_TREASURE, SiteSpawner.MAX_MOBS, BonePit::origins, BonePit::yawAt));
    }

    // 湧き台 (部品の座標。床は y = -13、敵が立つのは -12)。最後の部屋 (荷の間) がクリア後の再訪で戻る
    static List<SiteSpawner.Pad> pads() {
        String crawler = "skyabyss:moss_crawler";
        String beetle = Ch1Mobs.pick(Ch1Mobs.ROCK_SHELL_BEETLE, Ch1Mobs.STAND_IN_BEETLE);
        List<SiteSpawner.Pad> pads = new ArrayList<>();
        pads.add(new SiteSpawner.Pad(ROOM_MOUTH, -2, -12, 17, List.of(new SiteSpawner.Spawn(crawler, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_MOUTH, 2, -12, 22, List.of(new SiteSpawner.Spawn(crawler, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_MOUTH, 0, -12, 27, List.of(new SiteSpawner.Spawn(crawler, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_NEST, -27, -12, 24, List.of(new SiteSpawner.Spawn(crawler, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_NEST, -23, -12, 32, List.of(new SiteSpawner.Spawn(crawler, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_PACK, -23, -12, 2, List.of(new SiteSpawner.Spawn(beetle, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_PACK, -17, -12, -1, List.of(new SiteSpawner.Spawn(beetle, 1))));
        return pads;
    }

    // ---------------------------------------------------------------- 場所と向き
    static List<BlockPos> origins(MinecraftServer server) {
        List<BlockPos> list = new ArrayList<>();
        for (SiteData.Entry e : entries(server)) {
            list.add(e.pos);
            YAW.put(e.pos.asLong(), e.yaw);
        }
        return list;
    }

    static int yawAt(BlockPos origin) {
        return YAW.getOrDefault(origin.asLong(), 0);
    }

    // 建て終わった坑の記録
    public static List<SiteData.Entry> entries(MinecraftServer server) {
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

    // 建て終わり: 荷・戸・碑の位置を記録に残し (向きを回したあとの世界の座標)、碑に文を刻む
    static void finish(SiteJob job) {
        for (String name : List.of("pack", "tablet", "cold_tablet", "exit")) {
            BlockPos at = job.mark(name);
            if (at != null) {
                job.entry.extra(name, at);
            }
        }
        for (int k = 0; k < COLD_CELLS; k++) {
            BlockPos at = job.mark("cold" + k);
            if (at != null) {
                job.entry.extra("cold" + k, at);
            }
        }
        BlockPos tablet = job.mark("tablet");
        if (tablet != null) {
            Tablets.write(job.level, tablet, SIGN);
        }
        BlockPos cold = job.mark("cold_tablet");
        if (cold != null) {
            Tablets.write(job.level, cold, COLD_SIGN);
        }
        YAW.put(job.entry.pos.asLong(), job.entry.yaw);
    }

    // ---------------------------------------------------------------- 荷と冷えの戸
    @Nullable
    static SiteData.Entry pitWithPack(MinecraftServer server, BlockPos pos) {
        for (SiteData.Entry e : entries(server)) {
            if (pos.equals(e.extra("pack"))) {
                return e;
            }
        }
        return null;
    }

    @Nullable
    static SiteData.Entry pitWithDoor(MinecraftServer server, BlockPos pos) {
        for (SiteData.Entry e : entries(server)) {
            for (int k = 0; k < COLD_CELLS; k++) {
                if (pos.equals(e.extra("cold" + k))) {
                    return e;
                }
            }
        }
        return null;
    }

    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        var state = level.getBlockState(pos);
        if (state.is(Blocks.BARREL)) {
            SiteData.Entry pit = pitWithPack(level.getServer(), pos);
            if (pit != null) {
                event.setCanceled(true);
                openPack(player, level, pos);
            }
        } else if (state.is(Blocks.POLISHED_DEEPSLATE)) {
            SiteData.Entry pit = pitWithDoor(level.getServer(), pos);
            if (pit != null) {
                event.setCanceled(true);
                touchDoor(player, level, pit);
            }
        }
    }

    // 前の私の荷: 人ごとに一度、鉄 2・松明 8。頁 (伝承 ch1/trail_iron) は何度触れても記される (なくしても読み直せる)。初めて中身を出したら true
    public static boolean openPack(ServerPlayer player, ServerLevel level, @Nullable BlockPos at) {
        boolean first = !SiteTreasure.claimed(player, PACK);
        if (first) {
            SiteTreasure.claim(player, PACK);
            Notice.narrate(player, "message.skyabyss.ch1.pit.pack");
        } else {
            Notice.narrate(player, "message.skyabyss.ch1.pit.pack_empty");
        }
        if (at != null) {
            level.playSound(null, at, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.8f, 0.8f);
        }
        Tasks.later(30, () -> Lore.unlock(player, Ch1Prep.PAGE_IRON, true));
        return first;
    }

    // 着ている鉄の防具の部位の数
    public static int ironWorn(Player player) {
        int n = 0;
        for (ItemStack s : player.getInventory().armor) {
            if (s.is(Items.IRON_HELMET) || s.is(Items.IRON_CHESTPLATE) || s.is(Items.IRON_LEGGINGS) || s.is(Items.IRON_BOOTS)) {
                n++;
            }
        }
        return n;
    }

    public static boolean doorOpen(ServerLevel level, SiteData.Entry pit) {
        for (int k = 0; k < COLD_CELLS; k++) {
            BlockPos at = pit.extra("cold" + k);
            if (at != null && level.hasChunkAt(at) && level.getBlockState(at).is(Blocks.POLISHED_DEEPSLATE)) {
                return false;
            }
        }
        return true;
    }

    // 冷えの戸に触れた (自己テストからも)。鉄の防具を一つでも着ていれば開く。開いたら true
    public static boolean touchDoor(ServerPlayer player, ServerLevel level, SiteData.Entry pit) {
        if (doorOpen(level, pit)) {
            return false;
        }
        if (ironWorn(player) == 0) {
            Notice.narrate(player, "message.skyabyss.ch1.pit.cold_shut");
            BlockPos c = pit.extra("cold4");
            if (c != null) {
                level.playSound(null, c, SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 0.6f, 0.5f);
            }
            return false;
        }
        BlockPos centre = pit.extra("cold4");
        for (int k = 0; k < COLD_CELLS; k++) {
            BlockPos at = pit.extra("cold" + k);
            if (at != null && level.hasChunkAt(at) && level.getBlockState(at).is(Blocks.POLISHED_DEEPSLATE)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        if (centre != null) {
            level.playSound(null, centre, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0f, 0.5f);
            level.playSound(null, centre, SoundEvents.POWDER_SNOW_STEP, SoundSource.BLOCKS, 1.0f, 0.6f);
            level.sendParticles(ParticleTypes.SNOWFLAKE, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, 30, 0.6, 1.0, 0.6, 0.02);
            for (ServerPlayer p : Players.snapshot(level.players())) {
                if (p.distanceToSqr(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5) < 24 * 24) {
                    Notice.narrate(p, "message.skyabyss.ch1.pit.cold_open");
                }
            }
        }
        return true;
    }

    // 鉄の跡を受けている人か (坑に着いたときの一言を変える)
    static boolean onTrail(ServerPlayer player) {
        QuestLog.Entry e = QuestLog.of(player).entry(Ch1Prep.Q_IRON);
        return e != null && !e.done;
    }
}
