package jp.revati.skyabyss.story.ch5;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 深淵の層の近道 (docs/design/36 の 8 章。00 の 253)。設計図の印は tools/abyss/layer_common.py が置く。
//   巻き上げ機  層の底の休み場の磁石石 (印 shortcut_<層>_bottom)。閉じているときに右クリックすると開く (ワールドで共有)
//              開いたあとは右クリックで上の休み場へ (鎖を巻き上げる)
//   掛け金      層の上の休み場 (骨は着く所) の鉄格子の杭 (印 shortcut_<層>_top)。開いていれば右クリックで下の休み場へ
//   開いた鎖    掛け金から下へ鎖を 6 段掛けて見せる (飾り。読み込まれていれば開いたときと、人が来たときに掛け直す)
//   ほかから    層の仕掛けが開けるときは open(server, 層) (骨の層は巣の番を倒したときに)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssShortcuts {
    static final int CHAIN = 6;

    private AbyssShortcuts() {
    }

    public static boolean isOpen(MinecraftServer server, String layer) {
        return Data.get(server).open.contains(layer);
    }

    // 開く (初めて開いたなら true)
    public static boolean open(MinecraftServer server, String layer) {
        Data d = Data.get(server);
        boolean first = d.open.add(layer);
        if (first) {
            d.setDirty();
        }
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level != null) {
            hang(level, layer);
        }
        return first;
    }

    // 全部閉じる (開発用・自己テスト用。掛けた鎖の飾りは残る)
    public static void reset(MinecraftServer server) {
        Data d = Data.get(server);
        d.open.clear();
        d.setDirty();
    }

    // 掛け金から下へ鎖 (読み込まれているときだけ)
    static void hang(ServerLevel level, String layer) {
        BlockPos top = AbyssBuild.mark("shortcut_" + layer + "_top");
        if (top == null || !level.isLoaded(top)) {
            return;
        }
        // 杭の内側 (縦穴の芯の方) 1 マスに下がる
        double r = Math.hypot(top.getX(), top.getZ());
        BlockPos in = BlockPos.containing(top.getX() * (r - 1.5) / r, top.getY(), top.getZ() * (r - 1.5) / r);
        for (int i = 1; i <= CHAIN; i++) {
            BlockPos c = in.below(i);
            if (level.getBlockState(c).isAir()) {
                level.setBlock(c, Blocks.CHAIN.defaultBlockState(), 3);
            }
        }
    }

    // 立つ所: 印から縦穴の芯の方へ 1.5 (道の上)
    @Nullable
    static BlockPos stand(String mark) {
        BlockPos m = AbyssBuild.mark(mark);
        if (m == null) {
            return null;
        }
        double r = Math.hypot(m.getX(), m.getZ());
        return BlockPos.containing(m.getX() * (r - 1.5) / r + 0.5, m.getY(), m.getZ() * (r - 1.5) / r + 0.5);
    }

    @Nullable
    static String layerAt(BlockPos clicked, String end) {
        for (String layer : AbyssFinds.LAYERS) {
            BlockPos m = AbyssBuild.mark("shortcut_" + layer + "_" + end);
            if (m != null && m.equals(clicked)) {
                return layer;
            }
        }
        return null;
    }

    static void move(ServerPlayer p, @Nullable BlockPos to, String key) {
        if (to == null) {
            return;
        }
        Notice.hint(p, key);
        p.teleportTo(p.serverLevel(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.serverLevel().playSound(null, to, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 0.6f);
    }

    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !AbyssWorld.in(p)) {
            return;
        }
        MinecraftServer server = p.server;
        String bottom = layerAt(event.getPos(), "bottom");
        if (bottom != null) {
            event.setCanceled(true);
            if (!isOpen(server, bottom)) {
                open(server, bottom);
                Notice.narrate(p, "message.skyabyss.abyss.shortcut_open");
                p.serverLevel().playSound(null, event.getPos(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.5f, 0.5f);
            } else {
                move(p, stand("shortcut_" + bottom + "_top"), "message.skyabyss.abyss.shortcut_up");
                hang(p.serverLevel(), bottom);
            }
            return;
        }
        String top = layerAt(event.getPos(), "top");
        if (top != null) {
            event.setCanceled(true);
            if (isOpen(server, top)) {
                move(p, stand("shortcut_" + top + "_bottom"), "message.skyabyss.abyss.shortcut_down");
            } else {
                Notice.hint(p, "message.skyabyss.abyss.shortcut_top_closed");
            }
        }
    }

    // 開いた近道 (ワールドに一つ)
    static final class Data extends SavedData {
        private static final Factory<Data> FACTORY = new Factory<>(Data::new, Data::load);
        final Set<String> open = new HashSet<>();

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "skyabyss_abyss_shortcuts");
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            for (Tag t : tag.getList("Open", Tag.TAG_STRING)) {
                d.open.add(t.getAsString());
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            open.forEach(s -> list.add(StringTag.valueOf(s)));
            tag.put("Open", list);
            return tag;
        }
    }
}
