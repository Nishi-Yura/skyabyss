package jp.revati.skyabyss.story.ch6.core;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import jp.revati.skyabyss.story.ch6.field.MomentStop;
import jp.revati.skyabyss.story.ch6.mob.MoonCoreBoss;
import jp.revati.skyabyss.story.ch6.valley.StarLamps;
import jp.revati.skyabyss.town.TownGuestEntity;
import jp.revati.skyabyss.town.TownSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 月の核の見張り (docs/design/46 の 4.7)。月の世界のサーバーの 20 tick ごとに:
//   核の門のまわりに前の自分たち四人を立たせる (淵を終えた人が門のそばにいて、核の頼みが「下りる」までのあいだ)
//   核の間の床から落ちた人を床の入口へ (傷 4)・門を通らずに床へ来た人を門の前へ・主の間の片付け
//   段 3 の四人の術: 谷の私 = 自分の台を星灯りで照らす (残像を寄せない)・崖の私 = 円の中の人の跳びを高く・
//                    野の私 = 15 秒ごとに核を 2 秒止める・淵の私 = 円の中の人の傷を少し癒す (鏡で月の光を返す)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class CoreTicker {
    static final float FALL_CAP = 4f;
    static final int STOP_EVERY = 300;

    private CoreTicker() {
    }

    public static boolean built(ServerLevel level) {
        return MoonWorld.coreBuilt(level.getServer());
    }

    @SubscribeEvent
    static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != MoonWorld.LEVEL || !built(level)) {
            return;
        }
        long gt = level.getGameTime();
        if (gt % 20 != 0) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) {
                continue;
            }
            if (MoonCore.inChamber(p.position())) {
                if (fell(p) || guard(p)) {
                    continue;
                }
            }
        }
        gather(level);
        arts(level, gt);
        CoreArena.tick(level);
    }

    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.level().dimension() != MoonWorld.LEVEL || !MoonCore.inChamber(p.position())) {
            return;
        }
        double safe = p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        if (event.getDistance() > safe + FALL_CAP) {
            event.setDistance((float) (safe + FALL_CAP));
            event.setDamageMultiplier(1.0f);
        }
    }

    // 床から落ちた (床より 6 低い): 床の入口へ (下りられない人は門の前)
    static boolean fell(ServerPlayer p) {
        BlockPos c = MoonCore.mark("arena_center");
        if (p.getY() >= c.getY() - 6) {
            return false;
        }
        BlockPos to = CorePuzzles.mayDescend(p) ? MoonCore.mark("arena_entry") : MoonCore.mark("gate_front");
        p.teleportTo(p.serverLevel(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), 0f);
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            p.moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        }
        p.fallDistance = 0;
        p.hurt(p.damageSources().fellOutOfWorld(), FALL_CAP);
        Notice.narrate(p, "message.skyabyss.moon.core_fall");
        return true;
    }

    // 門を通らずに核の間へ来た人 (掘って入った人など) は門の前へ
    public static boolean guard(ServerPlayer p) {
        if (CorePuzzles.mayDescend(p)) {
            p.addTag(CoreArena.TAG);
            return false;
        }
        BlockPos to = MoonCore.mark("gate_front");
        p.teleportTo(p.serverLevel(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0f, 0f);
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            p.moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        }
        p.fallDistance = 0;
        Notice.narrate(p, "message.skyabyss.moon.core_barred");
        return true;
    }

    // 核の門のまわりの四人: 淵を終えて核の頼みが「下りる」までの人が門の 32 の内にいるあいだ
    static void gather(ServerLevel level) {
        BlockPos g = MoonCore.mark("core_gate");
        boolean want = level.players().stream().anyMatch(q -> q.blockPosition().closerThan(g, 32) && CorePuzzles.open(q)
                && CorePuzzles.stage(q) <= CorePuzzles.stageIndex("descend"));
        List<TownGuestEntity> near = level.getEntities(TownSetup.GUEST.get(), new AABB(g).inflate(12), e -> e.isAlive() && e.getTags().contains(GATHER_TAG));
        if (!want) {
            near.forEach(TownGuestEntity::discard);
            return;
        }
        for (int i = 0; i < 4; i++) {
            String id = CoreArena.SELVES[i];
            if (near.stream().anyMatch(e -> id.equals(e.guestId()))) {
                continue;
            }
            BlockPos at = MoonCore.mark("gather_" + i);
            TownGuestEntity e = TownSetup.GUEST.get().create(level);
            if (e == null) {
                continue;
            }
            e.setGuest(id);
            float yaw = (float) Math.toDegrees(Math.atan2(-(g.getX() - at.getX()), g.getZ() - at.getZ()));
            e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
            e.setHome(at);
            e.addTag(GATHER_TAG);
            level.addFreshEntity(e);
        }
    }

    public static final String GATHER_TAG = "skyabyss_moon_core_gather";

    // 段 3 の四人の術
    static void arts(ServerLevel level, long gt) {
        List<TownGuestEntity> allies = CoreArena.allyList(level);
        if (allies.isEmpty()) {
            return;
        }
        List<MoonCoreBoss> lords = CoreArena.lords(level);
        for (TownGuestEntity g : allies) {
            String id = g.guestId();
            if (id == null) {
                continue;
            }
            switch (id) {
                case "moon_past1" -> {
                    if (gt % 200 == 0) {
                        StarLamps.place(level, g.blockPosition(), g.getUUID());
                    }
                }
                case "moon_past2" -> {
                    for (ServerPlayer p : near(level, g)) {
                        p.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 1, true, false, true));
                    }
                }
                case "moon_past3" -> {
                    if (gt % STOP_EVERY == 0 && !lords.isEmpty()) {
                        MoonCoreBoss core = lords.get(0);
                        MomentStop.freeze(core, gt + MomentStop.DURATION);
                        level.sendParticles(ParticleTypes.END_ROD, g.getX(), g.getY() + 1.5, g.getZ(), 30, 0.4, 0.6, 0.4, 0.08);
                        for (ServerPlayer p : CoreArena.players(level)) {
                            Notice.narrate(p, "message.skyabyss.moon.core_ally_stop");
                        }
                    }
                }
                case "moon_past4" -> {
                    for (ServerPlayer p : near(level, g)) {
                        if (p.getHealth() < p.getMaxHealth()) {
                            p.heal(1.0f);
                            level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 3, 0.3, 0.5, 0.3, 0.02);
                        }
                    }
                }
                default -> {
                }
            }
        }
    }

    static List<ServerPlayer> near(ServerLevel level, TownGuestEntity g) {
        return level.getEntitiesOfClass(ServerPlayer.class, g.getBoundingBox().inflate(MoonCoreBoss.SAFE_R, 2, MoonCoreBoss.SAFE_R),
                p -> p.isAlive() && !p.isSpectator() && Math.hypot(p.getX() - g.getX(), p.getZ() - g.getZ()) <= MoonCoreBoss.SAFE_R);
    }
}
