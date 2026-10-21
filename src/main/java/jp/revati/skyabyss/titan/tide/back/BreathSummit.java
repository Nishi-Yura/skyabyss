package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.BreathLayout.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.party.Parties;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.titan.group.FirstRun;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.tide.heart.TideLoot;
import jp.revati.skyabyss.titan.tide.mob.TideMob;
import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 息の間の頂の戦い (塔の中の舞台の真珠守り。docs/design/24 の 5 章)。
//  - 舞台に立つ人がいて、そのグループが初回 (心臓を止めていない) で、真珠守りをまだ退けていなければ、3 秒の地鳴りのあと真珠守りが現れる。
//    周回のグループ (FirstRun の既知の道) とピースフルでは現れない (静かな通路)。周回のグループは舞台の再戦の台に触れると挑み直せる
//  - 戦っているあいだ、そのグループだけが舞台にいられる (ほかのグループは風の柱の下の皿 7 へ戻される。舞台へ昇る泡も働かない)。
//    舞台は円い床で、縁は塔の壁。縁から落ちることはない
//  - 戦う人がいなくなって 3 秒で、真珠守りは殻に沈んで消える (退けたあとは出ない)
//  - 退けると、そのグループに展望の間への風の柱が働く (BreathLift の deckOpen)。報酬と経験値
public final class BreathSummit {
    private BreathSummit() {
    }

    enum Fight {
        NONE, WAITING, ACTIVE
    }

    public static final int WAIT_TICKS = 60;
    public static final int WIPE_DELAY = 60;
    // 舞台に立って、戦いが始まるまでの tick
    public static final int DWELL_TICKS = 40;
    public static final int KEEPER_XP = 160;
    public static final int KEEPER_FIRST_BONUS = 80;
    public static final String SIGNAL = "tide_keeper_stilled";
    public static final String KEEPSAKE_ID = "tide_summit";

    private static Fight fight = Fight.NONE;
    @Nullable
    private static UUID owner;
    @Nullable
    private static UUID bossId;
    private static int waitLeft;
    private static int empty;
    private static final Set<UUID> PARTICIPANTS = new LinkedHashSet<>();
    private static final Map<UUID, Integer> DWELL = new HashMap<>();

    static void clear() {
        fight = Fight.NONE;
        owner = null;
        bossId = null;
        waitLeft = 0;
        empty = 0;
        PARTICIPANTS.clear();
        DWELL.clear();
    }

    public static AABB arena() {
        return arenaBox();
    }

