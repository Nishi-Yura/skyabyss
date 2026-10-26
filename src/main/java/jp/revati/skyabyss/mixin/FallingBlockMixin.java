package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

// 砂・砂利などは落ちない (2026-10-26 の試遊「はじまりの島の終わりで、地形ができるときに砂が落ちて進めない」)。
// 完全な RPG の決まり skyabyssAdventure (rpg/rules/RpgRules) が入っていれば、落ちる塊の予定の一拍で何もしない。
// 地形は決まった形のまま (壊さない・置かない遊び方なので、落ちて困ることはあっても、落ちて得することはない)
@Mixin(FallingBlock.class)
public abstract class FallingBlockMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void skyabyss$stayPut(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (jp.revati.skyabyss.rpg.rules.RpgRules.adventure(level)) {
            ci.cancel();
        }
    }
}
