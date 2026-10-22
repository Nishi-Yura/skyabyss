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
//   入口    都の下の大坑道の底の境の門 (34 の 4.7)。中身ができるまで門は開かない。開発用: /skyabyss abyss enter | leave | build
//   着く所  縦穴の上端の縁の踊り場 (設計図 abyss_layers の印 arrival。AbyssBuild が建てる)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssWorld {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "abyss"));
    public static final BlockPos ARRIVAL = new BlockPos(0, 240, 0);

    private AbyssWorld() {
    }

    // 縁の踊り場 (設計図の印 arrival) へ運ぶ。層をまだ建てていなければ建て始め、着く所の足もとだけ先に置く
    public static boolean enter(ServerPlayer p) {
        ServerLevel level = p.server.getLevel(LEVEL);
        if (level == null) {
            return false;
        }
        AbyssBuild.start(p.server);
        BlockPos at = arrival();
        level.getChunk(at);
        if (level.getBlockState(at.below()).isAir()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(at.offset(dx, -1, dz), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
                }
            }
        }
        p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 90f, 0f);
        return true;
    }

    public static BlockPos arrival() {
        BlockPos m = AbyssBuild.mark("arrival");
        return m == null ? ARRIVAL : m;
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
                        .then(Commands.literal("build").executes(c -> {
                            AbyssBuild.start(c.getSource().getServer());
                            c.getSource().sendSuccess(() -> Component.literal(AbyssBuild.built(c.getSource().getServer()) ? "already built" : "building the abyss layers"), false);
                            return 1;
                        }))
                        .then(Commands.literal("leave").executes(c -> {
                            leave(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(jp.revati.skyabyss.abyss.mobs.AbyssMobSpawner.command())));
    }
}
