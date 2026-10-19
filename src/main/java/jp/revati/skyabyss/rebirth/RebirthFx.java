package jp.revati.skyabyss.rebirth;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

// 転生した人の見せ場 (サーバー側): C12 墓の飾り (✦ の数・色・花) と C13 倒れ・立ち上がりの光 (パケットを送り、描くのは client)
public final class RebirthFx {
    public static final String FLAIR_TAG = "skyabyss_grave_flair";

    private RebirthFx() {
    }

    // ---------------------------------------------------------------- C13 倒れと回収の光
    // rise = false: 倒れた、true: 立ち上がった。転生 0 の人には何もしない。描くのは受け取った client (1 秒まで。設定で切れる)
    public static void burst(ServerPlayer p, boolean rise) {
        if (RebirthView.count(p) > 0) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new RebirthNet.BurstPayload(p.getId(), rise));
        }
    }

    // ---------------------------------------------------------------- C12 墓の飾り
    // 墓の花 (段ごとの種類と数)。1〜4 = 青い蘭 ×2、5〜14 = タンポポ ×3、15 以上 = ヒナゲシ ×4 (置ける空きだけ)
    static Block flower(int count) {
        return switch (RebirthTier.of(count)) {
            case TEAL -> Blocks.BLUE_ORCHID;
            case GOLD -> Blocks.DANDELION;
            default -> Blocks.ALLIUM;
        };
    }

    static int flowerCount(int count) {
        return switch (RebirthTier.of(count)) {
            case TEAL -> 2;
            case GOLD -> 3;
            case PRISM -> 4;
            default -> 0;
        };
    }

    // 墓ができた (Graves.create から)。転生 0・設定が切れているときは何もしない
    public static void graveCreated(ServerPlayer owner, ServerLevel level, BlockPos pos) {
        int n = RebirthView.count(owner);
        if (n <= 0 || !RebirthConfig.on(RebirthConfig.GRAVE_FLAIR)) {
            return;
        }
        spawnLabel(level, pos, n);
        int want = flowerCount(n);
        Block flower = flower(n);
        List<BlockPos> ring = new ArrayList<>();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            ring.add(pos.relative(d));
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            ring.add(pos.relative(d).relative(d.getClockWise()));
        }
        for (BlockPos p : ring) {
            if (want <= 0) {
                break;
            }
            BlockState f = flower.defaultBlockState();
            if (level.getBlockState(p).isAir() && f.canSurvive(level, p)) {
                level.setBlock(p, f, Block.UPDATE_ALL);
                want--;
            }
        }
    }

    // 墓の上に ✦ の数を浮かべる (バニラの文字の表示 = 向きは常に見る人へ。壊れた・開けた墓から消す)
    static void spawnLabel(ServerLevel level, BlockPos pos, int count) {
        Component text = RebirthText.symbols(count, 0L, true).append(Component.literal(" ")).append(RebirthText.level(50, count, 0L, true));
        CompoundTag tag = new CompoundTag();
        tag.putString("billboard", "center");
        tag.putInt("background", 0);
        tag.putBoolean("shadow", true);
        tag.putString("text", Component.Serializer.toJson(text, level.registryAccess()));
        Display.TextDisplay display = EntityType.TEXT_DISPLAY.create(level);
        if (display == null) {
            return;
        }
        display.load(tag);
        display.moveTo(pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5);
        display.addTag(FLAIR_TAG);
        level.addFreshEntity(display);
    }

    // 墓がなくなった (GraveBlock の onRemove から)
    public static void graveRemoved(ServerLevel level, BlockPos pos) {
        for (Display.TextDisplay d : labels(level, pos)) {
            d.discard();
        }
    }

    static List<Display.TextDisplay> labels(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(Display.TextDisplay.class, new AABB(pos).inflate(0.6, 1.6, 0.6), d -> d.getTags().contains(FLAIR_TAG));
    }
}
