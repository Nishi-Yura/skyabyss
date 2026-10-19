package jp.revati.skyabyss.titan.ember.heart;

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
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.gear.EmberGear;
import jp.revati.skyabyss.titan.ember.lore.EmberLore;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

// 余燼の心臓の報酬 (13 の 6.3節)。型は岩鯨 (TitanLoot)・潮殻 (TideLoot) と同じ。表と確率は装備の担当の EmberGear.SET (図鑑 10 品、TitanGearSet):
// - 一人ずつ別々の抽選で、持ち物へ直接。倒れていた・持ち物がいっぱいの人の分は預かる (TitanMemoryData の預かり)
// - 巨神の記憶 (天井) と図鑑は巨神ごとの記録 (TitanMemoryData の "ember")。ほかの巨神の記録とは別に数える
//   新しい品の出ない撃破で +1、3 以上で図鑑の確率 2 倍、5 で未所持を一つ保証。図鑑にない品を手にすると 0
// - 天の鍵の欠片は一人一回 (四つ目。巨神ごと)。受け取ったとき、四体を止めて欠片が四つなら、story の SkyKey が 1 秒ごとの見回りで一つの鍵にする
// - 初めての撃破では、専用伝説三つから一つを選ぶ
// 表は品の ID で持ち、登録されていない品は抽選から外す (図鑑にも記さない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberLoot {
    public static final String TITAN = EmberGear.TITAN;   // "ember"
    public static final int EMBER_LEVEL = EmberHeartSetup.Xp.LEVEL;
    // 成長の経験値 (13 の 6.6節)
    public static final int HEART_XP = EmberHeartSetup.Xp.HEART;
    public static final int HEART_FIRST_BONUS = EmberHeartSetup.Xp.HEART_FIRST_BONUS;

    // 品の ID
    public static final String SKY_KEY_FRAGMENT = id("sky_key_fragment");
    public static final String RESONANCE_SHARD = id("resonance_shard_ember");
    public static final String RESONANCE_CRYSTAL = id("resonance_crystal");
    public static final String SHARD = EmberGear.SHARD;
    public static final String CORE = EmberGear.CORE;
    public static final String HORN = EmberIds.id(EmberIds.EMBER_HORN).toString();
    // 専用伝説: 鍛え手の大鎚、灰被りの火掻き、余燼の灯
    public static final List<String> EXCLUSIVES = EmberGear.SET.exclusives();

    public static final float CORE_CHANCE = 0.25f;
    public static final float CRYSTAL_CHANCE = 0.40f;
    public static final float HORN_CHANCE = 0.50f;

    // 信号 (クエストの目的) と伝承の項目
    public static final String SIGNAL = "ember_heart_stilled";
    public static final String LORE_ENTRY = EmberIds.LORE_STILLED;

    private EmberLoot() {
    }

    private static String id(String path) {
        return SkyAbyss.MODID + ":" + path;
    }

    // 図鑑 (10 品): 鎧 4、戦斧、外套、護符、専用伝説 3
    public static List<String> collection() {
        return EmberGear.SET.collection();
    }

    public static float chance(String item) {
        return EmberGear.SET.chance(item);
    }

    public static boolean registered(String id) {
        return TitanGearSet.registered(id);
    }

    private static Optional<Item> item(String id) {
        return TitanGearSet.item(id);
    }

    public static int collected(TitanMemoryData.Record r) {
        return EmberGear.SET.collected(r);
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
        if (random.nextFloat() < HORN_CHANCE) {
            draws.add(new Draw(HORN, between(random, 2, 3), false));
        }
        TitanGearSet.Pieces got = EmberGear.SET.rollPieces(r, memory, random, available);
        if (got.pityShards()) {
            draws.add(new Draw(RESONANCE_SHARD, TitanLoot.PITY_SHARDS, false));
        }
        for (String piece : got.pieces()) {
            draws.add(new Draw(piece, 1, true));
        }
        List<String> fresh = EmberGear.SET.record(r, got.pieces());
        return new Roll(draws, got.pieces(), fresh, got.guaranteed(), got.pityShards(), memory);
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

    // ---------------------------------------------------------------- 撃破 (EmberHeartBattle が呼ぶ)
    // 1. 物語の進み (余燼を止めた: 四体目。レベルの上限 40。経験値より先に) 2. 成長の経験値 3. 一人ずつの報酬 (四つ目の欠片を含む) 4. クエストと伝承
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
            Roll roll = rollHeart(r, level.random, EmberLoot::registered);
            draws.addAll(roll.draws());
            List<String> unmade = new ArrayList<>();
            List<ItemStack> items = stacks(draws, level.random, unmade);
            int held = TitanLoot.handOver(server, uuid, player, items, 0);
            boolean first = !r.firstCleared;
            if (first) {
                r.firstCleared = true;
                r.choicePending = true;
            }
            if (!unmade.isEmpty()) {
                SkyAbyss.LOGGER.info("Ember heart reward for {} skipped items that are not made yet: {}", uuid, unmade);
            }
            if (player == null) {
                continue;
            }
            hooks(player);
            Notice.narrate(player, EmberLore.HEART_STILLED);
            if (fragment) {
                Notice.unlock(player, "message.skyabyss.fragment_received");
                // 四つが一つになるのは story の SkyKey が 1 秒ごとに見て組む。まだそろわないときだけ、手の中の語りを出す
                if (!jp.revati.skyabyss.story.SkyKey.ready(player)) {
                    Notice.narrate(player, EmberLore.HEART_FRAGMENT);
                }
            }
            Notice.narrate(player, EmberIds.LANG_HEART + "reward");
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

    // 成長の経験値: 参加者それぞれに満額 (推奨 Lv 30 から離れると減る)。初めての撃破は上乗せ。
    // パーティの仲間でその場にいなかった人には、分け前の割合
    private static void giveGrowth(ServerLevel level, List<ServerPlayer> participants) {
        List<ServerPlayer> seen = new ArrayList<>();
        for (ServerPlayer player : participants) {
            if (player == null || seen.contains(player)) {
                continue;
            }
            seen.add(player);
            int amount = (int) Math.round(HEART_XP * GrowthRules.levelFalloff(Growth.get(player).level, EMBER_LEVEL));
            if (Growth.discover(player, "first:" + SkyAbyss.MODID + ":" + EmberIds.EMBER_HEART)) {
                amount += HEART_FIRST_BONUS;
            }
            Growth.addXp(player, amount, "entity.skyabyss." + EmberIds.EMBER_HEART);
        }
        double rate = jp.revati.skyabyss.party.Parties.shareRate(level.getServer());
        for (ServerPlayer mate : jp.revati.skyabyss.party.Parties.absentMates(level, seen)) {
            int amount = (int) Math.round(HEART_XP * rate * GrowthRules.levelFalloff(Growth.get(mate).level, EMBER_LEVEL));
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
                    if (other != player && !jp.revati.skyabyss.titan.group.Groups.differ(other, player)) {
                        Notice.unlock(other, "message.skyabyss.legendary_other", player.getDisplayName(), TitanLoot.itemLink(shown));
                    }
                }
            } else if (fresh) {
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
                Notice.unlock(player, "message.skyabyss.gear_new", TitanLoot.itemLink(shown), collected(r), collection().size());
            }
        }
    }

    // 初回の選択: 押すと /titanmemory ember choose が走る。専用伝説がまだ作られていなければ、選べるようになるまで待つ
    private static void offerChoice(ServerPlayer player, TitanMemoryData.Record r) {
        List<String> ready = EXCLUSIVES.stream().filter(EmberLoot::registered).toList();
        if (ready.isEmpty()) {
            Notice.hint(player, EmberIds.LANG_HEART + "choice_later");
            return;
        }
        Notice.alert(player, "message.skyabyss.first_clear");
        MutableComponent row = Component.literal("   ");
        for (String id : ready) {
            ItemStack stack = new ItemStack(item(id).orElseThrow());
            row.append(TitanLoot.itemLink(stack).withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/titanmemory ember choose " + id))))
                    .append("  ");
        }
        player.sendSystemMessage(row);
        player.sendSystemMessage(ModText.hint("message.skyabyss.first_clear_hint"));
    }

    // ---------------------------------------------------------------- コマンド
    // /titanmemory ember              : 余燼の記憶と図鑑 (誰でも)
    // /titanmemory ember choose <品>  : 初回の選択
    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("titanmemory")
                .then(Commands.literal("ember").executes(EmberLoot::status)
                        .then(Commands.literal("choose").then(Commands.argument("item", StringArgumentType.greedyString()).executes(EmberLoot::choose)))));
    }

    private static TitanMemoryData.Record record(ServerPlayer player) {
        return TitanMemoryData.get(player.server).record(player.getUUID(), TITAN);
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TitanMemoryData.Record r = record(player);
        player.sendSystemMessage(ModText.title(EmberIds.LANG_HEART + "memory_title"));
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
        TitanMemoryData.Record r = record(player);
        String choice = StringArgumentType.getString(ctx, "item").trim();
        if (!r.choicePending || !EXCLUSIVES.contains(choice) || !registered(choice)) {
            Notice.warn(player, "message.skyabyss.first_clear_none");
            return 0;
        }
        r.choicePending = false;
        r.owned.add(choice);
        ItemStack gift = AffixRules.withAffixes(new ItemStack(item(choice).orElseThrow()), player.getRandom());
        int held = TitanLoot.handOver(player.server, player.getUUID(), player, List.of(gift), 0);
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        Notice.alert(player, "message.skyabyss.legendary_new", TitanLoot.itemLink(gift));
        TitanLoot.tellHeld(player, held);
        return 1;
    }

    // ---------------------------------------------------------------- 自己テスト用: 何回の撃破で図鑑が揃うか (6.4節の模擬)
    public static double meanClearsToComplete(int runs, long seed, Predicate<String> available) {
        RandomSource random = RandomSource.create(seed);
        long total = 0;
        for (int i = 0; i < runs; i++) {
            TitanMemoryData.Record r = new TitanMemoryData.Record();
            // 初回は専用伝説の中でいちばん出にくい余燼の灯を選ぶ (6.4節の前提)
            r.owned.add(EmberGear.LANTERN);
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
        ids.add(HORN);
        return ids;
    }
}
