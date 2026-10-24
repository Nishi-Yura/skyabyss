package jp.revati.skyabyss.rpg.rules;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 完全な RPG の遊び方の決まり (docs/design/40 の 2 章。2026-10-25 にユーザーが決めた)。
//   skyabyssAdventure  (既定 true) サバイバルの人はアドベンチャーで遊ぶ (壊さない・置かない)。入る・生き返る・世界を移る時と、2 秒ごとに見る。
//                      クリエイティブ・観戦者はそのまま
//   skyabyssNoWear     (既定 true) 人の持つ武器・防具・道具の耐久は減らない (mixin/ItemStackDurabilityMixin)
// 倒れても落とさない決まりは grave/DeathRules (既定の型 keep・耐久の減り 0)。
// 前から遊んでいた世界は、初めてこの版で開いたときに一度だけ、倒れたときの型を keep・耐久の減りを 0 にする (Migrated)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RpgRules {
    public static GameRules.Key<GameRules.BooleanValue> ADVENTURE;
    public static GameRules.Key<GameRules.BooleanValue> NO_WEAR;

    private RpgRules() {
    }

    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ADVENTURE = GameRules.register("skyabyssAdventure", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true));
            NO_WEAR = GameRules.register("skyabyssNoWear", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true));
        });
    }

    public static boolean adventure(ServerLevel level) {
        return ADVENTURE == null || level.getGameRules().getBoolean(ADVENTURE);
    }

    // 減らさない: 人 (偽の人は除く) の持ち物で、決まりが入っているとき
    public static boolean noWear(ServerLevel level, LivingEntity entity) {
        return entity instanceof Player && !(entity instanceof FakePlayer) && (NO_WEAR == null || level.getGameRules().getBoolean(NO_WEAR));
    }

    // サバイバルならアドベンチャーへ
    public static void enforce(ServerPlayer player) {
        if (player instanceof FakePlayer || player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL || !adventure(player.serverLevel())) {
            return;
        }
        player.setGameMode(GameType.ADVENTURE);
    }

    // ---------------------------------------------------------------- アドベンチャーでの左クリック
    // アドベンチャーではブロックを左クリックしても、手元がサーバーへ知らせない (壊す権利がないため)。試練の仕掛けの「叩く」
    // (RaidRun などの LeftClickBlock) が動かなくなるので、手元から AdventureClick を送り、サーバーで同じ合図を流す
    public record AdventureClick(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final Type<AdventureClick> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "adventure_click"));
        public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, AdventureClick> STREAM_CODEC =
                net.minecraft.network.codec.StreamCodec.of((buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeEnum(p.face);
                }, buf -> new AdventureClick(buf.readBlockPos(), buf.readEnum(net.minecraft.core.Direction.class)));

        @Override
        public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static final java.util.Map<java.util.UUID, Long> LAST_CLICK = new java.util.HashMap<>();

    @SubscribeEvent
    static void registerPayloads(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        event.registrar("rpg-rules-1").playToServer(AdventureClick.TYPE, AdventureClick.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        onAdventureClick(sp, payload);
                    }
                }));
    }

    static void onAdventureClick(ServerPlayer player, AdventureClick click) {
        if (player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE
                || player.distanceToSqr(click.pos().getX() + 0.5, click.pos().getY() + 0.5, click.pos().getZ() + 0.5) > 7 * 7) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST_CLICK.get(player.getUUID());
        if (last != null && now - last < 4) {
            return;
        }
        LAST_CLICK.put(player.getUUID(), now);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock(player, click.pos(),
                click.face(), net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.Action.START));
    }

    // ---------------------------------------------------------------- 前の世界の決まりを一度だけ移す
    static final class Migrated extends net.minecraft.world.level.saveddata.SavedData {
        static final String NAME = "skyabyss_rpg_rules";
        static final int VERSION = 1;
        private static final Factory<Migrated> FACTORY = new Factory<>(Migrated::new, Migrated::load);
        int version;

        static Migrated get(net.minecraft.server.MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        }

        static Migrated load(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            Migrated m = new Migrated();
            m.version = tag.getInt("version");
            return m;
        }

        @Override
        public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            tag.putInt("version", version);
            return tag;
        }
    }

    @SubscribeEvent
    static void onStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        migrate(event.getServer());
        continentBorder(event.getServer());
    }

    // 天と淵の大陸: 世界の縁は大陸の図の外の海の中 (一辺 4,400)。広いまま (既定) のときだけ狭める。人が変えた縁はそのまま
    static final double CONTINENT_BORDER = 4400;

    static void continentBorder(net.minecraft.server.MinecraftServer server) {
        net.minecraft.server.level.ServerLevel level = server.overworld();
        if (!(level.getChunkSource().getGenerator() instanceof jp.revati.skyabyss.world.continent.ContinentChunkGenerator)) {
            return;
        }
        net.minecraft.world.level.border.WorldBorder border = level.getWorldBorder();
        if (border.getSize() > 1.0e6) {
            border.setCenter(0, 0);
            border.setSize(CONTINENT_BORDER);
        }
    }

    public static void migrate(net.minecraft.server.MinecraftServer server) {
        Migrated m = Migrated.get(server);
        if (m.version >= Migrated.VERSION || jp.revati.skyabyss.grave.DeathRules.MODE == null) {
            return;
        }
        GameRules rules = server.getGameRules();
        rules.getRule(jp.revati.skyabyss.grave.DeathRules.MODE).set(jp.revati.skyabyss.grave.DeathRules.Mode.KEEP.ordinal(), server);
        rules.getRule(jp.revati.skyabyss.grave.DeathRules.DURABILITY).set(jp.revati.skyabyss.grave.DeathRules.DEFAULT_DURABILITY, server);
        m.version = Migrated.VERSION;
        m.setDirty();
        SkyAbyss.LOGGER.info("RPG rules: this world now keeps items on death and has no wear (one-time switch)");
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            enforce(player);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            enforce(player);
        }
    }

    @SubscribeEvent
    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            enforce(player);
        }
    }

    // /gamemode survival などで戻されたときも、2 秒のうちにアドベンチャーへ
    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 40 == 13) {
            enforce(player);
        }
    }
}
