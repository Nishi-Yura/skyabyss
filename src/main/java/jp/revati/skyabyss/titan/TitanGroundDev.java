package jp.revati.skyabyss.titan;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.tide.TideKeeper;
import jp.revati.skyabyss.titan.tide.TideShellEntity;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発用 (台本のスクショ用): 巨神が地形にめり込まない仕組み (TitanGround) の入り切りと、潮殻をその場に据える。
//   /skyabyss dev titanground <true|false>              測りを入れる (既定 true)。false で従来どおり (めり込む高さ・場所)
//   /skyabyss dev titanground place tide <x> <z> <yaw>  潮殻を (x, z) に向き yaw で呼び、すぐ休ませる (潮門が出る)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanGroundDev {
    private TitanGroundDev() {
    }

    // 浅瀬の休み場所の候補 (旧い決まり: 深さ 6〜14・岸から 40 以内) を格子で探し、体が地形にめり込む量 (ブロック。休む高さ = 海面 - REST_DRAFT のとき) を測る。
    // めり込みが大きい順と小さい順の数点を (x z yaw めり込み) で出す (撮影場所探し)
    private static int scout(net.minecraft.commands.CommandSourceStack src, int cx, int cz, int radius) {
        ServerLevel level = src.getServer().overworld();
        jp.revati.skyabyss.titan.solid.TitanVolume volume = jp.revati.skyabyss.titan.solid.TitanVolume.get("tide_shell");
        boolean was = TitanGround.enabled;
        TitanGround.enabled = true;
        java.util.List<double[]> found = new java.util.ArrayList<>();
        try {
            double restY = level.getSeaLevel() - TideShellEntity.REST_DRAFT;
            for (int x = cx - radius; x <= cx + radius && found.size() < 16; x += 40) {
                for (int z = cz - radius; z <= cz + radius && found.size() < 16; z += 40) {
                    level.getChunk(x >> 4, z >> 4);
                    if (!jp.revati.skyabyss.titan.tide.TideOcean.waterAtSurface(level, x, z)) {
                        continue;
                    }
                    int d = jp.revati.skyabyss.titan.tide.TideOcean.depth(level, x, z);
                    if (d < 6 || d > 14 || !jp.revati.skyabyss.titan.tide.TideOcean.nearShore(level, x, z, 40)) {
                        continue;
                    }
                    // いちばん近い陸の向き
                    double bestD = Double.MAX_VALUE;
                    double lx = x;
                    double lz = z;
                    for (int dir = 0; dir < 16; dir++) {
                        for (int s = 4; s <= 40; s += 4) {
                            double px = x + Math.cos(Math.PI / 8 * dir) * s;
                            double pz = z + Math.sin(Math.PI / 8 * dir) * s;
                            level.getChunk((int) Math.floor(px) >> 4, (int) Math.floor(pz) >> 4);
                            if (jp.revati.skyabyss.titan.tide.TideOcean.land(level, px, pz) && s < bestD) {
                                bestD = s;
                                lx = px;
                                lz = pz;
                            }
                        }
                    }
                    if (bestD == Double.MAX_VALUE) {
                        continue;
                    }
                    float yaw = (float) (Math.atan2(lz - z, lx - x) * 180.0 / Math.PI);
                    for (int fx = (x - 110) >> 4; fx <= (x + 110) >> 4; fx++) {
                        for (int fz = (z - 110) >> 4; fz <= (z + 110) >> 4; fz++) {
                            level.getChunk(fx, fz);
                        }
                    }
                    double need = TitanGround.requiredY(level, TitanGround.Surface.FLOOR, volume, x, z, yaw);
                    found.add(new double[] {x, z, yaw, need - restY});
                }
            }
        } finally {
            TitanGround.enabled = was;
        }
        found.sort((a, b) -> Double.compare(b[3], a[3]));
        StringBuilder sb = new StringBuilder("titanground scout (x z yaw overlap):");
        for (double[] f : found) {
            sb.append(String.format(java.util.Locale.ROOT, "\n  %d %d %.0f %.1f", (int) f[0], (int) f[1], f[2], f[3]));
        }
        String text = sb.toString();
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal(text), false);
        return found.size();
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("titanground")
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                            TitanGround.enabled = BoolArgumentType.getBool(ctx, "on");
                            return 1;
                        }))
                        .then(Commands.literal("scout")
                                .then(Commands.argument("cx", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                        .then(Commands.argument("cz", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(40, 1200))
                                                        .executes(ctx -> scout(ctx.getSource(),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "cx"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "cz"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius")))))))
                        .then(Commands.literal("callspot")
                                .then(Commands.argument("px", DoubleArgumentType.doubleArg()).then(Commands.argument("pz", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> {
                                            ServerLevel level = ctx.getSource().getServer().overworld();
                                            double px = DoubleArgumentType.getDouble(ctx, "px");
                                            double pz = DoubleArgumentType.getDouble(ctx, "pz");
                                            net.minecraft.world.phys.Vec3 spot = jp.revati.skyabyss.titan.tide.TideOcean.findCallSpot(level, px, pz);
                                            String text = spot == null ? "callspot: none" : String.format(java.util.Locale.ROOT, "callspot: %d %d %.0f dist %d", (int) spot.x, (int) spot.z,
                                                    spot.y, (int) Math.hypot(spot.x - px, spot.z - pz));
                                            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(text), false);
                                            return spot == null ? 0 : 1;
                                        }))))
                        .then(Commands.literal("place").then(Commands.literal("tide")
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg()).then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("yaw", DoubleArgumentType.doubleArg()).executes(ctx -> {
                                            ServerLevel level = ctx.getSource().getServer().overworld();
                                            TideShellEntity existing = TideKeeper.KEEPER.current(level);
                                            CompoundTag state = existing != null ? existing.saveState() : TideKeeper.KEEPER.slot(level).state;
                                            TideShellEntity titan = TideKeeper.KEEPER.spawn(level, DoubleArgumentType.getDouble(ctx, "x"),
                                                    DoubleArgumentType.getDouble(ctx, "z"), state.isEmpty() ? null : state);
                                            titan.setYRot((float) DoubleArgumentType.getDouble(ctx, "yaw"));
                                            titan.restHere();
                                            return 1;
                                        })))))))));
    }
}
