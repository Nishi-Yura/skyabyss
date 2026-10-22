package jp.revati.skyabyss.abyss.mobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch4.Ch4Mobs;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// 縄の人 (深淵だけ・docs/design/36 の 4・4.1 B 縄の束の人)。吹き抜けの真ん中の闇に縄で垂れ、縁の人を真ん中へ引く。動かない。
//   縁 (真ん中から 11〜16 ブロック、高さが近い) に人がいると、2 秒の予告 (縄がきしむ・粒が落ちる) のあと 2 秒引く。
//   引く力は歩く速さより弱い (歩いて離れれば抜けられる)。しゃがんでいる人は引かれない (踏みとどまれる)。
//   引かれて吹き抜けへ落ちたときの落ちる傷は 6 まで (引かれた人だけ。公平さのための決まり)。
//   引くのは 8 秒に一度まで。人を傷つけるのは落ちる高さだけ (この係は殴らない)。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public class RopeHanger extends AbyssMobB {
    public static final String ID = "rope_hanger";
    public static final double HP = 70;
    public static final double PULL_MIN = 11;
    public static final double PULL_MAX = 16;
    public static final double PULL_HEIGHT = 12;
    public static final int TELL_TICKS = 40;
    public static final int PULL_TICKS = 40;
    public static final int COOLDOWN_TICKS = 160;
    public static final double PUSH = 0.05;
    public static final float FALL_CAP = 6f;
    public static final long FALL_WINDOW = 200;
    private static final Map<UUID, Long> PULLED = new HashMap<>();
    private final List<UUID> pullers = new ArrayList<>();
    private int tell = -1;
    private int pulling = -1;
    private int cooldown;

    public RopeHanger(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "pull", "hurt", "death");
        this.xpReward = 18;
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, 0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return distanceToClosestPlayer > 96;
    }

    // 吹き抜けの真ん中 (x, z) の上。引く向きは真ん中へ
    public double axisX() {
        return getX();
    }

    public double axisZ() {
        return getZ();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        setDeltaMovement(Vec3.ZERO);
        if (cooldown > 0) {
            cooldown--;
        }
        if (pulling >= 0) {
            pullNow(sl);
            return;
        }
        if (tell >= 0) {
            if (tickCount % 8 == 0) {
                sl.sendParticles(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, getX(), getY() - 0.2, getZ(), 2, 0.3, 0, 0.3, 0);
                playSound(AbyssMobsBSetup.ROPE_CREAK.get(), 0.9f, 1.0f);
            }
            if (--tell < 0) {
                List<ServerPlayer> ps = candidates(sl);
                pullers.clear();
                for (ServerPlayer p : ps) {
                    pullers.add(p.getUUID());
                }
                if (!pullers.isEmpty()) {
                    pulling = PULL_TICKS;
                    playAction("pull");
                    playSound(AbyssMobsBSetup.ROPE_PULL.get(), 1.0f, 1.0f);
                }
                cooldown = COOLDOWN_TICKS;
            }
            return;
        }
        if (cooldown == 0 && !candidates(sl).isEmpty()) {
            tell = TELL_TICKS;
        }
    }

    // 縁にいる人 (引かれる人): 真ん中から PULL_MIN〜PULL_MAX、高さが近い、生きている、しゃがんでいない
    private List<ServerPlayer> candidates(ServerLevel sl) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : java.util.List.copyOf(sl.players())) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator() || p.isShiftKeyDown()) {
                continue;
            }
            double dx = p.getX() - axisX();
            double dz = p.getZ() - axisZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d >= PULL_MIN && d <= PULL_MAX && Math.abs(p.getY() - getY()) <= PULL_HEIGHT) {
                out.add(p);
            }
        }
        return out;
    }

    // 引く (予告のあとの PULL_TICKS の間)。引かれている人の中で、今も縁にいて、しゃがんでいない人だけ
    private void pullNow(ServerLevel sl) {
        pulling--;
        long now = sl.getGameTime();
        for (UUID id : pullers) {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
            if (p == null || !p.isAlive() || p.serverLevel() != sl || p.isShiftKeyDown()) {
                continue;
            }
            double dx = axisX() - p.getX();
            double dz = axisZ() - p.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > PULL_MAX + 3 || d < 1e-4) {
                continue;
            }
            p.push(dx / d * PUSH, 0, dz / d * PUSH);
            Long prev = PULLED.put(id, now);
            if (prev == null || now - prev > FALL_WINDOW) {
                // 引かれ始めの一行 (しゃがめば踏みとどまれることを知らせる)
                jp.revati.skyabyss.notice.Notice.bar(p, Component.translatable("message.skyabyss_abyss_b.rope_pull"));
            }
        }
        if (pulling < 0) {
            pullers.clear();
        }
    }

    // 引かれて落ちたときの傷を抑える (引かれた人だけ。落ちた先の層まで安全に届くように)
    @SubscribeEvent
    static void onFall(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !event.getSource().is(DamageTypeTags.IS_FALL)) {
            return;
        }
        Long at = PULLED.get(p.getUUID());
        if (at != null && p.serverLevel().getGameTime() - at <= FALL_WINDOW) {
            event.setAmount(Math.min(event.getAmount(), FALL_CAP));
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Ch4Mobs.drop(level, this, source, AbyssMobsBSetup.ROPE_KNOT.get(), 0.7, 1, 2);
    }
}
