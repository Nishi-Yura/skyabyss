package jp.revati.skyabyss.story.ch5.layers;

import java.util.List;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発用 (試遊・撮影・自己テスト): /skyabyss abyss layer ...
//   go <印> [向き]   設計図の印へ運ぶ (深淵の中へ)。向きは yaw (既定 180 = 北)
//   marks <層>       その層の印を三つ持たせる (巣・縄張りの払いも)
//   lord <層>        主の間の主を目覚めさせる (間に人がいなくても)
//   beat <層>        その層の主を倒したことにする
//   escort <層> <何>  形 B の護りの下り道: start (下り口から) / at1〜at3 (待ち場に着いたところ) / secure (次の待ち場を確かにする) / fail (気力を尽きさせる) / run (逃げの道から) / stop
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class LayerCommands {
    private LayerCommands() {
    }

    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyabyss").requires(s -> s.hasPermission(2))
                .then(Commands.literal("abyss").then(Commands.literal("layer")
                        .then(Commands.literal("go").then(Commands.argument("mark", StringArgumentType.word())
                                .executes(c -> go(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "mark"), 180f))
                                .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                        .executes(c -> go(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "mark"),
                                                FloatArgumentType.getFloat(c, "yaw"))))))
                        .then(Commands.literal("marks").then(Commands.argument("layer", StringArgumentType.word()).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            String layer = StringArgumentType.getString(c, "layer");
                            LayerData d = LayerData.get(p.server);
                            for (String k : List.of("nest_bone", "nest_storm")) {
                                d.set(p.getUUID(), "clear:" + k);
                            }
                            for (int i = 1; i <= 3; i++) {
                                d.set(p.getUUID(), "clear:wp_" + layer + "_" + i);
                            }
                            if (AbyssEscort.expedition(layer)) {
                                d.set(p.getUUID(), "escort:" + layer);
                            }
                            for (int i = 1; i <= 3; i++) {
                                AbyssMarks.take(p, layer, i);
                            }
                            return 1;
                        })))
                        .then(Commands.literal("lord").then(Commands.argument("layer", StringArgumentType.word()).executes(c -> {
                            ServerLevel level = c.getSource().getServer().getLevel(AbyssWorld.LEVEL);
                            String layer = StringArgumentType.getString(c, "layer");
                            if (level == null || Layers.index(layer) < 0 || AbyssLords.active(layer)) {
                                return 0;
                            }
                            AbyssLords.wake(level, layer, AbyssLords.inArena(level, layer), level.getGameTime());
                            c.getSource().sendSuccess(() -> Component.literal("woke the " + layer + " lord"), false);
                            return 1;
                        })))
                        .then(Commands.literal("escort").then(Commands.argument("layer", StringArgumentType.word())
                                .then(Commands.argument("what", StringArgumentType.word()).executes(c -> escort(c.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(c, "layer"), StringArgumentType.getString(c, "what"))))))
                        .then(Commands.literal("beat").then(Commands.argument("layer", StringArgumentType.word()).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            LayerData.get(p.server).set(p.getUUID(), "lord:" + StringArgumentType.getString(c, "layer"));
                            return 1;
                        }))))));
    }

    // escort <層> start | secure | fail | run | done | stop (形 B の護りの下り道。撮影・試遊用)
    static int escort(ServerPlayer p, String layer, String what) {
        ServerLevel level = p.server.getLevel(AbyssWorld.LEVEL);
        if (level == null || !AbyssEscort.expedition(layer)) {
            return 0;
        }
        long now = level.getGameTime();
        AbyssEscort.Run r = AbyssEscort.run(layer);
        if ("start".equals(what)) {
            if (r != null) {
                AbyssEscort.end(level, r, false);
            }
            AbyssEscort.start(level, layer, p, now);
            return 1;
        }
        if (r == null) {
            p.sendSystemMessage(Component.literal("no escort on " + layer));
            return 0;
        }
        switch (what) {
            case "secure" -> {
                if (r.state() == AbyssEscort.State.WALK) {
                    r.at = AbyssEscort.waitIndex(r, Math.min(3, r.secured() + 1));
                    AbyssEscort.enterWait(level, r, now);
                }
                if (r.state() == AbyssEscort.State.WAIT) {
                    AbyssEscort.secure(level, r, now, List.of(p));
                }
            }
            case "fail" -> AbyssEscort.setEndurance(r, 0);
            case "at1", "at2", "at3" -> {
                // 待ち場 k に着いたところ (前の待ち場は確かにしたことにする)
                int k = what.charAt(2) - '0';
                while (r.secured() < k - 1) {
                    if (r.state() != AbyssEscort.State.WAIT) {
                        r.at = AbyssEscort.waitIndex(r, r.secured() + 1);
                        AbyssEscort.enterWait(level, r, now);
                    }
                    AbyssEscort.secure(level, r, now, List.of(p));
                }
                r.at = AbyssEscort.waitIndex(r, k);
                AbyssEscort.enterWait(level, r, now);
            }
            case "run" -> {
                while (r.secured() < 3) {
                    if (r.state() != AbyssEscort.State.WAIT) {
                        r.at = AbyssEscort.waitIndex(r, r.secured() + 1);
                        AbyssEscort.enterWait(level, r, now);
                    }
                    AbyssEscort.secure(level, r, now, List.of(p));
                }
                AbyssEscort.startEscape(level, r, now);
            }
            case "stop" -> AbyssEscort.end(level, r, true);
            default -> {
                return 0;
            }
        }
        return 1;
    }

    static int go(ServerPlayer p, String mark, float yaw) {
        BlockPos m = AbyssBuild.mark(mark);
        ServerLevel level = p.server.getLevel(AbyssWorld.LEVEL);
        if (m == null || level == null) {
            p.sendSystemMessage(Component.literal("no mark " + mark));
            return 0;
        }
        p.teleportTo(level, m.getX() + 0.5, m.getY(), m.getZ() + 0.5, yaw, 10f);
        return 1;
    }
}
