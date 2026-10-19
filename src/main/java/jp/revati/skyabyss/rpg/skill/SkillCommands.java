package jp.revati.skyabyss.rpg.skill;

import java.util.Arrays;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// /skill                                     : 自分の技・技の点・マナ・置き場を見る (誰でも)
// /skyabyss skill show <人>                  : 人の技を見る (管理者。以下同じ)
// /skyabyss skill learn <人> <技> [段]       : 段を直接決める (前提は段 1 で埋める。点が足りなければ追加の点を足す。0 で忘れる)
// /skyabyss skill forget <人>                : 技をすべて忘れる (振り直しの回数は数えない)
// /skyabyss skill points <人> <数>           : 追加の技の点を決める
// /skyabyss skill mana <人> <量> | full       : マナを決める
// /skyabyss skill cooldowns <人>             : クールダウンをすべて消す
// /skyabyss skill slot <人> <1-4> <技|empty>  : 枠に置く
// /skyabyss skill cast <人> <1-4>            : 枠の技を使わせる (キーを押したのと同じ検めを通る)
// /skyabyss skill respecs <人> <回数>         : 振り直しの回数を決める (費用の確かめ用)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SkillCommands {
    private SkillCommands() {
    }

    private static final String[] SKILL_IDS = Arrays.stream(Skill.values()).map(s -> s.id).toArray(String[]::new);

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("skill").executes(ctx -> show(ctx.getSource(), ctx.getSource().getPlayerOrException())));
        d.register(Commands.literal(SkyAbyss.MODID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("skill")
                        .then(Commands.literal("show").then(player().executes(ctx -> show(ctx.getSource(), target(ctx)))))
                        .then(Commands.literal("learn").then(player().then(Commands.argument("skill", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(SKILL_IDS, b))
                                .executes(ctx -> learn(ctx, -1))
                                .then(Commands.argument("rank", IntegerArgumentType.integer(0, 3))
                                        .executes(ctx -> learn(ctx, IntegerArgumentType.getInteger(ctx, "rank")))))))
                        .then(Commands.literal("forget").then(player().executes(ctx -> {
                            SkillManager.forgetAll(target(ctx));
                            return done(ctx);
                        })))
                        .then(Commands.literal("points").then(player().then(Commands.argument("bonus", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                            ServerPlayer p = target(ctx);
                            SkillManager.get(p).bonusPoints = IntegerArgumentType.getInteger(ctx, "bonus");
                            SkillManager.get(p).sanitize(SkillManager.level(p));
                            SkillManager.sync(p);
                            SkillManager.applyModifiers(p);
                            return done(ctx);
                        }))))
                        .then(Commands.literal("mana").then(player()
                                .then(Commands.literal("full").executes(ctx -> {
                                    SkillManager.setMana(target(ctx), 1e9);
                                    return done(ctx);
                                }))
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0, 1024)).executes(ctx -> {
                                    SkillManager.setMana(target(ctx), DoubleArgumentType.getDouble(ctx, "amount"));
                                    return done(ctx);
                                }))))
                        .then(Commands.literal("cooldowns").then(player().executes(ctx -> {
                            ServerPlayer p = target(ctx);
                            SkillManager.get(p).cooldowns.clear();
                            SkillManager.get(p).globalCooldown = 0;
                            SkillManager.sync(p);
                            return done(ctx);
                        })))
                        .then(Commands.literal("slot").then(player().then(Commands.argument("slot", IntegerArgumentType.integer(1, SkillRules.SLOTS))
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                java.util.stream.Stream.concat(Arrays.stream(SKILL_IDS), java.util.stream.Stream.of("empty")), b))
                                        .executes(ctx -> {
                                            ServerPlayer p = target(ctx);
                                            String id = StringArgumentType.getString(ctx, "skill");
                                            Skill s = "empty".equals(id) ? null : Skill.byId(id);
                                            if (!"empty".equals(id) && s == null || !SkillManager.setSlot(p, IntegerArgumentType.getInteger(ctx, "slot") - 1, s)) {
                                                ctx.getSource().sendFailure(Component.translatable("message.skyabyss.skill.slot_failed"));
                                                return 0;
                                            }
                                            return done(ctx);
                                        })))))
                        .then(Commands.literal("cast").then(player().then(Commands.argument("slot", IntegerArgumentType.integer(1, SkillRules.SLOTS))
                                .executes(ctx -> {
                                    ServerPlayer p = target(ctx);
                                    SkillManager.CastResult r = SkillManager.cast(p, IntegerArgumentType.getInteger(ctx, "slot") - 1);
                                    ctx.getSource().sendSuccess(() -> Component.literal("cast: " + r.name()), false);
                                    return r == SkillManager.CastResult.OK ? 1 : 0;
                                }))))
                        .then(Commands.literal("calling").then(player().then(Commands.argument("tree", StringArgumentType.word())
                                .executes(ctx -> callingCommand(ctx, "")))))
                        .then(Commands.literal("rune").then(player().then(Commands.argument("skill", StringArgumentType.word())
                                .then(Commands.argument("index", IntegerArgumentType.integer(0, 4)).executes(ctx -> {
                                    // 開発用: 刻印を選んだことにする (極めている・天職の木のときだけ。点も武器も問わない)
                                    ServerPlayer p = target(ctx);
                                    Skill s = Skill.byId(StringArgumentType.getString(ctx, "skill"));
                                    int index = IntegerArgumentType.getInteger(ctx, "index");
                                    if (s == null || !SkillManager.get(p).runeAvailable(s) || index > 0 && Rune.of(s, index) == null) {
                                        ctx.getSource().sendFailure(Component.literal("rune not available"));
                                        return 0;
                                    }
                                    if (index == 0) {
                                        SkillManager.get(p).runes.remove(s.id);
                                    } else {
                                        SkillManager.get(p).runes.put(s.id, index);
                                    }
                                    SkillManager.sync(p);
                                    return done(ctx);
                                })))))
                        .then(Commands.literal("respecs").then(player().then(Commands.argument("count", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                            ServerPlayer p = target(ctx);
                            SkillManager.get(p).respecs = IntegerArgumentType.getInteger(ctx, "count");
                            SkillManager.sync(p);
                            return done(ctx);
                        }))))));
    }

    // 開発用: 進みに関わらず天職を決める (一つ目が空なら一つ目、そうでなければ二つ目)
    private static int callingCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String unused) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = target(ctx);
        SkillCalling.Result r = SkillCalling.choose(p, StringArgumentType.getString(ctx, "tree"), true);
        ctx.getSource().sendSuccess(() -> Component.literal("calling: " + r.name()), false);
        return r == SkillCalling.Result.OK ? 1 : 0;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> player() {
        return Commands.argument("player", EntityArgument.player());
    }

    private static ServerPlayer target(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return EntityArgument.getPlayer(ctx, "player");
    }

    private static int learn(CommandContext<CommandSourceStack> ctx, int rank) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = target(ctx);
        Skill s = Skill.byId(StringArgumentType.getString(ctx, "skill"));
        if (s == null) {
            ctx.getSource().sendFailure(Component.translatable("message.skyabyss.skill.unknown"));
            return 0;
        }
        SkillManager.forceRank(p, s, rank < 0 ? SkillManager.get(p).rank(s) + 1 : rank);
        return done(ctx);
    }

    private static int done(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = target(ctx);
        ctx.getSource().sendSuccess(() -> summary(p), true);
        return 1;
    }

    private static MutableComponent summary(ServerPlayer p) {
        SkillData d = SkillManager.get(p);
        int level = SkillManager.level(p);
        return Component.translatable("message.skyabyss.skill.summary", p.getDisplayName(), d.spentPoints(), d.totalPoints(level),
                Math.round(d.mana), Math.round(SkillManager.maxMana(p)), d.ranks.size(), d.respecs);
    }

    private static int show(CommandSourceStack source, ServerPlayer p) {
        SkillData d = SkillManager.get(p);
        int level = SkillManager.level(p);
        source.sendSystemMessage(ModText.title("message.skyabyss.skill.title").append(Component.literal("  ").append(p.getDisplayName())));
        source.sendSystemMessage(ModText.progress("message.skyabyss.skill.points_line", d.unspentPoints(level), d.totalPoints(level),
                Math.round(d.mana), Math.round(SkillManager.maxMana(p))));
        for (SkillTree tree : SkillTree.values()) {
            MutableComponent line = Component.translatable(tree.nameKey()).withStyle(ChatFormatting.GOLD).append(" ");
            if (!d.treeOpen(tree)) {
                line.append(Component.translatable("message.skyabyss.skill.sealed").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                for (Skill s : Skill.inTree(tree)) {
                    int r = d.rank(s);
                    line.append(Component.translatable(s.nameKey()).withStyle(r > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY))
                            .append(Component.literal(" " + r + "/" + s.maxRank + "  ").withStyle(ChatFormatting.GRAY));
                }
            }
            source.sendSystemMessage(line);
        }
        MutableComponent slots = Component.translatable("message.skyabyss.skill.slots").withStyle(ChatFormatting.GRAY);
        for (int i = 0; i < d.slots.length; i++) {
            Skill s = d.slot(i);
            slots.append(Component.literal("  " + (i + 1) + ":").withStyle(ChatFormatting.GRAY))
                    .append(s == null ? Component.literal("—").withStyle(ChatFormatting.DARK_GRAY)
                            : Component.translatable(s.nameKey()).withStyle(ChatFormatting.WHITE));
        }
        source.sendSystemMessage(slots);
        return 1;
    }
}
