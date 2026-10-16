package jp.revati.skyabyss.titan.ember.back.trial;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.quest.free.FreeQuestEvents;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberBackState;
import jp.revati.skyabyss.titan.ember.mob.EmberMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 三つの試練 (C1 灰の道・C2 鍛えの間・C3 火守り) の共通の道具と、毎 tick の見回り。
// - 試練の品 (灰の壺・火の鍵・熾) は背中の外へ持ち出せない・預からない (7.3節)。落とした壺と鍵は消す (一人一つ)
// - 試練が済んだ合図 (石が鳴らせるようになったとき): クエストの合図 ember_trial_<west|east|south> と、共通の back_trial_cleared (仲間にも)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberTrials {
    // クエストの合図 (tide_trial_*・storm_trial_* と同じ形)
    public static String signalOf(EmberBackState.Trial trial) {
        return "ember_trial_" + trial.id();
    }

    private static final Map<EmberBackState.Trial, Boolean> SIGNALLED = new EnumMap<>(EmberBackState.Trial.class);

    // 自己テスト用: true のあいだ、石を鳴らせるようにする・初めての打つ順を使った記録を、ワールドに書かない (呼ばれた回数だけ数える)
    public static boolean dryRun;
    public static final List<EmberBackState.Trial> READY_CALLS = new ArrayList<>();
    public static int firstForgeCalls;

    private EmberTrials() {
    }

    // 試練が済んだ (石を鳴らせるようにする)
    static void markReady(ServerLevel level, EmberBackState.Trial trial) {
        if (dryRun) {
            READY_CALLS.add(trial);
        } else {
            jp.revati.skyabyss.titan.ember.back.EmberBackTrials.markReady(level, trial);
        }
    }

    static void markFirstForgeUsed(ServerLevel level) {
        EmberBackState state = EmberBackState.get(level);
        if (dryRun) {
            firstForgeCalls++;
        } else if (!state.firstForgeUsed()) {
            state.markFirstForgeUsed();
        }
    }

    // ---------------------------------------------------------------- 道具
    public static ServerLevel level(MinecraftServer server) {
        return server == null ? null : server.getLevel(EmberIds.BACK);
    }

    public static DamageSource damage(ServerLevel level, ResourceKey<DamageType> type, net.minecraft.world.entity.Entity by) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), by);
    }

    // 箱の中にいる、生きている (観戦者でない) 人
    public static List<ServerPlayer> playersIn(ServerLevel level, EmberBackLayout.Box box) {
        return playersIn(level.players(), box);
    }

    public static List<ServerPlayer> playersIn(List<ServerPlayer> all, EmberBackLayout.Box box) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : all) {
            if (p.isAlive() && !p.isSpectator() && box.contains(p.position())) {
                out.add(p);
            }
        }
        return out;
    }

    // 試練の敵を湧かせる (保存しない印を付ける)。湧かせられなければ null
    public static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, double x, double y, double z, String... tags) {
        T mob = type.create(level);
        if (mob == null) {
            return null;
        }
        mob.moveTo(x, y, z, level.random.nextFloat() * 360f, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)), MobSpawnType.EVENT, null);
        mob.addTag(EmberMob.TRIAL_TAG);
        for (String t : tags) {
            mob.addTag(t);
        }
        level.addFreshEntity(mob);
        return mob;
    }

    // 箱の中の試練の敵を消す
    public static int clearMobs(ServerLevel level, EmberBackLayout.Box box) {
        int n = 0;
        for (Mob m : level.getEntitiesOfClass(Mob.class, box.aabb(), e -> e.getTags().contains(EmberMob.TRIAL_TAG))) {
            m.discard();
            n++;
        }
        return n;
    }

    // 箱の中に落ちている品を消す
    public static int clearItems(ServerLevel level, EmberBackLayout.Box box, Item... items) {
        int n = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box.aabb())) {
            if (isOneOf(e.getItem(), items)) {
                e.discard();
                n++;
            }
        }
        return n;
    }

    public static boolean isOneOf(ItemStack stack, Item... items) {
        for (Item i : items) {
            if (stack.is(i)) {
                return true;
            }
        }
        return false;
    }

    public static int count(Player player, Item item) {
        int n = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                n += inv.getItem(i).getCount();
            }
        }
        if (player.containerMenu.getCarried().is(item)) {
            n += player.containerMenu.getCarried().getCount();
        }
        return n;
    }

    // 持ち物から取り除く。取り除いた数
    public static int strip(Player player, Item... items) {
        int n = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (isOneOf(s, items)) {
                n += s.getCount();
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (isOneOf(player.containerMenu.getCarried(), items)) {
            n += player.containerMenu.getCarried().getCount();
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        return n;
    }

    public static Item[] allTrialItems() {
        return new Item[] {EmberTrialSetup.ASH_URN.get(), EmberTrialSetup.FIRE_KEY_HOT.get(), EmberTrialSetup.FIRE_KEY.get(),
                EmberTrialSetup.LIVE_COAL.get()};
    }

    // 全員の持ち物から消す (リセット)
    public static void stripAll(ServerLevel level, Item... items) {
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            strip(p, items);
        }
    }

    public static void hint(ServerPlayer p, String key, Object... args) {
        p.displayClientMessage(ModText.hint(key, args), true);
    }

    // ---------------------------------------------------------------- 合図 (試練が済んだ = 石が鳴らせる)
    // 石が鳴らせるようになったら (西: 奥の岸に着いた、東: 鍛え手を倒した、南: 火床が 100)、その部屋にいた人へ合図を送る。
    // 眠っている試練 (最初から鳴っている) と、リセットのとき (resetSignals) は送らない
    public static void resetSignals(ServerLevel level) {
        EmberBackState state = EmberBackState.get(level);
        for (EmberBackState.Trial t : EmberBackState.Trial.values()) {
            SIGNALLED.put(t, state.ready(t));
        }
    }

    static void tickSignals(ServerLevel level) {
        EmberBackState state = EmberBackState.get(level);
        for (EmberBackState.Trial t : EmberBackState.Trial.values()) {
            if (!state.ready(t) || SIGNALLED.getOrDefault(t, false)) {
                continue;
            }
            SIGNALLED.put(t, true);
            List<ServerPlayer> near = new ArrayList<>();
            for (EmberBackLayout.Box box : boxesOf(t)) {
                near.addAll(playersIn(level, box.grow(8)));
            }
            for (ServerPlayer p : near) {
                Quests.signal(p, signalOf(t));
            }
            Quests.signalWithParty(level, near, FreeQuestEvents.TRIAL_CLEARED);
        }
    }

    static List<EmberBackLayout.Box> boxesOf(EmberBackState.Trial t) {
        return switch (t) {
            case WEST -> List.of(EmberBackLayout.WEST);
            case EAST -> List.of(EmberBackLayout.EAST, EmberBackLayout.SMITH);
            case SOUTH -> List.of(EmberBackLayout.SOUTH);
        };
    }

    // ---------------------------------------------------------------- 見回り
    private static boolean outside(Player p, EmberBackLayout.Box... boxes) {
        for (EmberBackLayout.Box b : boxes) {
            if (b.grow(2).contains(p.position())) {
                return false;
            }
        }
        return true;
    }

    // 試練の品を、その部屋の外へ持ち出していないか (1 秒に一度)
    static void patrol(ServerLevel level, List<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            if (!p.isAlive()) {
                continue;
            }
            if (outside(p, EmberBackLayout.WEST) && strip(p, EmberTrialSetup.ASH_URN.get()) > 0) {
                hint(p, EmberIds.LANG_WEST + "urn_returned");
            }
            if (outside(p, EmberBackLayout.EAST, EmberBackLayout.SMITH)
                    && strip(p, EmberTrialSetup.FIRE_KEY_HOT.get(), EmberTrialSetup.FIRE_KEY.get()) > 0) {
                hint(p, EmberIds.LANG_EAST + "key_gone");
            }
            if (outside(p, EmberBackLayout.SOUTH) && strip(p, EmberTrialSetup.LIVE_COAL.get()) > 0) {
                hint(p, EmberIds.LANG_SOUTH + "coal_gone");
            }
        }
        // 落とした壺と鍵は消える (一人一つ)
        for (EmberBackLayout.Box b : List.of(EmberBackLayout.WEST, EmberBackLayout.EAST, EmberBackLayout.SMITH, EmberBackLayout.HUB)) {
            clearItems(level, b, EmberTrialSetup.ASH_URN.get(), EmberTrialSetup.FIRE_KEY_HOT.get(), EmberTrialSetup.FIRE_KEY.get());
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = level(event.getServer());
        if (level == null) {
            return;
        }
        AshPath.tick(level);
        Forge.tick(level);
        Hearth.tick(level);
        if (level.getGameTime() % 20 == 0) {
            if (!level.players().isEmpty()) {
                patrol(level, level.players());
            }
            tickSignals(level);
        }
    }

    // 呑まれる判定は、バニラの溶岩の傷が入る前 (毎 tick の頭) に見る
    @SubscribeEvent
    static void onPlayerPre(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer p && p.level().dimension() == EmberIds.BACK) {
            AshPath.checkEngulfed(p);
        }
    }

    // 灰溜まり (灰色の羊毛) で壺が満ちる。鍛え手の扉に火の鍵を差す
    @SubscribeEvent
    static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level) || level.dimension() != EmberIds.BACK) {
            return;
        }
        BlockPos pos = event.getPos();
        if (AshPath.refillAt(level, p, pos)) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            return;
        }
        if (EmberBackLayout.SMITH_DOOR.contains(pos) && level.getBlockState(pos).is(jp.revati.skyabyss.registry.ModBlocks.SEALED_GATE.get())) {
            if (!Forge.openDoor(level, p, event.getHand())) {
                hint(p, EmberIds.LANG_EAST + "door_hint");
            }
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            strip(p, allTrialItems());
        }
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        strip(event.getEntity(), allTrialItems());
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        strip(event.getEntity(), allTrialItems());
        AshPath.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SIGNALLED.clear();
        AshPath.clearMemory();
        Forge.clearMemory();
        Hearth.clearMemory();
    }

    public static Vec3 center(BlockPos p) {
        return Vec3.atBottomCenterOf(p);
    }
}
