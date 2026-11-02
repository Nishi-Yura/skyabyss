package jp.revati.skyabyss.story.finale;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 終章の雲海 (docs/design/50 の 4.1)。別のディメンション skyabyss:cloudsea (data/skyabyss/dimension*/cloudsea.json、バイオーム skyabyss:cloudsea)。
//   雲の海  y 36〜39 の雲 (CloudBlock)。雲の下 (FALL_Y より下) へ落ちた人は、最後に島に立っていた所へ戻し、体力の 2 割を失う
//   空      夜明けに止めた金の空と朝日、西に止まった月 (client/CloudSkyClient)
//   着く所  巡礼の門の島 (data/skyabyss/finale/arrival.json。tools/gen/world/build_cloud_arrival.py。版 1 で一度だけ流し込む)
//   島 1    鐘の島と着く所からの白い橋 (data/skyabyss/finale/isle1.json。build_cloud_isle1.py。版 2。BellIsle)
//   島 2    灯の島と鐘の島の主の間からの光の橋 (data/skyabyss/finale/isle2.json。build_cloud_isle2.py。版 3。LampIsle)
//   島 3    炉の島と灯の島の主の間からの光の橋 (data/skyabyss/finale/isle3.json。build_cloud_isle3.py。版 4。ForgeIsle)
//   島 4    約束の島と炉の島の主の間からの光の橋 (data/skyabyss/finale/isle4.json。build_cloud_isle4.py。版 5。PromiseIsle)
//   座      巡礼者の座と約束の島の主の間からの光の橋 (data/skyabyss/finale/seat.json。build_cloud_seat.py。版 6、雲の暦は版 7。PilgrimSeat・SeatArena・PilgrimTrial)
//   行き来  都の大鐘楼の門 (story/ch6/MoonGate が行き先を選ばせる。FinaleGate)。着く所の帰りの台 (天然磁石) を右クリックで都へ
//   門の鐘  巡礼の門の下の鐘を右クリック → 合図 finale_gate (作る順 2 で雲の島 1 への橋を架ける)
//   鐘の内  裏ボス「初代の空見」の舞台 (data/skyabyss/finale/founder.json。build_bell_inside.py。版 8。founder/FounderArena)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class FinaleWorld {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "cloudsea"));
    public static final int CLOUD_TOP = 39;
    public static final int FALL_Y = 20;
    public static final float FALL_COST = 0.2f;
    public static final String GATE_SIGNAL = "finale_gate";
    static final String ARRIVAL = "arrival";
    // 1 = 巡礼の門の島、2 = 島 1「鐘の島」と着く所からの白い橋 (BellIsle)
    static final int VERSION = 8;
    // 人ごとの最後に立っていた島の所 (保存しない。なければ着く所)
    private static final Map<UUID, BlockPos> SAFE = new HashMap<>();

    private FinaleWorld() {
    }

    public static boolean in(ServerPlayer p) {
        return p.level().dimension() == LEVEL;
    }

    public static FinaleTerrain arrival() {
        return FinaleTerrain.of(ARRIVAL);
    }

    public static BlockPos arrivalPos() {
        return arrival().mark("arrival");
    }

    public static boolean enter(ServerPlayer p) {
        ServerLevel level = p.server.getLevel(LEVEL);
        if (level == null) {
            return false;
        }
        build(level);
        BlockPos at = arrivalPos();
        level.getChunk(at);
        p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180f, -5f);
        SAFE.put(p.getUUID(), at);
        return true;
    }

    // 地上へ戻す: 都の門のそば (なければ起きる所)
    public static void leave(ServerPlayer p) {
        jp.revati.skyabyss.story.ch6.MoonWorld.leave(p);
    }

    public static void build(ServerLevel level) {
        Data d = Data.get(level.getServer());
        if (d.version >= VERSION) {
            return;
        }
        if (d.version < 1) {
            arrival().pour(level);
        }
        if (d.version < 2) {
            BellIsle.terrain().pour(level);
        }
        if (d.version < 3) {
            LampIsle.terrain().pour(level);
        }
        if (d.version < 4) {
            ForgeIsle.terrain().pour(level);
        }
        if (d.version < 5) {
            PromiseIsle.terrain().pour(level);
        }
        // 版 7: 座の台に雲の暦 (巡礼の試し)。座をもう一度流し込む
        if (d.version < 7) {
            PilgrimSeat.terrain().pour(level);
        }
        // 版 8: 裏ボスの鐘の内 (docs/design/53。南の閉じた大鐘)
        if (d.version < 8) {
            jp.revati.skyabyss.story.finale.founder.FounderArena.terrain().pour(level);
        }
        d.version = VERSION;
        d.setDirty();
    }

    public static boolean built(MinecraftServer server) {
        return Data.get(server).version >= 1;
    }

    public static int version(MinecraftServer server) {
        return Data.get(server).version;
    }

    // ---------------------------------------------------------------- 雲の下へ落ちた人を戻す
    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !in(p) || p.isSpectator() || (p.tickCount + p.getId()) % 5 != 0) {
            return;
        }
        rescueCheck(p);
    }

    // 一人の見回り (自己テストからも)。戻したら true
    public static boolean rescueCheck(ServerPlayer p) {
        // 固まった鐘雲の上は戻す所にしない (ほどけると、また落ちる)
        if (p.onGround() && p.getY() > CLOUD_TOP + 2 && !(p.level().getBlockState(p.blockPosition().below()).getBlock() instanceof BellCloudBlock)
                && !(p.level().getBlockState(p.blockPosition().below()).getBlock() instanceof RewindStoneBlock)) {
            SAFE.put(p.getUUID(), p.blockPosition());
            return false;
        }
        if (p.getY() >= FALL_Y) {
            return false;
        }
        BlockPos back = SAFE.getOrDefault(p.getUUID(), arrivalPos());
        p.teleportTo(p.serverLevel(), back.getX() + 0.5, back.getY(), back.getZ() + 0.5, p.getYRot(), 0f);
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            p.moveTo(back.getX() + 0.5, back.getY(), back.getZ() + 0.5, p.getYRot(), 0f);
        }
        p.resetFallDistance();
        p.setDeltaMovement(0, 0, 0);
        if (!p.isCreative()) {
            // 体力の 2 割 (防具では減らない。倒れはしない)
            p.setHealth(Math.max(1f, p.getHealth() - p.getMaxHealth() * FALL_COST));
        }
        p.serverLevel().playSound(null, back, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 0.7f);
        Notice.hint(p, "message.skyabyss.cloudsea.fell");
        return true;
    }

    // ---------------------------------------------------------------- 帰りの台・門の鐘
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand() != InteractionHand.MAIN_HAND || !in(p)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (pos.equals(arrival().mark("return"))) {
            event.setCanceled(true);
            p.serverLevel().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 0.7f);
            leave(p);
        } else if (pos.equals(arrival().mark("gate"))) {
            event.setCanceled(true);
            ringGate(p, pos);
        }
    }

    public static void ringGate(ServerPlayer p, BlockPos pos) {
        p.serverLevel().playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.4f, 0.8f);
        p.serverLevel().playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0f, 0.8f);
        Quests.signal(p, GATE_SIGNAL);
        Notice.narrate(p, "message.skyabyss.cloudsea.gate");
    }

    // 開発用: /skyabyss cloudsea enter | leave | ring <鐘の印> | lord | crumble | lamp <w|e|s> | eye | forge <w|e|n> | rewinder | selves | plume | seat <1|2|3> | ending <calm|break|accept|none> | trial <1..5> (権限 2)
    @SubscribeEvent
    static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("cloudsea")
                        .then(net.minecraft.commands.Commands.literal("enter").executes(c -> enter(c.getSource().getPlayerOrException()) ? 1 : 0))
                        .then(net.minecraft.commands.Commands.literal("leave").executes(c -> {
                            leave(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(net.minecraft.commands.Commands.literal("ring")
                                .then(net.minecraft.commands.Commands.argument("mark", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                                    ServerLevel l = c.getSource().getServer().getLevel(LEVEL);
                                    BellIsle.ring(l, BellIsle.mark(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "mark")));
                                    return 1;
                                })))
                        .then(net.minecraft.commands.Commands.literal("lord").executes(c -> BellArena.summon(c.getSource().getServer().getLevel(LEVEL)) != null ? 1 : 0))
                        .then(net.minecraft.commands.Commands.literal("crumble").executes(c -> {
                            BellArena.crumble(c.getSource().getServer().getLevel(LEVEL));
                            return 1;
                        }))
                        .then(net.minecraft.commands.Commands.literal("lamp")
                                .then(net.minecraft.commands.Commands.argument("key", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                                    LampIsle.lightLamp(c.getSource().getServer().getLevel(LEVEL), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "key"));
                                    return 1;
                                })))
                        .then(net.minecraft.commands.Commands.literal("eye").executes(c -> LampArena.summon(c.getSource().getServer().getLevel(LEVEL)) != null ? 1 : 0))
                        .then(net.minecraft.commands.Commands.literal("forge")
                                .then(net.minecraft.commands.Commands.argument("key", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                                    ForgeIsle.lightForge(c.getSource().getServer().getLevel(LEVEL), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "key"));
                                    return 1;
                                })))
                        .then(net.minecraft.commands.Commands.literal("rewinder").executes(c -> ForgeArena.summon(c.getSource().getServer().getLevel(LEVEL)) != null ? 1 : 0))
                        .then(net.minecraft.commands.Commands.literal("selves").executes(c -> PromiseIsle.join(c.getSource().getPlayerOrException()) ? 1 : 0))
                        .then(net.minecraft.commands.Commands.literal("plume").executes(c -> PromiseArena.summon(c.getSource().getServer().getLevel(LEVEL)) != null ? 1 : 0))
                        .then(net.minecraft.commands.Commands.literal("seat")
                                .then(net.minecraft.commands.Commands.argument("form", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3)).executes(c -> {
                                    SeatArena.toForm(c.getSource().getServer().getLevel(LEVEL), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "form"));
                                    return 1;
                                })))
                        .then(net.minecraft.commands.Commands.literal("trial")
                                .then(net.minecraft.commands.Commands.argument("tier", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, PilgrimTrial.MAX_TIER)).executes(c -> {
                                    SeatArena.startTrial(c.getSource().getServer().getLevel(LEVEL), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "tier"));
                                    return 1;
                                })))
                        .then(net.minecraft.commands.Commands.literal("ending")
                                .then(net.minecraft.commands.Commands.argument("which", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    String e = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "which");
                                    if (e.equals("none")) {
                                        p.getPersistentData().remove(FinaleEnding.KEY);
                                        FinaleEnding.sync(p);
                                        return 1;
                                    }
                                    if (!FinaleEnding.ENDINGS.contains(e)) {
                                        return 0;
                                    }
                                    FinaleEnding.apply(p, e);
                                    return 1;
                                })))));
    }

    static final class Data extends SavedData {
        private static final Factory<Data> FACTORY = new Factory<>(Data::new, Data::load);
        int version;

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "skyabyss_cloudsea_world");
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            d.version = tag.getInt("Version");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("Version", version);
            return tag;
        }
    }

    // サーバーが止まったとき (FinaleReset)
    static void resetAll() {
        SAFE.clear();
    }
}
