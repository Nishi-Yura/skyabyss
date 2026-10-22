package jp.revati.skyabyss.story.ch5;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 五章の深淵 (docs/design/36)。別のディメンション skyabyss:abyss (data/skyabyss/dimension*/abyss.json、バイオーム skyabyss:abyss)。
//   高さ    y -1536 〜 255。本編の層の大縦穴は y 240 → -400 (骨・塩・雷・灰・心臓)、心臓の下の果てない深みはそれより下 (36 の 2)
//   見た目  空も天井の光もなく、時刻は真夜中に止まる。霧は濃い (client/AbyssClient の DimensionSpecialEffects)
//   入口    都の下の大坑道の底の境の門 (34 の 4.7)。中身ができるまで門は開かない。開発用: /skyabyss abyss enter | leave
//   着く所  縦穴の縁の踊り場 (ARRIVAL)。まだ層の中身がないので、着いたときに小さな足場を置く (層を作ったら設計図の踊り場に替える)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssWorld {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "abyss"));
    public static final BlockPos ARRIVAL = new BlockPos(0, 240, 0);

    private AbyssWorld() {
    }

    // 縁の踊り場へ運ぶ (足場がなければ置く)
    public static boolean enter(ServerPlayer p) {
        ServerLevel level = p.server.getLevel(LEVEL);
        if (level == null) {
            return false;
        }
        level.getChunk(ARRIVAL);
        if (level.getBlockState(ARRIVAL.below()).isAir()) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlock(ARRIVAL.offset(dx, -1, dz), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
                }
            }
            level.setBlock(ARRIVAL.offset(2, 0, 2), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        }
        p.teleportTo(level, ARRIVAL.getX() + 0.5, ARRIVAL.getY(), ARRIVAL.getZ() + 0.5, p.getYRot(), 0f);
        return true;
    }

    // 地上へ戻す (その人の起きる所へ)
    public static void leave(ServerPlayer p) {
        MinecraftServer server = p.server;
        ServerLevel over = server.overworld();
        BlockPos at = p.getRespawnPosition() != null && p.getRespawnDimension() == Level.OVERWORLD ? p.getRespawnPosition() : over.getSharedSpawnPos();
        p.teleportTo(over, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, p.getYRot(), 0f);
    }

    public static boolean in(ServerPlayer p) {
        return p.level().dimension() == LEVEL;
    }

    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyabyss").requires(s -> s.hasPermission(2))
                .then(Commands.literal("abyss")
                        .then(Commands.literal("enter").executes(c -> {
                            boolean ok = enter(c.getSource().getPlayerOrException());
                            c.getSource().sendSuccess(() -> Component.literal(ok ? "entered the abyss" : "the abyss dimension is missing"), false);
                            return ok ? 1 : 0;
                        }))
                        .then(Commands.literal("leave").executes(c -> {
                            leave(c.getSource().getPlayerOrException());
                            return 1;
                        }))));
    }
}
