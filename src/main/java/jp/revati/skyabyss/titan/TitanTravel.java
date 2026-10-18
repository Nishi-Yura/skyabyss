package jp.revati.skyabyss.titan;

import jp.revati.skyabyss.notice.Notice;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModSounds;
import jp.revati.skyabyss.titan.dungeon.TitanBackBuilder;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 地上と「岩鯨の背」(専用のディメンション) を行き来させる。
// 地上へ垂れた鎖を右クリックすると、鎖が巻き上げられて体ごと背中の縁まで引き上げられる。
// 背中の錨石を右クリックすると鎖を伝って地上へ降り、端から落ちても風に受け止められて地上へ戻る。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanTravel {
    public static final ResourceKey<Level> TITAN_BACK = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "titan_back"));

    // 引き上げの途中で抜けた (ログアウト・サーバー停止)。次のログインで背中へ乗せる
    private static final String TAG_LIFT_PENDING = "skyabyss_lift_pending";
    // 鎖の軸からこれより離れたら (押し出された・岩鯨が呼び直された) 引き上げを打ち切る
    private static final double LIFT_MAX_DRIFT = 16.0;
    // 引き上げの速さ (ブロック/tick) と、打ち切るまでの最大時間
    private static final double LIFT_SPEED = 0.85;
    private static final int LIFT_MAX_TICKS = 160;
    // 上った場所 (オーバーワールド)。岩鯨がここから CARRIED_AWAY より遠くへ移っていたら、降りるときはここへ戻す。
    // 背中で倒れても消えないよう、復活で写される Player.PERSISTED_NBT_TAG の下に置く (TitanBackTrials.persisted() と同じ)
    private static final String TAG_BOARDED_FROM = "skyabyss_boarded_from";
    private static final double CARRIED_AWAY = 128.0;

    // 鎖に引き上げられている最中のプレイヤー → (どの岩鯨か, 経過tick)
    private static final Map<UUID, Lift> LIFTS = new HashMap<>();

    private record Lift(UUID whale, int ticks) {
    }

    private TitanTravel() {
    }

    // 背中の鎖を上っている人がいるか (巨神での移動は、その間は岩鯨を呼べない)
    public static boolean isLifting() {
        return !LIFTS.isEmpty();
    }

    public static void startLift(ServerPlayer player, StoneWhaleEntity whale) {
        if (LIFTS.containsKey(player.getUUID())) {
            return;
        }
        LIFTS.put(player.getUUID(), new Lift(whale.getUUID(), 0));
        player.setNoGravity(true);
        player.stopRiding();
        whale.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.TITAN_CHAIN.get(), SoundSource.PLAYERS, 2f, 1.0f);
        Notice.bar(player, ModText.hint("message.skyabyss.lifting"));
    }

    private static void tickLift(ServerPlayer player, Lift lift) {
        Entity entity = player.serverLevel().getEntity(lift.whale());
        if (!(entity instanceof StoneWhaleEntity whale) || player.level().dimension() != Level.OVERWORLD) {
            abortLift(player);
            return;
        }
        Vec3 top = whale.chainTop();
        if (Math.hypot(top.x - player.getX(), top.z - player.getZ()) > LIFT_MAX_DRIFT) {
            abortLift(player);
            return;
        }
        // 鎖の軸へ寄せながら、まっすぐ上へ巻き上げる
        double vx = (top.x - player.getX()) * 0.3;
        double vz = (top.z - player.getZ()) * 0.3;
        player.setDeltaMovement(vx, LIFT_SPEED, vz);
        player.hurtMarked = true;
        player.fallDistance = 0;
        if (lift.ticks() % 6 == 0) {
            player.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CHAIN.defaultBlockState()),
                    player.getX(), player.getY() + 1, player.getZ(), 4, 0.3, 0.3, 0.3, 0.05);
        }
        if (lift.ticks() % 20 == 0) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.TITAN_CHAIN.get(), SoundSource.PLAYERS, 1.2f, 1.2f);
        }
        if (player.getY() >= top.y - 1.5 || lift.ticks() >= LIFT_MAX_TICKS) {
            endLift(player);
            board(player, whale);
            return;
        }
        LIFTS.put(player.getUUID(), new Lift(lift.whale(), lift.ticks() + 1));
    }

    private static void endLift(ServerPlayer player) {
        LIFTS.remove(player.getUUID());
        player.setNoGravity(false);
        player.fallDistance = 0;
    }

    // 途中で打ち切る。宙にいるので、ゆっくり落ちる効果を付けて地上へ降ろす
    private static void abortLift(ServerPlayer player) {
        endLift(player);
        if (player.isAlive() && !player.onGround()) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 20, 0, false, false, true));
        }
    }

    // 上った場所を書く・取り出す (取り出すと消える)。前の版で persistent data の直下に書いた記録も読む
    public static void rememberBoarding(Player player, Vec3 pos) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        CompoundTag from = new CompoundTag();
        from.putDouble("x", pos.x);
        from.putDouble("y", pos.y);
        from.putDouble("z", pos.z);
        root.getCompound(Player.PERSISTED_NBT_TAG).put(TAG_BOARDED_FROM, from);
        root.remove(TAG_BOARDED_FROM);
    }

    @Nullable
    public static Vec3 takeBoardedFrom(Player player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag from = persisted.contains(TAG_BOARDED_FROM) ? persisted.getCompound(TAG_BOARDED_FROM) : root.getCompound(TAG_BOARDED_FROM);
        persisted.remove(TAG_BOARDED_FROM);
        root.remove(TAG_BOARDED_FROM);
        return from.contains("x") ? new Vec3(from.getDouble("x"), from.getDouble("y"), from.getDouble("z")) : null;
    }

    public static void board(ServerPlayer player, StoneWhaleEntity whale) {
        board(player, whale.getUUID());
    }

    // whaleId: どの岩鯨か。岩鯨は一体だけなので今は使わない (前は skyabyss_titan に書いていたが、読む所がなく消した)
    private static void board(ServerPlayer player, UUID whaleId) {
        // 乗る資格 (10 の 3 章。岩鯨は最初の巨神なので誰でも乗れる。後の巨神と同じ入口の形にしておく)
        if (!jp.revati.skyabyss.progress.Progression.checkBoard(player, "stone_whale")) {
            return;
        }
        ServerLevel back = player.server.getLevel(TITAN_BACK);
        if (back == null) {
            SkyAbyss.LOGGER.error("Titan back dimension is missing");
            return;
        }
        // 上った場所を覚えておく。背中にいる間に岩鯨が遠くへ運ばれても (巨神での移動)、降りるときはここへ戻す
        if (player.level().dimension() == Level.OVERWORLD) {
            rememberBoarding(player, player.position());
        }
        TitanBackData data = TitanBackData.get(back);
        if (data.needsBuild()) {
            // 起動時に建て直せなかったとき (自己テストなど) の保険。古い配置なら、進み具合も最初に戻す
            if (data.isBuilt()) {
                jp.revati.skyabyss.titan.dungeon.TitanBackTrials.resetAllGroups(back);
            }
            TitanBackBuilder.build(back, data);
        }
        BlockPos arrival = TitanBackLayout.arrival();
        player.teleportTo(back, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, 180f, 0f);
        back.playSound(null, arrival, ModSounds.TITAN_CHAIN.get(), SoundSource.PLAYERS, 1.5f, 0.9f);
        showTitle(player, "title.skyabyss.titan_back", "title.skyabyss.titan_back.sub");
        Notice.narrate(player, "message.skyabyss.arrived_back");
    }

    // 地上へ戻す。岩鯨の鎖の付け根の下へ、ゆっくり落ちる効果をつけて降ろす。
    // 岩鯨は背中に人がいる間は読み込まれたままだが、もし読み込まれていなければ、最後に見た位置の下へ降ろす
    public static void leave(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        Vec3 drop = null;
        StoneWhaleEntity whale = WhaleKeeper.current(overworld);
        TitanWorldData.Slot data = TitanWorldData.get(overworld).whale();
        if (whale != null) {
            Vec3 top = whale.chainTop();
            drop = new Vec3(top.x, top.y - 4, top.z);
        } else if (data.hasLast) {
            Vec3 top = StoneWhaleEntity.chainTop(data.lastX, data.lastY, data.lastZ, data.lastYaw);
            drop = new Vec3(top.x, top.y - 4, top.z);
        }
        // 背中にいる間に、岩鯨がほかの人を乗せて遠くへ泳いだ (巨神での移動)。上った場所の空へ戻す
        Vec3 boarded = takeBoardedFrom(player);
        if (boarded != null) {
            if (drop == null) {
                drop = boarded;
            } else if (Math.hypot(boarded.x - drop.x, boarded.z - drop.z) > CARRIED_AWAY) {
                drop = boarded;
                Notice.narrate(player, "message.skyabyss.travel.returned_home");
            }
        }
        if (drop == null) {
            BlockPos spawn = overworld.getSharedSpawnPos();
            int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
            drop = new Vec3(spawn.getX() + 0.5, y + 1, spawn.getZ() + 0.5);
        }
        player.fallDistance = 0;
        player.teleportTo(overworld, drop.x, drop.y, drop.z, player.getYRot(), 0f);
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 40, 0, false, false, true));
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Lift lift = LIFTS.get(player.getUUID());
        if (lift != null) {
            tickLift(player, lift);
            return;
        }
        // 背中から落ちた
        if (player.level().dimension() == TITAN_BACK && player.getY() < TitanBackLayout.BASE_Y - 50) {
            leave(player);
            showTitle(player, "title.skyabyss.fell", "title.skyabyss.fell.sub");
        }
    }

    // 背中の錨石を右クリックすると、鎖を伝って地上へ降りる
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().dimension() != TITAN_BACK || !event.getPos().equals(TitanBackLayout.anchor())) {
            return;
        }
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) {
            Notice.narrate(player, "message.skyabyss.descending");
            leave(player);
        }
    }

    // 引き上げの途中で抜けた人は、次のログインで背中の尾の台へ乗せる (宙から落とさない)。
    // ログアウトのイベントはプレイヤーの保存より前に来るので、ここで付けた印は保存される
    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Lift lift = LIFTS.get(player.getUUID());
            if (lift != null) {
                endLift(player);
                if (player.isAlive()) {
                    player.getPersistentData().putUUID(TAG_LIFT_PENDING, lift.whale());
                }
            }
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isNoGravity()) {
            // 引き上げの途中でサーバーが落ちた (印を付けられなかった) 場合も、宙から落とさない
            player.setNoGravity(false);
            if (!player.onGround()) {
                player.fallDistance = 0;
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 40, 0, false, false, true));
            }
        }
        CompoundTag tag = player.getPersistentData();
        if (tag.hasUUID(TAG_LIFT_PENDING)) {
            UUID whaleId = tag.getUUID(TAG_LIFT_PENDING);
            tag.remove(TAG_LIFT_PENDING);
            player.fallDistance = 0;
            if (player.isAlive() && player.level().dimension() == Level.OVERWORLD && player.server.getLevel(TITAN_BACK) != null) {
                board(player, whaleId);
            } else if (player.isAlive()) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 40, 0, false, false, true));
            }
        }
    }

    // 引き上げの途中で倒れた・別の次元へ移った人を、鎖から外す (目を覚ました後に引っ張られないように)
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && LIFTS.containsKey(player.getUUID())) {
            endLift(player);
        }
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && LIFTS.containsKey(player.getUUID())) {
            endLift(player);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && LIFTS.remove(player.getUUID()) != null) {
            player.setNoGravity(false);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        LIFTS.clear();
    }

    private static void showTitle(ServerPlayer player, String title, String subtitle) {
        Notice.place(player, ModText.title(title), ModText.subtitle(subtitle));
    }
}
