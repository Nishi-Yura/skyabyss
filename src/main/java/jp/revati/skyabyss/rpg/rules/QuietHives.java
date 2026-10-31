package jp.revati.skyabyss.rpg.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.treedecorators.BeehiveDecorator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 蜂の巣を置かない (2026-10-31 の試遊「蜂の巣からめっちゃ音が聞こえる」)。
// 蜂はバニラの生き物なので世界に入れない (RpgRules.onJoin)。巣は昼に蜂を出そうとして、出られないまま毎 tick 出る音 (BEEHIVE_EXIT) を鳴らす。
// 新しい木は巣を付けない (data/minecraft/worldgen/configured_feature の上書き。tools/gen/world/quiet_bee_trees.py)。
// 前の版でできたチャンクの巣は、読んだときに覚え、次の tick で空気にする (読む途中にはブロックを変えない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class QuietHives {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> PENDING = new ConcurrentHashMap<>();

    private QuietHives() {
    }

    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        for (var be : chunk.getBlockEntities().values()) {
            if (be instanceof BeehiveBlockEntity) {
                PENDING.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(be.getBlockPos().immutable());
            }
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            clear(level);
        }
    }

    // 覚えた巣を空気にする (読まれていない所は、また読まれたときに覚え直す)。取った数
    static int clear(ServerLevel level) {
        Set<BlockPos> set = PENDING.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (BlockPos pos : List.copyOf(set)) {
            set.remove(pos);
            if (level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof BeehiveBlock) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                n++;
            }
        }
        if (n > 0) {
            SkyAbyss.LOGGER.info("[hives] removed {} bee nest(s) in {}", n, level.dimension().location());
        }
        return n;
    }

    // 自己テスト "quiet hives": 木の飾りに蜂の巣が残っていない・読んだチャンクの巣は次の tick で消える
    public static void selfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        server.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).entrySet().forEach(e -> {
            if (e.getValue().config() instanceof TreeConfiguration tree && tree.decorators.stream().anyMatch(d -> d instanceof BeehiveDecorator)) {
                f.add("tree feature " + e.getKey().location() + " still grows bee nests");
            }
        });
        ServerLevel level = server.overworld();
        BlockPos at = level.getSharedSpawnPos().above(40);
        level.getChunkAt(at);
        net.minecraft.world.level.block.state.BlockState before = level.getBlockState(at);
        level.setBlock(at, Blocks.BEE_NEST.defaultBlockState(), 3);
        onChunkLoad(new ChunkEvent.Load(level.getChunkAt(at), false));
        int cleared = clear(level);
        if (cleared != 1 || !level.getBlockState(at).isAir()) {
            f.add("a bee nest in a loaded chunk was not cleared (cleared " + cleared + ", now " + level.getBlockState(at) + ")");
        }
        level.setBlock(at, before, 3);
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST quiet hives PASSED (no tree grows bee nests, old nests are cleared on load)");
        } else {
            f.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST quiet hives FAILED: {}", s));
        }
    }
}
