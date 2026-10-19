package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.group.RoomLocks;
import jp.revati.skyabyss.titan.storm.StormSetup;
import jp.revati.skyabyss.titan.storm.back.StormBackState.Trial;
import jp.revati.skyabyss.titan.storm.mob.StaticMoth;
import jp.revati.skyabyss.titan.storm.mob.StormMobSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// 拍の舵 (25 の 5章 C): 三つの試練の入口 (肩の門 w0・e0・前室 f0) にある風見の舵。凪の舵・嵐の舵のどちらかを回すと、
// その試練の中だけ、60 秒、拍を凪か嵐に固定できる (回した人のグループの分だけ)。もう一度同じ舵を回すと放す。
//  - 試練に行ける・行けないは変えない。変えるのは遊び方 (道の形・危険・速さ)。どちらの拍でも試練は終えられる (25 の 1章)
//  - 西: 嵐 = 昇り風が 13 (肋の間の登りの近道と滑空の輪)・横風 / 凪 = 昇り風が 4 (一段ずつ)・横風なし
//  - 東: 嵐 = 輪と雷が速く、放電の線が走る / 凪 = 遅く、線なし
//  - 前: 嵐 = 雷蛾が一匹、眼の間を舞って光をさえぎる (障害) / 凪 = 来ない
// 固定は StormBackClock の全体の拍とは別のグループごとの記録 (メモリだけ。リセット・背中の片付けで消える)。
// 見かけ (舵の光)・目盛り (風見の板の「舵」の表示)・効果は effectivePhase を通る
final class StormHelm {
    static final int LOCK_TICKS = 60 * 20;
    private static final String MOTH_TAG = "skyabyss_helm_moth";

    // 入口ごとの舵 (凪・嵐の二つ)。floor の上に置く
    record Helm(Trial trial, BlockPos calm, BlockPos storm) {
    }

    static final List<Helm> HELMS = List.of(
            new Helm(Trial.WEST, new BlockPos(-62, BONE_FLOOR + 1, BONE_Z1 + 2), new BlockPos(-58, BONE_FLOOR + 1, BONE_Z1 + 2)),
            new Helm(Trial.EAST, new BlockPos(58, BONE_FLOOR + 1, BONE_Z1 + 2), new BlockPos(62, BONE_FLOOR + 1, BONE_Z1 + 2)),
            new Helm(Trial.FRONT, new BlockPos(6, UPPER_GALLERY_Y + 1, F0.z1() + 2), new BlockPos(9, UPPER_GALLERY_Y + 1, F0.z1() + 2)));

    // 固定。グループ + 試練 → (拍、始まり、終わり)
    record Lock(StormBackClock.Phase phase, long start, long end) {
    }

    private static final Map<String, Lock> LOCKS = new HashMap<>();
    private static final Map<UUID, Long> HINT_AT = new HashMap<>();

    static {
        GroupOverlays.register(StormHelm::overlay);
    }

    private StormHelm() {
    }

    private static String key(UUID group, Trial t) {
        return group + "/" + t.id();
    }

    static void clear() {
        LOCKS.clear();
        HINT_AT.clear();
    }

    // ---------------------------------------------------------------- 効く拍
    // その試練・そのグループに効く拍 (舵で固定していればその拍、なければ背中の拍)
    static StormBackClock.Phase phase(ServerLevel level, Trial trial, UUID group) {
        Lock l = lockOf(level, trial, group);
        return l != null ? l.phase() : StormBackClock.phase(level);
    }

    @Nullable
    static Lock lockOf(ServerLevel level, @Nullable Trial trial, @Nullable UUID group) {
        if (trial == null || group == null) {
            return null;
        }
        Lock l = LOCKS.get(key(group, trial));
        return l != null && level.getGameTime() < l.end() ? l : null;
    }

    // 位置から試練 (広間・竜骨の間は null)
    @Nullable
    static Trial trialAt(Vec3 p) {
        if (StormBackLayout.inWest(p)) {
            return Trial.WEST;
        }
        if (StormBackLayout.inEast(p)) {
            return Trial.EAST;
        }
        if (StormBackLayout.inFront(p)) {
            return Trial.FRONT;
        }
        return null;
    }

