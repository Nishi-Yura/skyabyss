package jp.revati.skyabyss.trial;

import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModSounds;
import jp.revati.skyabyss.titan.dungeon.ResonanceStoneBlock;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.group.TrialSteles;
import jp.revati.skyabyss.titan.tide.back.TideBackLayout;
import jp.revati.skyabyss.titan.tide.back.TideBackState;
import jp.revati.skyabyss.titan.tide.back.TideBackTrials;
import jp.revati.skyabyss.titan.tide.back.TideChimeStoneBlock;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Slot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

// 巨神ごとの違い (背のディメンションが持つ、石・休み場・記録の仕組み)。試練のプール (TrialRuntime) は、新作のセルの石・戻りの光る床・石の光を、
// ここを通して扱う。岩鯨は共鳴石とグループの記録の旗、潮殻は潮鳴り石と TideBackState の鳴った印 (docs/design/29)。
public final class TrialBacks {
    private TrialBacks() {
    }

    public interface Back {
        // 背のディメンションが持つ巨神 (進行の名前)
        String titan();

        // 石の合図の巨神の名前 (TrialSteles)
        String signal();

        // 引いた枠の石が鳴っているか。drawn = そのセルを引いている (引いていない今週の試練は、セルごとの旗)
        boolean isRung(ServerLevel back, UUID group, Def def, boolean drawn);

        // 石を鳴らす (記録・光・音・知らせ。図鑑と報いは呼ぶ側)。drawn のとき、枠の石として数える
        void ring(ServerLevel back, UUID group, Def def, boolean drawn, ServerPlayer by, BlockPos stone);

        // 図鑑・報いを数えたあと (drawn の石の後始末)
        void afterCleared(ServerLevel back, UUID group, Def def, boolean drawn, ServerPlayer by);

        // 石の知らせ (すでに鳴っている石に触れた人へ、誰が鳴らしたか)
        void tellWho(ServerPlayer sp, Def def);

        // 休み場へ戻す
        void returnTo(ServerPlayer p, String restId);

        // 鳴った石の見かけ
        BlockState lit();
    }

    @Nullable
    public static Back of(@Nullable String titan) {
        if (Titans.STONE_WHALE.equals(titan)) {
            return WHALE;
        }
        if (Titans.TIDE_SHELL.equals(titan)) {
            return TIDE;
        }
        if (Titans.EMBER.equals(titan)) {
            return EMBER;
        }
        return null;
    }

    // ---------------------------------------------------------------- 岩鯨 (共鳴石。グループの記録の旗)
    private static final Back WHALE = new Back() {
        @Override
        public String titan() {
            return Titans.STONE_WHALE;
        }

        @Override
        public String signal() {
            return TrialSteles.WHALE;
        }

        private String flag(Def def, boolean drawn) {
            return drawn ? TitanBackTrials.STONE_FLAG + def.slot().stone : "wstone." + def.id();
        }

        @Override
        public boolean isRung(ServerLevel back, UUID group, Def def, boolean drawn) {
            return GroupProgress.of(back, group).flag(flag(def, drawn));
        }

        @Override
        public void ring(ServerLevel back, UUID group, Def def, boolean drawn, ServerPlayer by, BlockPos pos) {
            BackGroupData.Record r = GroupProgress.of(back, group);
            r.setFlag(flag(def, drawn), true);
            GroupOverlays.refresh(back, group);
            Groups.sound(back, group, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ModSounds.RESONANCE.get(), SoundSource.BLOCKS, 2.5f, 0.7f);
            for (ServerPlayer member : Groups.online(back, group)) {
                if (member.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 64 * 64) {
                    back.sendParticles(member, ParticleTypes.END_ROD, false, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.05);
                }
            }
        }

        @Override
        public void afterCleared(ServerLevel back, UUID group, Def def, boolean drawn, ServerPlayer by) {
            if (!drawn) {
                return;
            }
            // 岩鯨の石の三つ目なら、宝物庫の封印が開く (原型の石と同じ数え方)。クエストの石の合図もスロットの番号で数える
            TrialRuntime.withLegacySuppressed(() -> TrialSteles.onRung(back, group, TrialSteles.WHALE, String.valueOf(def.slot().stone),
                    Component.translatable(def.nameKey()), by));
            ResonanceStoneBlock.finishRung(back, by, group, GroupProgress.of(back, group));
        }

        @Override
        public void tellWho(ServerPlayer sp, Def def) {
            TrialSteles.tellWho(sp, TrialSteles.WHALE, String.valueOf(def.slot().stone));
        }

        @Override
        public void returnTo(ServerPlayer p, String restId) {
            TitanBackTrials.returnToCheckpoint(p, restId);
        }

        @Override
        public BlockState lit() {
            return ModBlocks.RESONANCE_STONE.get().defaultBlockState().setValue(ResonanceStoneBlock.ACTIVE, true);
        }
    };

    // ---------------------------------------------------------------- 潮殻 (潮鳴り石。TideBackState の鳴った印)
    public static TideBackState.Trial tideTrial(Slot slot) {
        return switch (slot) {
            case WALK -> TideBackState.Trial.WEST;
            case WIT -> TideBackState.Trial.EAST;
            case FIGHT -> TideBackState.Trial.FRONT;
        };
    }