    // 舞台にいる、生きている人
    static List<ServerPlayer> inArena(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, arenaBox(), p -> p.isAlive() && !p.isSpectator() && p.getY() >= ARENA_FLOOR + 0.5
                && p.getY() <= ARENA_FLOOR + 12);
    }

    // 戦い (地鳴りのあいだも) の最中か
    public static boolean busy(ServerLevel level) {
        return fight != Fight.NONE;
    }

    // そのグループにとって、舞台が使えないか (別のグループが戦っている)
    public static boolean busyFor(ServerLevel level, UUID group) {
        return fight != Fight.NONE && owner != null && !owner.equals(group);
    }

    @Nullable
    public static UUID owner() {
        return owner;
    }

    public static Fight fight() {
        return fight;
    }

    // 真珠守りが狙える人: 持ち主のグループの、舞台にいる人 (参加者がいれば参加者)
    public static List<ServerPlayer> fighters(ServerLevel level) {
        if (owner == null) {
            return List.of();
        }
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : inArena(level)) {
            if (Groups.isMember(p, owner) && !p.isCreative() && (PARTICIPANTS.isEmpty() || PARTICIPANTS.contains(p.getUUID()))) {
                list.add(p);
            }
        }
        return list;
    }

    // ---------------------------------------------------------------- 毎 tick (TideBackTrials から。人が背中にいるときだけ)
    static void tick(ServerLevel level, boolean peaceful) {
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        if (peaceful) {
            if (fight != Fight.NONE) {
                resetFight(level, false);
            }
            return;
        }
        List<ServerPlayer> inside = inArena(level);
        // 戦っているグループのほかは、舞台から戻す
        if (fight != Fight.NONE && owner != null) {
            for (ServerPlayer p : inside) {
                if (!Groups.isMember(p, owner)) {
                    eject(level, p);
                }
            }
        }
        switch (fight) {
            case NONE -> {
                DWELL.keySet().removeIf(g -> inside.stream().noneMatch(p -> Groups.keyOf(p).equals(g)));
                for (ServerPlayer p : inside) {
                    UUID g = Groups.keyOf(p);
                    int t = DWELL.merge(g, 10, Integer::sum);
                    if (t >= DWELL_TICKS && canStart(level, g)) {
                        begin(level, g, false);
                        break;
                    }
                }
            }
            case WAITING -> {
                if (fighters(level).isEmpty()) {
                    resetFight(level, false);
                    return;
                }
                waitLeft -= 10;
                if (waitLeft % 20 == 0) {
                    level.sendParticles(ParticleTypes.BUBBLE_POP, keeperHome().x, ARENA_FLOOR + 1.2, keeperHome().z, 12, 1.2, 0.3, 1.2, 0.05);
                    level.playSound(null, keeperHome().x, ARENA_FLOOR + 1, keeperHome().z, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.HOSTILE, 2f, 0.5f);
                }
                if (waitLeft <= 0 && owner != null) {
                    spawnKeeper(level, owner);
                }
            }
            case ACTIVE -> {
                if (fighters(level).isEmpty()) {
                    empty += 10;
                    if (empty >= WIPE_DELAY) {
                        resetFight(level, true);
                    }
                } else {
                    empty = 0;
                }
                PearlKeeper boss = boss(level);
                if (boss == null || !boss.isAlive()) {
                    // 持ち主の知らないうちに消えた (読み込み・コマンド): 戦いを戻す
                    resetFight(level, false);
                }
            }
        }
    }

    // この周で挑めるか: 初回のグループ (周回の既知の道は静か)・まだ退けていない
    static boolean canStart(ServerLevel level, UUID group) {
        return !TideBackState.of(level, group).keeperDefeated();
    }

    public static final String FIRST_ROOM_ID = "tide_breath";

    // 戦いを始める (地鳴りから。再戦の台からは rematch = true)
    static void begin(ServerLevel level, UUID group, boolean rematch) {
        fight = Fight.WAITING;
        owner = group;
        waitLeft = WAIT_TICKS;
        empty = 0;
        PARTICIPANTS.clear();
        for (ServerPlayer p : Groups.inBox(level, group, arenaBox())) {
            PARTICIPANTS.add(p.getUUID());
        }
        Groups.bar(level, group, ModText.battle("message.skyabyss.tide.keeper_rumble"));
        SkyAbyss.LOGGER.info("The pearl keeper is about to wake for group {} ({} participant(s){})", group, PARTICIPANTS.size(), rematch ? ", rematch" : "");
    }

    // 再戦の台 (周回のグループが挑み直す。初回のグループは自動で始まるので使えない)
    static boolean useBlock(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (pos.equals(REMATCH)) {
            UUID g = Groups.keyOf(player);
            if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
                Notice.bar(player, ModText.hint("message.skyabyss.tide.keeper_peaceful"));
            } else if (fight != Fight.NONE) {
                Notice.bar(player, ModText.hint("message.skyabyss.tide.keeper_busy"));
            } else if (FirstRun.isFirstRun(level, g) && !TideBackState.of(level, g).keeperDefeated()) {
                Notice.bar(player, ModText.hint("message.skyabyss.tide.keeper_first"));
            } else {
                begin(level, g, true);
            }
            return true;
        }
        if (pos.equals(KEEPSAKE)) {
            keepsake(player);
            return true;
        }
        if (pos.equals(RETURN_PAD)) {
            return false;
        }
        return false;
    }

    static Vec3 keeperHome() {
        Dish v = vent();
        double dx = WX - v.wx();
        double dz = WZ - v.wz();
        double l = Math.hypot(dx, dz);
        return new Vec3(WX + dx / l * 3.0, ARENA_FLOOR + 1, WZ + dz / l * 3.0);
    }

    static void spawnKeeper(ServerLevel level, UUID group) {
        PearlKeeper boss = TideBackSetup.PEARL_KEEPER.get().create(level);
        if (boss == null) {
            resetFight(level, false);
            return;
        }
        boss.addTag(TideMob.TRIAL_TAG);
        boss.setGroupId(group);
        boss.setChallengers(Math.max(1, Math.min(Parties.MAX_MEMBERS, PARTICIPANTS.size())));
        Vec3 home = keeperHome();
        boss.moveTo(home.x, home.y, home.z, 270f, 0);
        level.addFreshEntity(boss);
        boss.emerge(level, home);
        bossId = boss.getUUID();
        fight = Fight.ACTIVE;
        empty = 0;
        Groups.bar(level, group, ModText.battle("message.skyabyss.tide.keeper_begin"));
        SkyAbyss.LOGGER.info("The pearl keeper woke with {} participant(s)", PARTICIPANTS.size());
    }

    @Nullable
    static PearlKeeper boss(ServerLevel level) {
        return bossId != null && level.getEntity(bossId) instanceof PearlKeeper k ? k : null;
    }

    // 戦いを戻す (全滅・放棄・ピースフル・建て直し)
    public static void resetFight(ServerLevel level, boolean announce) {
        PearlKeeper boss = boss(level);
        if (boss != null) {
            level.sendParticles(ParticleTypes.BUBBLE_POP, boss.getX(), boss.getY() + 1.5, boss.getZ(), 40, 1.5, 1, 1.5, 0.05);
            boss.discard();
        }
        UUID group = owner;
        boolean was = fight == Fight.ACTIVE;
        fight = Fight.NONE;
        owner = null;
        bossId = null;
        waitLeft = 0;
        empty = 0;
        PARTICIPANTS.clear();
        DWELL.clear();
        if (announce && was && group != null) {
            Groups.bar(level, group, ModText.deny("message.skyabyss.tide.keeper_sank"));
        }
    }

    // 舞台にいてはいけない人を、風の柱の下の皿 7 へ戻す
    static void eject(ServerLevel level, ServerPlayer p) {
        Node n = dishNode(DISHES - 1);
        p.fallDistance = 0;
        p.teleportTo(level, n.landX(), n.landY(), n.landZ(), p.getYRot(), 0f);
        Notice.bar(p, ModText.deny("message.skyabyss.tide.breath_arena_busy"));
    }

    // ---------------------------------------------------------------- 退けた
    static void onKeeperDefeated(ServerLevel level, PearlKeeper boss) {
        if (!boss.getUUID().equals(bossId) || boss.groupId() == null) {
            return;
        }
        UUID group = boss.groupId();
        TideBackState.Group state = TideBackState.of(level, group);
        boolean first = !state.keeperEver();
        state.markKeeperDefeated();
        Set<UUID> ids = new LinkedHashSet<>(PARTICIPANTS);
        for (ServerPlayer p : Groups.inBox(level, group, arenaBox())) {
            ids.add(p.getUUID());
        }
        bossId = null;
        fight = Fight.NONE;
        owner = null;
        PARTICIPANTS.clear();
        DWELL.clear();
        MinecraftServer server = level.getServer();
        net.minecraft.util.RandomSource r = level.random;
        List<ServerPlayer> present = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            List<ItemStack> items = new ArrayList<>();
            if (r.nextFloat() < (first ? 0.9f : 0.5f)) {
                items.add(new ItemStack(TideMobSetup.TIDE_SHELL_SHARD.get(), 3 + r.nextInt(3)));
            }
            if (r.nextFloat() < 0.25f) {
                items.add(new ItemStack(TideMobSetup.TIDE_DRIED_SHELL.get(), 1 + r.nextInt(2)));
            }
            int held = TitanLoot.handOver(server, id, p, items, 40);
            if (p != null) {
                present.add(p);
                int amount = (int) Math.round(KEEPER_XP * GrowthRules.levelFalloff(Growth.get(p).level, TideLoot.TIDE_LEVEL));
                if (Growth.discover(p, "first:" + SkyAbyss.MODID + ":pearl_keeper")) {
                    amount += KEEPER_FIRST_BONUS;
                }
                Growth.addXp(p, amount, "entity.skyabyss.pearl_keeper");
                jp.revati.skyabyss.quest.Quests.signal(p, SIGNAL);
                Notice.unlock(p, "message.skyabyss.tide.keeper_defeated");
                if (!items.isEmpty()) {
                    Notice.send(p, TitanLoot.rewardLine(items));
                }
                TitanLoot.tellHeld(p, held);
            }
        }
        double rate = Parties.shareRate(server);
        for (ServerPlayer mate : Parties.absentMates(level, present)) {
            Growth.addXp(mate, (int) Math.round(KEEPER_XP * rate * GrowthRules.levelFalloff(Growth.get(mate).level, TideLoot.TIDE_LEVEL)), "growth.skyabyss.reason.party");
        }
        broadcast(level, group, ModText.lore("message.skyabyss.tide.keeper_vista_open"));
        SkyAbyss.LOGGER.info("The pearl keeper was stilled by {} participant(s)", ids.size());
    }

    static void broadcast(ServerLevel level, @Nullable UUID group, Component message) {
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, chamberBox())) {
            if (group == null || Groups.isMember(p, group)) {
                Notice.send(p, message);
            }
        }
    }

    // ---------------------------------------------------------------- 展望の間: 追憶の石 (人ごとに一度きり。備えの品と素材だけ)
    static void keepsake(ServerPlayer player) {
        List<ItemStack> items = List.of(new ItemStack(net.minecraft.world.item.Items.NAUTILUS_SHELL, 2),
                new ItemStack(jp.revati.skyabyss.registry.ModItems.RESONANCE_SHARD_TIDE_SHELL.get(), 1));
        if (FirstRun.grantKeepsake(player, KEEPSAKE_ID, items)) {
            Notice.unlock(player, "message.skyabyss.tide.keepsake_given");
            ((ServerLevel) player.level()).playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4f, 1.2f);
        } else {
            Notice.bar(player, ModText.hint("message.skyabyss.tide.keepsake_taken"));
        }
    }

    // 自己テスト用
    static Set<UUID> participants() {
        return PARTICIPANTS;
    }

    static List<ServerPlayer> everyone(ServerLevel level) {
        return Players.snapshot(level.players());
    }
}
