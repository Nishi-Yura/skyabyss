package jp.revati.skyabyss.titan;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// 岩鯨の見守り。岩鯨はワールドに一体だけで、見失ってはいけない。
// 仕組み (チケット、本物の UUID、読み込み直し、呼び直し) は巨神で共通の TitanKeeper にある。ここは岩鯨に固有のこと:
// - 呼び直しの位置は、プレイヤーの 280 ブロック先の空 (高度 200)
// - 背中 (titan_back) に人がいる間と、巨神での移動 (titan.travel) の最中は呼び直さない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class WhaleKeeper {
    static final double NEAR = TitanKeeper.NEAR;
    static final double SPAWN_DISTANCE = 280;

    static final TitanKeeper<StoneWhaleEntity> KEEPER = new TitanKeeper<>(TitanWorldData.STONE_WHALE, StoneWhaleEntity.class,
            "skyabyss_whale", "stone whale") {
        @Override
        protected StoneWhaleEntity create(ServerLevel level, double x, double z, @Nullable CompoundTag state) {
            StoneWhaleEntity whale = ModEntities.STONE_WHALE.get().create(level);
            if (whale == null) {
                throw new IllegalStateException("Failed to create stone whale");
            }
            whale.moveTo(x, 200, z, level.random.nextFloat() * 360f, 0f);
            if (state != null && !state.isEmpty()) {
                whale.restoreState(state);
            }
            return whale;
        }

        @Override
        protected CompoundTag saveState(StoneWhaleEntity whale) {
            return whale.saveState();
        }

        @Override
        protected Vec3 relocationPoint(ServerLevel level, ServerPlayer target) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            return new Vec3(target.getX() + Math.cos(angle) * SPAWN_DISTANCE, 200, target.getZ() + Math.sin(angle) * SPAWN_DISTANCE);
        }

        @Override
        protected boolean someoneAboard(ServerLevel overworld) {
            ServerLevel back = overworld.getServer().getLevel(TitanTravel.TITAN_BACK);
            return back != null && !back.players().isEmpty();
        }

        @Override
        protected void announce(ServerLevel level, StoneWhaleEntity whale) {
            double x = whale.getX();
            double z = whale.getZ();
            level.playSound(null, x, 200, z, ModSounds.TITAN_SONG.get(), SoundSource.AMBIENT, 24f, 0.8f);
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(x, 200, z) < NEAR * NEAR) {
                    player.sendSystemMessage(ModText.lore("message.skyabyss.titan_appears"));
                }
            }
        }

        @Override
        protected boolean inVoyage(StoneWhaleEntity whale) {
            return whale.inVoyage();
        }

        @Override
        protected boolean busy() {
            return jp.revati.skyabyss.titan.travel.Voyages.isBusy(jp.revati.skyabyss.titan.travel.TitanKind.STONE_WHALE);
        }
    };

    private WhaleKeeper() {
    }

    // 本物の岩鯨 (読み込まれているときだけ)
    @Nullable
    public static StoneWhaleEntity current(ServerLevel overworld) {
        return KEEPER.current(overworld);
    }

    // 毎 tick、オーバーワールドで呼ばれる
    static void tick(ServerLevel level) {
        KEEPER.tick(level);
    }

    // 新しい岩鯨を出し、本物として登録する
    public static StoneWhaleEntity spawn(ServerLevel level, double x, double z, @Nullable CompoundTag state) {
        return KEEPER.spawn(level, x, z, state);
    }

    @Nullable
    static ServerPlayer nearestEligible(ServerLevel level, double x, double z, double range) {
        return TitanKeeper.nearestEligible(level, x, z, range);
    }

    @Nullable
    static ServerPlayer randomEligible(ServerLevel level) {
        return TitanKeeper.randomEligible(level);
    }

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        KEEPER.handleJoin(event);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        KEEPER.reset();
    }

    // 自己テスト (SKYABYSS_SELFTEST): 一体だけの決まり、状態の引き継ぎ、チケット。
    // プレイヤーがいないので、試す場所のチャンクを強制読み込みし、エンティティが読み込まれるのを待ってから試す
    private static final int TEST_X = 4000;
    private static final int TEST_Z = 4000;
    // 呼ぶのは SelfTestRunner: 読み込み → 40 tick 待つ → 試す
    public static void selfTestLoad(net.minecraft.server.MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int dx = 0; dx <= 4; dx++) {
            level.setChunkForced((TEST_X >> 4) + dx, TEST_Z >> 4, true);
        }
    }

    public static void selfTestRun(net.minecraft.server.MinecraftServer server) {
        runSelfTest(server.overworld());
    }

    private static void runSelfTest(ServerLevel level) {
        TitanWorldData data = TitanWorldData.get(level);
        CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
        List<String> failures = new ArrayList<>();
        int x = TEST_X + 8;
        int z = TEST_Z + 8;
        StoneWhaleEntity first = spawn(level, x, z, null);
        if (current(level) != first) {
            failures.add("the spawned whale is not the current one");
        }
        if (KEEPER.ticketPos() == null || !KEEPER.ticketPos().equals(first.chunkPosition())) {
            failures.add("no ticket on the whale's chunk");
        }
        StoneWhaleEntity stray = ModEntities.STONE_WHALE.get().create(level);
        if (stray != null) {
            stray.moveTo(x + 20, 200, z, 0f, 0f);
            if (level.addFreshEntity(stray) || level.getEntity(stray.getUUID()) != null) {
                failures.add("a second whale was allowed to join");
            }
        }
        CompoundTag state = first.saveState();
        state.putInt("RestCooldown", 99999);
        StoneWhaleEntity second = spawn(level, x + 64, z, state);
        if (!first.isRemoved()) {
            failures.add("calling the whale again left the old one");
        }
        if (current(level) != second || !second.getUUID().equals(data.whaleId())) {
            failures.add("the new whale is not registered");
        }
        if (second.saveState().getInt("RestCooldown") != 2400 || second.chainLength() != 0f) {
            failures.add("the carried state was not clamped: " + second.saveState());
        }
        long count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof StoneWhaleEntity) {
                count++;
            }
        }
        if (count != 1) {
            failures.add(count + " whales are loaded");
        }
        // 片付け: 岩鯨を消し、記録を元に戻す
        second.discard();
        KEEPER.releaseTicket(level);
        data.restoreSlot(TitanWorldData.STONE_WHALE, saved, level.registryAccess());
        for (int dx = 0; dx <= 4; dx++) {
            level.setChunkForced((TEST_X >> 4) + dx, TEST_Z >> 4, false);
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST whale PASSED");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST whale FAILED: {}", f));
        }
    }
}
