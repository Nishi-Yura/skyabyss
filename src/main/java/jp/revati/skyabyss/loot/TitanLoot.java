package jp.revati.skyabyss.loot;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.WhaleboneCaptain;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
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
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// 個人抽選と「巨神の記憶」(天井)。
// 報酬はすべて、番兵長 (ボス) を倒したときにまとめて渡す (道中の敵は経験値だけ)。
// 広間にいた全員が、それぞれ別々の抽選で報酬を受け取り、記憶のカウンタが一つ進む:
//  - 5 以上: 伝説 (番兵長の鉾・水路の顎牙) の出る確率が2倍 (ソフト天井)
//  - 8 以上: まだ持っていない専用伝説を一つ必ず受け取る (ハード天井)。全部持っていれば共鳴の欠片に変わる
//  - まだ持っていない専用伝説を手にすると 0 に戻る (持っているものが重なっても戻らない)
// 初めて番兵長を倒したときは、専用伝説三つから一つを選んで受け取れる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanLoot {
    public static final String STONE_WHALE = "stone_whale";
    public static final int SOFT_PITY = 5;
    public static final int HARD_PITY = 8;
    public static final float PIKE_CHANCE = 0.20f;
    public static final float FANG_CHANCE = 0.06f;
    // 専用伝説がすべて手もとにあるとき、天井の代わりに受け取る共鳴の欠片
    public static final int PITY_SHARDS = 10;

    private TitanLoot() {
    }

    // 岩鯨の専用伝説 (天井と初回の選択の対象)
    public static List<Item> exclusives() {
        return List.of(ModItems.CAPTAIN_PIKE.get(), ModItems.WATERWAY_FANG.get(), ModItems.STONE_WHALE_PLATING.get());
    }

    public static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    // ---------------------------------------------------------------- 抽選 (ここだけで決まる。自己テストからも呼ぶ)
    public record Roll(List<ItemStack> items, @Nullable Item legendary, boolean fresh, boolean guaranteed, int memoryBefore) {
    }

    public static float pityMultiplier(int memory) {
        return memory >= SOFT_PITY ? 2f : 1f;
    }

    // 番兵長の撃破1回ぶんの、一人の報酬
    public static Roll rollBoss(TitanMemoryData.Record r, RandomSource random) {
        r.memory++;
        r.clears++;
        int before = r.memory;
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(ModItems.STONE_WHALE_CORE.get()));
        items.add(new ItemStack(ModItems.RESONANCE_CRYSTAL.get(), 1 + random.nextInt(2)));
        items.add(new ItemStack(ModItems.STONE_WHALE_SHARD.get(), 6 + random.nextInt(5)));
        // 道中の敵の分も、ここでまとめて
        if (random.nextFloat() < 0.35f) {
            items.add(new ItemStack(ModItems.BONE_SPIKE_ARROW.get(), 8 + random.nextInt(9)));
        }
        if (random.nextFloat() < 0.08f) {
            items.add(new ItemStack(ModItems.MOSS_TALISMAN.get()));
        }
        Item legendary = null;
        boolean guaranteed = false;
        if (r.memory >= HARD_PITY) {
            guaranteed = true;
            List<Item> missing = exclusives().stream().filter(i -> !r.owned.contains(id(i))).toList();
            if (missing.isEmpty()) {
                items.add(new ItemStack(ModItems.RESONANCE_SHARD.get(), PITY_SHARDS));
                r.memory = 0;
            } else {
                legendary = missing.get(random.nextInt(missing.size()));
            }
        } else {
            float roll = random.nextFloat();
            float mult = pityMultiplier(r.memory);
            if (roll < PIKE_CHANCE * mult) {
                legendary = ModItems.CAPTAIN_PIKE.get();
            } else if (roll < (PIKE_CHANCE + FANG_CHANCE) * mult) {
                legendary = ModItems.WATERWAY_FANG.get();
            }
        }
        boolean fresh = legendary != null && obtain(r, legendary);
        if (legendary != null) {
            items.add(new ItemStack(legendary));
        }
        return new Roll(items, legendary, fresh, guaranteed, before);
    }

    // 専用伝説を記録する。まだ持っていなかったなら記憶を 0 に戻して true
    private static boolean obtain(TitanMemoryData.Record r, Item legendary) {
        if (r.owned.add(id(legendary))) {
            r.memory = 0;
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- 倒したとき
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity mob = event.getEntity();
        if (mob instanceof WhaleboneCaptain && mob.level() instanceof ServerLevel level
                && level.dimension() == TitanTravel.TITAN_BACK && !event.isCanceled()) {
            onBossDeath(level, mob);
        }
    }

    private static void onBossDeath(ServerLevel level, LivingEntity boss) {
        List<ServerPlayer> players = new ArrayList<>(level.getEntitiesOfClass(ServerPlayer.class, TitanBackLayout.E3.aabb().inflate(6),
                p -> !p.isSpectator()));
        if (players.isEmpty() && boss.getKillCredit() instanceof ServerPlayer killer) {
            players.add(killer);
        }
        for (ServerPlayer player : players) {
            TitanMemoryData.Record r = record(player);
            Roll roll = rollBoss(r, level.random);
            for (ItemStack stack : roll.items()) {
                give(player, stack);
            }
            player.sendSystemMessage(ModText.progress("message.skyabyss.boss_reward"));
            if (roll.guaranteed()) {
                player.sendSystemMessage(ModText.lore(roll.legendary() != null ? "message.skyabyss.memory_guarantee" : "message.skyabyss.memory_all_owned"));
            }
            if (roll.legendary() != null) {
                announceLegendary(level, player, roll);
            }
            player.sendSystemMessage(memoryLine(r));
            if (!r.firstCleared) {
                r.firstCleared = true;
                r.choicePending = true;
                offerChoice(player);
            }
        }
    }

    private static TitanMemoryData.Record record(ServerPlayer player) {
        return TitanMemoryData.get(player.server).record(player.getUUID(), STONE_WHALE);
    }

    // その人にしか拾えない形で、倒れた場所に落とす
    private static void dropFor(ServerLevel level, LivingEntity at, ServerPlayer player, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, at.getX(), at.getY() + 0.5, at.getZ(), stack);
        item.setTarget(player.getUUID());
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    // 手もとへ直接。あふれた分は足もとに、本人にしか拾えない形で
    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            dropFor(player.serverLevel(), player, player, stack);
        }
    }

    // ---------------------------------------------------------------- 知らせ
    private static void announceLegendary(ServerLevel level, ServerPlayer player, Roll roll) {
        ItemStack shown = new ItemStack(roll.legendary());
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        player.sendSystemMessage(ModText.battle(roll.fresh() ? "message.skyabyss.legendary_new" : "message.skyabyss.legendary_dupe", itemLink(shown)));
        for (ServerPlayer other : level.players()) {
            if (other != player) {
                other.sendSystemMessage(ModText.progress("message.skyabyss.legendary_other", player.getDisplayName(), itemLink(shown)));
            }
        }
    }

    // 名前にカーソルを合わせると、装備の説明が出る
    private static MutableComponent itemLink(ItemStack stack) {
        return Component.literal("【").append(stack.getHoverName()).append("】")
                .withStyle(s -> s.withColor(0xF2B53A).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(stack))));
    }

    // 巨神の記憶 ◆◆◆◇◇◇◇◇ 3/8 (段階に応じた添え書き)
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
        } else if (r.memory >= SOFT_PITY) {
            line.append(Component.literal("  ")).append(Component.translatable("message.skyabyss.memory_soft").withStyle(s -> s.withColor(0xB89CFF)));
        }
        return line;
    }

    // 初回クリアの選択報酬: 押すと /titanmemory choose が走る
    private static void offerChoice(ServerPlayer player) {
        player.sendSystemMessage(ModText.battle("message.skyabyss.first_clear"));
        MutableComponent row = Component.literal("   ");
        for (Item item : exclusives()) {
            ItemStack stack = new ItemStack(item);
            row.append(itemLink(stack).withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/titanmemory choose " + id(item)))))
                    .append("  ");
        }
        player.sendSystemMessage(row);
        player.sendSystemMessage(ModText.hint("message.skyabyss.first_clear_hint"));
    }

    // ---------------------------------------------------------------- コマンド
    // /titanmemory                 : 自分の巨神の記憶を見る (誰でも)
    // /titanmemory choose <装備>    : 初回クリアの報酬を選ぶ (チャットの名前を押すと走る)
    // /skyabyss memory set <人> <n> : 記憶のカウンタを変える (管理者・テスト用)
    // /skyabyss memory clear <人>   : 記録を消す (管理者・テスト用)
    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("titanmemory")
                .executes(TitanLoot::status)
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
        MutableComponent owned = Component.empty();
        for (Item item : exclusives()) {
            boolean has = r.owned.contains(id(item));
            owned.append(Component.literal(has ? "■ " : "□ ").withStyle(has ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY))
                    .append(has ? itemLink(new ItemStack(item)) : Component.literal("？？？").withStyle(ChatFormatting.DARK_GRAY))
                    .append("  ");
        }
        player.sendSystemMessage(ModText.hint("message.skyabyss.memory_clears", r.clears, r.owned.size(), exclusives().size()));
        player.sendSystemMessage(owned);
        if (r.choicePending) {
            offerChoice(player);
        }
        return 1;
    }

    private static int choose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TitanMemoryData.Record r = record(player);
        ResourceLocation choice = ResourceLocationArgument.getId(ctx, "item");
        Item item = exclusives().stream().filter(i -> id(i).equals(choice.toString())).findFirst().orElse(null);
        if (!r.choicePending || item == null) {
            player.sendSystemMessage(ModText.deny("message.skyabyss.first_clear_none"));
            return 0;
        }
        r.choicePending = false;
        // 初回の贈り物は記憶のカウンタを戻さない (天井の進みはそのまま)
        r.owned.add(id(item));
        give(player, new ItemStack(item));
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        player.sendSystemMessage(ModText.battle("message.skyabyss.legendary_new", itemLink(new ItemStack(item))));
        return 1;
    }

    // ---------------------------------------------------------------- 自己テスト: 天井が効くか、平均で何周かかるか
    public static List<String> selfTest() {
        List<String> failures = new ArrayList<>();
        RandomSource random = RandomSource.create(12345);
        int trials = 20000;
        int worst = 0;
        long total = 0;
        int within5 = 0;
        for (int t = 0; t < trials; t++) {
            TitanMemoryData.Record r = new TitanMemoryData.Record();
            int runs = 0;
            while (r.owned.isEmpty()) {
                runs++;
                rollBoss(r, random);
                if (runs > HARD_PITY) {
                    failures.add("pity did not trigger within " + HARD_PITY + " clears");
                    break;
                }
            }
            worst = Math.max(worst, runs);
            total += runs;
            if (runs <= 5) {
                within5++;
            }
        }
        SkyAbyss.LOGGER.info("[selftest] titan memory: first exclusive legendary after avg {} clears, worst {}, within 5 clears {}%",
                String.format("%.2f", total / (double) trials), worst, String.format("%.1f", within5 * 100.0 / trials));
        // 全部そろったあとの天井は、共鳴の欠片に変わる
        TitanMemoryData.Record all = new TitanMemoryData.Record();
        exclusives().forEach(i -> all.owned.add(id(i)));
        all.memory = HARD_PITY - 1;
        Roll roll = rollBoss(all, random);
        if (roll.legendary() != null || all.memory != 0 || roll.items().stream().noneMatch(s -> s.is(ModItems.RESONANCE_SHARD.get()))) {
            failures.add("pity with everything owned should convert to resonance shards");
        }
        // 重なった伝説ではカウンタは戻らない
        TitanMemoryData.Record dupe = new TitanMemoryData.Record();
        dupe.owned.add(id(ModItems.CAPTAIN_PIKE.get()));
        dupe.owned.add(id(ModItems.WATERWAY_FANG.get()));
        for (int i = 0; i < 200 && dupe.memory < HARD_PITY - 1; i++) {
            rollBoss(dupe, random);
        }
        if (dupe.memory != HARD_PITY - 1) {
            failures.add("duplicate legendary reset the memory counter");
        }
        return failures;
    }
}
