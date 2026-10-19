package jp.revati.skyabyss.titan.audit;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.titan.tide.TideTravel;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 開発用: 建築の監査の指摘を世界の中で光らせる。
//   /skyabyss dev buildaudit here [半径]               : 自分のまわり (既定 40) を調べる
//   /skyabyss dev buildaudit <whale|tide|storm|ember>  : その背の全体 (建てた後。時間がかかる。許す例は除く)
//   /skyabyss dev buildaudit clear                     : 光を消す
// 指摘ごとに粒 (階段=炎、取りつけ・戸=魂の炎、浮き=終わりの棒、流体=水しぶき、暗さ=煙、そのほか=緑) を 1 秒ごとに 60 秒、実行者にだけ見せ、
// チャットに上位 15 件 (押すとそこへ飛ぶ) を出す。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BuildAuditDev {
    private static final List<BuildAudit.Finding> SHOWN = new ArrayList<>();
    private static ServerPlayer viewer;
    private static ServerLevel viewLevel;
    private static int ticksLeft;

    private BuildAuditDev() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("buildaudit")
                        .then(Commands.literal("clear").executes(ctx -> {
                            SHOWN.clear();
                            ticksLeft = 0;
                            return 1;
                        }))
                        .then(Commands.literal("here").executes(ctx -> here(ctx.getSource().getPlayerOrException(), 40))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(4, 120))
                                        .executes(ctx -> here(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.argument("structure", StringArgumentType.word()).executes(ctx -> structure(ctx.getSource().getPlayerOrException(),
                                StringArgumentType.getString(ctx, "structure")))))));
    }

    private static int here(ServerPlayer player, int r) {
        ServerLevel level = player.serverLevel();
        BlockPos c = player.blockPosition();
        BackReach.Box box = new BackReach.Box(c.getX() - r, Math.max(level.getMinBuildHeight(), c.getY() - r), c.getZ() - r, c.getX() + r,
                Math.min(level.getMaxBuildHeight() - 1, c.getY() + r), c.getZ() + r);
        BuildAudit.Report rep = new BuildAudit.Report();
        BuildAudit.scan(new BuildAudit.WorldGrid(level), box, "here", rep);
        return show(player, level, "here " + box, rep);
    }

    private static int structure(ServerPlayer player, String name) {
        ResourceKey<Level> key;
        BackReach.Box box;
        switch (name) {
            case "whale" -> {
                key = TitanTravel.TITAN_BACK;
                box = BuildAuditSelfTest.WHALE_BOX;
            }
            case "tide" -> {
                key = TideTravel.TIDE_BACK;
                box = BuildAuditSelfTest.TIDE_BOX;
            }
            case "storm" -> {
                key = StormTravel.STORM_BACK;
                box = BuildAuditSelfTest.STORM_BOX;
            }
            case "ember" -> {
                key = EmberIds.BACK;
                box = BuildAuditSelfTest.EMBER_BOX;
            }
            default -> {
                player.sendSystemMessage(Component.literal("unknown structure " + name + " (here <radius> | whale | tide | storm | ember | clear)"));
                return 0;
            }
        }
        ServerLevel level = player.server.getLevel(key);
        if (level == null) {
            player.sendSystemMessage(Component.literal("dimension " + key.location() + " is not loaded"));
            return 0;
        }
        BuildAudit.Report rep = new BuildAudit.Report();
        rep.allow.addAll(BuildAuditSelfTest.ALLOW);
        BuildAudit.scan(new BuildAudit.WorldGrid(level), box, name, rep);
        return show(player, level, name, rep);
    }

    private static int show(ServerPlayer player, ServerLevel level, String what, BuildAudit.Report rep) {
        SHOWN.clear();
        SHOWN.addAll(rep.findings);
        viewer = player;
        viewLevel = level;
        ticksLeft = 1200;
        player.sendSystemMessage(Component.literal("build audit " + what + ": " + rep.findings.size() + " findings, " + rep.suppressed.size() + " allowed, "
                + rep.scanned + " stairs/doors/ladders"));
        rep.counts().forEach((k, v) -> player.sendSystemMessage(Component.literal("  " + k + " x" + v)));
        for (int i = 0; i < Math.min(15, rep.findings.size()); i++) {
            BuildAudit.Finding f = rep.findings.get(i);
            String tp = "/execute in " + level.dimension().location() + " run tp @s " + f.pos().getX() + " " + (f.pos().getY() + 1) + " " + f.pos().getZ();
            player.sendSystemMessage(Component.literal("  " + f.kind() + " " + f.pos().toShortString() + " " + f.message())
                    .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tp))));
        }
        return rep.findings.size();
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (ticksLeft <= 0 || viewer == null || viewLevel == null || SHOWN.isEmpty()) {
            return;
        }
        ticksLeft--;
        if (ticksLeft % 20 != 0 || viewer.serverLevel() != viewLevel) {
            return;
        }
        for (BuildAudit.Finding f : SHOWN) {
            if (f.pos().distSqr(viewer.blockPosition()) > 200 * 200) {
                continue;
            }
            var particle = f.kind().startsWith("STAIR") ? ParticleTypes.FLAME
                    : f.kind().startsWith("ATTACH") || f.kind().startsWith("GATE") || f.kind().startsWith("DOOR") ? ParticleTypes.SOUL_FIRE_FLAME
                    : f.kind().startsWith("FLOAT") ? ParticleTypes.END_ROD
                    : f.kind().startsWith("FLUID") ? ParticleTypes.SPLASH
                    : f.kind().startsWith("LIGHT") ? ParticleTypes.SMOKE : ParticleTypes.HAPPY_VILLAGER;
            double x = f.pos().getX() + 0.5;
            double z = f.pos().getZ() + 0.5;
            for (int i = 0; i < 4; i++) {
                viewLevel.sendParticles(viewer, particle, true, x, f.pos().getY() + 0.5 + i * 0.6, z, 2, 0.15, 0.1, 0.15, 0.0);
            }
        }
    }
}
