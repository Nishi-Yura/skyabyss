package jp.revati.skyabyss.abyss.boss;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import jp.revati.skyabyss.abyss.mobs.AbyssMobKind;
import jp.revati.skyabyss.abyss.mobs.AbyssMobSpawner;
import jp.revati.skyabyss.abyss.mobs.HeartbeatMimic;
import jp.revati.skyabyss.abyss.mobs.LampEater;
import jp.revati.skyabyss.story.ch5.depths.DepthBoss;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

// 開発用 (撮影・見た目の確かめ): /skyabyss abyss boss <型> [周] [技|-|phase] [予告 tick] [距離]
//   型: lord_rib・lord_salt・lord_storm・lord_ash・king_bone・king_salt・king_storm・king_ash・king_shadow
//   立っている人の前に、動かない (NoAI) 主・王を一体、人の方を向けて置く。技を渡すと予告の溜めを流す (最後の姿で止まる)。
//   戦いは始まらない (AbyssLords・DepthBoss の外の一体)。消すのは /kill。
public final class BossDev {
    static final String[] NAMES = {"lord_rib", "lord_salt", "lord_storm", "lord_ash", "king_bone", "king_salt", "king_storm", "king_ash", "king_shadow"};
    static final String[] LORD_BODY = {AbyssMobKind.RIB_GUARD.id, AbyssMobKind.WHITE_SHELL_KEEPER.id, HeartbeatMimic.ID, LampEater.ID};

    private BossDev() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("boss").then(Commands.argument("name", StringArgumentType.word())
                .executes(c -> spawn(c, 0, "-", 40, 6))
                .then(Commands.argument("cycle", IntegerArgumentType.integer(0, 9))
                        .executes(c -> spawn(c, IntegerArgumentType.getInteger(c, "cycle"), "-", 40, 6))
                        .then(Commands.argument("act", StringArgumentType.word())
                                .executes(c -> spawn(c, IntegerArgumentType.getInteger(c, "cycle"), StringArgumentType.getString(c, "act"), 40, 6))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 200))
                                        .then(Commands.argument("dist", DoubleArgumentType.doubleArg(1, 40))
                                                .executes(c -> spawn(c, IntegerArgumentType.getInteger(c, "cycle"), StringArgumentType.getString(c, "act"),
                                                        IntegerArgumentType.getInteger(c, "ticks"), DoubleArgumentType.getDouble(c, "dist"))))))));
    }

    static int spawn(CommandContext<CommandSourceStack> c, int cycle, String act, int ticks, double dist) {
        CommandSourceStack src = c.getSource();
        String name = StringArgumentType.getString(c, "name");
        int idx = -1;
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(name)) {
                idx = i;
            }
        }
        if (idx < 0) {
            src.sendFailure(Component.literal("unknown boss (" + String.join(", ", NAMES) + ")"));
            return 0;
        }
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            src.sendFailure(Component.literal("a player is needed"));
            return 0;
        }
        boolean king = idx >= 4;
        int ki = idx - 4;
        String body = king ? DepthBoss.KINGS[ki].body() : LORD_BODY[idx];
        Vec3 fwd = p.getLookAngle().multiply(1, 0, 1).normalize();
        double x = p.getX() + fwd.x * dist;
        double z = p.getZ() + fwd.z * dist;
        // 床の上に置く (動かない一体は落ちないので、人の高さに置くと浮く)
        double y = p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(z));
        Entity e = AbyssMobSpawner.spawnAt(p.serverLevel(), body, x, y, z, 3);
        if (!(e instanceof LivingEntity le)) {
            src.sendFailure(Component.literal("body " + body + " did not spawn"));
            return 0;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(fwd.x, -fwd.z));
        le.setYRot(yaw);
        le.setYBodyRot(yaw);
        le.setYHeadRot(yaw);
        if (le instanceof Mob m) {
            m.setNoAi(true);
            m.setPersistenceRequired();
        }
        double sc = king ? DepthBoss.scale(1 + ki + 5 * cycle) : BossLook.authorScale(BossLook.pack(BossLook.LORD_RIB + idx, 0, 0));
        if (le.getAttribute(Attributes.SCALE) != null) {
            le.getAttribute(Attributes.SCALE).setBaseValue(sc);
        }
        int look = BossLook.pack(king ? BossLook.KING_BONE + ki : BossLook.LORD_RIB + idx, cycle, 0);
        BossLook.set(le, look);
        if (act.equals("phase")) {
            BossLook.setPhase(le, 1);
        } else if (act.startsWith("strike_")) {
            BossLook.strike(le, code(act.substring(7)));
        } else if (!act.equals("-")) {
            BossLook.windup(le, code(act), ticks);
        }
        src.sendSuccess(() -> Component.literal("boss " + name + " cycle " + cycle + " act " + act + " (" + BossLook.stem(look) + ")"), false);
        return 1;
    }

    // /skyabyss abyss bossdo <技|phase|strike_<技>|-> [予告 tick]: まわり 48 ブロックの主・王 (見た目のあるもの) に流す
    public static LiteralArgumentBuilder<CommandSourceStack> doCommand() {
        return Commands.literal("bossdo").then(Commands.argument("act", StringArgumentType.word())
                .executes(c -> act(c, StringArgumentType.getString(c, "act"), 40))
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 200))
                        .executes(c -> act(c, StringArgumentType.getString(c, "act"), IntegerArgumentType.getInteger(c, "ticks")))));
    }

    static int act(CommandContext<CommandSourceStack> c, String act, int ticks) {
        CommandSourceStack src = c.getSource();
        Vec3 at = src.getPosition();
        int n = 0;
        for (LivingEntity le : src.getLevel().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(48))) {
            if (!(le instanceof jp.revati.skyabyss.entity.GuardMob g) || g.bossLook() == 0) {
                continue;
            }
            n++;
            if (act.equals("phase")) {
                BossLook.setPhase(le, BossLook.phase(g.bossLook()) + 1);
            } else if (act.startsWith("strike_")) {
                BossLook.strike(le, code(act.substring(7)));
            } else if (act.equals("-")) {
                BossLook.clear(le);
            } else {
                BossLook.windup(le, code(act), ticks);
            }
        }
        int k = n;
        src.sendSuccess(() -> Component.literal("bossdo " + act + " on " + k), false);
        return n;
    }

    static int code(String act) {
        for (int i = 1; i < BossLook.ACT.length; i++) {
            if (BossLook.ACT[i].equals(act)) {
                return i;
            }
        }
        return 0;
    }
}
