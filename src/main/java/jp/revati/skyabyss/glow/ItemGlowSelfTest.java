package jp.revati.skyabyss.glow;

import java.util.List;
import java.util.UUID;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.LorePageItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// 持ち主だけの発光の自己テスト: 持ち主の一覧に入る・他の人の一覧には入らない・確保のない品は入らない・パケットが往復する
public final class ItemGlowSelfTest {
    private ItemGlowSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos p = level.getSharedSpawnPos();
        UUID alice = UUID.nameUUIDFromBytes("glow-a".getBytes());
        UUID bob = UUID.nameUUIDFromBytes("glow-b".getBytes());
        ItemEntity page = new ItemEntity(level, p.getX() + 0.5, p.getY() + 3, p.getZ() + 0.5, LorePageItem.of("shadow"));
        page.setTarget(alice);
        ItemEntity free = new ItemEntity(level, p.getX() + 1.5, p.getY() + 3, p.getZ() + 0.5, new ItemStack(Items.STICK));
        level.addFreshEntity(page);
        level.addFreshEntity(free);
        String fail = null;
        try {
            List<int[]> a = ItemGlow.collect(level, alice, p.getX(), p.getY(), p.getZ());
            List<int[]> b = ItemGlow.collect(level, bob, p.getX(), p.getY(), p.getZ());
            if (a.stream().noneMatch(e -> e[0] == page.getId() && e[1] == ItemGlow.PAGE)) {
                fail = "owner list lacks the page";
            } else if (a.stream().anyMatch(e -> e[0] == free.getId()) || b.stream().anyMatch(e -> e[0] == page.getId())) {
                fail = "unreserved or other player's item listed";
            } else {
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
                ItemGlow.Sync.STREAM_CODEC.encode(buf, new ItemGlow.Sync(new int[] {page.getId(), 7}, new byte[] {0, 1}));
                ItemGlow.Sync back = ItemGlow.Sync.STREAM_CODEC.decode(buf);
                if (back.ids().length != 2 || back.ids()[0] != page.getId() || back.kinds()[1] != 1) {
                    fail = "sync packet roundtrip";
                }
            }
        } finally {
            page.discard();
            free.discard();
        }
        if (fail == null) {
            SkyAbyss.LOGGER.info("SELFTEST item glow PASSED (owner-only list, unreserved excluded, packet roundtrip)");
        } else {
            SkyAbyss.LOGGER.error("SELFTEST item glow FAILED: {}", fail);
        }
    }
}
