package jp.revati.skyabyss.world.continent;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.loot.box.BoxKind;
import jp.revati.skyabyss.loot.box.LootBoxSetup;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.pouch.PouchRouting;
import jp.revati.skyabyss.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 逃げる獣を出す・報いる・締め出す (RareBeast のふるまいの外側。docs/design/44 の 7 章)。
//   出る     大陸を道ぞい (道から ROAD_NEAR まで) に旅している人に、CHECK_EVERY ごとに CHANCE で一体。その土地の獣が、
//            人の前の少し先 (AHEAD) に出る。町・都の近く・はじまりの島には出ない。一人に一体まで、出たあと COOLDOWN は出ない。時刻や天気では縛らない
//   報い     倒した人へ: 鐘銭 (土地ごと)・稀の封の箱一つ (土地の帯)・経験値 (見どころの発見の XP 倍)。捕まえた数を覚える
//   締め出し 逃げ切られた人は、その日 (サーバーの日付) はその土地の獣にもう出会わない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RareBeasts {
    static final String LOCK = "skyabyss_beast_lock";
    static final String CAUGHT = "skyabyss_beast_caught";
    static final int CHECK_EVERY = 20 * 20;
    static final float CHANCE = 0.035f;
    static final int COOLDOWN = 20 * 60 * 6;
    static final double ROAD_NEAR = 26;
    static final double TOWN_CLEAR = 110;
    static final double CAPITAL_CLEAR = 170;
    static final double AHEAD = 18;
    static final int XP_TIMES = 3;

    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final Map<UUID, UUID> ACTIVE = new HashMap<>();

    private RareBeasts() {
    }

    public static EntityType<RareBeast> type(RareBeast.Kind k) {
        return switch (k) {
            case HARE -> ModEntities.BELL_HARE.get();
            case ROLLER -> ModEntities.MOSS_ROLLER.get();
            case OTTER -> ModEntities.PEARL_OTTER.get();
            case WEASEL -> ModEntities.BOLT_WEASEL.get();
            case MOLE -> ModEntities.EMBER_MOLE.get();
        };
    }

    // ---------------------------------------------------------------- 人ごとの記録
    static CompoundTag persisted(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static CompoundTag tag(Player p, String key) {
        CompoundTag root = persisted(p);
        if (!root.contains(key)) {
            root.put(key, new CompoundTag());
        }
        return root.getCompound(key);
    }

    static String today() {
        return LocalDate.now().toString();
    }

    // 今日はこの獣に締め出されているか
    public static boolean locked(Player p, RareBeast.Kind k) {
        return today().equals(tag(p, LOCK).getString(k.id));
    }

    public static int caught(Player p, RareBeast.Kind k) {
        return tag(p, CAUGHT).getInt(k.id);
    }

    // ---------------------------------------------------------------- 出る (外れの帯の道ぞいだけ。2026-11-09 に外れへ移した。FrontierBand の ③)
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 13 || !RoadClearance.live()) {
            return;
        }
        ServerLevel over = server.overworld();
        long now = over.getGameTime();
        for (ServerPlayer p : over.players()) {
            if (p.isSpectator() || !p.isAlive() || active(over, p) != null) {
                continue;
            }
            long next = NEXT.computeIfAbsent(p.getUUID(), k -> now + CHECK_EVERY);
            if (now < next) {
                continue;
            }
            NEXT.put(p.getUUID(), now + CHECK_EVERY);
            RareBeast.Kind k = kindAt(p.getBlockX(), p.getBlockZ());
            if (k == null || locked(p, k) || !alongRoad(p.getX(), p.getZ()) || nearTown(p.getX(), p.getZ()) || !FrontierBand.far(p.getX(), p.getZ())
                    || p.getRandom().nextFloat() >= CHANCE) {
                continue;
            }
            if (appear(p, k) != null) {
                NEXT.put(p.getUUID(), now + COOLDOWN);
            }
        }
    }

    @Nullable
    static RareBeast.Kind kindAt(int x, int z) {
        ContinentMap map = ContinentMap.get();
        return map.inside(x, z) ? RareBeast.Kind.ofRegion(map.region(x, z)) : null;
    }

    // 道から ROAD_NEAR まで
    static boolean alongRoad(double x, double z) {
        for (List<BlockPos> line : ContinentLayout.get().roads()) {
            for (int i = 1; i < line.size(); i++) {
                BlockPos a = line.get(i - 1);
                BlockPos b = line.get(i);
                double dx = b.getX() - a.getX();
                double dz = b.getZ() - a.getZ();
                double l2 = dx * dx + dz * dz;
                double u = l2 == 0 ? 0 : Mth.clamp(((x - a.getX()) * dx + (z - a.getZ()) * dz) / l2, 0, 1);
                double px = a.getX() + u * dx - x;
                double pz = a.getZ() + u * dz - z;
                if (px * px + pz * pz <= ROAD_NEAR * ROAD_NEAR) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean nearTown(double x, double z) {
        ContinentLayout lay = ContinentLayout.get();
        BlockPos c = lay.capital();
        if (Math.hypot(x - c.getX(), z - c.getZ()) < CAPITAL_CLEAR) {
            return true;
        }
        for (String t : BoxKind.TITANS) {
            BlockPos town = lay.town(t.equals("stone_whale") ? "whale" : t.equals("tide_shell") ? "tide" : t.equals("thunder_wing") ? "storm" : "ember");
            if (town != null && Math.hypot(x - town.getX(), z - town.getZ()) < TOWN_CLEAR) {
                return true;
            }
        }
        return false;
    }

    // 人の前の少し先 (向きの ±50 度) の地面に出す。出せなければ null
    @Nullable
    public static RareBeast appear(ServerPlayer p, RareBeast.Kind k) {
        ServerLevel level = p.serverLevel();
        for (int tries = 0; tries < 8; tries++) {
            float yaw = p.getYRot() + (p.getRandom().nextFloat() - 0.5f) * 100f;
            Vec3 d = Vec3.directionFromRotation(0, yaw).scale(AHEAD + p.getRandom().nextInt(7));
            int x = Mth.floor(p.getX() + d.x);
            int z = Mth.floor(p.getZ() + d.z);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (!level.getFluidState(at.below()).isEmpty() || !level.getBlockState(at).canBeReplaced()) {
                continue;
            }
            RareBeast b = type(k).create(level);
            if (b == null) {
                return null;
            }
            b.moveTo(x + 0.5, y, z + 0.5, p.getRandom().nextFloat() * 360f, 0f);
            b.owner = p.getUUID();
            level.addFreshEntity(b);
            ACTIVE.put(p.getUUID(), b.getUUID());
            Notice.hint(p, "message.skyabyss.beast.appear." + k.id);
            if (k == RareBeast.Kind.HARE) {
                level.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 0.8f, 2.0f);
            }
            SkyAbyss.LOGGER.info("Rare beast {} appeared for {} at {} {} {}", k.id, p.getGameProfile().getName(), x, y, z);
            return b;
        }
        return null;
    }

    @Nullable
    static RareBeast active(ServerLevel level, Player p) {
        UUID id = ACTIVE.get(p.getUUID());
        if (id == null) {
            return null;
        }
        if (level.getEntity(id) instanceof RareBeast b && b.isAlive()) {
            return b;
        }
        ACTIVE.remove(p.getUUID());
        return null;
    }

    // ---------------------------------------------------------------- 逃げ切る・倒す
    static void onEscaped(RareBeast b) {
        if (b.owner == null || !(b.level() instanceof ServerLevel level)) {
            return;
        }
        ACTIVE.remove(b.owner);
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(b.owner);
        if (p != null) {
            lock(p, b.kind);
            Notice.hint(p, "message.skyabyss.beast.escaped", b.getType().getDescription());
        }
    }

    static void lock(Player p, RareBeast.Kind k) {
        tag(p, LOCK).putString(k.id, today());
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof RareBeast b) || b.level().isClientSide()) {
            return;
        }
        if (b.owner != null) {
            ACTIVE.remove(b.owner);
        }
        if (event.getSource().getEntity() instanceof ServerPlayer p) {
            reward(p, b.kind);
        }
    }

    // 捕まえた報い: 鐘銭・稀の封の箱一つ・経験値。数を覚える
    public static void reward(ServerPlayer p, RareBeast.Kind k) {
        Coins.give(p, k.coins);
        BoxKind box = BoxKind.rarity(jp.revati.skyabyss.item.GearTier.RARE, k.band);
        var item = box == null ? null : LootBoxSetup.item(box);
        if (item != null) {
            PouchRouting.giveOrDrop(p, new ItemStack(item));
        }
        int xp = XP_TIMES * ContinentPois.DISCOVER_XP[Math.max(0, Math.min(ContinentPois.DISCOVER_XP.length - 1, k.region))];
        jp.revati.skyabyss.rpg.Growth.addXp(p, xp, "growth.skyabyss.reason.beast");
        CompoundTag c = tag(p, CAUGHT);
        c.putInt(k.id, c.getInt(k.id) + 1);
        Notice.unlock(p, "message.skyabyss.beast.caught", type(k).getDescription(), k.coins);
        p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7f, 1.2f);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        NEXT.remove(event.getEntity().getUUID());
        ACTIVE.remove(event.getEntity().getUUID());
    }

    // ---------------------------------------------------------------- 開発・撮影
    // /skyabyss_beast spawn <獣> : 前に出す (道・町・確率・締め出しを見ない)。/skyabyss_beast unlock : 今日の締め出しを解く
    @SubscribeEvent
    static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("skyabyss_beast").requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("spawn")
                        .then(net.minecraft.commands.Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            RareBeast.Kind k = RareBeast.Kind.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind"));
                            if (k == null) {
                                ctx.getSource().sendFailure(Component.literal("bell_hare / moss_roller / pearl_otter / bolt_weasel / ember_mole"));
                                return 0;
                            }
                            return appear(p, k) == null ? 0 : 1;
                        })))
                .then(net.minecraft.commands.Commands.literal("unlock").executes(ctx -> {
                    persisted(ctx.getSource().getPlayerOrException()).remove(LOCK);
                    return 1;
                })));
    }

    static void forgetForTest(Player p) {
        persisted(p).remove(LOCK);
        persisted(p).remove(CAUGHT);
        NEXT.remove(p.getUUID());
        ACTIVE.remove(p.getUUID());
    }
}
