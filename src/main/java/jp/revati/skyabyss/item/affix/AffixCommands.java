package jp.revati.skyabyss.item.affix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.registry.ModDataComponents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 特性と交換のテスト用コマンド (管理者)。どれも自分の手の装備に効く
// /skyabyss affix show                   : 手の装備の特性と再鍛造の候補を見る
// /skyabyss affix reroll                 : 特性を抽選し直す (候補は消す)
// /skyabyss affix clear                  : 特性を外す (第1段階のセーブの装備と同じ状態。持ち物に戻ると抽選される)
// /skyabyss affix set <種類> <値>         : 特性を一つ足す・置き換える (幅や数の決まりは見ない。試すため)
// /skyabyss affix trial [回数]            : 費用なしで再鍛造の候補を作る
// /skyabyss exchange show <人>            : 交換の状態
// /skyabyss exchange reset <人>           : この周回の交換をやり直せるようにする
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AffixCommands {
    private AffixCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(SkyAbyss.MODID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("affix")
                        .then(Commands.literal("show").executes(AffixCommands::show))
                        .then(Commands.literal("reroll").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ItemStack stack = held(ctx);
                            if (stack == null) {
                                return 0;
                            }
                            stack.set(ModDataComponents.AFFIXES.get(), AffixRules.roll(stack, p.getRandom()));
                            stack.remove(ModDataComponents.REFORGE_OFFERS.get());
                            return show(ctx);
                        }))
                        .then(Commands.literal("clear").executes(ctx -> {
                            ItemStack stack = held(ctx);
                            if (stack == null) {
                                return 0;
                            }
                            stack.remove(ModDataComponents.AFFIXES.get());
                            stack.remove(ModDataComponents.REFORGE_OFFERS.get());
                            ctx.getSource().sendSuccess(() -> Component.literal("affixes removed (rolled again when it sits in an inventory)"), true);
                            return 1;
                        }))
                        .then(Commands.literal("set").then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(AffixType.values()).map(t -> t.id), b))
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 100)).executes(AffixCommands::set))))
                        .then(Commands.literal("trial")
                                .executes(ctx -> trial(ctx, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, AffixRules.MAX_TRIALS))
                                        .executes(ctx -> trial(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("exchange")
                        .then(Commands.literal("show").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                            TitanMemoryData.Record r = TitanLoot.peek(p);
                            ctx.getSource().sendSuccess(() -> Component.literal(p.getScoreboardName() + ": " + TitanLoot.exchangeState(r)
                                    + (r == null ? "" : " (clears " + r.clears + ", last exchange at " + r.lastExchangeClears + ", total " + r.exchanges + ")")), false);
                            return 1;
                        })))
                        .then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                            TitanMemoryData.Record r = TitanMemoryData.get(p.server).record(p.getUUID(), TitanLoot.STONE_WHALE);
                            r.lastExchangeClears = 0;
                            ctx.getSource().sendSuccess(() -> Component.literal(p.getScoreboardName() + ": " + TitanLoot.exchangeState(r)), true);
                            return 1;
                        })))));
    }

    // 手の装備 (特性の付く品でなければ断る)
    private static ItemStack held(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ItemStack stack = p.getMainHandItem();
        if (!AffixRules.eligible(stack)) {
            ctx.getSource().sendFailure(ModText.deny("message.skyabyss.affix.not_eligible"));
            return null;
        }
        return stack;
    }

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ItemStack stack = held(ctx);
        if (stack == null) {
            return 0;
        }
        ctx.getSource().sendSuccess(stack::getHoverName, false);
        if (!stack.has(ModDataComponents.AFFIXES.get())) {
            ctx.getSource().sendSuccess(() -> Component.translatable("gear.skyabyss.affix_unrolled"), false);
        }
        for (Affix a : AffixRules.affixes(stack)) {
            ctx.getSource().sendSuccess(() -> AffixRules.line(a).append(Component.literal("  (" + a.type() + " " + a.value() + ")")), false);
        }
        List<List<Affix>> offers = AffixRules.offers(stack);
        for (int i = 0; i < offers.size(); i++) {
            int n = i + 1;
            List<Affix> offer = offers.get(i);
            ctx.getSource().sendSuccess(() -> Component.literal("offer " + n + ": " + offer), false);
        }
        boolean valid = AffixRules.valid(stack, AffixRules.affixes(stack));
        ctx.getSource().sendSuccess(() -> Component.literal(valid ? "valid" : "outside the rules (set by command?)"), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ItemStack stack = held(ctx);
        if (stack == null) {
            return 0;
        }
        AffixType type = AffixType.byId(StringArgumentType.getString(ctx, "type"));
        if (type == null || type.slot != AffixRules.slotOf(stack.getItem())) {
            ctx.getSource().sendFailure(Component.literal("unknown affix for this item"));
            return 0;
        }
        double value = DoubleArgumentType.getDouble(ctx, "value");
        List<Affix> list = new ArrayList<>(AffixRules.affixes(stack));
        list.removeIf(a -> a.kind() == type);
        if (list.size() >= 4) {
            list.remove(0);
        }
        list.add(new Affix(type, value));
        stack.set(ModDataComponents.AFFIXES.get(), List.copyOf(list));
        return show(ctx);
    }

    private static int trial(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ItemStack stack = held(ctx);
        if (stack == null) {
            return 0;
        }
        for (int i = 0; i < count && AffixRules.canTrial(stack); i++) {
            AffixRules.addTrial(stack, p.getRandom());
        }
        return show(ctx);
    }
}
