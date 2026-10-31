package jp.revati.skyabyss.facility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.guild.GuildService;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.npc.NpcEntity;
import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.travel.TitanCalls;
import jp.revati.skyabyss.titan.young.YoungCarriers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;

// 係の人の会話から開く施設の働き (会話の結果 {"type": "facility", "open": ...}。quest/Action が呼ぶ)。
//   shop     : 店 (shop を書けばその店、書かなければ係の人の役の店)
//   stash    : 預かり所 (StashMenu)
//   inn_rest : 宿で目覚める場所を、宿の寝台 (印 inn_bed) にする
//   rumor    : 宿の噂 (町のクエストの入口。InnRumors に足した出どころ → なければ章ごとの決まった噂)
//   perch    : 止まり木の番が巨神の子を呼ぶ (笛と同じ決まり。出会った巨神の子から)
//   map      : 旅の地図 (M) を開く
//   guild    : ギルドの画面
//   repair   : 鍛冶師の修理 (手に持った品。傷みに応じた鐘銭)
//   guild_storage : ギルドの倉庫 (GuildStorage)
//   respec_quote / respec_skills / respec_skills_coins : 訓練場の師範の振り直し (TrainingMaster)
//   job_room : 職の間 (職を替える画面。JobChoice.openRoom。docs/design/48 の 3 章)
//   key_offer / key_return : 社の祭司。鍵の間に欠片を納める / 返してもらう (KeyRoom)
//   records   : 砦の老兵の試練の記録 (KeepRecords → client RecordsScreen)
//   brew_lesson : 薬師が醸造を教える (四章。手ほどきの品を一度だけ)
//   mount_shop : 厩舎の番。乗り物の店の画面 (mount/MountService。docs/design/38)
//   trade     : 取引所の番。取引所の画面 (trade/TradeService。番の前なので出す・申請ができる)
//   boxes     : 封切り師。封の箱の画面 (loot/box/BoxService。docs/design/52)
//   guide     : 都の案内人。施設の一覧の画面 → 選んだ施設の戸まで灯りの道 (TownGuide)
public final class FacilityActions {
    public static final java.util.Set<String> OPENS = java.util.Set.of("shop", "stash", "inn_rest", "rumor", "perch", "map", "guild", "repair",
            "guild_storage", "respec_quote", "respec_skills", "respec_skills_coins", "job_room",
            "key_offer", "key_return", "records", "brew_lesson", "trade", "mount_shop", "guide", "boxes");

    private FacilityActions() {
    }

    public static void run(ServerPlayer player, @Nullable NpcEntity npc, String open, @Nullable String shop) {
        switch (open) {
            case "shop" -> {
                String id = shop;
                if (id == null && npc instanceof AttendantEntity a && a.attendantRole() != null) {
                    id = a.attendantRole().shop;
                }
                if (id != null) {
                    ShopService.open(player, id, npc);
                }
            }
            case "stash" -> StashMenu.open(player, npc);
            case "inn_rest" -> innRest(player, npc);
            case "rumor" -> rumor(player);
            case "perch" -> perchCall(player);
            case "map" -> ShopService.send(player, new FacilityNetwork.OpenMap());
            case "guild" -> GuildService.open(player, npc);
            case "repair" -> repair(player);
            case "guild_storage" -> jp.revati.skyabyss.facility.guild.GuildStorage.open(player, npc);
            case "respec_quote" -> TrainingMaster.quote(player);
            case "job_room" -> jp.revati.skyabyss.rpg.skill.JobChoice.openRoom(player);
            case "respec_skills" -> TrainingMaster.respec(player, false, false);
            case "respec_skills_coins" -> TrainingMaster.respec(player, false, true);
            case "key_offer" -> KeyRoom.offerAll(player);
            case "key_return" -> KeyRoom.giveBack(player);
            case "records" -> KeepRecords.open(player);
            case "brew_lesson" -> brewLesson(player);
            case "trade" -> jp.revati.skyabyss.facility.trade.TradeService.open(player, npc);
            case "mount_shop" -> jp.revati.skyabyss.mount.MountService.open(player, npc);
            case "guide" -> TownGuide.openMenu(player);
            case "boxes" -> jp.revati.skyabyss.loot.box.BoxService.open(player, npc);
            default -> SkyAbyss.LOGGER.warn("[facility] unknown open {}", open);
        }
    }

