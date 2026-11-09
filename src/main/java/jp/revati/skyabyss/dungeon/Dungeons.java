package jp.revati.skyabyss.dungeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.loot.box.BoxKind;
import jp.revati.skyabyss.loot.box.LootBoxSetup;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.party.Parties;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.world.continent.ContinentPois;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 地下のダンジョンの入り方・進み方・帰り方 (docs/design/55 の 2 章)。
//   入る     大陸の入口の間 (本物の地下) の層の門 (天然磁石) を右クリック。近くの同じ組の人もいっしょに、組の写しへ。
//            組の写しがもうあれば、その checkpoint の層へ加わる。誰でも入れる (Lv の決まりはない。目安 Lv は地図と碑に書く)
//   続きから 着いた一番深い層を人ごとに覚え (PROGRESS)、新しく入るときは入る人の中で一番浅い層から (誰も飛ばさない)
//   層       next に乗ると次の層へ (鯨骨の坑はトロッコ)。帰り石 (天然磁石) を右クリックすると入口の間へ
//   倒れた   起きると checkpoint の層の入口へ (墓は作らない。写しの中の死は 11 の地上の死に入れない)
//   主       倒すと報酬 (cleared)、主の間の奥の光に入ると入口の間へ。主のあとの写しは CLEAR_END で閉じる
//   深く潜る 主を一度倒した人が、しゃがんで層の門を使う (組の写しはその深さのまま)。敵と主の体力と傷 +40%・仕掛けが速い (Mechanics)・
//            罠の休みが短い。主の箱は一つ上の階級で、ふつうとは別に一日一度 (2026-11-05 のダンジョンの頁 1-1)
//   坑の様子 その日の DungeonMood (宝・罠・鍵・群れ)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Dungeons {
    public static final String FOE_TAG = "skyabyss_dungeon_foe";
    public static final String KEY_TAG = "skyabyss_dungeon_key";
    // 人のデータ: 着いた一番深い層・越えた回数・主の箱を受け取った日 (ダンジョンごとに _<id> を付ける)
    public static final String PROGRESS = "skyabyss_dungeon_cp_";
    public static final String CLEARS = "skyabyss_dungeon_clears_";
    public static final String BOX_DAY = "skyabyss_dungeon_box_";
    public static final String DEEP_BOX_DAY = "skyabyss_dungeon_deepbox_";
    // 深く潜ったときの敵と主 (体力は修飾子、傷は onDeepHit で +40%)
    public static final String DEEP_TAG = "skyabyss_dungeon_deep";
    public static final double DEEP = 0.40;
    static final net.minecraft.resources.ResourceLocation DEEP_HEALTH = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "dungeon_deep_health");
    // 誰も写しにいなくなってから閉じるまで (抜けて戻っても続きから。10 分)
    static final int EMPTY_END = 12000;
    // 主のあと写しを閉じるまで
    static final int CLEAR_END = 2400;
    // 層の門・次の層・帰りの光に届く距離
    static final double GATE_REACH = 4.5;
    static final double STEP_REACH = 1.6;
    static final double GATHER = 16;
    static final Map<Integer, DungeonRun> RUNS = new HashMap<>();
    static final Map<UUID, Integer> BY_PLAYER = new HashMap<>();
    // 次の層へ運ばれる途中の人 (二度運ばない)
    private static final Map<UUID, Long> MOVING = new HashMap<>();
    private static int nextId = 1;

    private Dungeons() {
    }

    // いま開いている写しの、印 mark の位置 (そのダンジョンの全部の層。印のない層は飛ばす)
    public static java.util.List<BlockPos> marksOf(net.minecraft.server.MinecraftServer server, Dungeon d, String mark) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (DungeonRun run : RUNS.values()) {
            if (run.dungeon != d) {
                continue;
            }
            for (int f = 0; f < d.floors().length; f++) {
                if (run.floor(f).terrain.has(mark)) {
                    out.add(run.mark(f, mark));
                }
            }
        }
        return out;
    }

    public static DungeonRun runOf(ServerPlayer p) {
        Integer id = BY_PLAYER.get(p.getUUID());
        return id == null ? null : RUNS.get(id);
    }

    public static int progress(ServerPlayer p, Dungeon d) {
        return p.getPersistentData().getInt(PROGRESS + d.id);
    }

    public static int clears(ServerPlayer p, Dungeon d) {
        return p.getPersistentData().getInt(CLEARS + d.id);
    }

    // 称号の印 (rebirth/RebirthTitles): 主を一度でも倒したダンジョン "dungeon:<id>"
    public static java.util.Set<String> marks(ServerPlayer p) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (Dungeon d : Dungeon.values()) {
            if (clears(p, d) > 0) {
                out.add("dungeon:" + d.id);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 入る
    // 層の門を使った: 組の写しがあれば加わり、なければ近くの組の人と新しく始める
    public static DungeonRun enter(ServerPlayer p, Dungeon d) {
        return enter(p, d, false);
    }

    // 組の写しの続きに加わるか (自分の写しか、同じパーティーの人の写しがある)。加わるなら深さは写しのまま
    public static boolean joinsRun(ServerPlayer p, Dungeon d) {
        DungeonRun mine = runOf(p);
        if (mine != null && mine.dungeon == d && !mine.cleared) {
            return true;
        }
        var party = Parties.of(p);
        if (party == null) {
            return false;
        }
        for (DungeonRun r : RUNS.values()) {
            if (r.dungeon == d && !r.cleared && party.members.stream().anyMatch(r.members::contains)) {
                return true;
            }
        }
        return false;
    }

    // deep: 深く潜る (主を一度倒した人だけ。倒していなければふつうに入って知らせる)
    public static DungeonRun enter(ServerPlayer p, Dungeon d, boolean deep) {
        if (d.mini) {
            // 小ダンジョンには深く潜るはない
            deep = false;
        }
        if (deep && clears(p, d) <= 0) {
            deep = false;
            Notice.hint(p, "message.skyabyss.dungeon.deep_locked");
        }
        DungeonRun mine = runOf(p);
        if (mine != null && mine.dungeon != d) {
            // 別のダンジョンの写しの続きは捨てる (一人一つ)
            BY_PLAYER.remove(p.getUUID());
            mine.members.remove(p.getUUID());
            mine = null;
        }
        if (mine == null) {
            var party = Parties.of(p);
            if (party != null) {
                for (DungeonRun r : RUNS.values()) {
                    if (r.dungeon == d && !r.cleared && party.members.stream().anyMatch(r.members::contains)) {
                        mine = r;
                        break;
                    }
                }
            }
        }
        if (mine != null && !mine.cleared) {
            if (!mine.members.contains(p.getUUID())) {
                mine.members.add(p.getUUID());
            }
            BY_PLAYER.put(p.getUUID(), mine.id);
            moveTo(p, mine, mine.checkpoint);
            return mine;
        }
        List<ServerPlayer> squad = new ArrayList<>();
        squad.add(p);
        var party = Parties.of(p);
        for (ServerPlayer o : Parties.onlineMembers(p.server, party)) {
            if (o != p && o.level() == p.level() && o.distanceTo(p) < GATHER && runOf(o) == null && !o.isSpectator()) {
                squad.add(o);
            }
        }
        return start(p.server, d, squad, deep);
    }

    public static DungeonRun start(MinecraftServer server, Dungeon d, List<ServerPlayer> squad) {
        return start(server, d, squad, false);
    }

    public static DungeonRun start(MinecraftServer server, Dungeon d, List<ServerPlayer> squad, boolean deep) {
        ServerLevel level = DungeonWorld.level(server);
        if (level == null || squad.isEmpty()) {
            return null;
        }
        int slot = -1;
        for (int s = 0; s < DungeonWorld.SLOTS && slot < 0; s++) {
            final int fs = s;
            if (RUNS.values().stream().noneMatch(r -> r.dungeon == d && r.slot == fs)) {
                slot = s;
            }
        }
        if (slot < 0) {
            for (ServerPlayer p : squad) {
                Notice.hint(p, "message.skyabyss.dungeon.full");
            }
            return null;
        }
        List<UUID> ids = new ArrayList<>();
        int from = d.bossFloor();
        for (ServerPlayer p : squad) {
            ids.add(p.getUUID());
            from = Math.min(from, progress(p, d));
        }
        DungeonRun run = new DungeonRun(server, nextId++, d, slot, ids, level.getGameTime(), Math.max(0, from), deep, d.mini ? null : DungeonMood.today(d));
        RUNS.put(run.id, run);
        for (ServerPlayer p : squad) {
            BY_PLAYER.put(p.getUUID(), run.id);
        }
        SkyAbyss.LOGGER.info("[dungeon] run #{} {} slot {} from floor {} with {} player(s), deep {}, mood {}", run.id, d.id, slot, run.checkpoint, squad.size(), deep,
                run.mood == null ? "-" : run.mood.id);
        for (ServerPlayer p : squad) {
            moveTo(p, run, run.checkpoint);
            if (real(p)) {
                Notice.place(p, Component.translatable(d.nameKey()), Component.translatable(deep ? "message.skyabyss.dungeon.sub_deep" : "message.skyabyss.dungeon.sub", d.level, squad.size()));
                if (run.mood != null) {
                    Notice.hint(p, "message.skyabyss.dungeon.mood_enter", Component.translatable(run.mood.nameKey()), Component.translatable(run.mood.descKey()));
                } else {
                    Notice.hint(p, "message.skyabyss.dungeon.mini_enter");
                }
                if (deep) {
                    Notice.alert(p, "message.skyabyss.dungeon.deep_enter");
                }
            }
        }
        return run;
    }

    // その人を写しの層 floor の入口へ (層をまだ流していなければ流す)
    static void moveTo(ServerPlayer p, DungeonRun run, int floor) {
        ServerLevel level = run.level();
        if (level == null) {
            return;
        }
        DungeonFloor f = run.floor(floor);
        f.pourNow(level);
        Vec3 e = run.markCenter(floor, "entry");
        if (real(p)) {
            p.teleportTo(level, e.x, e.y, e.z, facing(run, floor), 0f);
        } else {
            // 自己テストの偽の人は次元をまたいで送れない (つながりが無い)。場所だけ置く
            p.setServerLevel(level);
            p.moveTo(e.x, e.y, e.z, facing(run, floor), 0f);
        }
        p.resetFallDistance();
        p.setDeltaMovement(Vec3.ZERO);
        if (floor > run.checkpoint) {
            run.checkpoint = floor;
        }
        var data = p.getPersistentData();
        if (data.getInt(PROGRESS + run.dungeon.id) < floor) {
            data.putInt(PROGRESS + run.dungeon.id, floor);
        }
        if (real(p)) {
            Notice.place(p, Component.translatable(run.dungeon.nameKey() + ".floor" + floor), null);
        }
        MOVING.put(p.getUUID(), level.getGameTime() + 40);
    }

    // 本当の人 (自己テストの偽の人には知らせも品も送らない)
    static boolean real(ServerPlayer p) {
        return !(p instanceof net.neoforged.neoforge.common.util.FakePlayer);
    }

    // 入口から最初の部屋 (なければ次の層の印) の方を向く
    static float facing(DungeonRun run, int floor) {
        var t = DungeonWorld.terrain(run.dungeon, floor);
        BlockPos e = t.mark("entry");
        String to = t.marksStarting("room_").isEmpty() ? (t.has("boss") ? "boss" : "next") : t.marksStarting("room_").get(0);
        if (!t.has(to)) {
            return 0f;
        }
        BlockPos q = t.mark(to);
        return (float) Math.toDegrees(Math.atan2(-(q.getX() - e.getX()), q.getZ() - e.getZ()));
    }

    // 入口の間へ帰す (写しの続きは残る)
    public static void sendBack(ServerPlayer p, Dungeon d) {
        ServerLevel over = p.server.overworld();
        if (d.mini) {
            MiniDungeons.sendHome(p, d);
            return;
        }
        BlockPos back = hall(over, d);
        // 層の門の方を向いて立つ
        BlockPos gate = ContinentPois.extraOf(over, d.poiKind(), "gate");
        float yaw = gate == null ? p.getYRot() : (float) Math.toDegrees(Math.atan2(-(gate.getX() - back.getX()), gate.getZ() - back.getZ()));
        p.teleportTo(over, back.getX() + 0.5, back.getY(), back.getZ() + 0.5, yaw, 0f);
        p.resetFallDistance();
        p.setDeltaMovement(Vec3.ZERO);
    }

    // 入口の間の帰り着く所 (見どころの印 back。まだ建っていなければ見どころの地面の上)
    static BlockPos hall(ServerLevel over, Dungeon d) {
        BlockPos back = ContinentPois.extraOf(over, d.poiKind(), "back");
        if (back != null) {
            return back;
        }
        BlockPos poi = ContinentPois.placeOf(d.poiKind());
        if (poi != null) {
            return over.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, poi);
        }
        return over.getSharedSpawnPos();
    }

    public static void leave(ServerPlayer p) {
        DungeonRun run = runOf(p);
        if (run == null) {
            if (DungeonWorld.in(p)) {
                p.teleportTo(p.server.overworld(), p.server.overworld().getSharedSpawnPos().getX() + 0.5, p.server.overworld().getSharedSpawnPos().getY(),
                        p.server.overworld().getSharedSpawnPos().getZ() + 0.5, 0f, 0f);
            }
            return;
        }
        sendBack(p, run.dungeon);
        Notice.hint(p, "message.skyabyss.dungeon.left");
    }

    static void end(DungeonRun run) {
        ServerLevel level = run.level();
        if (level != null) {
            for (DungeonFloor f : run.floors) {
                f.close();
            }
            for (Mob m : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(
                    DungeonWorld.origin(run.dungeon, run.slot, 0)).inflate(DungeonWorld.SLOT_DX / 2.0, 200, DungeonWorld.FLOOR_DZ * 3.0),
                    m -> m.getTags().contains(run.foeTag()))) {
                m.discard();
            }
        }
        RUNS.remove(run.id);
        BY_PLAYER.values().removeIf(id -> id == run.id);
        SkyAbyss.LOGGER.info("[dungeon] run #{} {} ended (cleared {})", run.id, run.dungeon.id, run.cleared);
    }

    // 深く潜った写しの敵と主: 体力 +40% (傷は onDeepHit)
    static void deepen(Mob m, DungeonRun run) {
        if (!run.deep) {
            return;
        }
        m.addTag(DEEP_TAG);
        var hp = m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.removeModifier(DEEP_HEALTH);
            hp.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(DEEP_HEALTH, DEEP,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            m.setHealth(m.getMaxHealth());
        }
    }

    // 深く潜った写しの敵と主の傷 +40% (近接・予告の技・飛び道具のどれでも。源の生き物の印で見る)
    @SubscribeEvent
    static void onDeepHit(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player && event.getSource().getEntity() != null
                && event.getSource().getEntity().getTags().contains(DEEP_TAG)) {
            event.setNewDamage((float) (event.getNewDamage() * (1 + DEEP)));
        }
    }

    // ---------------------------------------------------------------- 主を倒した
    static void cleared(DungeonRun run, ServerLevel level) {
        if (run.cleared) {
            return;
        }
        run.cleared = true;
        run.clearedAt = level.getGameTime();
        Dungeon d = run.dungeon;
        if (d.mini) {
            MiniDungeons.cleared(run, level);
            SkyAbyss.LOGGER.info("[dungeon] run #{} {} cleared in {}s", run.id, d.id, (level.getGameTime() - run.startedTick) / 20);
            return;
        }
        for (ServerPlayer p : run.present()) {
            if (run.floorOf(p) != d.bossFloor()) {
                continue;
            }
            reward(p, d, run.deep, run.mood);
        }
        SkyAbyss.LOGGER.info("[dungeon] run #{} {} cleared in {}s", run.id, d.id, (level.getGameTime() - run.startedTick) / 20);
    }

    // 越えた経験値 (55 の 2 章)。初めて越えた分は章の主の経験値 (章の 15%) の中 (04 の 3 章)
    public static int clearXp(Dungeon d) {
        return switch (d) {
            case BONE_PIT -> 100;
            case TIDE_CAVE -> 200;
            case STORM_HOLLOW -> 360;
            case MOLTEN_MINE -> 530;
            default -> 0;
        };
    }

    // 報酬 (55 の 2 章): 経験値・鐘銭・土地の粉はいつでも。主の封の箱は一日一度。初めて越えたら称号 (DungeonTitles)
    static void reward(ServerPlayer p, Dungeon d) {
        reward(p, d, false, DungeonMood.today(d));
    }

    // 主の箱の階級: ふつうは目安 Lv 20 未満がレア、以上がエピック。深く潜ったときは一つ上
    public static jp.revati.skyabyss.item.GearTier boxTier(Dungeon d, boolean deep) {
        var base = d.level < 20 ? jp.revati.skyabyss.item.GearTier.RARE : jp.revati.skyabyss.item.GearTier.EPIC;
        return deep ? (base == jp.revati.skyabyss.item.GearTier.RARE ? jp.revati.skyabyss.item.GearTier.EPIC : jp.revati.skyabyss.item.GearTier.HEROIC) : base;
    }

    static void reward(ServerPlayer p, Dungeon d, boolean deep, DungeonMood mood) {
        var data = p.getPersistentData();
        int clears = data.getInt(CLEARS + d.id) + 1;
        data.putInt(CLEARS + d.id, clears);
        data.putInt(PROGRESS + d.id, 0);
        String today = jp.revati.skyabyss.util.DayClock.today().toString();
        String dayKey = (deep ? DEEP_BOX_DAY : BOX_DAY) + d.id;
        boolean box = !today.equals(data.getString(dayKey));
        if (box) {
            data.putString(dayKey, today);
        }
        // 主の装飾品が育つ (深く潜ったら 2 回ぶん。DungeonGear)
        if (clears > 1) {
            DungeonGear.grow(p, d, deep ? 2 : 1);
        }
        if (!real(p)) {
            return;
        }
        int xp = (int) Math.round(clearXp(d) * (mood == DungeonMood.TRAPS ? 1 + DungeonMood.TRAP_XP : 1));
        jp.revati.skyabyss.rpg.Growth.addXp(p, xp, "growth.skyabyss.reason.dungeon");
        int coins = 10 + d.level * 2;
        Coins.give(p, coins);
        Quests.give(p, new ItemStack(ModItems.socketShard(d.element).get(), mood == DungeonMood.SWARM ? 3 : 2));
        Notice.place(p, Component.translatable("message.skyabyss.dungeon.clear_title"), Component.translatable(d.nameKey()));
        p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1f, 1f);
        Notice.gain(p, "message.skyabyss.dungeon.reward", xp, coins);
        if (box) {
            BoxKind kind = BoxKind.rarity(boxTier(d, deep), (d.level - 1) / 10 + 1);
            var item = kind == null ? null : LootBoxSetup.item(kind);
            if (item != null) {
                Quests.give(p, new ItemStack(item));
                Notice.gain(p, "message.skyabyss.dungeon.box");
            }
        }
        if (clears == 1) {
            Notice.unlock(p, "message.skyabyss.dungeon.first", Component.translatable(d.nameKey()));
            // 初めて越えた人に、そのダンジョンだけの装飾品を一つ (DungeonGear)
            // 装飾品の無いダンジョンでは何も渡さず、知らせも出さない (2026-11-08 の声「空気を手に入れた という表記はやめて」)
            ItemStack trinket = new ItemStack(DungeonGear.item(d));
            if (!trinket.isEmpty()) {
                DungeonGear.setClears(trinket, 1);
                Quests.give(p, trinket);
                Notice.gain(p, "message.skyabyss.dungeon.trinket", trinket.getHoverName());
            }
            jp.revati.skyabyss.rebirth.Rebirths.updateTitles(p);
        }
        Quests.signal(p, "dungeon:" + d.id);
        Notice.hint(p, "message.skyabyss.dungeon.exit_hint");
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        gateHints(event.getServer());
        if (RUNS.isEmpty()) {
            return;
        }
        ServerLevel level = DungeonWorld.level(event.getServer());
        if (level == null) {
            return;
        }
        long t = level.getGameTime();
        MOVING.values().removeIf(until -> until < t);
        for (DungeonRun run : List.copyOf(RUNS.values())) {
            tick(level, run, t);
        }
    }

    static void tick(ServerLevel level, DungeonRun run, long t) {
        List<ServerPlayer> here = run.present();
        // 次の層と主の間を、少しずつ先に流しておく
        for (int f = run.checkpoint + 1; f < run.floors.length; f++) {
            if (!run.floor(f).ready()) {
                run.floor(f).pourStep(level);
                break;
            }
        }
        for (int f = 0; f < run.floors.length; f++) {
            List<ServerPlayer> on = new ArrayList<>();
            for (ServerPlayer p : here) {
                if (run.floorOf(p) == f) {
                    on.add(p);
                }
            }
            if (!on.isEmpty() || (f == run.dungeon.bossFloor() && run.floor(f).boss != null)) {
                run.floor(f).tick(level, t, on);
            }
            for (ServerPlayer p : on) {
                steps(level, run, f, p, t);
            }
        }
        if (run.cleared && t - run.clearedAt > CLEAR_END) {
            for (ServerPlayer p : here) {
                sendBack(p, run.dungeon);
            }
            end(run);
            return;
        }
        run.emptyTicks = here.isEmpty() ? run.emptyTicks + 1 : 0;
        if (run.emptyTicks >= EMPTY_END) {
            end(run);
        }
    }

    // 入口の間の層の門のそばに来た人へ、入り方を一度知らせる (寄るたびに。HINT_REST の間はくり返さない)
    static final Map<UUID, Long> HINTED = new HashMap<>();
    static final int HINT_REST = 2400;

    static void gateHints(MinecraftServer server) {
        ServerLevel over = server.overworld();
        long t = over.getGameTime();
        if (t % 40 != 0 || over.players().isEmpty()) {
            return;
        }
        for (Dungeon d : Dungeon.mids()) {
            BlockPos gate = ContinentPois.extraOf(over, d.poiKind(), "gate");
            if (gate == null) {
                continue;
            }
            for (ServerPlayer p : over.players()) {
                if (p.blockPosition().distSqr(gate) < 7 * 7 && t - HINTED.getOrDefault(p.getUUID(), (long) -HINT_REST) >= HINT_REST) {
                    HINTED.put(p.getUUID(), t);
                    Notice.hint(p, "message.skyabyss.dungeon.gate_hint");
                    DungeonMood m = DungeonMood.today(d);
                    Notice.hint(p, "message.skyabyss.dungeon.mood_enter", Component.translatable(m.nameKey()), Component.translatable(m.descKey()));
                    if (clears(p, d) > 0) {
                        Notice.hint(p, "message.skyabyss.dungeon.deep_hint");
                    }
                }
            }
        }
    }

    // 立った所の仕掛け: 次の層へ・帰りの光・入口の帰り石の知らせ
    static void steps(ServerLevel level, DungeonRun run, int floor, ServerPlayer p, long t) {
        if (!p.isAlive() || MOVING.containsKey(p.getUUID())) {
            return;
        }
        DungeonFloor f = run.floor(floor);
        if (!f.ready()) {
            return;
        }
        if (floor < run.dungeon.bossFloor() && f.terrain.has("next") && p.position().distanceTo(f.center("next")) < STEP_REACH) {
            MOVING.put(p.getUUID(), t + 60);
            if (run.dungeon == Dungeon.BONE_PIT && floor == 0) {
                // トロッコで下の層へ (乗った音のあと、着いた所で止まる音)
                level.playSound(null, p.blockPosition(), SoundEvents.MINECART_RIDING, SoundSource.BLOCKS, 1f, 0.8f);
                Notice.hint(p, "message.skyabyss.dungeon.bone_pit.cart");
                Tasks.later(30, () -> {
                    if (p.isAlive() && !p.hasDisconnected() && RUNS.containsKey(run.id)) {
                        moveTo(p, run, floor + 1);
                        level.playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4f, 1.6f);
                    }
                });
            } else {
                level.playSound(null, p.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1f, 0.8f);
                moveTo(p, run, floor + 1);
            }
            return;
        }
        if (floor == run.dungeon.bossFloor() && f.bossDown && f.terrain.has("exit") && p.position().distanceTo(f.center("exit")) < 2.2) {
            MOVING.put(p.getUUID(), t + 60);
            level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
            sendBack(p, run.dungeon);
            Notice.hint(p, "message.skyabyss.dungeon.returned", Component.translatable(run.dungeon.nameKey()));
        }
    }

    // ---------------------------------------------------------------- 出来事
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        BlockPos pos = event.getPos();
        if (DungeonWorld.in(p)) {
            DungeonRun run = runOf(p);
            int f = run == null ? -1 : run.floorOf(p);
            if (run != null && f >= 0 && run.floor(f).terrain.has("leave") && pos.equals(run.mark(f, "leave"))) {
                event.setCanceled(true);
                leave(p);
            } else if (run != null && f >= 0 && run.dungeon.mini && MiniDungeons.pack(p, run, f, pos)) {
                event.setCanceled(true);
            }
            return;
        }
        if (p.level().dimension() != Level.OVERWORLD) {
            return;
        }
        // 章の跡地の小ダンジョンの口の門 (MiniDungeons)
        Dungeon mini = MiniDungeons.gateAt(p.server, pos);
        if (mini != null) {
            event.setCanceled(true);
            MiniDungeons.use(p, mini);
            return;
        }
        // 入口の間の層の門
        for (Dungeon d : Dungeon.mids()) {
            BlockPos gate = ContinentPois.extraOf(p.serverLevel(), d.poiKind(), "gate");
            if (gate != null && gate.distManhattan(pos) <= 1) {
                event.setCanceled(true);
                // 主を一度倒した人には「ふつうに入る / 深く潜る」を選ぶ画面 (DeepChoice。前はしゃがんで使うと深く潜った)
                DeepChoice.use(p, d);
                return;
            }
        }
    }

    // 雷晶の空洞の結晶: 殴ると溜まる
    @SubscribeEvent
    static void onHitBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !DungeonWorld.in(p) || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        DungeonRun run = runOf(p);
        int f = run == null ? -1 : run.floorOf(p);
        if (f >= 0) {
            Mechanics.strike(run.floor(f), p.serverLevel(), event.getPos());
        }
    }

    // 矢・投げた物が結晶に当たっても溜まる
    @SubscribeEvent
    static void onProjectile(ProjectileImpactEvent event) {
        if (!(event.getProjectile().level() instanceof ServerLevel level) || level.dimension() != DungeonWorld.LEVEL
                || !(event.getRayTraceResult() instanceof BlockHitResult hit) || !(event.getProjectile().getOwner() instanceof ServerPlayer p)) {
            return;
        }
        DungeonRun run = runOf(p);
        DungeonWorld.Place at = DungeonWorld.place(hit.getBlockPos());
        if (run != null && at != null && at.dungeon() == run.dungeon && at.slot() == run.slot) {
            Mechanics.strike(run.floor(at.floor()), level, hit.getBlockPos());
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level) || level.dimension() != DungeonWorld.LEVEL || !e.getTags().contains(FOE_TAG)) {
            return;
        }
        for (DungeonRun run : RUNS.values()) {
            if (e.getTags().contains(run.foeTag())) {
                DungeonWorld.Place at = DungeonWorld.place(e.blockPosition());
                int f = at == null ? run.dungeon.bossFloor() : at.floor();
                run.floor(f).foeDied(level, e);
                return;
            }
        }
    }

    // 起きたら checkpoint の層の入口へ (写しがもう無ければ入口の間へ)
    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.isEndConquered()) {
            return;
        }
        DungeonRun run = runOf(p);
        if (run == null) {
            return;
        }
        Tasks.later(2, () -> {
            if (p.isAlive() && !p.hasDisconnected() && RUNS.containsKey(run.id)) {
                moveTo(p, run, run.checkpoint);
                Notice.hint(p, "message.skyabyss.dungeon.woke");
            }
        });
    }

    // 入り直したとき: 写しが続いていれば checkpoint の層へ、終わっていれば入口の間へ
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !DungeonWorld.in(p)) {
            return;
        }
        DungeonRun run = runOf(p);
        DungeonWorld.Place at = DungeonWorld.place(p.blockPosition());
        Dungeon d = run != null ? run.dungeon : at != null ? at.dungeon() : Dungeon.BONE_PIT;
        Tasks.later(10, () -> {
            if (run != null && RUNS.containsKey(run.id)) {
                moveTo(p, run, run.checkpoint);
            } else {
                sendBack(p, d);
            }
        });
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        for (DungeonRun run : RUNS.values()) {
            for (DungeonFloor f : run.floors) {
                f.close();
            }
        }
        RUNS.clear();
        BY_PLAYER.clear();
        MOVING.clear();
    }

    // ---------------------------------------------------------------- 開発用
    // /skyabyss dungeon enter <id> (層の門と同じ) | floor <0..2> (その層へ) | leave | boss (主を倒す) | progress (続きを消す)
    //                  | hall <id> (入口の間の帰り着く所へ) | mouth <id> (地上の口の前、口を向いて。撮影用)
    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dungeon")
                        .then(Commands.literal("enter").then(Commands.argument("id", StringArgumentType.word()).suggests((c, b) -> {
                            for (Dungeon d : Dungeon.values()) {
                                b.suggest(d.id);
                            }
                            return b.buildFuture();
                        }).executes(c -> {
                            Dungeon d = Dungeon.byId(StringArgumentType.getString(c, "id"));
                            return d != null && enter(c.getSource().getPlayerOrException(), d) != null ? 1 : 0;
                        }).then(Commands.literal("deep").executes(c -> {
                            Dungeon d = Dungeon.byId(StringArgumentType.getString(c, "id"));
                            return d != null && enter(c.getSource().getPlayerOrException(), d, true) != null ? 1 : 0;
                        }))))
                        .then(Commands.literal("floor").then(Commands.argument("floor", IntegerArgumentType.integer(0, Dungeon.BOSS_FLOOR)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            DungeonRun run = runOf(p);
                            if (run == null) {
                                return 0;
                            }
                            moveTo(p, run, IntegerArgumentType.getInteger(c, "floor"));
                            return 1;
                        })))
                        .then(Commands.literal("leave").executes(c -> {
                            leave(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("boss").executes(c -> {
                            DungeonRun run = runOf(c.getSource().getPlayerOrException());
                            if (run == null || run.floor(run.dungeon.bossFloor()).boss == null) {
                                return 0;
                            }
                            var e = run.level().getEntity(run.floor(run.dungeon.bossFloor()).boss);
                            if (e instanceof LivingEntity le) {
                                le.hurt(le.damageSources().genericKill(), Float.MAX_VALUE);
                            }
                            return 1;
                        }))
                        .then(Commands.literal("hall").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                            Dungeon d = Dungeon.byId(StringArgumentType.getString(c, "id"));
                            if (d == null) {
                                return 0;
                            }
                            sendBack(c.getSource().getPlayerOrException(), d);
                            return 1;
                        })))
                        .then(Commands.literal("mouth").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                            Dungeon d = Dungeon.byId(StringArgumentType.getString(c, "id"));
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            BlockPos[] front = d == null ? null : ContinentPois.frontOf(p.serverLevel(), d.poiKind(), 16);
                            if (front == null) {
                                return 0;
                            }
                            Vec3 a = Vec3.atBottomCenterOf(front[0]);
                            Vec3 b = Vec3.atBottomCenterOf(front[1]);
                            float yaw = (float) Math.toDegrees(Math.atan2(-(b.x - a.x), b.z - a.z));
                            p.teleportTo(p.serverLevel(), a.x, a.y + 5, a.z, yaw, 14f);
                            return 1;
                        })))
                        .then(Commands.literal("goto").then(Commands.argument("mark", StringArgumentType.word())
                                .executes(c -> gotoMark(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "mark"), null))
                                .then(Commands.argument("face", StringArgumentType.word())
                                        .executes(c -> gotoMark(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "mark"),
                                                StringArgumentType.getString(c, "face"))))))
                        .then(Commands.literal("progress").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            for (Dungeon d : Dungeon.values()) {
                                p.getPersistentData().remove(PROGRESS + d.id);
                            }
                            return 1;
                        }))));
    }

    // 今の層の印へ (face があればその印の方を向く。印が岩の中なら face の方へ空きまで寄る。撮影・開発用)
    static int gotoMark(ServerPlayer p, String mark, String face) {
        DungeonRun run = runOf(p);
        int f = run == null ? -1 : run.floorOf(p);
        if (f < 0 || !run.floor(f).terrain.has(mark) || (face != null && !run.floor(f).terrain.has(face))) {
            return 0;
        }
        ServerLevel level = run.level();
        Vec3 at = run.markCenter(f, mark);
        Vec3 to = face == null ? null : run.markCenter(f, face);
        if (to != null) {
            Vec3 step = to.subtract(at).normalize();
            for (int i = 0; i < 24; i++) {
                BlockPos b = BlockPos.containing(at);
                if (level.getBlockState(b).isAir() && level.getBlockState(b.above()).isAir()) {
                    break;
                }
                at = at.add(step);
            }
        }
        float yaw = p.getYRot();
        float pitch = p.getXRot();
        if (to != null) {
            Vec3 d = to.subtract(at.add(0, 1.6, 0));
            yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        }
        p.teleportTo(level, at.x, at.y, at.z, yaw, pitch);
        return 1;
    }

    // 自己テスト用
    public static DungeonRun startForTest(MinecraftServer server, Dungeon d, List<ServerPlayer> squad) {
        return start(server, d, squad);
    }

    public static DungeonRun startForTest(MinecraftServer server, Dungeon d, List<ServerPlayer> squad, boolean deep) {
        return start(server, d, squad, deep);
    }

    public static void rewardForTest(ServerPlayer p, Dungeon d, boolean deep, DungeonMood mood) {
        reward(p, d, deep, mood);
    }

    public static void endForTest(DungeonRun run) {
        end(run);
    }

    public static void tickForTest(DungeonRun run) {
        ServerLevel level = run.level();
        tick(level, run, level.getGameTime());
    }
}
