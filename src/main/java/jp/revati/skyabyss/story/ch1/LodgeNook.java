package jp.revati.skyabyss.story.ch1;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.SiteData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 帰る場所 (E12): 鐘の宿りの小屋の中に、ベッド (西の壁ぎわ) とかまど (東の壁ぎわ) を置く。
// 夜に眠れる・火で温まれる、人のいる気配。言伝ての板は FreeQuestEvents が鐘のそばに立てるので、ここでは置かない。
// 宿りの部品 (data/skyabyss/opening/shelter.json) には手を入れず、建て終わったあとに、部品の印 (door・bell・stand) から向きを割り出して足す。
// すでに何かがある所には置かない。世界ごとに一度 (Ch1Data.nook)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class LodgeNook {
    private static final int EVERY = 200;

    private LodgeNook() {
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || level.getGameTime() % EVERY != 53) {
            return;
        }
        Ch1Data data = Ch1Data.get(level.getServer().overworld());
        if (data.nook || !OpeningSites.planned(level.getServer()) || !OpeningSites.built(level.getServer(), OpeningSites.Kind.SHELTER)) {
            return;
        }
        if (place(level)) {
            data.nook = true;
            data.setDirty();
        }
    }

    // 置く。宿りがまだ読み込まれていないなら false (あとでやり直す)
    static boolean place(ServerLevel level) {
        SiteData sites = SiteData.get(level.getServer().overworld());
        SiteData.Entry entry = sites.first("shelter");
        if (entry == null) {
            return false;
        }
        BlockPos door = entry.extra("door");
        BlockPos bell = entry.extra("bell");
        if (door == null || bell == null || !level.hasChunkAt(door) || !level.hasChunkAt(bell)) {
            return false;
        }
        Frame frame = frameOf(door, bell);
        if (frame == null) {
            // 向きが割り出せない (人の手が入って部品と食い違う): あきらめて済みにする
            SkyAbyss.LOGGER.warn("[ch1] could not read the lodge orientation; the nook is skipped");
            return true;
        }
        BlockPos origin = frame.origin();
        int rot = frame.rot();
        boolean mirror = frame.mirror();
        BlockPos foot = at(-2, 1, -3, rot, mirror, origin);
        BlockPos head = at(-2, 1, -4, rot, mirror, origin);
        if (level.getBlockState(foot).isAir() && level.getBlockState(head).isAir()) {
            level.setBlock(foot, Blueprint.transform(Blocks.BROWN_BED.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH)
                    .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), rot, mirror), 3);
            level.setBlock(head, Blueprint.transform(Blocks.BROWN_BED.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH)
                    .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), rot, mirror), 3);
        }
        BlockPos oven = at(2, 1, -3, rot, mirror, origin);
        if (level.getBlockState(oven).isAir()) {
            BlockState smoker = Blocks.SMOKER.defaultBlockState().setValue(net.minecraft.world.level.block.SmokerBlock.FACING, net.minecraft.core.Direction.SOUTH);
            level.setBlock(oven, Blueprint.transform(smoker, rot, mirror), 3);
        }
        return true;
    }

    // 宿りの向き (原点 = 戸の一つ下。部品の座標 → 世界: Blueprint.transform(x, y + 1, z, rot, mirror, origin))
    record Frame(BlockPos origin, int rot, boolean mirror, Blueprint bp) {
        // 宿りの部品の座標 (x, y, z。y は最初の段 = 0) の世界の位置
        BlockPos at(int x, int y, int z) {
            return Blueprint.transform(x, y + 1, z, rot, mirror, origin);
        }
    }

    // 戸 (door) と鐘 (bell) の位置から、宿りの向きを割り出す。合わなければ null
    @Nullable
    static Frame frameOf(BlockPos door, BlockPos bell) {
        Blueprint bp = Blueprint.get("shelter");
        BlockPos origin = door.below();
        for (int r = 0; r < 4; r++) {
            for (int m = 0; m < 2; m++) {
                if (matches(bp, "bell", r, m == 1, origin, bell) && matches(bp, "door", r, m == 1, origin, door)) {
                    return new Frame(origin, r, m == 1, bp);
                }
            }
        }
        return null;
    }

    // 建て終わった宿りの向き (まだ・読み込まれていなければ null)
    @Nullable
    static Frame frame(ServerLevel level) {
        SiteData.Entry entry = SiteData.get(level.getServer().overworld()).first("shelter");
        if (entry == null) {
            return null;
        }
        BlockPos door = entry.extra("door");
        BlockPos bell = entry.extra("bell");
        if (door == null || bell == null || !level.hasChunkAt(door) || !level.hasChunkAt(bell)) {
            return null;
        }
        return frameOf(door, bell);
    }

    private static BlockPos at(int x, int y, int z, int rot, boolean mirror, BlockPos origin) {
        return Blueprint.transform(x, y + 1, z, rot, mirror, origin);
    }

    private static boolean matches(Blueprint bp, String mark, int rot, boolean mirror, BlockPos origin, @Nullable BlockPos world) {
        int[] m = bp.marks.get(mark);
        return m != null && world != null && Blueprint.transform(m[0], m[1] + 1, m[2], rot, mirror, origin).equals(world);
    }
}
