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
        // 1: 倒れても落とさない・減らない。2: バニラの keepInventory も入れる (2026-10-27 の声「持ち物の保持を on にするだけで落とさないのでは」)
        static final int VERSION = 2;
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
        if (m.version < 1) {
            rules.getRule(jp.revati.skyabyss.grave.DeathRules.MODE).set(jp.revati.skyabyss.grave.DeathRules.Mode.KEEP.ordinal(), server);
            rules.getRule(jp.revati.skyabyss.grave.DeathRules.DURABILITY).set(jp.revati.skyabyss.grave.DeathRules.DEFAULT_DURABILITY, server);
        }
        // 持ち物の保持 (バニラ)。入っていれば倒れたときの持ち物はバニラのとおり全部残る (DeathEvents は何もしない)
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        m.version = Migrated.VERSION;
        m.setDirty();
        SkyAbyss.LOGGER.info("RPG rules: this world now keeps items on death and has no wear (one-time switch)");
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            enforce(player);
            starterKit(player);
        }
    }

    // ---------------------------------------------------------------- はじめの持ち物 (docs/design/40 の 4 章)
    // 初めて入った人に一度だけ: 軽い防具 (革の四つ。空いている所は身に着ける)・食べ物 10。武器は渡さない (職を選ぶと始めの武器。2026-10-26 の声「職業選んでから戦い」)。
    // 決まり (skyabyssAdventure) が切れている世界では渡さない
    static final String KIT_TAG = "skyabyss_rpg_kit";

    public static void starterKit(ServerPlayer player) {
        if (player instanceof FakePlayer || !adventure(player.serverLevel()) || player.getPersistentData().getBoolean(KIT_TAG)) {
            return;
        }
        player.getPersistentData().putBoolean(KIT_TAG, true);
        net.minecraft.world.item.Item[] armor = {net.minecraft.world.item.Items.LEATHER_HELMET, net.minecraft.world.item.Items.LEATHER_CHESTPLATE,
            net.minecraft.world.item.Items.LEATHER_LEGGINGS, net.minecraft.world.item.Items.LEATHER_BOOTS};
        net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
            net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
        for (int i = 0; i < armor.length; i++) {
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(armor[i]);
            if (player.getItemBySlot(slots[i]).isEmpty()) {
                player.setItemSlot(slots[i], stack);
            } else {
                jp.revati.skyabyss.quest.Quests.give(player, stack);
            }
        }
        jp.revati.skyabyss.quest.Quests.give(player, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 10));
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

    // ---------------------------------------------------------------- 地形は壊れない (2026-10-26 の声「爆発で地形壊れるよ」)
    // 決まりが入っていれば、どの世界でも爆発 (クリーパー・TNT・敵の技) はブロックを壊さない。人や敵への傷はそのまま
    @SubscribeEvent
    static void onExplosion(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && adventure(level)) {
            event.getAffectedBlocks().clear();
        }
    }

    // ---------------------------------------------------------------- バニラの敵は湧かない (2026-10-26 の声「通常モブはもう出ないようにして。全敵 mod の敵」)
    // 決まりが入っていれば、バニラの敵 (ゾンビ・骸骨・クリーパー・ファントム・略奪隊…) とさまよう商人は、自然・巡回・湧き台・構造物・増援では湧かない。
    // 動物も (2026-10-27)。Mod が場面のために呼ぶ物 (EVENT・COMMAND) は止めない
    static final java.util.Set<net.minecraft.world.entity.MobSpawnType> NATURAL_KINDS = java.util.EnumSet.of(
            net.minecraft.world.entity.MobSpawnType.NATURAL, net.minecraft.world.entity.MobSpawnType.CHUNK_GENERATION,
            net.minecraft.world.entity.MobSpawnType.PATROL, net.minecraft.world.entity.MobSpawnType.SPAWNER,
            net.minecraft.world.entity.MobSpawnType.STRUCTURE, net.minecraft.world.entity.MobSpawnType.REINFORCEMENT,
            net.minecraft.world.entity.MobSpawnType.JOCKEY, net.minecraft.world.entity.MobSpawnType.TRIAL_SPAWNER);

    // バニラの生き物 (敵・動物・コウモリ・魚・さまよう商人…)。2026-10-27 の声「バニラのモブまだいる。動物とか」で動物も入れた。
    // 村人だけは残す (都の村人と五章の罠の人は Mod が置くバニラの村人)
    public static boolean vanillaEnemy(net.minecraft.world.entity.Entity e) {
        if (!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals("minecraft")
                || !(e instanceof net.minecraft.world.entity.Mob)) {
            return false;
        }
        return !(e instanceof net.minecraft.world.entity.npc.Villager);
    }

    @SubscribeEvent
    static void onFinalizeSpawn(net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent event) {
        if (event.getLevel().getLevel() instanceof ServerLevel level && adventure(level) && NATURAL_KINDS.contains(event.getSpawnType())
                && vanillaEnemy(event.getEntity())) {
            event.setSpawnCancelled(true);
        }
    }

    // どんな湧き方でも (村への襲撃・ファントム・変身・湧き台・前の世界に残っていた分も)、バニラの敵とさまよう商人は世界に入れない。
    // 決まりの入った世界か大陸の世界のとき。試遊の確かめ (自己テスト) はバニラのゾンビを使うので、そのあいだは入れる
    @SubscribeEvent
    static void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && vanillaEnemy(event.getEntity()) && !jp.revati.skyabyss.SelfTestRunner.enabled()
                && (adventure(level) || level.getChunkSource().getGenerator() instanceof jp.revati.skyabyss.world.continent.ContinentChunkGenerator)) {
            event.setCanceled(true);
        }
    }

    // 敵もブロックを壊さない・持ち去らない (エンダーマン・ラヴェジャー・ゾンビの戸破り…)
    @SubscribeEvent
    static void onGriefing(net.neoforged.neoforge.event.entity.EntityMobGriefingEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level && adventure(level) && !(event.getEntity() instanceof Player)) {
            event.setCanGrief(false);
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
