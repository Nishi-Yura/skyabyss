package jp.revati.skyabyss.story.finale;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// 雲の海 (docs/design/50 の 4.1)。当たりのない白いブロック。中にいる間は落ちが遅い (霞は画面の側 client/CloudSkyClient)。
// 雲の下へ抜けた人は FinaleWorld が最後に立っていた島へ戻す
public class CloudBlock extends Block {
    public CloudBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // 落ちをゆっくりに (雲に沈む)。横にはふつうに動ける
        entity.makeStuckInBlock(state, new Vec3(0.9, 0.35, 0.9));
        entity.resetFallDistance();
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighbour, net.minecraft.core.Direction dir) {
        return neighbour.is(this) || super.skipRendering(state, neighbour, dir);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