    private static final Back TIDE = new Back() {
        @Override
        public String titan() {
            return Titans.TIDE_SHELL;
        }

        @Override
        public String signal() {
            return TrialSteles.TIDE;
        }

        @Override
        public boolean isRung(ServerLevel back, UUID group, Def def, boolean drawn) {
            return drawn ? TideBackState.of(back, group).rung(tideTrial(def.slot())) : GroupProgress.of(back, group).flag("wstone." + def.id());
        }

        @Override
        public void ring(ServerLevel back, UUID group, Def def, boolean drawn, ServerPlayer by, BlockPos pos) {
            if (drawn) {
                // 潮殻の三つの石 (西・東・前) のうち、その枠の石を鳴らす。広間の床の灯り・抜け道・三つ目で潮の底の門が開く、までを潮殻の仕組みが行う
                TideBackState.Group state = TideBackState.of(back, group);
                TrialRuntime.withLegacySuppressed(() -> TideBackTrials.ring(back, state, tideTrial(def.slot()), by));
                return;
            }
            GroupProgress.of(back, group).setFlag("wstone." + def.id(), true);
            GroupOverlays.refresh(back, group);
            TideBackTrials.notifyGroup(back, group, pos);
        }

        @Override
        public void afterCleared(ServerLevel back, UUID group, Def def, boolean drawn, ServerPlayer by) {
        }

        @Override
        public void tellWho(ServerPlayer sp, Def def) {
            TrialSteles.tellWho(sp, TrialSteles.TIDE, tideTrial(def.slot()).id());
        }

        @Override
        public void returnTo(ServerPlayer p, String restId) {
            BlockPos at = TideBackTrials.restSpawn(p.serverLevel(), restId);
            p.fallDistance = 0;
            p.teleportTo(p.serverLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), 0f);
        }

        @Override
        public BlockState lit() {
            return jp.revati.skyabyss.titan.tide.back.TideBackSetup.CHIME_STONE.get().defaultBlockState().setValue(TideChimeStoneBlock.ACTIVE, true);
        }
    };

    // 休み場の名前から、その位置 (潮殻)
    public static BlockPos tideRest(String id) {
        for (TideBackLayout.Rest r : TideBackLayout.RESTS) {
            if (r.id().equals(id)) {
                return r.spawn();
            }
        }
        return TideBackLayout.ARRIVAL;
    }

    // ---------------------------------------------------------------- 余燼 (熾り石。EmberBackState の鳴った印 ember.rung.<西・東・南>。歩 = 西、智 = 東、闘 = 南)
    public static jp.revati.skyabyss.titan.ember.back.EmberBackState.Trial emberTrial(Slot slot) {
        return switch (slot) {
            case WALK -> jp.revati.skyabyss.titan.ember.back.EmberBackState.Trial.WEST;
            case WIT -> jp.revati.skyabyss.titan.ember.back.EmberBackState.Trial.EAST;
            case FIGHT -> jp.revati.skyabyss.titan.ember.back.EmberBackState.Trial.SOUTH;
        };
    }

    private static final Back EMBER = new Back() {
        @Override
        public String titan() {
            return Titans.EMBER;
        }

        @Override
        public String signal() {
            return TrialSteles.EMBER;
        }

        @Override
        public boolean isRung(ServerLevel back, UUID group, Def def, boolean drawn) {
            return drawn ? jp.revati.skyabyss.titan.ember.back.EmberBackState.of(back, group).rung(emberTrial(def.slot()))
                    : GroupProgress.of(back, group).flag("wstone." + def.id());
        }

        @Override
        public void ring(ServerLevel back, UUID group, Def def, boolean drawn, ServerPlayer by, BlockPos pos) {
            if (drawn) {
                // 引いた枠の熾り石を鳴らす: 鳴った印・抜け道の光る床・踏破の経験値・クエスト・三つ目で炉の門が開く、までを余燼の仕組みが行う
                TrialRuntime.withLegacySuppressed(() -> jp.revati.skyabyss.titan.ember.back.EmberBackTrials.ring(back, group, emberTrial(def.slot()), by));
                return;
            }
            GroupProgress.of(back, group).setFlag("wstone." + def.id(), true);
            GroupOverlays.refresh(back, group);
            back.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 20, 0.3, 0.6, 0.3, 0.03);
        }

        @Override
        public void afterCleared(ServerLevel back, UUID group, Def def, boolean drawn, ServerPlayer by) {
        }

        @Override
        public void tellWho(ServerPlayer sp, Def def) {
            TrialSteles.tellWho(sp, TrialSteles.EMBER, emberTrial(def.slot()).id());
        }

        @Override
        public void returnTo(ServerPlayer p, String restId) {
            BlockPos at = jp.revati.skyabyss.titan.ember.back.EmberBackTrials.restSpawn(p.serverLevel(), restId);
            p.fallDistance = 0;
            p.teleportTo(p.serverLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), 0f);
            jp.revati.skyabyss.titan.ember.back.EmberHeat.reset(p);
        }

        @Override
        public BlockState lit() {
            return jp.revati.skyabyss.titan.ember.back.EmberBackSetup.KINDLE_STONE.get().defaultBlockState()
                    .setValue(jp.revati.skyabyss.titan.ember.back.KindleStoneBlock.ACTIVE, true);
        }
    };
}
