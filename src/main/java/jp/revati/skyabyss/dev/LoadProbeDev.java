package jp.revati.skyabyss.dev;

import java.util.Arrays;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import jp.revati.skyabyss.SkyAbyss;

// 開発用: /skyabyss dev load <名前> [秒]。サーバーの 1 tick にかかった時間を秒の間だけ数え、記録に
// 「LOAD <名前>: avg … p95 … max … ms, entities …, chunks …」を書く (重い場所の見回り。撮影の台本 tools/shots/load_probe.txt)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class LoadProbeDev {
    private LoadProbeDev() {
    }

    private static String label;
    private static long[] samples = new long[0];
    private static int count;
    private static long start;

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("load")
                        .then(Commands.argument("label", StringArgumentType.word())
                                .executes(ctx -> begin(StringArgumentType.getString(ctx, "label"), 20))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 120))
                                        .executes(ctx -> begin(StringArgumentType.getString(ctx, "label"), IntegerArgumentType.getInteger(ctx, "seconds"))))))));
    }

    private static int begin(String name, int seconds) {
        label = name;
        samples = new long[seconds * 20];
        count = 0;
        return 1;
    }

    @SubscribeEvent
    static void onPre(ServerTickEvent.Pre event) {
        if (label != null) {
            start = System.nanoTime();
        }
    }

    @SubscribeEvent
    static void onPost(ServerTickEvent.Post event) {
        if (label == null || start == 0) {
            return;
        }
        samples[count++] = System.nanoTime() - start;
        if (count < samples.length) {
            return;
        }
        long[] s = Arrays.copyOf(samples, count);
        Arrays.sort(s);
        double avg = Arrays.stream(s).average().orElse(0) / 1e6;
        MinecraftServer server = event.getServer();
        int entities = 0;
        int ours = 0;
        int chunks = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                entities++;
                if (e.getType().builtInRegistryHolder().key().location().getNamespace().equals(SkyAbyss.MODID)) {
                    ours++;
                }
            }
            chunks += level.getChunkSource().getLoadedChunksCount();
        }
        SkyAbyss.LOGGER.info(String.format(java.util.Locale.ROOT, "LOAD %s: avg %.2f p95 %.2f max %.2f ms over %d ticks, entities %d (mod %d), chunks %d",
                label, avg, s[(int) (s.length * 0.95)] / 1e6, s[s.length - 1] / 1e6, s.length, entities, ours, chunks));
        label = null;
        start = 0;
    }
}
