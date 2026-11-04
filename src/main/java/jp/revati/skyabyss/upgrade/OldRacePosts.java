package jp.revati.skyabyss.upgrade;

import java.util.ArrayList;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.SiteData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 早駆けの道の標を片付ける (2026-11-03 の声「早駆けの道消して。いらない」)。早駆けの仕組みはもうないので、前の版で建った標 (SiteData の種類 KIND) は残り物。
// 標のチャンクが読み込まれていたら、柱・灯り・旗と石の台を取り、台の下の地面はまわりの地面に揃え、印を消す (何度流れても同じ。印がなければ何もしない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class OldRacePosts {
    static final String KIND = "race";

    private OldRacePosts() {
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 100 != 37) {
            return;
        }
        sweep(event.getServer().overworld());
    }

    // 読み込まれた標を片付けた数
    static int sweep(ServerLevel over) {
        SiteData data = SiteData.get(over);
        int n = 0;
        for (SiteData.Entry e : new ArrayList<>(data.entries(KIND))) {
            if (!over.hasChunkAt(e.pos)) {
                continue;
            }
            clear(over, e.pos);
            data.remove(e);
            data.setDirty();
            n++;
        }
        return n;
    }

    // 建てたときの形: base に木口、上に柵二つ・木口・灯り、木口の横に赤い旗、base の下 3x3 が石の台
    static void clear(ServerLevel level, BlockPos base) {
        for (int i = 4; i >= 0; i--) {
            level.setBlock(base.above(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos flag = base.above(3).relative(d);
            if (level.getBlockState(flag).is(Blocks.RED_WALL_BANNER)) {
                level.setBlock(flag, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        BlockState ground = groundNear(level, base.below());
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                BlockPos g = base.offset(a, -1, b);
                BlockState s = level.getBlockState(g);
                if (s.is(Blocks.POLISHED_ANDESITE) || s.is(Blocks.ANDESITE) || s.is(Blocks.COARSE_DIRT)) {
                    level.setBlock(g, ground, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    // 台のすぐ外の地面 (台と同じ高さで、上が空いている物)。見つからなければ草
    static BlockState groundNear(ServerLevel level, BlockPos center) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos q = center.relative(d, 2);
            BlockState s = level.getBlockState(q);
            if (s.isSolidRender(level, q) && level.getBlockState(q.above()).canBeReplaced()) {
                return s;
            }
        }
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }
}
