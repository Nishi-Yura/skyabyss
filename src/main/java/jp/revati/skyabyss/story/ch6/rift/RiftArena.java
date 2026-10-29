package jp.revati.skyabyss.story.ch6.rift;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import jp.revati.skyabyss.story.ch6.mob.MoonMobsSetup;
import jp.revati.skyabyss.story.ch6.mob.WaningBlade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// 下弦の淵の主の間 (淵の真ん中の柱の上。docs/design/46 の 4.6)。門で下りた人が来ると残月の剣守が (主の残像と) 現れ、誰もいなくなると片付ける。
// 段 3 で床の東の外周が崩れ (片付けで戻す)、主の間の人ごとに人の残像 (EchoShade.chase) が立つ。
// 倒れたら主の間の内の人 (と組) に合図 moon_lord_waning。倒したあとも、また来れば戦える (繰り返し)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RiftArena {
    public static final String TAG = "skyabyss_moon_rift_arena";
    static final double RADIUS = 19;
    static final int EMPTY_TICKS = 200;
    // 段 3 で崩れる外周 (床の中心からの距離と、東を 0 度とした角度の幅)
    static final double CRUMBLE_IN = 9.5;
    static final double CRUMBLE_HALF = 95;
    private static int empty;
    private static final List<BlockPos> CRUMBLED = new ArrayList<>();
    private static final List<BlockState> CRUMBLED_WAS = new ArrayList<>();

    private RiftArena() {
    }

    static AABB box() {
        return new AABB(MoonRift.mark("arena_center")).inflate(RADIUS, 14, RADIUS);
    }

    public static List<WaningBlade> lords(ServerLevel level) {
        return level.getEntitiesOfClass(WaningBlade.class, box(), e -> e.isAlive() && !e.echo());
    }

    static void arrive(ServerLevel level, ServerPlayer p) {
        Notice.place(p, Component.translatable("area.skyabyss.moon_rift_arena"), Component.translatable("area.skyabyss.moon_rift_arena.sub"));
        if (lords(level).isEmpty()) {
            Tasks.later(40, () -> summon(level));
        }
    }

    // 残月の剣守と主の残像を呼ぶ (床の東寄りに、星の粒から立つ)
    public static WaningBlade summon(ServerLevel level) {
        List<WaningBlade> now = lords(level);
        if (!now.isEmpty()) {
            return now.get(0);
        }
        BlockPos c = MoonRift.mark("arena_center");
        BlockPos at = c.offset(5, 0, 0);
        WaningBlade e = MoonMobsSetup.WANING_BLADE.get().create(level);
        if (e == null) {
            return null;
        }
        BlockPos entry = MoonRift.mark("arena_entry");
        float yaw = (float) Math.toDegrees(Math.atan2(-(entry.getX() - at.getX()), entry.getZ() - at.getZ()));
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        boolean matched = MoonMob.waningMoon(level);
        // 主の体力は区画の倍率をかけない数字 (850。下弦は 1.2 倍)
        e.tuneMoon(3, matched, false, WaningBlade.HP / MoonMob.SECTION_HP[3], WaningBlade.DMG / MoonMob.SECTION_DMG[3]);
        e.addTag(TAG);
        level.addFreshEntity(e);
        e.playAction("roar");
        WaningBlade.spawnEcho(level, e, WaningBlade.DELAY);
        level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 2, at.getZ() + 0.5, 80, 1.0, 2.0, 1.0, 0.05);
        level.playSound(null, at, MoonMobsSetup.BLADE_ROAR.get(), SoundSource.HOSTILE, 2.0f, 0.8f);
        empty = 0;
        return e;
    }

    // 段 3: 床の東の外周が崩れ、主の間の人ごとに人の残像が立つ
    public static void waning(ServerLevel level, WaningBlade lord) {
        crumble(level);
        float dmg = (float) (8 * MoonMob.SECTION_DMG[3]);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box(), e -> e.isAlive() && !e.isSpectator())) {
            EchoShade s = EchoShade.chase(level, p, dmg);
            if (s != null) {
                s.addTag(TAG);
            }
            Notice.narrate(p, "message.skyabyss.moon.blade_waning");
        }
    }

    static void crumble(ServerLevel level) {
        if (!CRUMBLED.isEmpty()) {
            return;
        }
        BlockPos c = MoonRift.mark("arena_center").below();
        int r = 16;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.hypot(dx, dz);
                double a = Math.toDegrees(Math.atan2(dz, dx));
                if (d < CRUMBLE_IN || d > r || Math.abs(a) > CRUMBLE_HALF) {
                    continue;
                }
                for (int dy = 0; dy >= -2; dy--) {
                    BlockPos p = c.offset(dx, dy, dz);
                    BlockState st = level.getBlockState(p);
                    if (!st.isAir()) {
                        CRUMBLED.add(p);
                        CRUMBLED_WAS.add(st);
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                        if (dy == 0 && (dx + dz) % 3 == 0) {
                            level.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 2, 0.3, 0.2, 0.3, 0.02);
                        }
                    }
                }
            }
        }
        level.playSound(null, c, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 3.0f, 0.5f);
    }

    static void mend(ServerLevel level) {
        for (int i = 0; i < CRUMBLED.size(); i++) {
            level.setBlock(CRUMBLED.get(i), CRUMBLED_WAS.get(i), 2);
        }
        CRUMBLED.clear();
        CRUMBLED_WAS.clear();
    }

    public static boolean crumbled() {
        return !CRUMBLED.isEmpty();
    }

    // 誰もいなくなったら片付ける (RiftTicker から 20 tick ごと)
    static void tick(ServerLevel level) {
        List<WaningBlade> l = lords(level);
        if (l.isEmpty()) {
            if (crumbled()) {
                mend(level);
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
        for (Entity e : level.getEntitiesOfClass(Entity.class, box().inflate(4), e -> e.getTags().contains(TAG))) {
            e.discard();
        }
        mend(level);
        empty = 0;
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof WaningBlade lord) || lord.echo() || !(lord.level() instanceof ServerLevel level)
                || level.dimension() != MoonWorld.LEVEL) {
            return;
        }
        for (Entity e : level.getEntitiesOfClass(Entity.class, box().inflate(4), e -> e != lord && e.getTags().contains(TAG))) {
            e.discard();
        }
        Tasks.later(60, () -> mend(level));
        List<ServerPlayer> present = level.getEntitiesOfClass(ServerPlayer.class, box(), e -> e.isAlive());
        Quests.signalWithParty(level, present, RiftPuzzles.LORD);
        for (ServerPlayer p : present) {
            Notice.narrate(p, "message.skyabyss.moon.blade_down");
        }
    }
}
