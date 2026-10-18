package jp.revati.skyabyss.titan.tide.back;

import jp.revati.skyabyss.notice.Notice;
import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.party.Parties;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.tide.heart.TideLoot;
import jp.revati.skyabyss.titan.tide.mob.BubblePuffer;
import jp.revati.skyabyss.titan.tide.mob.ReefScuttler;
import jp.revati.skyabyss.titan.tide.mob.TideMob;
import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 前の試練 群れの干潟 (08 の 4.3節) と中ボス潮招き (5.2節)。
// 巣: 6 か所の候補からリセットの種で 3 つ。干潮で口を開けて 5 秒ごとに磯走りを 2 体 (一つの巣から生きているのは 3 体、人が一人増えるごとに +1)。
//     満潮では殻を閉じて傷が通らない。体力は 60 × (1 + 0.6 × (干潟にいる人 - 1))。
// 満潮のあいだは、泡吹きが水面の上に 2 体。
// 潮招き: 最後の巣が壊れると 5 秒の集合 → 干潟の口が閉じる (干潟とトンネルと前室の人が参加者) → 干潮なら現れる (満潮なら引くまで待つ)。
//   参加者が干潟にいなくなって 3 秒たつと砂に潜って消え、口が開く (壊した巣はそのまま。次は潮招きからやり直す)
// 干潟は占有する部屋 (TideRooms.FLATS)。巣・招き潮の水・潮招き・群れは、いまの持ち主のグループ (パーティ、または一人) のもの。
// 人数・参加者・狙う相手・報酬は持ち主のグループの人だけで数える。干潟の口と干潟の門は、戦っているグループにだけ閉じる
public final class TideFlats {
    public static final String NEST_TAG = "skyabyss_flats_nest_";
    public static final String FLATS_TAG = "skyabyss_flats_mob";
    public static final int GATHER_TICKS = 100;
    public static final int WIPE_DELAY = 60;
    public static final int NEST_INTERVAL = 100;
    public static final int NEST_BURST = 2;

    enum Fight {
        NONE, GATHER, WAITING, ACTIVE
    }

    private static final UUID[] NESTS = new UUID[6];
    private static final int[] NEST_TIMER = new int[6];
    private static Fight fight = Fight.NONE;
    private static int fightTimer;
    private static int empty;
    @Nullable
    private static UUID bossId;
    // 口と門を閉じているグループ (戦いの間)
    @Nullable
    private static UUID sealedFor;
    private static final Set<UUID> participants = new LinkedHashSet<>();
    private static int beckonTicks;
    private static int beckonWarn;
    private static TideClock.Phase lastPhase;

    private TideFlats() {
    }

    // ---------------------------------------------------------------- 外から見る状態
    public static boolean nestsOpen(ServerLevel level) {
        return TideClock.phase(level) == TideClock.Phase.LOW;
    }

    public static boolean beckoning() {
        return beckonTicks > 0;
    }

    // 招き潮の予告か、満ちている間 (潮見の目盛りを赤くする。持ち主のグループの人にだけ)
    public static boolean beckonAlert() {
        return beckonTicks > 0 || beckonWarn > 0;
    }

    // 干潟の溜めの、招き潮による目標の段 (島の潮より高ければこちら)
    public static int beckonLevel() {
        return beckonTicks > 0 ? FLATS_DEPTH : 0;
    }

    public static boolean flatsFlooded(ServerLevel level) {
        TideField.Pool p = TideBackTrials.FIELD.pool("flats");
        return p != null && p.level() >= 1;
    }

    public static boolean fightSealed() {
        return fight == Fight.WAITING || fight == Fight.ACTIVE || fight == Fight.GATHER && fightTimer <= 0;
    }

    public static boolean fightActive() {
        return fight != Fight.NONE;
    }

    @Nullable
    public static TideBeckoner boss(ServerLevel level) {
        return bossId != null && level.getEntity(bossId) instanceof TideBeckoner b ? b : null;
    }

