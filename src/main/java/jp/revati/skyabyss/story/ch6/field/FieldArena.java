package jp.revati.skyabyss.story.ch6.field;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import jp.revati.skyabyss.story.ch6.mob.FullKeeper;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import jp.revati.skyabyss.story.ch6.mob.MoonMobsSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// 満月の野の主の間 (満月の台の上。docs/design/46 の 4.5)。門で上がった人が来ると望の灯守が現れ、誰もいなくなると片付ける。
// 倒れたら主の間の内の人 (と組) に合図 moon_lord_full。倒したあとも、また来れば戦える (繰り返し)。片付けで満ち引きの速さも戻す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class FieldArena {
    public static final String TAG = "skyabyss_moon_field_arena";
    static final double RADIUS = 20;
    static final int EMPTY_TICKS = 200;
    private static int empty;

    private FieldArena() {
    }

    static AABB box() {
        return new AABB(MoonField.mark("arena_center")).inflate(RADIUS, 14, RADIUS);
    }

    public static List<FullKeeper> lords(ServerLevel level) {
        return level.getEntitiesOfClass(FullKeeper.class, box(), Entity::isAlive);
    }

    static void arrive(ServerLevel level, ServerPlayer p) {
        Notice.place(p, Component.translatable("area.skyabyss.moon_field_arena"), Component.translatable("area.skyabyss.moon_field_arena.sub"));
        if (lords(level).isEmpty()) {
            Tasks.later(40, () -> summon(level));
        }
    }

    // 望の灯守を呼ぶ (主の出る所に、光の柱から降りてくる)
    public static FullKeeper summon(ServerLevel level) {
        List<FullKeeper> now = lords(level);
        if (!now.isEmpty()) {
            return now.get(0);
        }
        BlockPos at = MoonField.mark("lord_spawn");
        FullKeeper e = MoonMobsSetup.FULL_KEEPER.get().create(level);
        if (e == null) {
            return null;
        }
        BlockPos entry = MoonField.mark("arena_entry");
        float yaw = (float) Math.toDegrees(Math.atan2(-(entry.getX() - at.getX()), entry.getZ() - at.getZ()));
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        boolean full = MoonMob.fullMoon(level);
        // 主の体力は区画の倍率をかけない数字 (900。満月は 1.2 倍)
        e.tuneMoon(2, full, false, FullKeeper.HP / MoonMob.SECTION_HP[2], FullKeeper.DMG / MoonMob.SECTION_DMG[2]);
        e.addTag(TAG);
        level.addFreshEntity(e);
        e.playAction("roar");
        level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 3, at.getZ() + 0.5, 80, 1.2, 3.0, 1.2, 0.05);
        level.playSound(null, at, MoonMobsSetup.KEEPER_ROAR.get(), net.minecraft.sounds.SoundSource.HOSTILE, 2.0f, 0.8f);
        empty = 0;
        return e;
    }

    // 誰もいなくなったら片付ける (FieldTicker から 20 tick ごと)
    static void tick(ServerLevel level) {
        List<FullKeeper> l = lords(level);
        if (l.isEmpty()) {
            if (FieldTide.fast()) {
                FieldTide.setFast(false);
            }
            return;
        }
        boolean anyone = !level.getEntitiesOfClass(ServerPlayer.class, box(), e -> e.isAlive() && !e.isSpectator()).isEmpty();
        empty = anyone ? 0 : empty + 20;
        if (empty >= EMPTY_TICKS) {
            clear(level);
        }
    }

    public static void clear(ServerLevel level) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, box(), e -> e.getTags().contains(TAG))) {
            e.discard();
        }
        FieldTide.setFast(false);
        empty = 0;
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof FullKeeper lord) || !(lord.level() instanceof ServerLevel level) || level.dimension() != MoonWorld.LEVEL) {
            return;
        }
        FieldTide.setFast(false);
        List<ServerPlayer> present = level.getEntitiesOfClass(ServerPlayer.class, box(), e -> e.isAlive());
        Quests.signalWithParty(level, present, FieldPuzzles.LORD);
        for (ServerPlayer p : present) {
            Notice.narrate(p, "message.skyabyss.moon.keeper_down");
        }
    }
}