    // 昇り風の上がる高さ (その人に効く拍で。荒れる・静まるの間は全体の拍の途中の高さ)
    static double liftHeight(ServerLevel level, ServerPlayer p) {
        Lock l = lockOf(level, trialAt(p.position()), Groups.keyOf(p));
        if (l == null) {
            return StormBackClock.liftHeight(level);
        }
        return l.phase() == StormBackClock.Phase.STORM ? StormBackClock.STORM_LIFT : StormBackClock.CALM_LIFT;
    }

    // 嵐の強さ (風見の板の揺れ)。固定していれば 1 か 0
    static double strength(ServerLevel level, ServerPlayer p) {
        Lock l = lockOf(level, trialAt(p.position()), Groups.keyOf(p));
        if (l == null) {
            return StormBackClock.strength(level);
        }
        return l.phase() == StormBackClock.Phase.STORM ? 1 : 0;
    }

    // ---------------------------------------------------------------- 舵を回す
    static boolean isHelm(BlockPos pos) {
        return helmAt(pos) != null;
    }

    @Nullable
    static Helm helmAt(BlockPos pos) {
        for (Helm h : HELMS) {
            if (h.calm().equals(pos) || h.storm().equals(pos)) {
                return h;
            }
        }
        return null;
    }

    static void use(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Helm h = helmAt(pos);
        if (h == null) {
            return;
        }
        set(level, Groups.keyOf(player), h.trial(), h.storm().equals(pos) ? StormBackClock.Phase.STORM : StormBackClock.Phase.CALM, player);
    }

    // 固定する (同じ拍の固定が残っていれば放す)。by は知らせを出す人 (なければ null)
    static void set(ServerLevel level, UUID group, Trial trial, StormBackClock.Phase phase, @Nullable ServerPlayer by) {
        Lock cur = lockOf(level, trial, group);
        long now = level.getGameTime();
        boolean release = cur != null && cur.phase() == phase;
        if (release) {
            LOCKS.remove(key(group, trial));
        } else {
            LOCKS.put(key(group, trial), new Lock(phase, now, now + LOCK_TICKS));
        }
        Helm h = HELMS.get(trial.ordinal());
        BlockPos at = phase == StormBackClock.Phase.STORM ? h.storm() : h.calm();
        level.playSound(null, at, StormSetup.GALE.get(), SoundSource.BLOCKS, 1.2f, phase == StormBackClock.Phase.STORM ? 0.7f : 1.5f);
        level.playSound(null, at, SoundEvents.COPPER_HIT, SoundSource.BLOCKS, 1.4f, 1.0f);
        StormFx.to(group, () -> StormFx.burst(level, phase == StormBackClock.Phase.STORM ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.CLOUD,
                Vec3.atCenterOf(at).add(0, 0.8, 0), 18, 0.4));
        String key = release ? "message.skyabyss.storm.helm_release" : phase == StormBackClock.Phase.STORM ? "message.skyabyss.storm.helm_storm" : "message.skyabyss.storm.helm_calm";
        Component msg = release ? ModText.hint(key, Component.translatable("trial.skyabyss.storm_" + trial.id()))
                : ModText.progress(key, Component.translatable("trial.skyabyss.storm_" + trial.id()), LOCK_TICKS / 20);
        for (ServerPlayer p : Groups.online(level, group)) {
            if (p == by || trialAt(p.position()) == trial) {
                Notice.bar(p, msg);
            }
        }
        GroupOverlays.refresh(level, group);
        StormBackTrials.sendGaugeNow(level);
    }

    // 見かけ: 固定している舵が光る (そのグループにだけ)
    static void overlay(ServerLevel level, UUID group, BiConsumer<BlockPos, BlockState> out) {
        if (level.dimension() != jp.revati.skyabyss.titan.storm.StormTravel.STORM_BACK) {
            return;
        }
        for (Helm h : HELMS) {
            Lock l = lockOf(level, h.trial(), group);
            if (l == null) {
                continue;
            }
            if (l.phase() == StormBackClock.Phase.STORM) {
                out.accept(h.storm(), StormBackSetup.HELM_STORM.get().defaultBlockState().setValue(StormBlocks.Helm.ACTIVE, true));
            } else {
                out.accept(h.calm(), StormBackSetup.HELM_CALM.get().defaultBlockState().setValue(StormBlocks.Helm.ACTIVE, true));
            }
        }
    }

