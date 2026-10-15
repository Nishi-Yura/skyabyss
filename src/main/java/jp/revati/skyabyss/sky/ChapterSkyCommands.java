package jp.revati.skyabyss.sky;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 1章の空の演出のテスト用コマンド (OP)。
//   /skyabyss scar check          : 見ている向きの前後96ブロックの峰を探し、削れるか・削れない理由を出す (削らない)
//   /skyabyss scar here           : そのうち最初に安全だった峰を今すぐ削る (確率・1章の条件は問わない。安全の確かめは同じ。
//                                   近くの人の距離だけ 24 → 8 に緩める)
//   /skyabyss scar list           : これまでの削り跡
//   /skyabyss eye show [player]   : 今すぐ雲の目を開く (夜・月・1章・屋外の条件を問わない)
//   /skyabyss eye next [player]   : 次に開きうるまでの秒数、見た回数、今開けない理由
//   /skyabyss eye reset [player]  : 雲の目の記録を消す
//   /skyabyss moon                : 今の月の近さ
//   /skyabyss moon set <0〜1>     : 月の近さを固定する / moon clear で外す
//   /skyabyss chapter1 [player]   : 1章に入っているか / chapter1 set <player> <true|false> で「巨神の背に乗った」を変える
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ChapterSkyCommands {
    static final double SEARCH = 96.0;

    private ChapterSkyCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(SkyAbyss.MODID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("scar")
                        .then(Commands.literal("check").executes(ctx -> scar(ctx, false)))
                        .then(Commands.literal("here").executes(ctx -> scar(ctx, true)))
                        .then(Commands.literal("list").executes(ChapterSkyCommands::scarList)))
                .then(Commands.literal("eye")
                        .then(Commands.literal("show")
                                .executes(ctx -> eyeShow(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> eyeShow(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("next")
                                .executes(ctx -> eyeNext(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> eyeNext(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("reset")
                                .executes(ctx -> eyeReset(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> eyeReset(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("moon")
                        .executes(ChapterSkyCommands::moonStatus)
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0f, 1f))
                                        .executes(ctx -> moonSet(ctx, FloatArgumentType.getFloat(ctx, "value")))))
                        .then(Commands.literal("clear").executes(ctx -> moonSet(ctx, -1f))))
                .then(Commands.literal("chapter1")
                        .executes(ctx -> chapterStatus(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> chapterStatus(ctx, EntityArgument.getPlayer(ctx, "player"))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> chapterSet(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                        BoolArgumentType.getBool(ctx, "value"))))))));
    }

    private static int scar(CommandContext<CommandSourceStack> ctx, boolean carve) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.pass.overworld_only"));
            return 0;
        }
        if (PeakScarEvents.busy()) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.scar.busy"));
            return 0;
        }
        double yaw = Math.toRadians(player.getYRot());
        float dirX = (float) -Math.sin(yaw);
        float dirZ = (float) Math.cos(yaw);
        List<PeakScar.Candidate> found = PeakScar.findCandidates(level, player.getX() - dirX * SEARCH, player.getZ() - dirZ * SEARCH,
                dirX, dirZ, SEARCH * 2, SEARCH);
        if (found.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.scar.check_none"));
            return 0;
        }
        float strength = ChapterSky.moonNearness(level);
        List<String> reasons = new ArrayList<>();
        for (PeakScar.Candidate c : found.subList(0, Math.min(6, found.size()))) {
            PeakScar.Result r = PeakScar.plan(level, c, dirX, dirZ, strength, level.random, carve ? 8.0 : PeakScar.PLAYER_CLEAR);
            String where = String.format(Locale.ROOT, "%d %d %d", c.x(), c.y(), c.z());
            if (r.plan() != null && carve) {
                PeakScarEvents.begin(level, r.plan());
                ctx.getSource().sendSuccess(() -> ModText.lore("commands.skyabyss.scar.carved", where, r.plan().removed()), true);
                return 1;
            }
            String line = r.plan() != null ? "ok (" + r.plan().removed() + " blocks)" : r.reason();
            ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.scar.check", where, line), false);
            reasons.add(line);
        }
        if (carve) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.scar.none", String.join(", ", reasons)));
            return 0;
        }
        return 1;
    }

    private static int scarList(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        List<ChapterSkyData.Scar> scars = ChapterSkyData.get(level).scars();
        StringBuilder sb = new StringBuilder();
        for (ChapterSkyData.Scar s : scars) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(s.pos().getX()).append(' ').append(s.pos().getY()).append(' ').append(s.pos().getZ());
        }
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.scar.list", scars.size(), sb.toString()), false);
        return scars.size();
    }

    private static int eyeShow(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.pass.overworld_only"));
            return 0;
        }
        CloudEyePayload p = CloudEyeEvents.open(level, player);
        // 向きも添える (試す人が /tp @s ~ ~ ~ <向き> <-見上げる角度> で向けるように)
        ctx.getSource().sendSuccess(() -> ModText.lore("commands.skyabyss.eye.opened", player.getDisplayName(), p.stage(),
                Math.round(p.yaw()), Math.round(p.elevation())), true);
        return 1;
    }

    private static int eyeNext(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        ChapterSkyData.Entry entry = ChapterSkyData.get(level).entry(player.getUUID());
        long remaining = entry.nextEye < 0 ? -1 : Math.max(0, entry.nextEye - level.getGameTime()) / 20;
        String why = CloudEyeEvents.ineligible(level, player);
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.eye.next", player.getDisplayName(), remaining,
                entry.eyeSightings, why == null ? "ok" : why), false);
        return 1;
    }

    private static int eyeReset(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        ChapterSkyData data = ChapterSkyData.get(level);
        ChapterSkyData.Entry entry = data.entry(player.getUUID());
        entry.nextEye = level.getGameTime();
        entry.eyeSightings = 0;
        entry.eyeMisses = 0;
        data.changed();
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.eye.reset", player.getDisplayName()), true);
        return 1;
    }

    private static int moonStatus(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        float moon = ChapterSky.moonNearness(level);
        String source = ChapterSkyData.get(level).moonOverride() >= 0 ? "set" : "provisional";
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.moon.status",
                String.format(Locale.ROOT, "%.2f", moon), source), false);
        return 1;
    }

    private static int moonSet(CommandContext<CommandSourceStack> ctx, float value) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        ChapterSkyData.get(level).setMoonOverride(value);
        if (value < 0) {
            ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.moon.cleared"), true);
        } else {
            ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.moon.set", String.format(Locale.ROOT, "%.2f", value)), true);
        }
        return 1;
    }

    private static int chapterStatus(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        ChapterSkyData.Entry entry = ChapterSkyData.get(player.serverLevel()).entry(player.getUUID());
        boolean lore = Lore.state(player).has(ChapterSky.CHAPTER_ONE_LORE);
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.chapter1.status", player.getDisplayName(),
                CommonComponents.optionStatus(ChapterSky.inChapterOne(player)), CommonComponents.optionStatus(entry.boarded),
                CommonComponents.optionStatus(lore)), false);
        return 1;
    }

    private static int chapterSet(CommandContext<CommandSourceStack> ctx, ServerPlayer player, boolean value) {
        ChapterSkyData data = ChapterSkyData.get(player.serverLevel());
        data.entry(player.getUUID()).boarded = value;
        data.changed();
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.chapter1.set", player.getDisplayName(),
                CommonComponents.optionStatus(value)), true);
        return 1;
    }
}
