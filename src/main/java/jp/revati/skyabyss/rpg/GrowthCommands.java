package jp.revati.skyabyss.rpg;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.GrowthRules.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// /growth                         : 自分のレベル・経験値・ステータス・記憶の断片を見る (誰でも)
// /growth add <ステータス> [数]     : 力を振り分ける (画面を開けないときのため。誰でも)
// /skyabyss level show <人>        : 人の成長を見る (管理者)
// /skyabyss level set <人> <1-50>  : レベルを決める (経験値は 0 から。使いすぎた力は戻る)
// /skyabyss level addxp <人> <量>  : 経験値を足す (レベルアップの演出も出る)
// /skyabyss level resetstats <人>  : 振り分けを全部戻す
// /skyabyss level reset <人>       : 成長をすべて初めに戻す (発見の記録と記憶の断片も消す)
// /skyabyss fragment give <人> <ID> [数] / clear <人> : 記憶の断片 (器を試すため)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class GrowthCommands {
    private GrowthCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("growth")
                .executes(ctx -> show(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("add").then(Commands.argument("stat", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Stat.values()).map(s -> s.id), b))
                        .executes(ctx -> add(ctx, 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, GrowthRules.STAT_CAP))
                                .executes(ctx -> add(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))));
        d.register(Commands.literal(SkyAbyss.MODID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("level")
                        .then(Commands.literal("show").then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> show(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, GrowthRules.MAX_LEVEL)).executes(ctx -> {
                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                    Growth.setLevel(p, IntegerArgumentType.getInteger(ctx, "level"));
                                    return done(ctx, p);
                                }))))
                        .then(Commands.literal("addxp").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000)).executes(ctx -> {
                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                    Growth.addXp(p, IntegerArgumentType.getInteger(ctx, "amount"), "growth.skyabyss.reason.command");
                                    return done(ctx, p);
                                }))))
                        .then(Commands.literal("resetstats").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                            Growth.resetStats(p);
                            return done(ctx, p);
                        })))
                        .then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                            GrowthData data = Growth.get(p);
                            data.deserializeNBT(p.registryAccess(), new net.minecraft.nbt.CompoundTag());
                            Growth.sync(p);
                            Growth.applyModifiers(p);
                            return done(ctx, p);
                        }))))
                .then(Commands.literal("fragment")
                        .then(Commands.literal("give").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(MemoryFragments.KNOWN, b))
                                        .executes(ctx -> giveFragment(ctx, 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> giveFragment(ctx, IntegerArgumentType.getInteger(ctx, "count")))))))
                        .then(Commands.literal("clear").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                            Growth.clearFragments(p);
                            return done(ctx, p);
                        })))));
    }

    private static int done(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        ctx.getSource().sendSuccess(() -> summary(player), true);
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Stat stat = Stat.byId(StringArgumentType.getString(ctx, "stat"));
        if (stat == null) {
            player.sendSystemMessage(ModText.deny("message.skyabyss.growth.unknown_stat"));
            return 0;
        }
        int[] add = new int[Stat.values().length];
        add[stat.ordinal()] = amount;
        if (!Growth.allocate(player, add)) {
            player.sendSystemMessage(ModText.deny("message.skyabyss.growth.allocate_failed"));
            return 0;
        }
        player.sendSystemMessage(ModText.progress("message.skyabyss.growth.allocated",
                Component.translatable(stat.nameKey()), Growth.get(player).stat(stat)));
        return 1;
    }

    private static int giveFragment(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        int added = Growth.grantFragment(player, id, count);
        if (added < count) {
            ctx.getSource().sendFailure(Component.translatable("message.skyabyss.growth.fragment_full", count - added));
        }
        return done(ctx, player);
    }

    // 一行の要約 (管理者のコマンドの返事)
    private static MutableComponent summary(ServerPlayer player) {
        GrowthData d = Growth.get(player);
        return Component.translatable("message.skyabyss.growth.summary", player.getDisplayName(), d.level, d.xp,
                d.xpNeed(), d.stat(Stat.STRENGTH), d.stat(Stat.AGILITY), d.stat(Stat.SPIRIT),
                d.stat(Stat.VITALITY), d.unspentPoints(), d.fragmentCount(), d.fragmentCapacity);
    }

    private static int show(CommandSourceStack source, ServerPlayer player) {
        GrowthData d = Growth.get(player);
        source.sendSystemMessage(ModText.title("message.skyabyss.growth.title").append(Component.literal("  ").append(player.getDisplayName())));
        Component next = d.level >= GrowthRules.MAX_LEVEL
                ? Component.translatable("gui.skyabyss.growth.max")
                : Component.translatable("message.skyabyss.growth.xp", d.xp, d.xpNeed());
        source.sendSystemMessage(ModText.progress("message.skyabyss.growth.level_line", d.level, next));
        MutableComponent stats = Component.empty();
        for (Stat s : Stat.values()) {
            stats.append(Component.translatable(s.nameKey()).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" " + d.stat(s) + "   ").withStyle(ChatFormatting.WHITE));
        }
        source.sendSystemMessage(stats);
        source.sendSystemMessage(ModText.hint("message.skyabyss.growth.points", d.unspentPoints()));
        source.sendSystemMessage(ModText.hint("message.skyabyss.growth.effects",
                pct(GrowthRules.meleeMultiplier(d.stat(Stat.STRENGTH))),
                pct(GrowthRules.moveMultiplier(d.stat(Stat.AGILITY))),
                pct(GrowthRules.projectileMultiplier(d.stat(Stat.AGILITY))),
                jp.revati.skyabyss.Hearts.of(GrowthRules.bonusHealth(d.stat(Stat.VITALITY))),
                (int) Math.round(GrowthRules.BASE_MANA + GrowthRules.bonusMana(d.stat(Stat.SPIRIT)))));
        MutableComponent frag = ModText.lore("message.skyabyss.growth.fragments", d.fragmentCount(), d.fragmentCapacity);
        source.sendSystemMessage(frag);
        if (!d.fragments.isEmpty()) {
            MutableComponent list = Component.literal("  ");
            d.fragments.forEach((id, n) -> list.append(Component.translatable(MemoryFragments.nameKey(id)).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" ×" + n + "  ").withStyle(ChatFormatting.GRAY)));
            source.sendSystemMessage(list);
        }
        return 1;
    }

    private static String pct(double v) {
        return String.format("%.1f", v * 100);
    }
}