    // ---------------------------------------------------------------- 毎 5 tick
    static void tick(ServerLevel level) {
        long now = level.getGameTime();
        for (Iterator<Map.Entry<String, Lock>> it = LOCKS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Lock> e = it.next();
            if (now >= e.getValue().end()) {
                it.remove();
                String[] k = e.getKey().split("/");
                UUID group = UUID.fromString(k[0]);
                Trial t = Trial.byId(k[1]);
                for (ServerPlayer p : Groups.online(level, group)) {
                    if (trialAt(p.position()) == t) {
                        Notice.bar(p, ModText.hint("message.skyabyss.storm.helm_end"));
                    }
                }
                GroupOverlays.refresh(level, group);
                StormBackTrials.sendGaugeNow(level);
            }
        }
        if (now % 20 == 0) {
            tickMoths(level);
        }
        tickHints(level, now);
    }

    // 前の試練: 嵐に固定したグループが眼の間を持っていれば、雷蛾を一匹。固定が終わる・凪にすると片付ける
    static void tickMoths(ServerLevel level) {
        UUID eye = RoomLocks.owner(StormBackTrials.EYE_ROOM);
        List<Entity> moths = level.getEntities((Entity) null, StormBackTrials.eyeRoom(), e -> e.getTags().contains(MOTH_TAG) && e.isAlive());
        Lock l = lockOf(level, Trial.FRONT, eye);
        boolean want = eye != null && l != null && l.phase() == StormBackClock.Phase.STORM && level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL;
        for (Entity m : moths) {
            if (!want || !(m instanceof GroupOwned o) || !eye.equals(o.groupId())) {
                m.discard();
            }
        }
        if (want && moths.stream().noneMatch(Entity::isAlive)) {
            spawnMoth(level, eye);
        }
    }

    static void spawnMoth(ServerLevel level, UUID group) {
        StaticMoth moth = StormMobSetup.STATIC_MOTH.get().create(level);
        if (moth == null) {
            return;
        }
        Vec3 c = Vec3.atBottomCenterOf(EYE_HALL_CENTER);
        moth.moveTo(c.x + 4, c.y + 2.5, c.z, level.random.nextFloat() * 360f, 0f);
        moth.addTag(MOTH_TAG);
        moth.addTag(StormBackTrials.TRIAL_TAG);
        moth.setGroupId(group);
        moth.setPersistenceRequired();
        level.addFreshEntity(moth);
    }

    // 舵の近くで、使い方の一行 (45 秒に一度)
    private static void tickHints(ServerLevel level, long now) {
        if (now % 10 != 0) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || now - HINT_AT.getOrDefault(p.getUUID(), -10000L) < 900) {
                continue;
            }
            for (Helm h : HELMS) {
                Vec3 mid = Vec3.atCenterOf(h.calm()).add(Vec3.atCenterOf(h.storm())).scale(0.5);
                if (p.position().distanceToSqr(mid) < 4.5 * 4.5) {
                    HINT_AT.put(p.getUUID(), now);
                    Notice.bar(p, ModText.hint("message.skyabyss.storm.helm_hint." + h.trial().id()));
                    break;
                }
            }
        }
    }

    // 自己テスト・コマンド: 固定の残り tick (なければ 0)
    static int remaining(ServerLevel level, Trial trial, UUID group) {
        Lock l = lockOf(level, trial, group);
        return l == null ? 0 : (int) (l.end() - level.getGameTime());
    }

    // そのグループの固定をすべて消す (グループのリセット)
    static void clearGroup(ServerLevel level, UUID group) {
        boolean any = LOCKS.keySet().removeIf(k -> k.startsWith(group + "/"));
        if (any) {
            GroupOverlays.refresh(level, group);
        }
    }
}
