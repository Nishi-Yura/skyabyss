package jp.revati.skyabyss.titan.storm.heart;

import jp.revati.skyabyss.util.Players;

import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 雷翼の心臓の報酬 (12 の 6.3節)。型は岩鯨・潮殻 (TitanLoot、TideLoot) と同じ:
// - 一人ずつ別々の抽選で、持ち物へ直接。倒れていた・持ち物がいっぱいの人の分は預かる (TitanMemoryData の預かり)
// - 巨神の記憶 (天井) と図鑑は巨神ごとの記録 (TitanMemoryData の "thunder_wing")。岩鯨・潮殻の記録とは別に数える。
//   新しい品の出ない撃破で +1、3 以上で図鑑の確率 2 倍、5 で未所持を一つ保証。図鑑にない品を手にすると 0
// - 天の鍵の欠片は一人一回 (巨神ごと。三つ目の欠片)
// - 初めての撃破では、専用伝説三つから一つを選ぶ (/titanmemory storm)
// 装備の品 (羽衣・弓・足環・護符・専用伝説) と素材は、装備の担当 (12 の 11.2節の担当 5) が後で作る。表は品の ID で持ち、
// 登録されていない品は抽選から外す (図鑑にも記さない)。品が登録されれば、そのまま出るようになる。ID の表は 12 の 17 章
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormLoot {
    public static final String TITAN = Titans.THUNDER_WING;   // "thunder_wing"
    public static final int STORM_LEVEL = 24;
    // 成長の経験値 (12 の 6.6節)。試練の外で倒したときの量は GrowthRewards.MOD_ENEMIES の thunder_wing_heart
    public static final int HEART_XP = 480;
    public static final int HEART_FIRST_BONUS = 300;

    // 品の ID は装備の担当の StormGear (12 の 11.1節の名前)。まだ登録されていない品は抽選から外す
    public static final String SKY_KEY_FRAGMENT = id("sky_key_fragment");
    public static final String RESONANCE_SHARD = id("resonance_shard_thunder_wing");
    public static final String RESONANCE_CRYSTAL = id("resonance_crystal");
    public static final String SHARD = jp.revati.skyabyss.item.storm.StormGear.SHARD;
    public static final String CORE = jp.revati.skyabyss.item.storm.StormGear.CORE;
    public static final String FRUIT = jp.revati.skyabyss.item.storm.StormGear.FRUIT;
    // 専用伝説 (図鑑の並び: 雷獣の爪、羽守の薙刀、嵐の目の羽根)
    public static final List<String> EXCLUSIVES = jp.revati.skyabyss.item.storm.StormGear.SET.exclusives();

    public static final float CORE_CHANCE = 0.25f;
    public static final float CRYSTAL_CHANCE = 0.40f;
    public static final float FRUIT_CHANCE = 0.50f;

    // クエストと伝承へのフック: クエストの目的 "signal" に thunder_heart_stilled、伝承の項目 chapter1/storm_stilled (晴れた空、12 の 9.3節)。
    // 項目のデータは伝承の担当 (担当 6) が書く。項目がまだなければ Lore.unlock は何もしない
    public static final String SIGNAL = "thunder_heart_stilled";
    public static final String LORE_ENTRY = "chapter1/storm_stilled";

    private StormLoot() {
    }

    private static String id(String path) {
        return SkyAbyss.MODID + ":" + path;
    }

    // 図鑑 (10 品): 羽衣 4、弓、足環、護符、専用伝説 3 (雷獣の爪・羽守の薙刀・嵐の目の羽根)。
    // 表と確率は装備の担当の StormGear.SET (巨神ごとの装備の表、TitanGearSet。潮殻の TideLoot と同じ形)
    public static List<String> collection() {
        return jp.revati.skyabyss.item.storm.StormGear.SET.collection();
    }

    public static float chance(String item) {
        return jp.revati.skyabyss.item.storm.StormGear.SET.chance(item);
    }

    // 登録されている品か (まだ作られていない品は false)
    public static boolean registered(String id) {
        return item(id).isPresent();
    }

    private static Optional<Item> item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(rl);
    }

    public static int collected(TitanMemoryData.Record r) {
        return (int) collection().stream().filter(r.owned::contains).count();
    }

    // ---------------------------------------------------------------- 抽選 (ここだけで決まる。自己テストからも呼ぶ)
    public record Draw(String id, int count, boolean gear) {
    }

    // draws: 渡す品 / pieces: 出た図鑑の品 / fresh: そのうち初めて / guaranteed: ハード天井で決まった品
    public record Roll(List<Draw> draws, List<String> pieces, List<String> fresh, @Nullable String guaranteed, boolean pityShards, int memory) {
    }

    private static int between(RandomSource random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    // 撃破 1 回ぶんの、一人の報酬 (天の鍵の欠片と初回の選択は onHeartDefeat)。available: 出せる品か
    public static Roll rollHeart(TitanMemoryData.Record r, RandomSource random, Predicate<String> available) {
        r.memory++;
        r.clears++;
        int memory = r.memory;
        List<Draw> draws = new ArrayList<>();
        draws.add(new Draw(SHARD, between(random, 4, 7), false));
        draws.add(new Draw(RESONANCE_SHARD, between(random, 1, 3), false));
        if (r.clears == 1 || random.nextFloat() < CORE_CHANCE) {
            draws.add(new Draw(CORE, 1, false));
        }
        if (random.nextFloat() < CRYSTAL_CHANCE) {
            draws.add(new Draw(RESONANCE_CRYSTAL, 1, false));
        }
        if (random.nextFloat() < FRUIT_CHANCE) {
            draws.add(new Draw(FRUIT, between(random, 2, 3), false));
        }
        List<String> pieces = new ArrayList<>();
        String guaranteed = null;
        boolean pityShards = false;
        if (memory >= TitanLoot.HARD_PITY) {
            List<String> missing = collection().stream().filter(i -> available.test(i) && !r.owned.contains(i)).toList();
            if (missing.isEmpty()) {
                pityShards = true;
                draws.add(new Draw(RESONANCE_SHARD, TitanLoot.PITY_SHARDS, false));
                r.memory = 0;
            } else {
                guaranteed = missing.get(random.nextInt(missing.size()));
                pieces.add(guaranteed);
            }
        }
        float mult = TitanLoot.pityMultiplier(memory);
        for (String item : collection()) {
            if (!item.equals(guaranteed) && available.test(item) && random.nextFloat() < chance(item) * mult) {
                pieces.add(item);
            }
        }
        List<String> fresh = new ArrayList<>();
        for (String piece : pieces) {
            draws.add(new Draw(piece, 1, true));
            if (r.owned.add(piece)) {
                fresh.add(piece);
            }
        }
        if (!fresh.isEmpty()) {
            r.memory = 0;
        }
        return new Roll(draws, pieces, fresh, guaranteed, pityShards, memory);
    }

    // 品の ID から渡す品を作る。まだ登録されていない品は空 (数えて知らせる)
    private static List<ItemStack> stacks(List<Draw> draws, RandomSource random, List<String> unmade) {
        List<ItemStack> out = new ArrayList<>();
        for (Draw d : draws) {
            Optional<Item> item = item(d.id());
            if (item.isEmpty()) {
                unmade.add(d.id());
                continue;
            }
            ItemStack stack = new ItemStack(item.get(), d.count());
            out.add(d.gear() ? AffixRules.withAffixes(stack, random) : stack);
        }
        return out;
    }

    // ---------------------------------------------------------------- 撃破 (StormHeartBattle が呼ぶ)
    // 1. 物語の進み (雷翼を止めた: 余燼の出現と乗る資格、レベルの上限 27 → 33。上限が上がってから経験値を渡す)
    // 2. 成長の経験値 3. 一人ずつの報酬 (天の鍵の欠片は一人一回) 4. クエストと伝承へ知らせる
    public static void onHeartDefeat(ServerLevel level, List<ServerPlayer> participants, Collection<UUID> ids) {
        MinecraftServer server = level.getServer();
        List<UUID> unique = new ArrayList<>(new LinkedHashSet<>(ids));
        jp.revati.skyabyss.progress.Progression.onTitanStopped(server, unique, TITAN);
        giveGrowth(level, participants);
        TitanMemoryData memory = TitanMemoryData.get(server);
        for (UUID uuid : unique) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            TitanMemoryData.Record r = memory.record(uuid, TITAN);
            List<Draw> draws = new ArrayList<>();
            boolean fragment = false;
            if (!r.fragment) {
                r.fragment = true;
                fragment = true;
                draws.add(new Draw(SKY_KEY_FRAGMENT, 1, false));
            }
            Roll roll = rollHeart(r, level.random, StormLoot::registered);
            draws.addAll(roll.draws());
            List<String> unmade = new ArrayList<>();
            List<ItemStack> items = stacks(draws, level.random, unmade);
            // 属性の欠片・技の修飾ルーン (item/extra/GearFreedomLoot。装備の確率は変えない)
            items.addAll(jp.revati.skyabyss.item.extra.GearFreedomLoot.heartDrops(TITAN, level.random));
            int held = TitanLoot.handOver(server, uuid, player, items, 0);
            boolean first = !r.firstCleared;
            if (first) {
                r.firstCleared = true;
                r.choicePending = true;
            }
            memory.setDirty();
            if (!unmade.isEmpty()) {
                SkyAbyss.LOGGER.info("Thunder wing heart reward for {} skipped items that are not made yet: {}", uuid, unmade);
            }
            if (player == null) {
                continue;
            }
            hooks(player);
            if (fragment) {
                Notice.unlock(player, "message.skyabyss.fragment_received");
            }
            Notice.narrate(player, "message.skyabyss.storm_heart.reward");
            Notice.gain(player, TitanLoot.rewardLine(items));
            announce(level, player, r, roll, items);
            if (roll.guaranteed() != null) {
                Notice.narrate(player, "message.skyabyss.memory_guarantee");
            } else if (roll.pityShards()) {
                Notice.narrate(player, "message.skyabyss.memory_all_owned");
            }
            Notice.gain(player, TitanLoot.memoryLine(r));
            TitanLoot.tellHeld(player, held);
            if (first) {
                offerChoice(player, r);
            }
        }
    }

    // 成長の経験値: 参加者それぞれに満額 (推奨 Lv24 から離れると減る)。初めての撃破は上乗せ。
    // パーティの仲間でその場にいなかった人には、分け前の割合 (岩鯨の GrowthRewards.onBossDefeat と同じ決まり)
    private static void giveGrowth(ServerLevel level, List<ServerPlayer> participants) {
        List<ServerPlayer> seen = new ArrayList<>();
        for (ServerPlayer player : participants) {
            if (player == null || seen.contains(player)) {
                continue;
            }
            seen.add(player);
            int amount = (int) Math.round(HEART_XP * GrowthRules.levelFalloff(Growth.get(player).level, STORM_LEVEL));
            if (Growth.discover(player, "first:" + SkyAbyss.MODID + ":thunder_wing_heart")) {
                amount += HEART_FIRST_BONUS;
            }
            Growth.addXp(player, amount, "entity.skyabyss.thunder_wing_heart");
        }
        double rate = jp.revati.skyabyss.party.Parties.shareRate(level.getServer());
        for (ServerPlayer mate : jp.revati.skyabyss.party.Parties.absentMates(level, seen)) {
            int amount = (int) Math.round(HEART_XP * rate * GrowthRules.levelFalloff(Growth.get(mate).level, STORM_LEVEL));
            Growth.addXp(mate, amount, "growth.skyabyss.reason.party");
        }
    }

    private static void hooks(ServerPlayer player) {
        jp.revati.skyabyss.quest.Quests.signal(player, SIGNAL);
        jp.revati.skyabyss.lore.Lore.unlock(player, LORE_ENTRY, true);
    }

    private static void announce(ServerLevel level, ServerPlayer player, TitanMemoryData.Record r, Roll roll, List<ItemStack> items) {
        for (String piece : roll.pieces()) {
            ItemStack shown = items.stream().filter(s -> TitanLoot.id(s.getItem()).equals(piece)).findFirst().orElse(null);
            if (shown == null) {
                continue;
            }
            boolean fresh = roll.fresh().contains(piece);
            if (EXCLUSIVES.contains(piece)) {
                player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
                Notice.alert(player, fresh ? "message.skyabyss.legendary_new" : "message.skyabyss.legendary_dupe", TitanLoot.itemLink(shown));
                for (ServerPlayer other : Players.snapshot(level.players())) {
                    if (other != player) {
                        Notice.unlock(other, "message.skyabyss.legendary_other", player.getDisplayName(), TitanLoot.itemLink(shown));
                    }
                }
            } else if (fresh) {
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
                Notice.unlock(player, "message.skyabyss.gear_new", TitanLoot.itemLink(shown), collected(r), collection().size());
            }
        }
    }

    // 初回の選択: 画面 (loot/reward/HeartReward) が眠りの場面のあとに開く。ここは案内の一行だけ
    private static void offerChoice(ServerPlayer player, TitanMemoryData.Record r) {
        jp.revati.skyabyss.loot.reward.HeartReward.announce(player, TITAN);
    }

    // ---------------------------------------------------------------- コマンド
    // /titanmemory storm              : 雷翼の記憶と図鑑 (誰でも)
    // /titanmemory storm choose <品>  : 初回の選択
    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("titanmemory")
                .then(Commands.literal("storm").executes(StormLoot::status)
                        .then(Commands.literal("choose").then(Commands.argument("item", StringArgumentType.greedyString()).executes(StormLoot::choose)))));
    }

    private static TitanMemoryData.Record record(ServerPlayer player) {
        return TitanMemoryData.get(player.server).record(player.getUUID(), TITAN);
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TitanMemoryData.Record r = record(player);
        player.sendSystemMessage(ModText.title("message.skyabyss.memory_title_thunder_wing"));
        player.sendSystemMessage(TitanLoot.memoryLine(r));
        player.sendSystemMessage(ModText.hint("message.skyabyss.memory_clears", r.clears, collected(r), collection().size()));
        MutableComponent owned = Component.empty();
        for (String id : collection()) {
            boolean has = r.owned.contains(id);
            owned.append(Component.literal(has ? "■ " : "□ ").withStyle(has ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
            Optional<Item> item = item(id);
            if (item.isPresent() && (has || !EXCLUSIVES.contains(id))) {
                ItemStack stack = new ItemStack(item.get());
                owned.append(has ? TitanLoot.itemLink(stack) : stack.getHoverName().copy().withStyle(ChatFormatting.DARK_GRAY));
            } else {
                owned.append(Component.literal("？？？").withStyle(ChatFormatting.DARK_GRAY));
            }
            owned.append("  ");
        }
        player.sendSystemMessage(owned);
        if (r.choicePending) {
            offerChoice(player, r);
        }
        return 1;
    }

    private static int choose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String choice = StringArgumentType.getString(ctx, "item").trim();
        // 決まりは画面と同じ一箇所 (HeartReward.claim)
        if (jp.revati.skyabyss.loot.reward.HeartReward.claim(player, TITAN, choice) != jp.revati.skyabyss.loot.reward.HeartReward.Result.OK) {
            Notice.warn(player, "message.skyabyss.first_clear_none");
            return 0;
        }
        return 1;
    }

    // ---------------------------------------------------------------- 自己テスト用: 何回の撃破で図鑑が揃うか (6.4節の模擬)
    public static double meanClearsToComplete(int runs, long seed, Predicate<String> available) {
        RandomSource random = RandomSource.create(seed);
        long total = 0;
        for (int i = 0; i < runs; i++) {
            TitanMemoryData.Record r = new TitanMemoryData.Record();
            // 初回は専用伝説で一番出にくい嵐の目の羽根を選ぶ (6.4節の前提)
            r.owned.add(jp.revati.skyabyss.item.storm.StormGear.EYE_FEATHER);
            int n = 0;
            while (collected(r) < collection().size() && n < 200) {
                rollHeart(r, random, available);
                n++;
            }
            total += n;
        }
        return total / (double) runs;
    }

    static Set<String> allIds() {
        Set<String> ids = new LinkedHashSet<>(collection());
        ids.add(SHARD);
        ids.add(CORE);
        ids.add(FRUIT);
        return ids;
    }
}
