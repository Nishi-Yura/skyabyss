package jp.revati.skyabyss.titan.storm;

import jp.revati.skyabyss.notice.Notice;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.TitanKeeper;
import jp.revati.skyabyss.titan.storm.back.StormFx;
import jp.revati.skyabyss.titan.storm.back.Updraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 昇り風の柱 (12 の 2.4節)。止まり木の左の足もとから 8 ブロック左に立つ、見えない当たり判定。
// 半径 2.5、地面から羽根の門 (底から 25) の高さまで。中に入った人を Updraft.lift で持ち上げ、頂で白い閃光と雷鳴のあと背中へ渡す。
// 巨神が止まっているあいだだけ在る (飛び立てば消える)。保存しない (StormSetup で noSave)。
public class GaleColumnEntity extends Entity {
    public static final double RADIUS = 2.5;
    // 資格のない人が断られたあと、もう一度断るまで (tick)
    private static final int REFUSE_QUIET = 60;
    private static final int SLOW_FALL_AFTER_LEAVING = 120;
    private static final int HINT_EVERY = 40;
    private static final double HINT_RANGE = 16.0;

    private UUID wing;
    private double baseX;
    private double baseY;
    private double baseZ;
    private double top;
    // 資格の断りを出した人 → 次に断るまでの tick
    private final Map<UUID, Long> quietUntil = new HashMap<>();
    // 今、柱の中で持ち上げている人
    private final Set<UUID> lifting = new HashSet<>();

    public GaleColumnEntity(EntityType<? extends GaleColumnEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    // 作った直後に一度だけ呼ぶ。(x, z) の地面 (読み込まれていなければ top - 25) に立て、頂は top
    public void setup(UUID wingId, double x, double z, double topY) {
        this.wing = wingId;
        this.top = topY;
        // 根元がブロックの中なら、地表の高さまで上げる (heightmap が地表を返す)
        double surface = level() instanceof ServerLevel sl ? StormPerch.surfaceAt(sl, x, z) : Double.NaN;
        this.baseY = Double.isNaN(surface) ? topY - StormWingEntity.GATE_UP : surface;
        this.baseX = x;
        this.baseZ = z;
        setPos(baseX, baseY, baseZ);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        ServerLevel level = (ServerLevel) level();
        StormWingEntity w = wing == null ? null : level.getEntity(wing) instanceof StormWingEntity ww ? ww : null;
        if (w == null || !w.isPerched()) {
            discard();
            return;
        }
        // 見た目: 雨粒と木の葉と羽根が上へ吸い上げられ、根元で渦が巻く
        if (tickCount % 2 == 0) {
            Updraft.particles(level, new Vec3(baseX, baseY, baseZ), RADIUS, top - baseY, 6);
        }
        {
            // 昇り風の光 (乗れる人にだけ見える。眠っているあいだは柱ごと消える)
            java.util.List<Vec3> glow = new java.util.ArrayList<>();
            for (double y = baseY + 2; y < top; y += 6) {
                glow.add(new Vec3(baseX, y, baseZ));
            }
            glow.add(new Vec3(baseX, top, baseZ));
            jp.revati.skyabyss.titan.end.BoardGlow.emit(level, Titans.THUNDER_WING, glow);
        }
        if (tickCount % 10 == 0) {
            StormFx.ring(level, ParticleTypes.CLOUD, new Vec3(baseX, baseY + 0.5, baseZ), RADIUS, 8);
            level.playSound(null, baseX, baseY, baseZ, StormSetup.GALE.get(), SoundSource.AMBIENT, 1.2f, 0.9f + random.nextFloat() * 0.2f);
        }
        Set<UUID> inside = new HashSet<>();
        for (ServerPlayer p : java.util.List.copyOf(level.players())) { // arrive() で別の次元へ移すので写しを回す
            if (!TitanKeeper.eligible(p) || !inColumn(p.getX(), p.getY(), p.getZ())) {
                continue;
            }
            inside.add(p.getUUID());
            // 資格がなければ、断りを一行出す (3 秒は反応しない)。持ち上げない
            if (!Progression.canBoard(p, Titans.THUNDER_WING)) {
                long now = level.getGameTime();
                if (quietUntil.getOrDefault(p.getUUID(), 0L) <= now) {
                    Progression.checkBoard(p, Titans.THUNDER_WING);
                    quietUntil.put(p.getUUID(), now + REFUSE_QUIET);
                }
                continue;
            }
            // 備えの三灯 (21 の 1.2): 赤があれば一度は持ち上げず、柱を出て入り直した二度目 (5 秒以内) で上る。上りの途中は聞かない
            if (!lifting.contains(p.getUUID()) && !jp.revati.skyabyss.readiness.BoardReadiness.attempt(p, Titans.THUNDER_WING,
                    jp.revati.skyabyss.readiness.BoardReadiness.Mode.WALK_IN)) {
                continue;
            }
            lifting.add(p.getUUID());
            if (Updraft.lift(p, top)) {
                lifting.remove(p.getUUID());
                arrive(level, p);
            }
        }
        // 柱から横へ出た人には、低速落下を 6 秒付ける (落ちて死なないように)
        for (UUID id : Set.copyOf(lifting)) {
            if (inside.contains(id)) {
                continue;
            }
            lifting.remove(id);
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_AFTER_LEAVING, 0, false, false, true));
            }
        }
        if (tickCount % HINT_EVERY == 0) {
            for (ServerPlayer p : level.players()) {
                if (TitanKeeper.eligible(p) && Math.hypot(p.getX() - baseX, p.getZ() - baseZ) < HINT_RANGE) {
                    Notice.bar(p, ModText.hint("message.skyabyss.storm.column_hint"));
                }
            }
        }
    }

    // 頂に着いた: 雷鳴を鳴らし、背中へ渡す (白い閃光は StormTravel.board が画面へ送る)
    private void arrive(ServerLevel level, ServerPlayer p) {
        level.playSound(null, p.getX(), p.getY(), p.getZ(), StormSetup.THUNDER_FAR.get(), SoundSource.PLAYERS, 2f, 1.0f);
        StormTravel.board(p);
    }

    // 柱の中 (半径 2.5、地面から頂まで) にいるか
    public boolean inColumn(double x, double y, double z) {
        double dx = x - baseX;
        double dz = z - baseZ;
        return dx * dx + dz * dz <= RADIUS * RADIUS && y >= baseY - 1 && y <= top + 1;
    }

    public double topY() {
        return top;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    // 保存しない (StormSetup で noSave)。読み書きする中身はない
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
