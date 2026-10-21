package jp.revati.skyabyss.opening.site;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 章の跡地の開発コマンド (本番の jar では登録されない)。撮影の台本 (tools/shots/chapter_grounds.txt) が使う。
//   /skyabyss dev grounds info                       四つの跡地 (中心・形・祠・道しるべの数)
//   /skyabyss dev grounds prepare <章>               その章の跡地を整え、小要素と道しるべを置いて、その場で建て終える (チャンクを読む)
//   /skyabyss dev grounds go <章> <何> <距離> <高さ> <yaw> <pitch>
//        何 = center (跡地の中心) | shape (整えた形の中心) | shrine (祠の置き場) | sea (潮殻の休む場所) | road (道しるべのまんなか)。
//        その点から yaw の向きを見る位置へ (距離だけ手前、地面から高さ) 体ごと置く
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ChapterGroundsCommands {
    private ChapterGroundsCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("grounds")
                        .then(Commands.literal("info").executes(ChapterGroundsCommands::info))
                        .then(Commands.literal("sites").executes(ChapterGroundsCommands::sites))
                        .then(Commands.literal("prepare").then(Commands.argument("chapter", StringArgumentType.word()).executes(ChapterGroundsCommands::prepare)))
                        .then(Commands.literal("shrine").then(Commands.argument("chapter", StringArgumentType.word()).executes(ChapterGroundsCommands::shrine)))
                        .then(Commands.literal("go").then(Commands.argument("chapter", StringArgumentType.word()).then(Commands.argument("what", StringArgumentType.word())
                                .then(Commands.argument("dist", FloatArgumentType.floatArg(0, 600)).then(Commands.argument("up", FloatArgumentType.floatArg(-40, 200))
                                        .then(Commands.argument("yaw", FloatArgumentType.floatArg(-360, 720)).then(Commands.argument("pitch", FloatArgumentType.floatArg(-90, 90))
                                                .executes(ChapterGroundsCommands::go)))))))))));
    }

    // 章の名前: whale|tide|storm|ember (または章の ID)
    static Chapters chapter(String name) {
        return switch (name) {
            case "whale", "1" -> Chapters.WHALE;
            case "tide", "2" -> Chapters.TIDE;
            case "storm", "3" -> Chapters.STORM;
            case "ember", "4" -> Chapters.EMBER;
            default -> Chapters.byId(name);
        };
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        SiteData data = SiteData.get(server.overworld());
        StringBuilder sb = new StringBuilder("chapter grounds: planned=" + data.groundsPlanned() + " busy=" + ChapterGrounds.busy());
        for (SiteData.Ground g : data.grounds()) {
            sb.append("\n  ").append(g.chapter).append(' ').append(g.center.toShortString()).append(' ').append(g.shape).append(g.shaped ? " shaped" : " NOT shaped")
                    .append(" shrine ").append(g.shrine == null ? "-" : g.shrine.toShortString()).append(" road ").append(g.road.size());
        }
        String s = sb.toString();
        SkyAbyss.LOGGER.info(s);
        ctx.getSource().sendSuccess(() -> Component.literal(s), false);
        return 1;
    }

    // その章の巨神の祠を跡地の置き場に建てる (心臓を止めたときと同じ。撮影と確かめ用。人は動かさない)
    private static int shrine(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        Chapters ch = chapter(StringArgumentType.getString(ctx, "chapter"));
        if (ch == null || ch.titan() == null) {
            ctx.getSource().sendFailure(Component.literal("no such chapter (whale|tide|storm|ember)"));
            return 0;
        }
        BlockPos at = jp.revati.skyabyss.titan.shrine.TitanShrine.ensure(server, ch.titan());
        ctx.getSource().sendSuccess(() -> Component.literal("shrine of " + ch.titan() + " at " + at.toShortString()), false);
        return 1;
    }

    private static int prepare(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        Chapters ch = chapter(StringArgumentType.getString(ctx, "chapter"));
        if (ch == null || ChapterGrounds.spec(ch) == null) {
            ctx.getSource().sendFailure(Component.literal("no such chapter (whale|tide|storm|ember)"));
            return 0;
        }
        ServerPlayer p = ctx.getSource().getPlayer();
        ChapterGrounds.finishNow(server);
        ChapterGrounds.prepare(server, ch);
        ChapterGrounds.finishNow(server);
        if (p != null) {
            ChapterSites.requestGround(server, p.getUUID(), ch);
        }
        long t0 = System.nanoTime();
        // 場所探し (別のスレッド) を待ってから建てる
        ChapterSites.finishTasksNow(server);
        SiteBuilds.buildAllNow(server);
        SiteNetwork.broadcast(server);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        ctx.getSource().sendSuccess(() -> Component.literal("prepared " + ch.id() + " in " + ms + " ms"), false);
        return 1;
    }

    // 建った場所の一覧 (種類・番号・部品・位置) をログへ。撮影の台本を書くとき用
    private static int sites(CommandContext<CommandSourceStack> ctx) {
        SiteData data = SiteData.get(ctx.getSource().getServer().overworld());
        StringBuilder sb = new StringBuilder("built sites:");
        for (SiteData.Entry e : data.allEntries()) {
            if (e.built) {
                sb.append("\n  ").append(e.kind).append(e.index).append(' ').append(e.siteId).append(" bp=").append(e.blueprint).append(' ').append(e.pos.toShortString());
            }
        }
        String s = sb.toString();
        SkyAbyss.LOGGER.info(s);
        ctx.getSource().sendSuccess(() -> Component.literal(s), false);
        return 1;
    }

    // 建った場所を、部品の名前か場所の ID で (名前.n で n 番目)。なければ null
    private static BlockPos site(MinecraftServer server, String what) {
        String name = what;
        int nth = 0;
        int dot = what.lastIndexOf('.');
        if (dot > 0) {
            try {
                nth = Integer.parseInt(what.substring(dot + 1));
                name = what.substring(0, dot);
            } catch (NumberFormatException ex) {
                // 名前に点がある
            }
        }
        int seen = 0;
        for (SiteData.Entry e : SiteData.get(server.overworld()).allEntries()) {
            if (e.built && (name.equals(e.blueprint) || name.equals(e.siteId) || name.equals(e.kind))) {
                if (seen++ == nth) {
                    return e.pos;
                }
            }
        }
        return null;
    }

    private static int go(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        Chapters ch = chapter(StringArgumentType.getString(ctx, "chapter"));
        SiteData.Ground g = ch == null ? null : ChapterGrounds.ground(server, ch);
        if (g == null) {
            ctx.getSource().sendFailure(Component.literal("no ground for that chapter"));
            return 0;
        }
        BlockPos at = switch (StringArgumentType.getString(ctx, "what")) {
            case "shape" -> g.shapeCenter.equals(BlockPos.ZERO) ? g.center : g.shapeCenter;
            case "shrine" -> g.shrine;
            case "sea" -> g.sea;
            case "road" -> g.road.isEmpty() ? null : g.road.get(g.road.size() / 2);
            case "center" -> g.center;
            default -> site(server, StringArgumentType.getString(ctx, "what"));
        };
        if (at == null) {
            ctx.getSource().sendFailure(Component.literal("that point is not set"));
            return 0;
        }
        float dist = FloatArgumentType.getFloat(ctx, "dist");
        float up = FloatArgumentType.getFloat(ctx, "up");
        float yaw = FloatArgumentType.getFloat(ctx, "yaw");
        float pitch = FloatArgumentType.getFloat(ctx, "pitch");
        double fx = -Math.sin(Math.toRadians(yaw));
        double fz = Math.cos(Math.toRadians(yaw));
        ServerLevel level = server.overworld();
        double x = at.getX() + 0.5 - fx * dist;
        double z = at.getZ() + 0.5 - fz * dist;
        level.getChunk(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        ctx.getSource().getPlayerOrException().teleportTo(level, x, Math.max(ground, at.getY()) + up, z, yaw, pitch);
        ctx.getSource().sendSuccess(() -> Component.literal("go " + ch.id() + " " + at.toShortString()), false);
        return 1;
    }
}
