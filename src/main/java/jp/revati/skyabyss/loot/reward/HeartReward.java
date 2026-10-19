package jp.revati.skyabyss.loot.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.cinematic.Cinematics;
import jp.revati.skyabyss.cinematic.Scenes;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.story.ch1.Tasks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 心臓を止めたあとに一度だけ選べる遺物 (初回の専用伝説) の選択。docs/design/30_heart_reward_ui.md。
// 四体とも同じ決まり。選べる品は巨神の装備の表 (TitanGearSets) の専用伝説、持ち主は TitanMemoryData の記録 (choicePending)。
//  - 一人ずつ別 (記録は UUID ごと。パーティの仲間はそれぞれの画面でそれぞれ選ぶ)
//  - 決めるのはサーバーだけ。choicePending を倒して渡すのは claim の一箇所で、同じ人の二度目は NOT_PENDING で断る
//  - 保存は記録の choicePending のまま (ログアウト・サーバー再起動をまたぐ)。選ぶまで消えない
//  - 開くのは、心臓の「眠り」の場面 (titan_end_<巨神>) が終わってから (スキップでも)。場面のない二回目以降・始められなかった人は降ろされて少しあと
//  - 「あとで」にしても、旅嚢の「遺物」・控え帳の行・/titanmemory reward から、選ぶまで開ける
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class HeartReward {
    // 場面が終わってから画面を開くまで (余韻の札と重ならないように)。場面がないときは、降ろされてから
    public static final int OPEN_AFTER_SCENE = 50;
    public static final int OPEN_AFTER_CARRY = 110;
    // 場面・別の画面の最中は待つ。何度待つか、一度の待ち
    private static final int RETRY_TICKS = 40;
    private static final int RETRY_MAX = 6;

    public enum Result {
        OK, NOT_PENDING, BAD_ITEM, OWNED
    }

    // 自己テスト用: 最後に開かせた (パケットを送った・送るはずだった) 巨神。偽のプレイヤーには送らない
    private static final Map<UUID, String> LAST_OPEN = new ConcurrentHashMap<>();

    private HeartReward() {
    }

    // 登録 (Mod のコンストラクタから)。場面の終わりに開く
    public static void register() {
        for (String titan : List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER)) {
            Cinematics.onEnd(Scenes.endId(titan), (player, skipped) -> scheduleOpen(player, titan, OPEN_AFTER_SCENE, true));
        }
    }

    // ---------------------------------------------------------------- 選べる品・持ち主
    // その巨神の選べる専用伝説 (まだ作られていない品は外す)
    public static List<String> options(String titan) {
        TitanGearSet set = TitanGearSets.get(titan);
        return set == null ? List.of() : set.exclusives().stream().filter(TitanGearSet::registered).toList();
    }

    // 選ぶのを待っている巨神 (選べる品があるものだけ。表の並び)
    public static List<String> pending(MinecraftServer server, UUID uuid) {
        TitanMemoryData data = TitanMemoryData.get(server);
        List<String> out = new ArrayList<>();
        for (TitanGearSet set : TitanGearSets.all()) {
            TitanMemoryData.Record r = data.peek(uuid, set.titan);
            if (r != null && r.choicePending && !options(set.titan).isEmpty()) {
                out.add(set.titan);
            }
        }
        return out;
    }

    public static boolean hasPending(ServerPlayer player) {
        return !pending(player.server, player.getUUID()).isEmpty();
    }

    // 持っている印 (options の並びで 1 ビットずつ)
    static int ownedMask(TitanMemoryData.Record r, List<String> options) {
        int mask = 0;
        for (int i = 0; i < options.size(); i++) {
            if (r.owned.contains(options.get(i))) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    // ---------------------------------------------------------------- 選ぶ (ここだけで決まる。コマンド・パケット・自己テストが呼ぶ)
    public static Result claim(ServerPlayer player, String titan, String itemId) {
        TitanGearSet set = TitanGearSets.get(titan);
        if (set == null) {
            return Result.BAD_ITEM;
        }
        TitanMemoryData data = TitanMemoryData.get(player.server);
        TitanMemoryData.Record r = data.record(player.getUUID(), titan);
        if (!r.choicePending) {
            return Result.NOT_PENDING;
        }
        List<String> options = options(titan);
        if (!options.contains(itemId)) {
            return Result.BAD_ITEM;
        }
        // すでに持っている品は、ほかに選べる品があるあいだは選べない (同じ品を二つもらっても意味がない)
        if (r.owned.contains(itemId) && options.stream().anyMatch(o -> !r.owned.contains(o))) {
            return Result.OWNED;
        }
        Item item = TitanGearSet.item(itemId).orElseThrow();
        // ここで倒す。以降の同じ呼び出しは NOT_PENDING になる
        r.choicePending = false;
        // 初回の贈り物は記憶のカウンタを戻さない (天井の進みはそのまま)
        r.owned.add(itemId);
        ItemStack gift = AffixRules.withAffixes(new ItemStack(item), player.getRandom());
        int held = TitanLoot.handOver(player.server, player.getUUID(), player, List.of(gift), 0);
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
        Notice.alert(player, "message.skyabyss.legendary_new", TitanLoot.itemLink(gift));
        TitanLoot.tellHeld(player, held);
        sendState(player);
        // 天職を選べるようになったら (一章の心臓のあと・雷翼のあと)、報酬の画面のあとに天職の画面を開く
        jp.revati.skyabyss.rpg.skill.SkillCalling.offerSoon(player, jp.revati.skyabyss.rpg.skill.SkillCalling.OFFER_DELAY, false);
        return Result.OK;
    }

    // ---------------------------------------------------------------- 開く
    // 画面を開かせる (選べる遺物があるときだけ。true = 開かせた)
    public static boolean open(ServerPlayer player, String titan, boolean auto) {
        TitanMemoryData.Record r = TitanMemoryData.get(player.server).peek(player.getUUID(), titan);
        List<String> options = options(titan);
        if (r == null || !r.choicePending || options.isEmpty()) {
            return false;
        }
        LAST_OPEN.put(player.getUUID(), titan);
        if (!(player instanceof FakePlayer)) {
            HeartRewardNet.holdOnOpen(player);
            PacketDistributor.sendToPlayer(player, new HeartRewardNet.Open(titan, options, ownedMask(r, options), auto));
        }
        return true;
    }

    // 待っている遺物のうち先頭の画面を開く
    public static boolean openFirst(ServerPlayer player, boolean auto) {
        for (String titan : pending(player.server, player.getUUID())) {
            if (open(player, titan, auto)) {
                return true;
            }
        }
        return false;
    }

    // 少しあとに開く。場面の最中・死んでいる・抜けている間は待ち、選び終えていれば何もしない
    public static void scheduleOpen(ServerPlayer player, String titan, int delay, boolean auto) {
        scheduleOpen(player, titan, delay, auto, 0);
    }

    private static void scheduleOpen(ServerPlayer player, String titan, int delay, boolean auto, int tries) {
        Tasks.later(delay, () -> {
            if (player.hasDisconnected() || player.isRemoved()) {
                return;
            }
            if (!player.isAlive() || Cinematics.active(player) || jp.revati.skyabyss.titan.end.TitanEnd.holding(player)) {
                if (tries < RETRY_MAX) {
                    scheduleOpen(player, titan, RETRY_TICKS, auto, tries + 1);
                }
                return;
            }
            open(player, titan, auto);
        });
    }

    // 心臓の報酬の知らせから呼ぶ (チャットの選択肢の代わり)。画面は場面のあとに開くので、ここは一行だけ
    public static void announce(ServerPlayer player, String titan) {
        sendState(player);
        if (options(titan).isEmpty()) {
            Notice.hint(player, "skyabyss_reward.later_made");
        } else {
            Notice.hint(player, "skyabyss_reward.announce");
        }
    }

    static void sendState(ServerPlayer player) {
        if (!(player instanceof FakePlayer) && !player.hasDisconnected()) {
            PacketDistributor.sendToPlayer(player, new HeartRewardNet.State(hasPending(player)));
        }
    }

    @Nullable
    public static String lastOpened(UUID uuid) {
        return LAST_OPEN.get(uuid);
    }

    public static void clearLastOpened(UUID uuid) {
        LAST_OPEN.remove(uuid);
    }

    // ---------------------------------------------------------------- パケット
    static void onClaimPacket(ServerPlayer player, String titan, String item) {
        Result result = claim(player, titan, item);
        PacketDistributor.sendToPlayer(player, new HeartRewardNet.Result(result.ordinal(), titan, item));
        if (result == Result.NOT_PENDING || result == Result.BAD_ITEM) {
            sendState(player);
        }
    }

    static void onRequestPacket(ServerPlayer player) {
        if (!openFirst(player, false)) {
            Notice.hint(player, "skyabyss_reward.none");
            sendState(player);
        }
    }

    static void onLaterPacket(ServerPlayer player, String titan) {
        if (TitanMemoryData.get(player.server).peek(player.getUUID(), titan) != null) {
            Notice.hint(player, "skyabyss_reward.later");
        }
    }

    // ---------------------------------------------------------------- 入ったとき
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            sendState(player);
            if (hasPending(player)) {
                Tasks.later(100, () -> {
                    if (!player.hasDisconnected() && hasPending(player)) {
                        Notice.hint(player, "skyabyss_reward.waiting");
                    }
                });
            }
        }
    }

    // ---------------------------------------------------------------- コマンド
    // /titanmemory reward                : 受け取れる遺物の画面を開く
    // /skyabyss dev heartreward <巨神>    : (管理者・確かめ用) その巨神の遺物を選べる状態にして開く
    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("titanmemory")
                .then(Commands.literal("reward").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (!openFirst(player, false)) {
                        Notice.hint(player, "skyabyss_reward.none");
                        return 0;
                    }
                    return 1;
                })));
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("heartreward")
                        .then(Commands.argument("titan", StringArgumentType.word()).executes(ctx -> dev(ctx.getSource(), StringArgumentType.getString(ctx, "titan"))))
                        .then(Commands.literal("reset").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            for (TitanGearSet set : TitanGearSets.all()) {
                                TitanMemoryData.Record r = TitanMemoryData.get(player.server).record(player.getUUID(), set.titan);
                                r.choicePending = false;
                            }
                            sendState(player);
                            return 1;
                        })))));
    }

    private static int dev(CommandSourceStack source, String titan) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (options(titan).isEmpty()) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("no exclusives for " + titan));
            return 0;
        }
        TitanMemoryData.Record r = TitanMemoryData.get(player.server).record(player.getUUID(), titan);
        r.firstCleared = true;
        r.choicePending = true;
        sendState(player);
        open(player, titan, true);
        return 1;
    }
}
