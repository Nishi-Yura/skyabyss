package jp.revati.skyabyss.loot;

import jp.revati.skyabyss.util.Players;

import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.BoneGull;
import jp.revati.skyabyss.entity.BoneMortar;
import jp.revati.skyabyss.entity.BoneSentinel;
import jp.revati.skyabyss.entity.ElderSentinel;
import jp.revati.skyabyss.entity.MossCrawler;
import jp.revati.skyabyss.entity.RibArcher;
import jp.revati.skyabyss.forge.ForgeRules;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// 岩鯨の報酬 (周回前提)。表と期待周回数は docs/design/02_stone_whale.md 6章。
// - どの報酬も一人ずつ別々の抽選。人数が増えても、自分の取り分は減らない (取り合いもない)
// - 番兵長 (中ボス): 必ず出るのは経験値だけ。欠片・干し肉・番兵長の鉾は低い確率
// - 岩鯨の心臓 (最終ボス): 天の鍵の欠片 (一人一回)、少しの素材。装備は一つずつ低い確率で抽選し、一度では揃わない
// - 道中の番兵: 倒した人にだけ拾える形で、素材をまれに落とす
// - 倒れていた・抜けていた・持ち物がいっぱいの人の分は預かり、目を覚ましたとき・入ったとき・/titanmemory claim で渡す
// 巨神の記憶 (天井) は「新しい装備が出なかった心臓の撃破」の数:
//  - 3 以上: 装備の確率が2倍 (ソフト天井)
//  - 5 で: 図鑑にまだない装備を一つ必ず受け取る (ハード天井)。全部持っていれば共鳴の欠片に変わる
//  - 図鑑にない装備を手にすると 0 に戻る (持っているものが重なっても戻らない)
// 初めて心臓を倒したときは、専用の逸品三つから一つを選んで受け取れる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanLoot {
    public static final String STONE_WHALE = "stone_whale";
    public static final int SOFT_PITY = 3;
    public static final int HARD_PITY = 5;
    public static final float SOFT_MULTIPLIER = 2f;
    // 図鑑がすべて埋まっているとき、天井の代わりに受け取る共鳴の欠片
    public static final int PITY_SHARDS = 10;

    // 心臓: 装備 (一つずつ別の抽選。ソフト天井で2倍)
    public static final float ARMOR_CHANCE = 0.25f;
    public static final float GREATSWORD_CHANCE = 0.20f;
    public static final float CHARM_CHANCE = 0.20f;
    public static final float PIKE_CHANCE = 0.04f;
    public static final float FANG_CHANCE = 0.04f;
    public static final float PLATING_CHANCE = 0.02f;
    // 心臓: そのほか (天井とは関係しない)
    public static final float CORE_CHANCE = 0.25f;
    public static final float CRYSTAL_CHANCE = 0.40f;
    public static final float JERKY_CHANCE = 0.50f;
    public static final float ARROW_CHANCE = 0.35f;
    public static final float TALISMAN_CHANCE = 0.06f;

    // 番兵長
    public static final int CAPTAIN_XP = 40;
    public static final float CAPTAIN_SHARD_CHANCE = 0.60f;
    public static final float CAPTAIN_RESONANCE_CHANCE = 0.35f;
    public static final float CAPTAIN_JERKY_CHANCE = 0.20f;
    public static final float CAPTAIN_PIKE_CHANCE = 0.05f;

    private TitanLoot() {
    }

    // ---------------------------------------------------------------- 図鑑
    // 岩鯨の専用の逸品 (初回の選択の対象)
    public static List<Item> exclusives() {
        return List.of(ModItems.CAPTAIN_PIKE.get(), ModItems.WATERWAY_FANG.get(), ModItems.STONE_WHALE_PLATING.get());
    }

    public static List<Item> armorSet() {
        return List.of(ModItems.STONE_WHALE_HELMET.get(), ModItems.STONE_WHALE_CHESTPLATE.get(),
                ModItems.STONE_WHALE_LEGGINGS.get(), ModItems.STONE_WHALE_BOOTS.get());
    }

    // 図鑑: 天井が数える装備 (甲殻4部位・大剣・護符・職の章の武器 5 つ・専用の逸品3つ)
    public static List<Item> collection() {
        List<Item> all = new ArrayList<>(armorSet());
        all.add(ModItems.WHALEBONE_GREATSWORD.get());
        all.add(ModItems.RESONANCE_CHARM.get());
        // 章の杖と鈴杖 (docs/design/43 の 5 段目)
        all.add(jp.revati.skyabyss.item.ChapterGear.WHALE_STAFF_ITEM.get());
        all.add(jp.revati.skyabyss.item.ChapterGear.WHALE_BELL_ITEM.get());
        all.add(jp.revati.skyabyss.item.ChapterGear.WHALE_HAMMER_ITEM.get());
        all.add(jp.revati.skyabyss.item.ChapterGear.WHALE_BOW_ITEM.get());
        all.add(jp.revati.skyabyss.item.ChapterGear.WHALE_DAGGER_ITEM.get());
        all.addAll(exclusives());
        return all;
    }

    // 心臓での、その装備の基本の確率
    public static float heartChance(Item item) {
        if (armorSet().contains(item)) {
            return ARMOR_CHANCE;
        }
        if (item == ModItems.WHALEBONE_GREATSWORD.get()) {
            return GREATSWORD_CHANCE;
        }
        if (item == ModItems.RESONANCE_CHARM.get()) {
            return CHARM_CHANCE;
        }
        if (jp.revati.skyabyss.item.ChapterGear.WHALE.contains(id(item))) {
            return jp.revati.skyabyss.item.ChapterGear.CHANCE;
        }
        if (item == ModItems.CAPTAIN_PIKE.get()) {
            return PIKE_CHANCE;
        }
        if (item == ModItems.WATERWAY_FANG.get()) {
            return FANG_CHANCE;
        }
        if (item == ModItems.STONE_WHALE_PLATING.get()) {
            return PLATING_CHANCE;
        }
        return 0f;
    }

    public static boolean isExclusive(Item item) {
        return exclusives().contains(item);
    }

    public static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    public static int collected(TitanMemoryData.Record r) {
        return (int) collection().stream().filter(i -> r.owned.contains(id(i))).count();
    }

    // ---------------------------------------------------------------- 抽選 (ここだけで決まる。自己テストからも呼ぶ)
    // pieces: 出た図鑑の装備 / fresh: そのうち初めてのもの / guaranteed: ハード天井で決まったもの
    public record Roll(List<ItemStack> items, List<Item> pieces, List<Item> fresh, @Nullable Item guaranteed, boolean pityShards, int memory) {
    }

    public static float pityMultiplier(int memory) {
        return memory >= SOFT_PITY ? SOFT_MULTIPLIER : 1f;
    }

    private static int between(RandomSource random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    // 心臓の撃破1回ぶんの、一人の報酬 (天の鍵の欠片と初回の選択は onHeartDefeat が扱う)
    public static Roll rollHeart(TitanMemoryData.Record r, RandomSource random) {
        r.memory++;
        r.clears++;
        int memory = r.memory;
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(ModItems.STONE_WHALE_SHARD.get(), between(random, 4, 7)));
        items.add(new ItemStack(ModItems.RESONANCE_SHARD_STONE_WHALE.get(), between(random, 1, 3)));
        // 心核は初めての撃破で必ず、そのあとは 25%
        if (r.clears == 1 || random.nextFloat() < CORE_CHANCE) {
            items.add(new ItemStack(ModItems.STONE_WHALE_CORE.get()));
        }
        if (random.nextFloat() < CRYSTAL_CHANCE) {
            items.add(new ItemStack(ModItems.RESONANCE_CRYSTAL.get()));
        }
        if (random.nextFloat() < JERKY_CHANCE) {
            items.add(new ItemStack(ModItems.CLOUD_JERKY.get(), between(random, 2, 3)));
        }
        if (random.nextFloat() < ARROW_CHANCE) {
            items.add(new ItemStack(ModItems.BONE_SPIKE_ARROW.get(), between(random, 8, 16)));
        }
        if (random.nextFloat() < TALISMAN_CHANCE) {
            items.add(new ItemStack(ModItems.MOSS_TALISMAN.get()));
        }
        List<Item> pieces = new ArrayList<>();
        Item guaranteed = null;
        boolean pityShards = false;
        if (memory >= HARD_PITY) {
            List<Item> missing = collection().stream().filter(i -> !r.owned.contains(id(i))).toList();
            if (missing.isEmpty()) {
                pityShards = true;
                items.add(new ItemStack(ModItems.RESONANCE_SHARD_STONE_WHALE.get(), PITY_SHARDS));
                r.memory = 0;
            } else {
                guaranteed = missing.get(random.nextInt(missing.size()));
                pieces.add(guaranteed);
            }
        }
        float mult = pityMultiplier(memory);
        for (Item item : collection()) {
            if (item != guaranteed && random.nextFloat() < heartChance(item) * mult) {
                pieces.add(item);
            }
        }
        List<Item> fresh = record(r, pieces, items, random);
        return new Roll(items, pieces, fresh, guaranteed, pityShards, memory);
    }

    // 番兵長の撃破1回ぶんの、一人の報酬 (経験値は別。記憶のカウンタは進めない)
    public static Roll rollCaptain(TitanMemoryData.Record r, RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        if (random.nextFloat() < CAPTAIN_SHARD_CHANCE) {
            items.add(new ItemStack(ModItems.STONE_WHALE_SHARD.get(), between(random, 2, 4)));
        }
        if (random.nextFloat() < CAPTAIN_RESONANCE_CHANCE) {
            items.add(new ItemStack(ModItems.RESONANCE_SHARD_STONE_WHALE.get(), between(random, 1, 2)));
        }
        if (random.nextFloat() < CAPTAIN_JERKY_CHANCE) {
            items.add(new ItemStack(ModItems.CLOUD_JERKY.get(), between(random, 1, 2)));
        }
        List<Item> pieces = new ArrayList<>();
        if (random.nextFloat() < CAPTAIN_PIKE_CHANCE) {
            pieces.add(ModItems.CAPTAIN_PIKE.get());
        }
        List<Item> fresh = record(r, pieces, items, random);
        return new Roll(items, pieces, fresh, null, false, r.memory);
    }

    // 出た装備を渡す品に加え (特性はここで決まる)、図鑑に記す。初めてのものがあれば記憶を 0 に戻す
    private static List<Item> record(TitanMemoryData.Record r, List<Item> pieces, List<ItemStack> items, RandomSource random) {
        List<Item> fresh = new ArrayList<>();
        for (Item piece : pieces) {
            items.add(AffixRules.withAffixes(new ItemStack(piece), random));
            if (r.owned.add(id(piece))) {
                fresh.add(piece);
            }
        }
        if (!fresh.isEmpty()) {
            r.memory = 0;
        }
        return fresh;
    }

    // 道中の番兵を倒した人への、一体ぶんの抽選。甲殻が出たら図鑑にも記す
    public static List<ItemStack> rollGuard(LivingEntity mob, @Nullable TitanMemoryData.Record r, RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        if (mob instanceof BoneMortar || mob instanceof BoneGull || mob instanceof ElderSentinel) {
            return rollNewGuard(mob, r, random);
        }
        if (!(mob instanceof BoneSentinel || mob instanceof RibArcher || mob instanceof MossCrawler)) {
            return items;
        }
        if (random.nextFloat() < (mob instanceof BoneSentinel ? 0.30f : 0.25f)) {
            items.add(new ItemStack(ModItems.STONE_WHALE_SHARD.get(), between(random, 1, 2)));
        }
        if (random.nextFloat() < 0.05f) {
            items.add(new ItemStack(ModItems.RESONANCE_SHARD_STONE_WHALE.get()));
        }
        if (random.nextFloat() < 0.03f) {
            items.add(new ItemStack(ModItems.CLOUD_JERKY.get()));
        }
        if (mob instanceof RibArcher && random.nextFloat() < 0.25f) {
            items.add(new ItemStack(ModItems.BONE_SPIKE_ARROW.get(), between(random, 2, 4)));
        }
        if (mob instanceof MossCrawler && random.nextFloat() < 0.03f) {
            items.add(new ItemStack(ModItems.MOSS_TALISMAN.get()));
        }
        if (mob instanceof BoneSentinel && random.nextFloat() < 0.01f) {
            Item piece = armorSet().get(random.nextInt(4));
            items.add(AffixRules.withAffixes(new ItemStack(piece), random));
            if (r != null && r.owned.add(id(piece))) {
                r.memory = 0;
            }
        }
        return items;
    }

    // 背の新しい敵: 骨の砲手・骨鳥は欠片と骨片、老番兵 (名づき) は欠片・岩殻片・骨片が必ずいくらか、防具の欠片が低い確率で
    public static List<ItemStack> rollNewGuard(LivingEntity mob, @Nullable TitanMemoryData.Record r, RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        if (mob instanceof ElderSentinel) {
            items.add(new ItemStack(ModItems.STONE_WHALE_SHARD.get(), between(random, 2, 3)));
            items.add(new ItemStack(jp.revati.skyabyss.guide.GuideSetup.BONE_CHIP.get(), between(random, 2, 4)));
            if (random.nextFloat() < 0.60f) {
                items.add(new ItemStack(jp.revati.skyabyss.guide.GuideSetup.ROCK_SHELL_SCRAP.get(), between(random, 1, 2)));
            }
            if (random.nextFloat() < 0.15f) {
                items.add(new ItemStack(ModItems.RESONANCE_SHARD_STONE_WHALE.get()));
            }
            if (random.nextFloat() < 0.10f) {
                items.add(new ItemStack(ModItems.CLOUD_JERKY.get()));
            }
            if (random.nextFloat() < 0.08f) {
                Item piece = armorSet().get(random.nextInt(4));
                items.add(AffixRules.withAffixes(new ItemStack(piece), random));
                if (r != null && r.owned.add(id(piece))) {
                    r.memory = 0;
                }
            }
            return items;
        }
        boolean mortar = mob instanceof BoneMortar;
        if (random.nextFloat() < (mortar ? 0.25f : 0.08f)) {
            items.add(new ItemStack(ModItems.STONE_WHALE_SHARD.get(), between(random, 1, mortar ? 2 : 1)));
        }
        if (random.nextFloat() < (mortar ? 0.40f : 0.30f)) {
            items.add(new ItemStack(jp.revati.skyabyss.guide.GuideSetup.BONE_CHIP.get(), between(random, 1, mortar ? 2 : 1)));
        }
        if (random.nextFloat() < (mortar ? 0.04f : 0.02f)) {
            items.add(new ItemStack(ModItems.RESONANCE_SHARD_STONE_WHALE.get()));
        }
        if (random.nextFloat() < 0.03f) {
            items.add(new ItemStack(ModItems.CLOUD_JERKY.get()));
        }
        return items;
    }

    // ---------------------------------------------------------------- 入口 (T が撃破時に呼ぶ。FIX_PLAN の約束)
    // participants: その戦いに参加したオンラインの人 (途中で倒れて復活待ちの人も含む。経験値はこの人たちへ)
    // ids: 参加者の UUID (切断した人も含む。進み・欠片・報酬は UUID で渡し、オフラインの人は預かりへ入る)
    public static void onHeartDefeat(ServerLevel level, List<ServerPlayer> participants, java.util.Collection<UUID> ids) {
        List<UUID> all = allIds(participants, ids);
        // 物語の進み (経験値より先に。レベルの上限が上がってから心臓の経験値を渡す。progress/Progression)
        jp.revati.skyabyss.progress.Progression.onTitanStopped(level.getServer(), all, STONE_WHALE);
        // 成長の経験値 (参加者それぞれに満額。rpg/GrowthRewards)
        jp.revati.skyabyss.rpg.GrowthRewards.onBossDefeat(level, participants, "heart");
        MinecraftServer server = level.getServer();
        TitanBackData data = TitanBackData.get(level);
        for (UUID uuid : all) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            TitanMemoryData.Record r = memory(server).record(uuid, STONE_WHALE);
            if (canReceive(player)) {
                syncOwned(player, r);
            }
            List<ItemStack> items = new ArrayList<>();
            boolean fragment = false;
            if (!r.fragment) {
                r.fragment = true;
                // 以前の記録 (背中のデータ) で受け取り済みなら渡さない
                if (data.claimFragment(uuid)) {
                    fragment = true;
                    items.add(new ItemStack(ModItems.SKY_KEY_FRAGMENT.get()));
                }
            } else if (canReceive(player) && jp.revati.skyabyss.record.Keepsakes.fragmentMissing(player)) {
                // 受け取ったのにどこにも見当たらない欠片は、鼓動のそばで再び見つかる (11 の 4章)
                items.add(new ItemStack(ModItems.SKY_KEY_FRAGMENT.get()));
                Notice.narrate(player, "skyabyss_record.guard.fragment_restored");
            }
            Roll roll = rollHeart(r, level.random);
            items.addAll(roll.items());
            // 属性の欠片・技の修飾ルーン (item/extra/GearFreedomLoot。装備の確率は変えない)
            items.addAll(jp.revati.skyabyss.item.extra.GearFreedomLoot.heartDrops(STONE_WHALE, level.random));
            // 巨神のヒロイック武器 (図鑑の表の外。一本ずつまれに。item/HeroicGear)
            items.addAll(jp.revati.skyabyss.item.HeroicGear.heartDrops(STONE_WHALE, level.random));
            int held = handOver(server, uuid, player, items, 0);
            boolean first = !r.firstCleared;
            if (first) {
                r.firstCleared = true;
                r.choicePending = jp.revati.skyabyss.loot.reward.HeartReward.CHOICE_ON_FIRST_CLEAR;
            }
            if (player == null) {
                continue;
            }
            if (fragment) {
                Notice.unlock(player, "message.skyabyss.fragment_received");
            }
            Notice.narrate(player, "message.skyabyss.boss_reward");
            Notice.gain(player, rewardLine(items));
            announcePieces(level, player, r, roll);
            if (roll.guaranteed() != null) {
                Notice.narrate(player, "message.skyabyss.memory_guarantee");
            } else if (roll.pityShards()) {
                Notice.narrate(player, "message.skyabyss.memory_all_owned");
            }
            Notice.gain(player, memoryLine(r));
            tellHeld(player, held);
            if (first) {
                offerChoice(player);
            }
        }
    }

    public static void onCaptainDefeat(ServerLevel level, List<ServerPlayer> participants, java.util.Collection<UUID> ids) {
        // 成長の経験値 (参加者それぞれに満額。rpg/GrowthRewards)
        jp.revati.skyabyss.rpg.GrowthRewards.onBossDefeat(level, participants, "captain");
        MinecraftServer server = level.getServer();
        for (UUID uuid : allIds(participants, ids)) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            TitanMemoryData.Record r = memory(server).record(uuid, STONE_WHALE);
            Roll roll = rollCaptain(r, level.random);
            int held = handOver(server, uuid, player, roll.items(), CAPTAIN_XP);
            if (player == null) {
                continue;
            }
            Notice.narrate(player, "message.skyabyss.mid_boss_reward");
            Notice.gain(player, rewardLine(roll.items()));
            announcePieces(level, player, r, roll);
            tellHeld(player, held);
        }
    }

    // 道中の番兵: 倒した人にだけ拾える形で落とす (背中の上で、プレイヤーが倒したときだけ)
    @SubscribeEvent
    static void onGuardDrops(LivingDropsEvent event) {
        LivingEntity mob = event.getEntity();
        if (!(mob.level() instanceof ServerLevel level) || level.dimension() != TitanTravel.TITAN_BACK) {
            return;
        }
        ServerPlayer killer = null;
        if (event.getSource().getEntity() instanceof ServerPlayer p) {
            killer = p;
        } else if (mob.getKillCredit() instanceof ServerPlayer p) {
            killer = p;
        }
        if (killer == null) {
            return;
        }
        for (ItemStack stack : rollGuard(mob, record(killer), level.random)) {
            ItemEntity item = new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), stack);
            item.setTarget(killer.getUUID());
            item.setDefaultPickUpDelay();
            event.getDrops().add(item);
        }
    }

    // ---------------------------------------------------------------- 渡す・預かる
    private static List<UUID> unique(List<ServerPlayer> participants) {
        Set<UUID> seen = new LinkedHashSet<>();
        for (ServerPlayer p : participants) {
            if (p != null) {
                seen.add(p.getUUID());
            }
        }
        return new ArrayList<>(seen);
    }

    // オンラインの人と UUID の集合を合わせる (切断した人も落とさない)
    static List<UUID> allIds(List<ServerPlayer> participants, java.util.Collection<UUID> ids) {
        Set<UUID> seen = new LinkedHashSet<>(unique(participants));
        seen.addAll(ids);
        return new ArrayList<>(seen);
    }

    private static TitanMemoryData memory(MinecraftServer server) {
        return TitanMemoryData.get(server);
    }

    private static TitanMemoryData.Record record(ServerPlayer player) {
        return memory(player.server).record(player.getUUID(), STONE_WHALE);
    }

    // 生きていて、いまサーバーにいる人だけが、その場で受け取れる。
    // 倒れている人に渡すと、目を覚ましたときに預かり物 (TitanBackTrials) で上書きされて消えるため
    public static boolean canReceive(@Nullable ServerPlayer player) {
        return player != null && player.isAlive() && !player.hasDisconnected();
    }

    // 手もとにある図鑑の装備は、持っているものとして数える (以前の宝箱で得たものなど)
    private static void syncOwned(ServerPlayer player, TitanMemoryData.Record r) {
        List<Item> all = collection();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && all.contains(stack.getItem())) {
                r.owned.add(id(stack.getItem()));
            }
        }
    }

    // 渡せる分は手もとへ。受け取れない人の分と、あふれた分は預かる。預かった品の数を返す
    public static int handOver(MinecraftServer server, UUID uuid, @Nullable ServerPlayer player, List<ItemStack> items, int xp) {
        if (!canReceive(player)) {
            TitanMemoryData.Pending pending = memory(server).pending(uuid);
            items.forEach(s -> pending.items.add(s.copy()));
            pending.xp += xp;
            return Math.max(1, items.size());
        }
        if (xp > 0) {
            player.giveExperiencePoints(xp);
        }
        int held = 0;
        for (ItemStack stack : items) {
            ItemStack left = give(player, stack.copy());
            if (!left.isEmpty()) {
                memory(server).pending(uuid).items.add(left);
                held++;
            }
        }
        return held;
    }

    // 手もとへ入れる (留め袋に入る品は袋へ。docs/design/22 の 4.1)。入りきらなかった分を返す
    private static ItemStack give(ServerPlayer player, ItemStack stack) {
        return jp.revati.skyabyss.pouch.PouchRouting.give(player, stack);
    }

    public static void tellHeld(ServerPlayer player, int held) {
        if (held <= 0) {
            return;
        }
        if (canReceive(player)) {
            Notice.warn(player, "message.skyabyss.reward_overflow", held);
        } else {
            Notice.hint(player, "message.skyabyss.reward_pending");
        }
    }

    // 預かっている報酬を渡す。入りきらない分はまた預かる
    public static void deliver(ServerPlayer player, boolean quietIfEmpty) {
        TitanMemoryData data = memory(player.server);
        if (!data.hasPending(player.getUUID())) {
            if (!quietIfEmpty) {
                Notice.hint(player, "message.skyabyss.reward_none_pending");
            }
            return;
        }
        if (!canReceive(player)) {
            return;
        }
        TitanMemoryData.Pending pending = data.takePending(player.getUUID());
        if (pending.xp > 0) {
            player.giveExperiencePoints(pending.xp);
        }
        List<ItemStack> given = new ArrayList<>();
        int held = 0;
        for (ItemStack stack : pending.items) {
            int before = stack.getCount();
            ItemStack shown = stack.copy();
            ItemStack left = give(player, stack);
            if (left.getCount() < before) {
                given.add(shown.copyWithCount(before - left.getCount()));
            }
            if (!left.isEmpty()) {
                data.pending(player.getUUID()).items.add(left);
                held++;
            }
        }
        Notice.unlock(player, "message.skyabyss.reward_delivered");
        if (!given.isEmpty()) {
            Notice.gain(player, rewardLine(given));
        }
        tellHeld(player, held);
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            deliver(player, true);
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            deliver(player, true);
        }
    }

    // ---------------------------------------------------------------- 知らせ
    // 受け取ったもの: 【岩鯨の鱗片】×5 【共鳴の欠片】×2 … (名前にカーソルで説明)
    public static MutableComponent rewardLine(List<ItemStack> items) {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (ItemStack s : items) {
            counts.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        MutableComponent list = Component.empty();
        if (counts.isEmpty()) {
            list.append(Component.translatable("message.skyabyss.reward_nothing").withStyle(ChatFormatting.DARK_GRAY));
        }
        counts.forEach((item, count) -> {
            list.append(itemLink(new ItemStack(item)));
            if (count > 1) {
                list.append(Component.literal("×" + count).withStyle(ChatFormatting.GRAY));
            }
            list.append(" ");
        });
        return ModText.hint("message.skyabyss.reward_list", list);
    }

    private static void announcePieces(ServerLevel level, ServerPlayer player, TitanMemoryData.Record r, Roll roll) {
        for (Item piece : roll.pieces()) {
            // 特性の付いた実物を見せる (名前にカーソルで特性まで見える)
            ItemStack shown = roll.items().stream().filter(s -> s.is(piece)).findFirst().orElseGet(() -> new ItemStack(piece));
            boolean fresh = roll.fresh().contains(piece);
            if (isExclusive(piece)) {
                player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
                Notice.alert(player, fresh ? "message.skyabyss.legendary_new" : "message.skyabyss.legendary_dupe", itemLink(shown));
                for (ServerPlayer other : Players.snapshot(level.players())) {
                    if (other != player) {
                        Notice.unlock(other, "message.skyabyss.legendary_other", player.getDisplayName(), itemLink(shown));
                    }
                }
            } else if (fresh) {
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
                Notice.unlock(player, "message.skyabyss.gear_new", itemLink(shown), collected(r), collection().size());
            }
        }
    }

    // 名前にカーソルを合わせると、装備の説明が出る
    public static MutableComponent itemLink(ItemStack stack) {
        return Component.literal("【").append(stack.getHoverName()).append("】")
                .withStyle(s -> s.withColor(0xF2B53A).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(stack))));
    }

    // 巨神の記憶 ◆◆◆◇◇ 3/5 (段階に応じた添え書き)
    public static MutableComponent memoryLine(TitanMemoryData.Record r) {
        MutableComponent bar = Component.empty();
        for (int i = 1; i <= HARD_PITY; i++) {
            boolean lit = i <= r.memory;
            int color = !lit ? 0x4A4E5A : i >= HARD_PITY ? 0xF2B53A : i >= SOFT_PITY ? 0xB89CFF : 0x9FE8FF;
            bar.append(Component.literal(lit ? "◆" : "◇").withStyle(s -> s.withColor(color)));
        }
        MutableComponent line = Component.literal("✦ ").withStyle(s -> s.withColor(0x9FE8FF))
                .append(Component.translatable("message.skyabyss.memory_label").withStyle(s -> s.withColor(0x9FE8FF)))
                .append(" ").append(bar)
                .append(Component.literal(" " + Math.min(r.memory, HARD_PITY) + "/" + HARD_PITY).withStyle(ChatFormatting.GRAY));
        if (r.memory >= HARD_PITY - 1) {
            line.append(Component.literal("  ")).append(Component.translatable("message.skyabyss.memory_next_guaranteed").withStyle(s -> s.withColor(0xF2B53A)));
        } else if (r.memory >= SOFT_PITY - 1) {
            line.append(Component.literal("  ")).append(Component.translatable("message.skyabyss.memory_soft").withStyle(s -> s.withColor(0xB89CFF)));
        }
        return line;
    }

    // 初回クリアの選択報酬: 画面 (loot/reward/HeartReward) が眠りの場面のあとに開く。ここは案内の一行だけ
    private static void offerChoice(ServerPlayer player) {
        jp.revati.skyabyss.loot.reward.HeartReward.announce(player, STONE_WHALE);
    }

    // ---------------------------------------------------------------- コマンド
    // /titanmemory                 : 自分の巨神の記憶と図鑑を見る (誰でも)
    // /titanmemory choose <装備>    : 初回クリアの報酬を選ぶ (チャットの名前を押すと走る)
    // /titanmemory claim           : 預かっている報酬を受け取る
    // /skyabyss memory set <人> <n> : 記憶のカウンタを変える (管理者・テスト用)
    // /skyabyss memory clear <人>   : 記録を消す (管理者・テスト用。天の鍵の欠片の記録は残す)
    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("titanmemory")
                .executes(TitanLoot::status)
                .then(Commands.literal("claim").executes(ctx -> {
                    deliver(ctx.getSource().getPlayerOrException(), false);
                    return 1;
                }))
                .then(Commands.literal("choose").then(Commands.argument("item", ResourceLocationArgument.id()).executes(TitanLoot::choose))));
        dispatcher.register(Commands.literal(SkyAbyss.MODID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("memory")
                        .then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, HARD_PITY)).executes(ctx -> {
                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                    record(p).memory = IntegerArgumentType.getInteger(ctx, "value");
                                    ctx.getSource().sendSuccess(() -> memoryLine(record(p)), true);
                                    return 1;
                                }))))
                        .then(Commands.literal("clear").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                            TitanMemoryData.Record r = record(p);
                            r.memory = 0;
                            r.clears = 0;
                            r.owned.clear();
                            r.firstCleared = false;
                            r.choicePending = false;
                            ctx.getSource().sendSuccess(() -> memoryLine(r), true);
                            return 1;
                        })))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TitanMemoryData.Record r = record(player);
        player.sendSystemMessage(ModText.title("message.skyabyss.memory_title_stone_whale"));
        player.sendSystemMessage(memoryLine(r));
        player.sendSystemMessage(ModText.hint("message.skyabyss.memory_clears", r.clears, collected(r), collection().size()));
        MutableComponent owned = Component.empty();
        for (Item item : collection()) {
            boolean has = r.owned.contains(id(item));
            owned.append(Component.literal(has ? "■ " : "□ ").withStyle(has ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
            if (has) {
                owned.append(itemLink(new ItemStack(item)));
            } else if (isExclusive(item)) {
                owned.append(Component.literal("？？？").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                owned.append(new ItemStack(item).getHoverName().copy().withStyle(ChatFormatting.DARK_GRAY));
            }
            owned.append("  ");
        }
        player.sendSystemMessage(owned);
        // 共鳴の炉での交換 (欠片 30 で専用の逸品)
        ForgeRules.ExchangeState exchange = exchangeState(r);
        if (exchange == ForgeRules.ExchangeState.READY || exchange == ForgeRules.ExchangeState.USED) {
            player.sendSystemMessage(ModText.hint(exchange == ForgeRules.ExchangeState.READY
                    ? "message.skyabyss.exchange.status_ready" : "message.skyabyss.exchange.status_used"));
        }
        if (memory(player.server).hasPending(player.getUUID())) {
            player.sendSystemMessage(ModText.hint("message.skyabyss.reward_pending"));
        }
        if (r.choicePending) {
            offerChoice(player);
        }
        return 1;
    }

    private static int choose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceLocation choice = ResourceLocationArgument.getId(ctx, "item");
        // 決まりは画面と同じ一箇所 (HeartReward.claim)
        if (jp.revati.skyabyss.loot.reward.HeartReward.claim(player, STONE_WHALE, choice.toString()) != jp.revati.skyabyss.loot.reward.HeartReward.Result.OK) {
            Notice.warn(player, "message.skyabyss.first_clear_none");
            return 0;
        }
        return 1;
    }

    // ---------------------------------------------------------------- 交換 (共鳴の炉。03 の 2.4節)
    // 共鳴の欠片 30 で、まだ持っていない専用の逸品を一つ。心臓を倒すごとに一度 (周回を促し、急がせない)。
    // 天井のカウンタは戻さない (買ったものは「心臓から出た新しい品」ではないため。初回の選択と同じ扱い)
    public static List<Item> missingExclusives(TitanMemoryData.Record r) {
        return exclusives().stream().filter(i -> !r.owned.contains(id(i))).toList();
    }

    public static ForgeRules.ExchangeState exchangeState(@Nullable TitanMemoryData.Record r) {
        if (r == null || r.clears <= 0) {
            return ForgeRules.ExchangeState.NO_CLEAR;
        }
        if (missingExclusives(r).isEmpty()) {
            return ForgeRules.ExchangeState.ALL_OWNED;
        }
        return r.lastExchangeClears >= r.clears ? ForgeRules.ExchangeState.USED : ForgeRules.ExchangeState.READY;
    }

    // 炉の画面に見せる: 持っている専用の逸品の印 (1 ビットずつ)
    public static int ownedExclusiveMask(@Nullable TitanMemoryData.Record r) {
        int mask = 0;
        List<Item> all = exclusives();
        for (int i = 0; i < all.size(); i++) {
            if (r != null && r.owned.contains(id(all.get(i)))) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    @Nullable
    public static TitanMemoryData.Record peek(ServerPlayer player) {
        return memory(player.server).peek(player.getUUID(), STONE_WHALE);
    }

    // 交換の決まり (払う前に検める)。できたら null、できなければ断る理由の翻訳キー
    @Nullable
    public static String exchangeDenial(TitanMemoryData.Record r, Item item, boolean affordable) {
        return switch (exchangeState(r)) {
            case NO_CLEAR -> "message.skyabyss.exchange.no_clear";
            case USED -> "message.skyabyss.exchange.used";
            case ALL_OWNED -> "message.skyabyss.exchange.all_owned";
            case READY -> !isExclusive(item) || r.owned.contains(id(item)) ? "message.skyabyss.exchange.owned"
                    : !affordable ? "message.skyabyss.exchange.no_shards" : null;
        };
    }

    // 交換する。払う・図鑑に記す・渡す。できたら true
    public static boolean exchange(ServerPlayer player, Item item) {
        TitanMemoryData.Record r = record(player);
        syncOwned(player, r);
        String denial = exchangeDenial(r, item, ForgeRules.exchangeAffordable(player, STONE_WHALE));
        if (denial != null) {
            Notice.warn(player, denial);
            return false;
        }
        ForgeRules.payExchange(player, STONE_WHALE);
        r.owned.add(id(item));
        r.lastExchangeClears = r.clears;
        r.exchanges++;
        ItemStack stack = AffixRules.withAffixes(new ItemStack(item), player.getRandom());
        int held = handOver(player.server, player.getUUID(), player, List.of(stack), 0);
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        Notice.alert(player, "message.skyabyss.exchange.done", itemLink(stack));
        tellHeld(player, held);
        return true;
    }

    // ---------------------------------------------------------------- 自己テスト: 天井が効くか、何周で揃うか (1〜4人)
    public static List<String> selfTest() {
        List<String> failures = new ArrayList<>();
        RandomSource random = RandomSource.create(12345);
        List<Item> all = collection();
        List<Item> armor = armorSet();
        int parties = 10000;
        for (int players = 1; players <= 4; players++) {
            long armorSum = 0;
            long rareSum = 0;
            long allSum = 0;
            long partyArmorSum = 0;
            long clears = 0;
            long pityHits = 0;
            int worstAll = 0;
            int longestDry = 0;
            for (int t = 0; t < parties; t++) {
                List<TitanMemoryData.Record> party = new ArrayList<>();
                int[] armorAt = new int[players];
                int[] rareAt = new int[players];
                int[] allAt = new int[players];
                int[] dry = new int[players];
                for (int p = 0; p < players; p++) {
                    party.add(new TitanMemoryData.Record());
                }
                int run = 0;
                boolean done = false;
                while (!done && run < 200) {
                    run++;
                    done = true;
                    for (int p = 0; p < players; p++) {
                        TitanMemoryData.Record r = party.get(p);
                        if (allAt[p] > 0) {
                            continue;
                        }
                        // 番兵長も毎回倒す (番兵長の鉾が初めて出ても、記憶は 0 に戻る)
                        boolean captainFresh = !rollCaptain(r, random).fresh().isEmpty();
                        Roll roll = rollHeart(r, random);
                        clears++;
                        if (roll.guaranteed() != null) {
                            pityHits++;
                        }
                        if (run == 1) {
                            // 初回の選択 (専用の逸品のどれか一つ)
                            r.owned.add(id(exclusives().get(random.nextInt(3))));
                        }
                        dry[p] = roll.fresh().isEmpty() && !captainFresh ? dry[p] + 1 : 0;
                        longestDry = Math.max(longestDry, dry[p]);
                        if (armorAt[p] == 0 && armor.stream().allMatch(i -> r.owned.contains(id(i)))) {
                            armorAt[p] = run;
                        }
                        if (rareAt[p] == 0 && all.subList(0, 6).stream().allMatch(i -> r.owned.contains(id(i)))) {
                            rareAt[p] = run;
                        }
                        if (all.stream().allMatch(i -> r.owned.contains(id(i)))) {
                            allAt[p] = run;
                        } else {
                            done = false;
                        }
                    }
                }
                int partyArmor = 0;
                for (int p = 0; p < players; p++) {
                    armorSum += armorAt[p];
                    rareSum += rareAt[p];
                    allSum += allAt[p];
                    worstAll = Math.max(worstAll, allAt[p]);
                    partyArmor = Math.max(partyArmor, armorAt[p]);
                    if (allAt[p] == 0) {
                        failures.add("loot sim: collection not complete within 200 clears");
                    }
                }
                partyArmorSum += partyArmor;
            }
            double n = parties * (double) players;
            SkyAbyss.LOGGER.info("[selftest] loot sim {} player(s) x {} parties: per player avg clears -> armor set {}, armor+sword+charm {}, full collection {} (worst {}); whole party armor set {}; hard pity on {}% of clears; longest dry streak {}",
                    players, parties, fmt(armorSum / n), fmt(rareSum / n), fmt(allSum / n), worstAll,
                    fmt(partyArmorSum / (double) parties), fmt(pityHits * 100.0 / clears), longestDry);
            if (longestDry >= HARD_PITY) {
                failures.add("loot sim: " + longestDry + " clears in a row without a new piece (pity should stop at " + (HARD_PITY - 1) + ")");
            }
            double avgArmor = armorSum / n;
            if (avgArmor < 4 || avgArmor > 9) {
                failures.add("loot sim: armor set takes " + fmt(avgArmor) + " clears on average (target 5-8)");
            }
            if (allSum / n < 8) {
                failures.add("loot sim: full collection is too fast (" + fmt(allSum / n) + " clears)");
            }
        }
        // 1回の撃破で全部は出ない (どの人数でも、一度で図鑑の半分を超えることはまれ)
        int bigFirst = 0;
        for (int i = 0; i < 10000; i++) {
            TitanMemoryData.Record r = new TitanMemoryData.Record();
            if (rollHeart(r, random).fresh().size() >= all.size()) {
                bigFirst++;
            }
        }
        if (bigFirst > 0) {
            failures.add("one clear yielded the whole collection " + bigFirst + " times");
        }
        // 番兵長: 必ず出る品はない
        int captainEmpty = 0;
        int captainPike = 0;
        for (int i = 0; i < 10000; i++) {
            Roll roll = rollCaptain(new TitanMemoryData.Record(), random);
            if (roll.items().isEmpty()) {
                captainEmpty++;
            }
            if (!roll.pieces().isEmpty()) {
                captainPike++;
            }
        }
        SkyAbyss.LOGGER.info("[selftest] loot sim captain: nothing but xp {}%, captain pike {}%",
                fmt(captainEmpty / 100.0), fmt(captainPike / 100.0));
        if (captainEmpty == 0) {
            failures.add("captain always drops an item");
        }
        // 全部そろったあとの天井は、共鳴の欠片に変わる
        TitanMemoryData.Record full = new TitanMemoryData.Record();
        all.forEach(i -> full.owned.add(id(i)));
        full.memory = HARD_PITY - 1;
        Roll roll = rollHeart(full, random);
        if (roll.guaranteed() != null || !roll.pityShards() || full.memory != 0) {
            failures.add("pity with everything owned should convert to resonance shards");
        }
        // 重なった装備ではカウンタは戻らない
        TitanMemoryData.Record dupe = new TitanMemoryData.Record();
        all.subList(0, all.size() - 1).forEach(i -> dupe.owned.add(id(i)));
        dupe.memory = 0;
        for (int i = 0; i < 400 && dupe.memory < HARD_PITY - 1; i++) {
            rollHeart(dupe, random);
            if (dupe.owned.size() == all.size()) {
                break;
            }
        }
        if (dupe.owned.size() < all.size() && dupe.memory != HARD_PITY - 1) {
            failures.add("duplicate piece reset the memory counter");
        }
        // 天井の5回目では、まだない装備が必ず出る
        TitanMemoryData.Record edge = new TitanMemoryData.Record();
        edge.memory = HARD_PITY - 1;
        if (rollHeart(edge, random).guaranteed() == null) {
            failures.add("hard pity did not guarantee a missing piece");
        }
        return failures;
    }

    private static String fmt(double v) {
        return String.format("%.2f", v);
    }
}