    public static Set<UUID> participants() {
        return participants;
    }

    @Nullable
    static UUID owner() {
        return TideRooms.owner(TideRooms.FLATS);
    }

    static AABB flatsBox() {
        return FLATS.aabb().inflate(0, 6, 0);
    }

    // 干潟にいる、持ち主のグループの生きている人
    static List<ServerPlayer> inFlats(ServerLevel level) {
        UUID owner = owner();
        if (owner == null) {
            return List.of();
        }
        return Groups.inBox(level, owner, flatsBox());
    }

    // 潮招きが狙える人 (参加者で、干潟にいて生きている人。参加者がいなければ干潟にいる人。どちらも持ち主のグループだけ)
    public static List<ServerPlayer> fighters(ServerLevel level) {
        List<ServerPlayer> all = inFlats(level);
        if (participants.isEmpty()) {
            return all;
        }
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : all) {
            if (participants.contains(p.getUUID()) && !p.isCreative()) {
                list.add(p);
            }
        }
        return list;
    }

    // 人数 (1〜6 = パーティの上限)
    static int headcount(ServerLevel level) {
        return Math.max(1, Math.min(Parties.MAX_MEMBERS, inFlats(level).size()));
    }

    // ---------------------------------------------------------------- 毎 tick (TideBackTrials から。人が背中にいるときだけ)
    static void tick(ServerLevel level, boolean peaceful) {
        if (beckonTicks > 0) {
            beckonTicks--;
        }
        if (beckonWarn > 0) {
            beckonWarn--;
        }
        UUID owner = owner();
        if (owner == null) {
            return;
        }
        TideBackState.Group state = TideBackState.of(level, owner);
        TideClock.Phase phase = TideClock.phase(level);
        if (phase != lastPhase) {
            onPhase(level, state, phase);
            lastPhase = phase;
        }
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        if (peaceful) {
            if (fight != Fight.NONE) {
                resetFight(level, false);
            }
            return;
        }
        if (!state.rung(TideBackState.Trial.FRONT)) {
            tickNests(level, state);
        }
        tickFight(level, state);
    }

    private static void onPhase(ServerLevel level, TideBackState.Group state, TideClock.Phase phase) {
        // 巣の口 (干潮で開き、満ちはじめると閉じる)
        boolean open = phase == TideClock.Phase.LOW;
        for (int site = 0; site < 6; site++) {
            if ((state.nestSites() & (1 << site)) != 0 && !state.nestBroken(site) && level.isLoaded(nestPos(site))) {
                setNestMouth(level, site, open);
            }
        }
        // 満潮で泡吹き、干潮で沈む
        if (phase == TideClock.Phase.HIGH && !inFlats(level).isEmpty() && !state.rung(TideBackState.Trial.FRONT)) {
            for (int i = 0; i < 2; i++) {
                int x = -16 + level.random.nextInt(33);
                int z = -100 + FLATS_DZ + level.random.nextInt(26);
                Mob puffer = spawn(level, TideMobSetup.BUBBLE_PUFFER.get(), new BlockPos(x, FLATS_FLOOR + 4, z), state.id);
                if (puffer != null) {
                    puffer.addTag(FLATS_TAG);
                }
            }
        }
        if (phase == TideClock.Phase.LOW) {
            for (Entity e : level.getEntitiesOfClass(BubblePuffer.class, flatsBox().inflate(4), e -> e.getTags().contains(FLATS_TAG))) {
                level.sendParticles(ParticleTypes.BUBBLE_POP, e.getX(), e.getY(), e.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
                e.discard();
            }
        }
    }

    // ---------------------------------------------------------------- 巣
    public static BlockPos nestPos(int site) {
        int[] s = NEST_SITES[site];
        return new BlockPos(s[0], FLATS_FLOOR + 1, s[1]);
    }

    // 塚のブロックを置く (3x3、高さ 2。口は真ん中の上)
    static void buildNest(ServerLevel level, int site, boolean broken) {
        BlockPos c = nestPos(site);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = 0; y <= 2; y++) {
                    level.setBlock(c.offset(dx, y, dz), Blocks.AIR.defaultBlockState(), TideKit.FLAGS);
                }
            }
        }
        if (broken) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx + dz) % 2 == 0) {
                        level.setBlock(c.offset(dx, 0, dz), Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState(), TideKit.FLAGS);
                    }
                }
            }
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean center = dx == 0 && dz == 0;
                level.setBlock(c.offset(dx, 0, dz), (dx + dz) % 2 == 0 ? Blocks.RED_TERRACOTTA.defaultBlockState() : Blocks.PINK_TERRACOTTA.defaultBlockState(), TideKit.FLAGS);
                if (!center) {
                    level.setBlock(c.offset(dx, 1, dz), Math.abs(dx) + Math.abs(dz) == 2 ? Blocks.BROWN_TERRACOTTA.defaultBlockState() : Blocks.RED_TERRACOTTA.defaultBlockState(), TideKit.FLAGS);
                }
            }
        }
        // 外側の小さな突起 (珊瑚の棘)
        for (int[] d : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
            level.setBlock(c.offset(d[0], 0, d[1]), Blocks.DEAD_FIRE_CORAL_BLOCK.defaultBlockState(), TideKit.FLAGS);
        }
        setNestMouth(level, site, nestsOpen(level));
    }

    static void setNestMouth(ServerLevel level, int site, boolean open) {
        BlockPos mouth = nestPos(site).above();
        BlockState now = level.getBlockState(mouth);
        BlockState want = open ? Blocks.OCHRE_FROGLIGHT.defaultBlockState() : Blocks.MUD_BRICKS.defaultBlockState();
        if (!now.is(want.getBlock())) {
            level.setBlock(mouth, want, TideKit.FLAGS);
            level.playSound(null, mouth, open ? TideBackSetup.NEST_OPEN.get() : TideBackSetup.NEST_CLOSE.get(), SoundSource.HOSTILE, 1.2f, 1f);
        }
    }

    private static void tickNests(ServerLevel level, TideBackState.Group state) {
        boolean open = nestsOpen(level);
        int people = headcount(level);
        for (int site = 0; site < 6; site++) {
            if ((state.nestSites() & (1 << site)) == 0 || state.nestBroken(site)) {
                continue;
            }
            ReefNest nest = NESTS[site] != null && level.getEntity(NESTS[site]) instanceof ReefNest n && n.isAlive() ? n : null;
            if (nest == null) {
                nest = TideBackSetup.REEF_NEST.get().create(level);
                if (nest == null) {
                    continue;
                }
                BlockPos c = nestPos(site);
                nest.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0, 0);
                nest.site = site;
                float hp = ReefNest.BASE_HEALTH * (1 + 0.6f * (people - 1));
                nest.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
                nest.setHealth(hp);
                nest.addTag(TideMob.TRIAL_TAG);
                nest.setGroupId(state.id);
                level.addFreshEntity(nest);
                NESTS[site] = nest.getUUID();
                buildNest(level, site, false);
            }
            // 巣の強さは、干潟に入ってきた人数に合わせて上げる (下げない)。前は巣ができたとき (人がまだ前室にいるうち) に数え、
            // 二人で入っても一人の強さのままだった。削った割合は変えない
            float want = ReefNest.BASE_HEALTH * (1 + 0.6f * (people - 1));
            float max = (float) nest.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
            if (want > max + 0.5f) {
                float frac = nest.getHealth() / max;
                nest.getAttribute(Attributes.MAX_HEALTH).setBaseValue(want);
                nest.setHealth(want * frac);
            }
            if (!open) {
                NEST_TIMER[site] = 40;
                continue;
            }
            if (level.getGameTime() % 20 == 0) {
                level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, nest.getX(), nest.getY() + 2.2, nest.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
            }
            NEST_TIMER[site] -= 10;
            if (NEST_TIMER[site] > 0 || inFlats(level).stream().allMatch(p -> p.isCreative())) {
                continue;
            }
            NEST_TIMER[site] = NEST_INTERVAL;
            // 一つの巣から生きているのは 3 体 (人が一人増えるごとに +1)。干潟全体でも 8 体 (+2) まで (試遊で 3 つの巣から 20 体になり、一人では巣に届かなかった)
            int cap = 3 + (people - 1);
            int all = 8 + 2 * (people - 1);
            String tag = NEST_TAG + site;
            int alive = level.getEntitiesOfClass(ReefScuttler.class, flatsBox().inflate(10), e -> e.getTags().contains(tag) && owned(e, state.id)).size();
            int total = level.getEntitiesOfClass(ReefScuttler.class, flatsBox().inflate(10), e -> e.getTags().contains(FLATS_TAG) && owned(e, state.id)).size();
            for (int i = 0; i < NEST_BURST && alive < cap && total < all; i++, alive++, total++) {
                Mob m = spawn(level, TideMobSetup.REEF_SCUTTLER.get(), nestPos(site).offset(level.random.nextInt(3) - 1, 2, level.random.nextInt(3) - 1),
                        state.id);
                if (m != null) {
                    m.addTag(tag);
                    m.addTag(FLATS_TAG);
                }
            }
        }
    }

    // そのグループの印を持つ個体か
    static boolean owned(Entity e, UUID group) {
        return e instanceof GroupOwned o && group.equals(o.groupId());
    }

    // 試練の敵を湧かせる。group はその敵を持つグループ (ほかのグループには見えず、狙わず、傷を与え合わない)。addFreshEntity の前に入れる
    @Nullable
    static Mob spawn(ServerLevel level, net.minecraft.world.entity.EntityType<? extends Mob> type, BlockPos at, @Nullable UUID group) {
        Mob mob = type.create(level);
        if (mob == null) {
            return null;
        }
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
        mob.addTag(TideMob.TRIAL_TAG);
        if (mob instanceof GroupOwned owned) {
            owned.setGroupId(group);
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null);
        level.addFreshEntity(mob);
        return mob;
    }

    // 巣が壊れた (ReefNest.die から)。進みは巣の持ち主のグループに書く
    static void onNestBroken(ServerLevel level, ReefNest nest) {
        UUID group = nest.groupId();
        if (group == null) {
            return;
        }
        TideBackState.Group state = TideBackState.of(level, group);
        int site = nest.site();
        if (site < 0 || state.nestBroken(site)) {
            return;
        }
        state.breakNest(site);
        NESTS[site] = null;
        buildNest(level, site, true);
        level.playSound(null, nest.blockPosition(), TideBackSetup.NEST_BREAK.get(), SoundSource.HOSTILE, 2f, 1f);
        level.sendParticles(ParticleTypes.BUBBLE_POP, nest.getX(), nest.getY() + 1, nest.getZ(), 60, 1.2, 0.8, 1.2, 0.1);
        // 落とす物 (08 の 6.1節): 巣を壊したとき干潟にいた、そのグループの人それぞれ。潮殻の欠片 50% で 2〜3、共鳴の欠片 10%
        for (ServerPlayer p : Groups.inBox(level, group, flatsBox())) {
            List<ItemStack> items = new ArrayList<>();
            RandomSource r = level.random;
            if (r.nextFloat() < 0.5f) {
                items.add(new ItemStack(TideMobSetup.TIDE_SHELL_SHARD.get(), 2 + r.nextInt(2)));
            }
            if (r.nextFloat() < 0.10f) {
                item(TideLoot.RESONANCE_SHARD).ifPresent(i -> items.add(new ItemStack(i)));
            }
            TitanLoot.handOver(level.getServer(), p.getUUID(), p, items, 0);
        }
        int left = 0;
        for (int s = 0; s < 6; s++) {
            if ((state.nestSites() & (1 << s)) != 0 && !state.nestBroken(s)) {
                left++;
            }
        }
        broadcast(level, group, left > 0 ? ModText.progress("message.skyabyss.tide.nest_broken", 3 - left, 3)
                : ModText.lore("message.skyabyss.tide.nests_all_broken"));
        if (left == 0 && group.equals(owner())) {
            beginGather(level);
        }
    }

    private static java.util.Optional<Item> item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? java.util.Optional.empty() : BuiltInRegistries.ITEM.getOptional(rl);
    }

    // ---------------------------------------------------------------- 潮招きの戦い (持ち主のグループだけ)
    static void beginGather(ServerLevel level) {
        if (fight != Fight.NONE || owner() == null) {
            return;
        }
        fight = Fight.GATHER;
        fightTimer = GATHER_TICKS;
        participants.clear();
        broadcast(level, owner(), ModText.battle("message.skyabyss.tide.beckoner_gathering", GATHER_TICKS / 20));
    }

    private static void tickFight(ServerLevel level, TideBackState.Group state) {
        if (state.beckonerDefeated() || state.rung(TideBackState.Trial.FRONT)) {
            if (fight != Fight.NONE) {
                resetFight(level, false);
            }
            return;
        }
        UUID owner = state.id;
        switch (fight) {
            case NONE -> {
                // 巣を壊したあと、干潟へ入り直したら集合からやり直す
                if (state.allNestsBroken() && !inFlats(level).isEmpty()) {
                    beginGather(level);
                }
            }
            case GATHER -> {
                if (inFlats(level).isEmpty() && Groups.inBox(level, owner, approach()).isEmpty()) {
                    fight = Fight.NONE;
                    return;
                }
                fightTimer -= 10;
                if (fightTimer > 0) {
                    if (fightTimer % 20 == 0) {
                        Component text = ModText.battle("message.skyabyss.gate_countdown", fightTimer / 20);
                        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, FLATS.aabb().inflate(6, 6, 50), p -> Groups.isMember(p, owner))) {
                            Notice.bar(p, text);
                        }
                    }
                    return;
                }
                // 口を閉じ、トンネルと前室の (持ち主のグループの) 人を干潟へ入れる。閉じたときにいた人が参加者
                for (ServerPlayer p : Groups.inBox(level, owner, approach())) {
                    pullIn(p);
                }
                sealMouth(level, owner, true);
                participants.clear();
                for (ServerPlayer p : inFlats(level)) {
                    participants.add(p.getUUID());
                }
                fight = Fight.WAITING;
                empty = 0;
                if (!nestsOpen(level)) {
                    broadcast(level, owner, ModText.lore("message.skyabyss.tide.beckoner_waits"));
                }
            }
            case WAITING -> {
                if (wiped(level)) {
                    return;
                }
                if (TideClock.phase(level) == TideClock.Phase.LOW) {
                    spawnBoss(level, owner);
                }
            }
            case ACTIVE -> {
                TideBeckoner boss = boss(level);
                if (boss == null || !boss.isAlive()) {
                    // 倒されずに消えた (コマンドなど): 戦いを戻す
                    if (!state.beckonerDefeated()) {
                        resetFight(level, true);
                    }
                    return;
                }
                if (!boss.arena().contains(boss.position())) {
                    boss.moveTo(BECKONER_HOME.getX() + 0.5, BECKONER_HOME.getY(), BECKONER_HOME.getZ() + 0.5);
                }
                if (wiped(level)) {
                    return;
                }
                for (ServerPlayer p : inFlats(level)) {
                    participants.add(p.getUUID());
                }
                letBack(level);
            }
        }
    }

    // トンネル (干潟の口の手前) と前室
    static AABB approach() {
        return new AABB(-5, TUNNEL_FLOOR - 1, MOUTH_Z, 6, TUNNEL_FLOOR + 6, F0.z2() + 1);
    }

    private static void pullIn(ServerPlayer p) {
        p.fallDistance = 0;
        p.teleportTo(p.serverLevel(), 0.5, FLATS_FLOOR + 1, MOUTH_Z - 4 + 0.5, 180f, 0f);
    }

    // 参加者が干潟にいなくなって 3 秒たつと、潮招きは砂に潜って消える
    private static boolean wiped(ServerLevel level) {
        if (!fighters(level).isEmpty()) {
            empty = 0;
            return false;
        }
        empty += 10;
        if (empty >= WIPE_DELAY) {
            resetFight(level, true);
            return true;
        }
        return false;
    }

    // 戦いの途中で倒れて前室から戻った参加者は、口に触れると干潟へ戻れる
    private static void letBack(ServerLevel level) {
        AABB door = new AABB(-1, TUNNEL_FLOOR + 1, MOUTH_Z + 1, 2, TUNNEL_FLOOR + 4, MOUTH_Z + 3);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, door, p -> p.isAlive() && participants.contains(p.getUUID()))) {
            pullIn(p);
            Notice.bar(p, ModText.battle("message.skyabyss.gate_rejoin"));
        }
    }

    private static void spawnBoss(ServerLevel level, UUID owner) {
        TideBeckoner boss = TideBackSetup.TIDE_BECKONER.get().create(level);
        if (boss == null) {
            return;
        }
        boss.addTag(TideMob.TRIAL_TAG);
        boss.setGroupId(owner);
        // 挑む人数 (1〜6 = パーティの上限)
        boss.setChallengers(Math.max(1, Math.min(Parties.MAX_MEMBERS, participants.size())));
        Vec3 home = Vec3.atBottomCenterOf(BECKONER_HOME);
        boss.moveTo(home.x, home.y, home.z, 180f, 0);
        level.addFreshEntity(boss);
        boss.emergeAt(level, home);
        bossId = boss.getUUID();
        fight = Fight.ACTIVE;
        empty = 0;
        broadcast(level, owner, ModText.battle("message.skyabyss.tide.beckoner_begin"));
        SkyAbyss.LOGGER.info("The tide beckoner rose with {} participant(s)", participants.size());
    }

    // 戦いを戻す (全滅・放棄・ピースフル・部屋を手放す)。壊した巣はそのまま
    public static void resetFight(ServerLevel level, boolean announce) {
        TideBeckoner boss = boss(level);
        if (boss != null) {
            level.sendParticles(ParticleTypes.BUBBLE_POP, boss.getX(), boss.getY() + 1, boss.getZ(), 40, 2, 1, 2, 0.05);
            boss.discard();
        }
        bossId = null;
        boolean was = fight == Fight.WAITING || fight == Fight.ACTIVE;
        fight = Fight.NONE;
        participants.clear();
        empty = 0;
        beckonTicks = 0;
        beckonWarn = 0;
        UUID group = sealedFor != null ? sealedFor : owner();
        sealMouth(level, group, false);
        if (announce && was && group != null) {
            broadcast(level, group, ModText.deny("message.skyabyss.tide.beckoner_sank"));
        }
    }

    // 干潟の口と、広間の干潟の門を、そのグループにだけ閉じる / やめる (門は潮の既定に戻る)
    static void sealMouth(ServerLevel level, @Nullable UUID group, boolean seal) {
        UUID who;
        if (seal && group != null) {
            who = group;
            GroupGates.seal(level, who, TideGates.mouth());
            GroupGates.seal(level, who, TideGates.flats());
            sealedFor = who;
        } else if (!seal && sealedFor != null) {
            who = sealedFor;
            GroupGates.release(level, who, TideGates.mouth());
            GroupGates.release(level, who, TideGates.flats());
            sealedFor = null;
        } else {
            return;
        }
        for (ServerPlayer p : Groups.online(level, who)) {
            if (p.distanceToSqr(0.5, TUNNEL_FLOOR + 2, MOUTH_Z) < 48 * 48) {
                p.playNotifySound(seal ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2f, 0.5f);
            }
        }
    }

    // 招き潮の予告 (目盛りを赤く)
    static void warnBeckon(ServerLevel level, int ticks) {
        beckonWarn = ticks;
        TideBackTrials.sendGaugeNow(level);
    }

    static void beckon(ServerLevel level, int ticks) {
        beckonTicks = ticks;
        beckonWarn = 0;
        TideBackTrials.sendGaugeNow(level);
    }

    // 怒りの咆哮: 巣のあった所から磯走りを 4 体 (潮招きのグループのもの)
    static void roarSpawn(ServerLevel level, TideBeckoner boss) {
        UUID group = boss.groupId();
        if (group == null) {
            return;
        }
        TideBackState.Group state = TideBackState.of(level, group);
        List<Integer> sites = new ArrayList<>();
        for (int s = 0; s < 6; s++) {
            if ((state.nestSites() & (1 << s)) != 0) {
                sites.add(s);
            }
        }
        for (int i = 0; i < 4 && !sites.isEmpty(); i++) {
            int s = sites.get(i % sites.size());
            Mob m = spawn(level, TideMobSetup.REEF_SCUTTLER.get(), nestPos(s).above(), group);
            if (m != null) {
                m.addTag(FLATS_TAG);
            }
        }
    }

    // ---------------------------------------------------------------- 潮招きを倒した (08 の 6.2節)
    public static final int BECKONER_XP = 130;
    public static final int BECKONER_FIRST_BONUS = 60;
    public static final String CLAW = "skyabyss:tidecaller_claw";
    public static final String SIGNAL = "tide_beckoner_stilled";

    static void onBeckonerDefeated(ServerLevel level, TideBeckoner boss) {
        if (!boss.getUUID().equals(bossId) || boss.groupId() == null) {
            return;
        }
        UUID group = boss.groupId();
        TideBackState.Group state = TideBackState.of(level, group);
        state.markBeckonerDefeated();
        // 参加者 = 戦いの参加者 + 倒れた瞬間に干潟にいた、そのグループの人
        Set<UUID> ids = new LinkedHashSet<>(participants);
        for (ServerPlayer p : Groups.inBox(level, group, flatsBox())) {
            ids.add(p.getUUID());
        }
        bossId = null;
        fight = Fight.NONE;
        participants.clear();
        beckonTicks = 0;
        sealMouth(level, group, false);
        MinecraftServer server = level.getServer();
        RandomSource r = level.random;
        for (UUID id : ids) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            List<ItemStack> items = new ArrayList<>();
            if (r.nextFloat() < 0.60f) {
                items.add(new ItemStack(TideMobSetup.TIDE_SHELL_SHARD.get(), 2 + r.nextInt(3)));
            }
            if (r.nextFloat() < 0.35f) {
                item(TideLoot.RESONANCE_SHARD).ifPresent(i -> items.add(new ItemStack(i, 1 + r.nextInt(2))));
            }
            if (r.nextFloat() < 0.20f) {
                items.add(new ItemStack(TideMobSetup.TIDE_DRIED_SHELL.get(), 1 + r.nextInt(2)));
            }
            // 潮招きの大鋏 5% (特性を付け、図鑑に記す。titan/tide/gear)
            items.addAll(jp.revati.skyabyss.titan.tide.gear.TideGearLoot.rollBeckoner(server, id, r));
            int held = TitanLoot.handOver(server, id, p, items, 40);
            if (p != null) {
                int amount = (int) Math.round(BECKONER_XP * GrowthRules.levelFalloff(Growth.get(p).level, TideLoot.TIDE_LEVEL));
                if (Growth.discover(p, "first:" + SkyAbyss.MODID + ":tide_beckoner")) {
                    amount += BECKONER_FIRST_BONUS;
                }
                Growth.addXp(p, amount, "entity.skyabyss.tide_beckoner");
                jp.revati.skyabyss.quest.Quests.signal(p, SIGNAL);
                Notice.unlock(p, "message.skyabyss.tide.beckoner_defeated");
                if (!items.isEmpty()) {
                    Notice.send(p, TitanLoot.rewardLine(items));
                }
                TitanLoot.tellHeld(p, held);
            }
        }
        // パーティの仲間で居合わせなかった人には、分け前の割合
        List<ServerPlayer> present = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                present.add(p);
            }
        }
        double rate = Parties.shareRate(server);
        for (ServerPlayer mate : Parties.absentMates(level, present)) {
            Growth.addXp(mate, (int) Math.round(BECKONER_XP * rate * GrowthRules.levelFalloff(Growth.get(mate).level, TideLoot.TIDE_LEVEL)),
                    "growth.skyabyss.reason.party");
        }
        broadcast(level, group, ModText.lore("message.skyabyss.tide.front_stone_ready"));
        SkyAbyss.LOGGER.info("The tide beckoner was defeated by {} participant(s)", ids.size());
    }

    // 干潟の近くにいる、そのグループの人へ
    static void broadcast(ServerLevel level, @Nullable UUID group, Component message) {
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, FLATS.aabb().inflate(10, 10, 50))) {
            if (group == null || Groups.isMember(p, group)) {
                Notice.send(p, message);
            }
        }
    }

    // ---------------------------------------------------------------- 部屋を作り直す (新しい持ち主のグループの種と進みから。null は最初の形)
    static void reset(ServerLevel level, @Nullable TideBackState.Group state) {
        resetFight(level, false);
        int sites = state == null ? 0b000111 : state.nestSites();
        boolean frontDone = state != null && state.rung(TideBackState.Trial.FRONT);
        for (int site = 0; site < 6; site++) {
            if (NESTS[site] != null && level.getEntity(NESTS[site]) instanceof ReefNest n) {
                n.discard();
            }
            NESTS[site] = null;
            NEST_TIMER[site] = 40;
            boolean chosen = (sites & (1 << site)) != 0;
            if (chosen) {
                // 壊した巣と、石が鳴った (眠っている) 干潟の巣は、崩れた形
                buildNest(level, site, frontDone || state != null && state.nestBroken(site));
            } else {
                // 選ばれなかった候補地は、ただの砂に戻す
                BlockPos c = nestPos(site);
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int y = 0; y <= 2; y++) {
                            level.setBlock(c.offset(dx, y, dz), Blocks.AIR.defaultBlockState(), TideKit.FLAGS);
                        }
                    }
                }
            }
        }
        for (Entity e : level.getEntitiesOfClass(Entity.class, flatsBox().inflate(10), e -> e.getTags().contains(FLATS_TAG) || e instanceof ReefNest)) {
            e.discard();
        }
        lastPhase = null;
    }

    // 部屋を手放した: 戦いを戻し、巣と群れを消す (次の持ち主が入ったときに、その種で作り直す)
    static void release(ServerLevel level, UUID formerOwner) {
        resetFight(level, false);
        for (int site = 0; site < 6; site++) {
            if (NESTS[site] != null && level.getEntity(NESTS[site]) instanceof ReefNest n) {
                n.discard();
            }
            NESTS[site] = null;
        }
        for (Entity e : level.getEntitiesOfClass(Entity.class, flatsBox().inflate(10), e -> e.getTags().contains(FLATS_TAG) || e instanceof ReefNest)) {
            e.discard();
        }
        beckonTicks = 0;
        beckonWarn = 0;
        lastPhase = null;
    }

    static void clear() {
        java.util.Arrays.fill(NESTS, null);
        java.util.Arrays.fill(NEST_TIMER, 0);
        fight = Fight.NONE;
        bossId = null;
        sealedFor = null;
        participants.clear();
        empty = 0;
        beckonTicks = 0;
        beckonWarn = 0;
        lastPhase = null;
    }
}
