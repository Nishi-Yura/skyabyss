package jp.revati.skyabyss.story.ch5;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 深淵の層の大縦穴を建てる (docs/design/36 の A2)。設計図 abyss_layers (tools/abyss/gen_abyss_layers.py。原点 = 縦穴の中心、y はそのまま)。
//   一度に建てず、1 tick に BUDGET_MS だけ進める (約 27 万ブロック)。何もない空間なので、置くだけ (隣への知らせは出さない)
//   建て終えたら版 (VERSION) を記録する。版を上げると建て直す。建てている途中で止まったら、次の起動で最初から (置くのは変わるブロックだけ)
//   始まるのは、だれかが深淵に入ったとき (AbyssWorld.enter) か、開発用 /skyabyss abyss build
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssBuild {
    public static final String BLUEPRINT = "abyss_layers";
    public static final int VERSION = 5;
    static final long BUDGET_MS = 20;

    @Nullable
    private static List<Blueprint.Cell> cells;
    private static int index = -1;

    private AbyssBuild() {
    }

    public static boolean built(MinecraftServer server) {
        return Data.get(server).version >= VERSION;
    }

    public static boolean building() {
        return index >= 0;
    }

    // 建て始める (建て終えていれば何もしない)
    public static void start(MinecraftServer server) {
        if (built(server) || building()) {
            return;
        }
        cells = Blueprint.get(BLUEPRINT).cells;
        index = 0;
        SkyAbyss.LOGGER.info("[abyss] building the layers ({} blocks)", cells.size());
    }

    // 印の場所 (深淵の座標)
    @Nullable
    public static BlockPos mark(String name) {
        int[] m = Blueprint.get(BLUEPRINT).marks.get(name);
        return m == null ? null : new BlockPos(m[0], m[1], m[2]);
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        step(event.getServer());
    }

    private static void step(MinecraftServer server) {
        if (index < 0 || cells == null) {
            return;
        }
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            index = -1;
            return;
        }
        long deadline = System.nanoTime() + BUDGET_MS * 1_000_000L;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        while (index < cells.size()) {
            Blueprint.Cell c = cells.get(index++);
            p.set(c.x(), c.y(), c.z());
            if (level.getBlockState(p) != c.state()) {
                level.setBlock(p, c.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            if ((index & 255) == 0 && System.nanoTime() > deadline) {
                return;
            }
        }
        Data d = Data.get(server);
        d.version = VERSION;
        d.setDirty();
        SkyAbyss.LOGGER.info("[abyss] layers built (version {})", VERSION);
        index = -1;
        cells = null;
    }

    // 建て終えるまで、その場で進める (自己テスト用)
    public static void finishNow(MinecraftServer server) {
        start(server);
        while (index >= 0) {
            ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
            if (level == null) {
                return;
            }
            step(server);
        }
    }

    // 建てた版 (ワールドに一つ)
    static final class Data extends SavedData {
        private static final Factory<Data> FACTORY = new Factory<>(Data::new, Data::load);
        int version;

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "skyabyss_abyss_build");
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            d.version = tag.getInt("Version");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("Version", version);
            return tag;
        }
    }
}
