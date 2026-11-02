package jp.revati.skyabyss.trial.raid;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.trial.Trial;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

// 試練の部屋の難しさをそろえる共通の決まり (docs/design/32 の 9.6。2026-10-28 の試遊「巨神の試練、まだ簡単すぎるやつと、むずいのある」)。
//  1. 時間切れ: 部屋が自分の時計 (RaidRoom.timerTicks) を持たないとき、歩・智は LIMIT_WALK_WIT、闘は LIMIT_FIGHT。過ぎると全滅と同じやり直し。
//     全滅するたびに EXTRA_PER_WIPE だけ延びる。帯の残り時間にも出す
//  2. 邪魔の敵: 敵のいない部屋 (謎解き・運び) にも、始まって HARASS_AFTER から HARASS_EVERY ごとに、その巨神の背の敵が一体ずつ出る
//     (部屋の敵が人数ぶんより少ないときだけ)。闘の部屋と中ボスの間には出さない
//  3. 救い: 同じ部屋で全滅するたびに、そのあと出る敵の体力と傷が ASSIST_STEP ずつ下がる (ASSIST_MAX 回まで)。重い部屋で詰まらない
public final class RaidPace {
    private RaidPace() {
    }

    public static final int LIMIT_WALK_WIT = 20 * 300;
    public static final int LIMIT_FIGHT = 20 * 360;
    public static final int EXTRA_PER_WIPE = 20 * 60;
    public static final int HARASS_AFTER = 20 * 15;
    public static final int HARASS_EVERY = 20 * 20;
    public static final double ASSIST_STEP = 0.12;
    public static final int ASSIST_MAX = 4;
    static final ResourceLocation ASSIST_HP = ResourceLocation.fromNamespaceAndPath("skyabyss", "raid_assist_hp");
    static final ResourceLocation ASSIST_DMG = ResourceLocation.fromNamespaceAndPath("skyabyss", "raid_assist_damage");

    @Nullable
    static Trial.Slot slotOf(RaidRun.Run run) {
        if (run.mid) {
            return null;
        }
        Trial.Def d = jp.revati.skyabyss.trial.TrialCatalog.get(run.ctx.roomId());
        return d == null ? null : d.slot();
    }

    // この部屋の決まりの時間 (tick)。部屋が自分の時計を持つか、中ボスの間なら -1
    public static int limit(RaidRun.Run run) {
        Trial.Slot slot = slotOf(run);
        if (slot == null || run.room.timerTicks() >= 0) {
            return -1;
        }
        return (slot == Trial.Slot.FIGHT ? LIMIT_FIGHT : LIMIT_WALK_WIT) + EXTRA_PER_WIPE * run.wipes;
    }

    // 帯に出す残り (部屋の時計があればそれ)
    public static int timerTicks(RaidRun.Run run, long now) {
        int own = run.room.timerTicks();
        if (own >= 0 || run.phase != RaidRun.Phase.ACTIVE) {
            return own;
        }
        int lim = limit(run);
        return lim < 0 ? -1 : (int) Math.max(0, run.activeSince + lim - now);
    }

    // RaidRun.tickRun の ACTIVE から (部屋の tick のあと)
    static void tick(RaidRun.Run run, long now) {
        RaidCtx ctx = run.ctx;
        // 始まりの時刻を持たないまま動いている部屋 (直に始めた部屋) は、いまを始まりとする
        if (run.activeSince <= 0) {
            run.activeSince = now;
            run.lastHarass = now;
        }
        int lim = limit(run);
        if (lim >= 0 && now - run.activeSince > lim) {
            ctx.fail("message.skyabyss.raid.timeout");
            return;
        }
        Trial.Slot slot = slotOf(run);
        if (slot == null || slot == Trial.Slot.FIGHT || now - run.activeSince < HARASS_AFTER || now - run.lastHarass < HARASS_EVERY) {
            return;
        }
        run.lastHarass = now;
        List<ServerPlayer> standing = ctx.standing();
        if (standing.isEmpty() || ctx.aliveMobs().size() >= Math.max(1, standing.size())) {
            return;
        }
        ServerPlayer target = standing.get(ctx.rng().nextInt(standing.size()));
        BlockPos at = spot(ctx, target);
        if (at != null) {
            harasser(ctx, at);
        }
    }

    // 狙う人から 6〜9 離れた、足と頭が空いて床のある所 (部屋の箱の中)
    @Nullable
    static BlockPos spot(RaidCtx ctx, ServerPlayer p) {
        ServerLevel level = ctx.level();
        for (int i = 0; i < 12; i++) {
            double a = ctx.rng().nextDouble() * Math.PI * 2;
            double d = 6 + ctx.rng().nextDouble() * 3;
            BlockPos base = BlockPos.containing(p.getX() + Math.cos(a) * d, p.getY(), p.getZ() + Math.sin(a) * d);
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos b = base.above(dy);
                if (ctx.cell().bounds().contains(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5) && level.getBlockState(b).isAir()
                        && level.getBlockState(b.above()).isAir() && level.getBlockState(b.below()).isSolid()) {
                    return b;
                }
            }
        }
        return null;
    }

    // その巨神の背の小さな敵を一体
    static void harasser(RaidCtx ctx, BlockPos at) {
        String id = switch (ctx.titan()) {
            case "tide_shell" -> "skyabyss:reef_scuttler";
            case "thunder_wing" -> "skyabyss:static_moth";
            case "ember" -> "skyabyss:ash_thrall";
            default -> null;
        };
        if (id == null) {
            ctx.spawnGuard("crawler", at);
            return;
        }
        EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
        if (type.create(ctx.level()) instanceof Mob mob) {
            ServerLevel level = ctx.level();
            mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
            if (mob instanceof jp.revati.skyabyss.titan.group.GroupOwned owned) {
                owned.setGroupId(ctx.group());
            }
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, null);
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
            ctx.adopt(mob);
        }
    }

    // 救い: 全滅の数に応じて、部屋の敵の体力と傷を下げる (RaidCtx.adopt から)
    static void assist(Mob mob, int wipes) {
        int n = Math.min(ASSIST_MAX, wipes);
        if (n <= 0) {
            return;
        }
        double f = -ASSIST_STEP * n;
        AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && hp.getModifier(ASSIST_HP) == null) {
            hp.addPermanentModifier(new AttributeModifier(ASSIST_HP, f, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            mob.setHealth(mob.getMaxHealth());
        }
        AttributeInstance dmg = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null && dmg.getModifier(ASSIST_DMG) == null) {
            dmg.addPermanentModifier(new AttributeModifier(ASSIST_DMG, f, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
