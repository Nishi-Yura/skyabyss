package jp.revati.skyabyss.facility.guild;

import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// ギルドのコマンド (画面の代わり。誰でも使える)。日本語の名前は "" で囲む。
//   /guild create <名前> <印> [地の色] [模様の色]   作る (色は white / red / … の DyeColor の名前)
//   /guild apply <名前>                             申請する (誰でも入れるギルドならそのまま入る)。join も同じ
//   /guild accept <人> / deny <人>                  長が申請を受ける / 断る
//   /guild leave                                    抜ける (長は抜けられない。先に leader で譲るか disband で解散する)
//   /guild kick <人>                                長が外す
//   /guild open <true|false>                        長が「誰でも入れる」を切り替える
//   /guild info [名前] / list                       中身 / 一覧
//   /guild menu [つまみ 0..3]                       ギルドの画面を開く (組合の受付と同じ。0 人・1 頼み・2 旗・3 功績)
//   /guild officer <人> <true|false>                長が副長にする / 戻す
//   /guild take <0..2>                              長が倉庫から取り出せる位を決める (0 誰でも・1 副長から・2 長だけ)
//   /guild storage / deliver / banner               倉庫を開く / 今週の頼みに納める / 旗を受け取る
//   /guild leader <人>                              長がほかの人に長を譲る (前の長は副長に)
//   /guild disband confirm                          長がギルドを解散する (confirm を付けないと、やり方を言うだけ)
//   /guild pattern <模様> / plate <色|none>         長が旗の模様 / 名札の特別な色を替える (はじめからのものか、功績で開いたもの)
//   /guild achievements                             ギルドの功績の進み
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class GuildCommands {
    private static final SuggestionProvider<CommandSourceStack> NAMES = (ctx, b) -> SharedSuggestionProvider.suggest(
            GuildData.get(ctx.getSource().getServer()).all().stream().map(g -> StringArgumentType.escapeIfRequired(g.name)), b);
    private static final SuggestionProvider<CommandSourceStack> COLORS = (ctx, b) -> SharedSuggestionProvider.suggest(
            java.util.Arrays.stream(DyeColor.values()).map(DyeColor::getName), b);

    private GuildCommands() {
    }

    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("guild")
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("tag", StringArgumentType.string())
                                        .executes(c -> create(c, "white", "black"))
                                        .then(Commands.argument("color", StringArgumentType.word()).suggests(COLORS)
                                                .executes(c -> create(c, StringArgumentType.getString(c, "color"), "black"))
                                                .then(Commands.argument("accent", StringArgumentType.word()).suggests(COLORS)
                                                        .executes(c -> create(c, StringArgumentType.getString(c, "color"),
                                                                StringArgumentType.getString(c, "accent"))))))))
                .then(Commands.literal("apply").then(Commands.argument("name", StringArgumentType.string()).suggests(NAMES)
                        .executes(c -> tell(c, Guilds.apply(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("join").then(Commands.argument("name", StringArgumentType.string()).suggests(NAMES)
                        .executes(c -> tell(c, Guilds.apply(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("accept").then(Commands.argument("player", StringArgumentType.word())
                        .executes(c -> answer(c, true))))
                .then(Commands.literal("deny").then(Commands.argument("player", StringArgumentType.word())
                        .executes(c -> answer(c, false))))
                .then(Commands.literal("leave").executes(c -> tell(c, Guilds.leave(c.getSource().getPlayerOrException()))))
                .then(Commands.literal("kick").then(Commands.argument("player", StringArgumentType.word())
                        .executes(GuildCommands::kick)))
                .then(Commands.literal("open").then(Commands.argument("open", BoolArgumentType.bool())
                        .executes(c -> tell(c, Guilds.setOpen(c.getSource().getPlayerOrException(), BoolArgumentType.getBool(c, "open"))))))
                .then(Commands.literal("info")
                        .executes(c -> info(c, null))
                        .then(Commands.argument("name", StringArgumentType.string()).suggests(NAMES)
                                .executes(c -> info(c, StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("list").executes(GuildCommands::list))
                .then(Commands.literal("menu").executes(c -> {
                    GuildService.open(c.getSource().getPlayerOrException(), null);
                    return 1;
                }).then(Commands.argument("tab", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 3)).executes(c -> {
                    GuildService.open(c.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "tab"));
                    return 1;
                })))
                .then(Commands.literal("officer").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                        .then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c -> report(c,
                                Guilds.setOfficer(c.getSource().getPlayerOrException(), net.minecraft.commands.arguments.EntityArgument.getPlayer(c, "player").getUUID(),
                                        com.mojang.brigadier.arguments.BoolArgumentType.getBool(c, "on")))))))
                .then(Commands.literal("take").then(Commands.argument("rank", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 2))
                        .executes(c -> report(c, Guilds.setTakeRank(c.getSource().getPlayerOrException(),
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "rank"))))))
                .then(Commands.literal("storage").executes(c -> GuildStorage.open(c.getSource().getPlayerOrException(), null) ? 1 : 0))
                .then(Commands.literal("deliver").executes(c -> report(c, GuildRequests.deliver(c.getSource().getPlayerOrException()))))
                .then(Commands.literal("banner").executes(c -> report(c, Guilds.takeBanner(c.getSource().getPlayerOrException()))))
                .then(Commands.literal("leader").then(Commands.argument("player", StringArgumentType.word())
                        .executes(c -> {
                            ServerPlayer leader = c.getSource().getPlayerOrException();
                            UUID who = find(leader, StringArgumentType.getString(c, "player"), false);
                            return tell(c, who == null ? Guilds.Result.NOT_MEMBER : Guilds.transfer(leader, who));
                        })))
                .then(Commands.literal("disband")
                        .executes(c -> {
                            c.getSource().sendFailure(Component.translatable("message.skyabyss.guild.disband_confirm"));
                            return 0;
                        })
                        .then(Commands.literal("confirm").executes(c -> tell(c, Guilds.disband(c.getSource().getPlayerOrException())))))
                .then(Commands.literal("pattern").then(Commands.argument("pattern", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(GuildEmblems.ALL.stream().map(GuildEmblems.Emblem::id), b))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            GuildData.Guild g = Guilds.of(p);
                            if (g == null) {
                                return tell(c, Guilds.Result.NOT_IN_GUILD);
                            }
                            String id = StringArgumentType.getString(c, "pattern");
                            if (GuildEmblems.byId(id) == null) {
                                return tell(c, Guilds.Result.PATTERN_LOCKED);
                            }
                            return tell(c, Guilds.setEmblem(p, g.tag, g.color, g.accent, id, g.plate));
                        })))
                .then(Commands.literal("plate").then(Commands.argument("plate", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(java.util.stream.Stream.of("none"),
                                GuildEmblems.PLATES.stream().map(GuildEmblems.Plate::id)), b))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            GuildData.Guild g = Guilds.of(p);
                            if (g == null) {
                                return tell(c, Guilds.Result.NOT_IN_GUILD);
                            }
                            String id = StringArgumentType.getString(c, "plate");
                            return tell(c, Guilds.setEmblem(p, g.tag, g.color, g.accent, g.pattern, id.equals("none") ? "" : id));
                        })))
                .then(Commands.literal("achievements").executes(GuildCommands::achievements)));
    }

    private static int achievements(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        GuildData.Guild g = Guilds.of(c.getSource().getPlayerOrException());
        if (g == null) {
            return tell(c, Guilds.Result.NOT_IN_GUILD);
        }
        for (GuildAchievements.Achievement a : GuildAchievements.ALL) {
            boolean done = g.unlocked.contains(a.id());
            int v = Math.min(a.target(), GuildAchievements.value(g, a.kind()));
            net.minecraft.network.chat.MutableComponent line = Component.literal(done ? "✔ " : "・ ").append(Component.translatable(a.nameKey()))
                    .append("  " + v + "/" + a.target() + "  ");
            for (Component r : GuildAchievements.rewards(a.id())) {
                line.append(r).append(" ");
            }
            c.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int report(CommandContext<CommandSourceStack> c, Guilds.Result r) throws CommandSyntaxException {
        Notice.hint(c.getSource().getPlayerOrException(), r.key);
        return r.ok() ? 1 : 0;
    }

    private static int create(CommandContext<CommandSourceStack> c, String color, String accent) throws CommandSyntaxException {
        DyeColor a = DyeColor.byName(color, DyeColor.WHITE);
        DyeColor b = DyeColor.byName(accent, DyeColor.BLACK);
        return tell(c, Guilds.create(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "name"),
                StringArgumentType.getString(c, "tag"), a.getId(), b.getId()));
    }

    private static int answer(CommandContext<CommandSourceStack> c, boolean accept) throws CommandSyntaxException {
        ServerPlayer leader = c.getSource().getPlayerOrException();
        UUID who = find(leader, StringArgumentType.getString(c, "player"), true);
        if (who == null) {
            return tell(c, Guilds.Result.NOT_APPLICANT);
        }
        return tell(c, Guilds.answer(leader, who, accept));
    }

    private static int kick(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer leader = c.getSource().getPlayerOrException();
        UUID who = find(leader, StringArgumentType.getString(c, "player"), false);
        if (who == null) {
            return tell(c, Guilds.Result.NOT_MEMBER);
        }
        return tell(c, Guilds.kick(leader, who));
    }

    // 自分のギルドの申請した人 / 入っている人から、名前で探す
    private static UUID find(ServerPlayer leader, String name, boolean applicants) {
        GuildData.Guild g = Guilds.of(leader);
        if (g == null) {
            return null;
        }
        for (UUID u : applicants ? g.applicants : g.members) {
            if (g.nameOf(u).equalsIgnoreCase(name)) {
                return u;
            }
        }
        return null;
    }

    private static int tell(CommandContext<CommandSourceStack> c, Guilds.Result r) {
        if (r.ok()) {
            c.getSource().sendSuccess(() -> Component.translatable(r.key), false);
            return 1;
        }
        c.getSource().sendFailure(Component.translatable(r.key));
        return 0;
    }

    private static int info(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        GuildData data = GuildData.get(c.getSource().getServer());
        GuildData.Guild g = name == null ? data.of(c.getSource().getPlayerOrException().getUUID()) : data.byName(name);
        if (g == null) {
            c.getSource().sendFailure(Component.translatable(name == null ? Guilds.Result.NOT_IN_GUILD.key : Guilds.Result.NO_GUILD.key));
            return 0;
        }
        StringBuilder members = new StringBuilder();
        for (UUID u : g.members) {
            if (!members.isEmpty()) {
                members.append(", ");
            }
            members.append(g.nameOf(u));
        }
        GuildData.Guild gg = g;
        c.getSource().sendSuccess(() -> Guilds.prefix(gg.badge()).append(Component.translatable("message.skyabyss.guild.info", gg.name,
                gg.nameOf(gg.leader), gg.members.size(), GuildData.MAX_MEMBERS, members.toString())), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        GuildData data = GuildData.get(c.getSource().getServer());
        if (data.all().isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable("message.skyabyss.guild.none"), false);
            return 0;
        }
        for (GuildData.Guild g : data.all()) {
            c.getSource().sendSuccess(() -> Guilds.prefix(g.badge()).append(Component.translatable("message.skyabyss.guild.list_row", g.name,
                    g.members.size(), GuildData.MAX_MEMBERS)), false);
        }
        return data.all().size();
    }
}