    // ---------------------------------------------------------------- 宿
    // 宿の寝台の印 (いちばん近いもの。都と土地の町)。印がなければ宿の女将の足もと
    @Nullable
    public static BlockPos innBed(ServerPlayer player, @Nullable NpcEntity npc) {
        Map<String, BlockPos> beds = OpeningSites.shelterExtras(player.server, Attendants.INN_BED);
        BlockPos from = npc != null ? npc.blockPosition() : player.blockPosition();
        BlockPos best = null;
        // 都の宿と、天と淵の大陸の土地の町の宿 (話している宿の主人にいちばん近い寝台)
        List<BlockPos> all = new java.util.ArrayList<>(beds.values());
        all.addAll(jp.revati.skyabyss.world.continent.ContinentTowns.innBeds(player.server));
        for (BlockPos b : all) {
            if (best == null || b.distSqr(from) < best.distSqr(from)) {
                best = b;
            }
        }
        if (best == null && npc != null) {
            best = npc.home() != null ? npc.home() : npc.blockPosition();
        }
        return best;
    }

    // 宿を寝る場所に決めた人が起きる (復活する) とき、町の真ん中 (鐘のそば) に立たせる。宿の寝台に決めていなければ null
    @Nullable
    public static net.minecraft.world.phys.Vec3 plazaSpotFor(ServerPlayer player) {
        BlockPos home = player.getRespawnPosition();
        if (home == null || player.getRespawnDimension() != Level.OVERWORLD) {
            return null;
        }
        boolean inn = false;
        for (BlockPos b : OpeningSites.shelterExtras(player.server, Attendants.INN_BED).values()) {
            if (b.distSqr(home) <= 9) {
                inn = true;
                break;
            }
        }
        if (!inn) {
            return null;
        }
        BlockPos centre = OpeningSites.bell(player.server).orElse(OpeningSites.shelter(player.server).orElse(null));
        if (centre == null) {
            return null;
        }
        net.minecraft.server.level.ServerLevel level = player.server.overworld();
        for (int r = 1; r <= 5; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = 2; dy >= -3; dy--) {
                        BlockPos at = centre.offset(dx, dy, dz);
                        if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir() && level.getBlockState(at.below()).isSolid()) {
                            return net.minecraft.world.phys.Vec3.atBottomCenterOf(at);
                        }
                    }
                }
            }
        }
        return net.minecraft.world.phys.Vec3.atBottomCenterOf(centre.above());
    }

    public static boolean innRest(ServerPlayer player, @Nullable NpcEntity npc) {
        BlockPos bed = innBed(player, npc);
        if (bed == null) {
            return false;
        }
        // 印が寝台の上なら寝台そのもの、そうでなければその場所に (forced = 寝台がなくても目覚める)
        boolean isBed = player.serverLevel().getBlockState(bed).getBlock() instanceof BedBlock;
        player.setRespawnPosition(Level.OVERWORLD, bed, player.getYRot(), !isBed, false);
        Notice.hint(player, "message.skyabyss.inn.rested");
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 0.3f, 1.6f);
        return true;
    }

    // ---------------------------------------------------------------- 噂 (町のクエストの入口)
    // 噂の出どころ。町のクエスト (35 の 3.5) が足す。その人に今出せる噂の文があれば返す (なければ null)
    public interface RumorSource {
        @Nullable
        Component rumor(ServerPlayer player);
    }

    private static final List<RumorSource> RUMORS = new CopyOnWriteArrayList<>();
    // 決まった噂の数 (章ごと。lang の message.skyabyss.inn.rumor.ch<章>.<1..N>)
    public static final int FIXED_RUMORS = 3;

    public static void addRumorSource(RumorSource source) {
        RUMORS.add(source);
    }

    public static Component rumorFor(ServerPlayer player) {
        for (RumorSource s : RUMORS) {
            Component c = s.rumor(player);
            if (c != null) {
                return c;
            }
        }
        int ch = Math.min(4, Coins.chapter(player));
        long day = ShopService.day(player);
        int n = (int) Math.floorMod(day + player.getUUID().getLeastSignificantBits(), FIXED_RUMORS) + 1;
        return Component.translatable("message.skyabyss.inn.rumor.ch" + ch + "." + n);
    }

    static void rumor(ServerPlayer player) {
        Notice.narrate(player, "message.skyabyss.inn.rumor_head", rumorFor(player));
    }

    // ---------------------------------------------------------------- 止まり木
    // 出会った巨神の子を、笛と同じ決まりで呼ぶ (YoungCarriers.whistle)。どれにも会っていなければ一言
    public static boolean perchCall(ServerPlayer player) {
        List<String> met = new ArrayList<>();
        for (String t : Titans.all()) {
            if (TitanCalls.met(player, t)) {
                met.add(t);
            }
        }
        if (met.isEmpty()) {
            Notice.hint(player, "message.skyabyss.perch.no_titan");
            return false;
        }
        for (String t : met) {
            if (YoungCarriers.whistle(player, t)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 醸造の手ほどき (四章)
    public static final int BREW_CHAPTER_STAGE = 3;

    // 四章 (心臓を三つ止めた) から。火の地への差し入れ (火除けの薬と傷薬を一本ずつ) を一度だけ
    public static boolean brewLesson(ServerPlayer player) {
        if (jp.revati.skyabyss.progress.Progression.stage(player) < BREW_CHAPTER_STAGE && !player.isCreative()) {
            Notice.hint(player, "message.skyabyss.apothecary.not_yet");
            return false;
        }
        if (!FacilityMarks.add(player, FacilityMarks.BREW_LESSON)) {
            Notice.hint(player, "message.skyabyss.apothecary.lesson_again");
            return false;
        }
        for (ItemStack s : brewKit()) {
            jp.revati.skyabyss.quest.Quests.give(player, s);
        }
        Notice.gain(player, "message.skyabyss.apothecary.lesson_kit");
        return true;
    }

    // 火の地への差し入れ (2026-10-25: 醸造台は冒険の決まりで置けないので、手ほどきの品の代わりに薬を一本ずつ。00 の 278)
    public static List<ItemStack> brewKit() {
        return List.of(new ItemStack(jp.revati.skyabyss.item.outfit.ConsumableGear.FIRE_WARD.get()), new ItemStack(jp.revati.skyabyss.item.outfit.ConsumableGear.HEALING.get()));
    }

    // ---------------------------------------------------------------- 修理
    // 修理の値段 (傷みの割合 x 20、1 以上)
    public static int repairCost(ItemStack stack) {
        if (!stack.isDamageableItem() || !stack.isDamaged()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(20.0 * stack.getDamageValue() / Math.max(1, stack.getMaxDamage())));
    }

    public static boolean repair(ServerPlayer player) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        int cost = repairCost(held);
        if (cost <= 0) {
            Notice.hint(player, "message.skyabyss.smith.nothing_to_repair");
            return false;
        }
        if (!Coins.take(player, cost)) {
            Notice.hint(player, "message.skyabyss.smith.repair_cost", cost);
            return false;
        }
        held.setDamageValue(0);
        Notice.hint(player, "message.skyabyss.smith.repaired", held.getHoverName(), cost);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5f, 1.2f);
        return true;
    }
}
