package jp.revati.skyabyss.titan.tide.back;

import java.util.List;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.tide.TideTravel;
import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 潮殻の背の隠れた宝 (26 の 9 章): 行き止まりの窪み・展望・ひれの先に、碑 (言い伝え) と見た目だけの宝箱を置く。
// 右クリックすると、その人に一度だけ小さな贈り物 (食べ物・潮殻の欠片・干し貝・経験値) が渡る。必須の道ではない (いつも開いている)。
// 取った印は PlayerPersisted に残る (死んでも消えない)。宝箱の向きは、前が空いている方
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideCaches {
    private static final String TAG = "skyabyss_tide_cache_";

    // id・宝箱の位置 (床の上の 1 ブロック)・箱の前の向き・碑の位置と向き (壁に掛ける。碑の文は lang の sign.skyabyss.tide_lore_<id>.1〜4)
    public record Cache(String id, BlockPos chest, Direction front, BlockPos tablet, Direction tabletFace, int xp, int shards, int shells, int food) {
        public String tabletKey() {
            return "sign.skyabyss.tide_lore_" + id;
        }
    }

    // 宝箱のない碑 (ひれの道の案内と貝の間の決まりの一言)
    public record Tablet(String id, BlockPos pos, Direction face) {
        public String key() {
            return "sign.skyabyss.tide_lore_" + id;
        }
    }

    public static final List<Cache> CACHES = new java.util.ArrayList<>();
    public static final List<Tablet> TABLETS = new java.util.ArrayList<>();

    static {
        // 庭の窪み
        add("garden_a", -52, 65, 6, Direction.WEST, -55, 69, 8, Direction.NORTH, 30, 1, 0, 2);
        add("garden_b", -95, 65, 54, Direction.NORTH, -97, 69, 51, Direction.EAST, 30, 0, 1, 2);
        // 首の廊下の窪み
        add("neck_a", -8, 67, -36, Direction.SOUTH, -10, 68, -38, Direction.EAST, 35, 2, 0, 0);
        add("neck_b", 8, 67, -42, Direction.SOUTH, 10, 68, -44, Direction.WEST, 35, 1, 1, 0);
        // 縁の回廊の窪み
        add("cave", -9, 70, 52, Direction.WEST, -5, 71, 50, Direction.WEST, 25, 0, 1, 3);
        // 灯台のテラス
        add("terrace", 95, 93, -62, Direction.SOUTH, 86, 94, -64, Direction.SOUTH, 50, 2, 1, 2);
        // 冠の回廊 (予約の間 1〜3)
        add("crown_1", 29, 114, -8, Direction.WEST, 31, 116, -5, Direction.WEST, 40, 1, 1, 2);
        add("crown_2", 25, 131, -8, Direction.WEST, 27, 133, -5, Direction.WEST, 45, 2, 1, 0);
        add("crown_3", 21, 147, -8, Direction.WEST, 23, 149, -5, Direction.WEST, 60, 2, 2, 0);
        // ひれの先端 (西・東)
        add("fin_w_tip", -177, 66, -22, Direction.EAST, -179, 68, -20, Direction.EAST, 70, 3, 2, 3);
        add("fin_e_tip", 177, 66, -22, Direction.WEST, 179, 68, -20, Direction.WEST, 70, 3, 2, 3);
        // ひれの道の入口の案内と、貝の間の決まり
        tab("fin_w_gate", -82, 69, -12, Direction.SOUTH);
        tab("fin_e_gate", 98, 76, -16, Direction.WEST);
        tab("fin_w1", -123, 74, -29, Direction.SOUTH);
        tab("fin_w2", -143, 74, -29, Direction.SOUTH);
        tab("fin_e1", 123, 74, -29, Direction.SOUTH);
        tab("fin_e2", 143, 74, -29, Direction.SOUTH);
    }

    static void tab(String id, int x, int y, int z, Direction face) {
        TABLETS.add(new Tablet(id, new BlockPos(x, y, z), face));
    }

    static void add(String id, int cx, int cy, int cz, Direction front, int tx, int ty, int tz, Direction face, int xp, int shards, int shells, int food) {
        CACHES.add(new Cache(id, new BlockPos(cx, cy, cz), front, new BlockPos(tx, ty, tz), face, xp, shards, shells, food));
    }

    private TideCaches() {
    }

    // 建てるとき: 宝箱と碑を置く
    static void build(TideKit kit) {
        for (Cache c : CACHES) {
            put(kit, c);
        }
        for (Tablet t : TABLETS) {
            kit.wallSign(t.pos(), t.face(), t.key());
        }
    }

    private static void put(TideKit kit, Cache c) {
        kit.set(c.chest(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, c.front()));
        kit.wallSign(c.tablet(), c.tabletFace(), c.tabletKey());
    }

    // 部屋を掘り直したあとに、その部屋の宝箱と碑だけ置き直す
    static void place(TideKit kit, String id) {
        for (Tablet t : TABLETS) {
            if (t.id().equals(id)) {
                kit.wallSign(t.pos(), t.face(), t.key());
            }
        }
        for (Cache c : CACHES) {
            if (c.id().equals(id)) {
                put(kit, c);
            }
        }
    }

    public static boolean isCache(BlockPos pos) {
        for (Cache c : CACHES) {
            if (c.chest().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().dimension() != TideTravel.TIDE_BACK
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        if (!event.getLevel().getBlockState(event.getPos()).is(Blocks.CHEST)) {
            return;
        }
        for (Cache c : CACHES) {
            if (c.chest().equals(event.getPos())) {
                event.setCanceled(true);
                open(player, c);
                return;
            }
        }
    }

    static boolean taken(Player player, Cache c) {
        return persisted(player).getBoolean(TAG + c.id());
    }

    static void open(ServerPlayer player, Cache c) {
        CompoundTag tag = persisted(player);
        if (tag.getBoolean(TAG + c.id())) {
            Notice.bar(player, ModText.hint("message.skyabyss.tide_cache_empty"));
            return;
        }
        tag.putBoolean(TAG + c.id(), true);
        if (c.shards() > 0) {
            give(player, new ItemStack(TideMobSetup.TIDE_SHELL_SHARD.get(), c.shards()));
        }
        if (c.shells() > 0) {
            give(player, new ItemStack(TideMobSetup.TIDE_DRIED_SHELL.get(), c.shells()));
        }
        if (c.food() > 0) {
            give(player, new ItemStack(ModItems.CLOUD_JERKY.get(), c.food()));
        }
        player.level().addFreshEntity(new ExperienceOrb(player.level(), player.getX(), player.getY(), player.getZ(), c.xp()));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.4f, 1.2f);
        Notice.send(player, ModText.progress("message.skyabyss.tide_cache_found"));
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
