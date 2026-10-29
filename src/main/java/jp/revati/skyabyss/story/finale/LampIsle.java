package jp.revati.skyabyss.story.finale;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.finale.mob.CloudMobsSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3f;

// 島 2「灯の島」(docs/design/50 の 4.2.2)。地形は data/skyabyss/finale/isle2.json (tools/gen/world/build_cloud_isle2.py。FinaleWorld の版 3)。
//   視線   灯台の灯の部屋 (印 lamp_room) から光の筋が島を回る (PERIOD で一周、幅 HALF_WIDTH × 2、RANGE まで)。筋の中で灯から見えている人は重くなる
//          (歩み半分・跳べない)。HOLD より長くいると毎秒 最大体力の 6% の傷。陰 (岩・石垣・小屋) で外れる。灯蛾に「灯された」人は陰でも見つかる。
//          主の間の筋は灯守の目が回す (同じ gaze を使う)
//   漁の灯 西・東・南東の突堤の先 (印 lamp_<w|e|s>)。右クリックで 30 秒ともる。合図 finale_lamp_<k> (近くの人と組へ)。突堤の付け根で前の浜の人の最後の言葉
//   扉     灯台の扉 (印 door): 三つともした人 (頼みの段 door 以降) を主の間へ。主の間の帰りの台で扉の前へ
//   光の橋 鐘の島の主の間から下る橋 (印 bridge_gate のあたり)。鐘の島の頼みを終えていない人は主の間の入口へ戻す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class LampIsle {
    public static final String NAME = "isle2";
    public static final String QUEST = "main_finale_lamp";
    public static final int PERIOD = 240;
    public static final double HALF_WIDTH = Math.toRadians(10);
    public static final double RANGE = 70;
    public static final int HOLD = 40;
    public static final float BURN = 0.06f;
    public static final int LAMP_TICKS = 600;
    static final String[] LAMPS = {"w", "e", "s"};
    static final double ARRIVE_NEAR = 10;
    static final double FRAG_NEAR = 8;
    private static final ResourceLocation SLOW = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "gaze_slow");
    private static final ResourceLocation NOJUMP = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "gaze_jump");
    private static final DustParticleOptions BEAM = new DustParticleOptions(new Vector3f(1.0f, 0.93f, 0.7f), 1.6f);
    // 人 → 筋の中に続けている tick・灯されている終わりの時刻
    private static final Map<UUID, Integer> HELD = new HashMap<>();
    private static final Map<UUID, Long> LIT = new HashMap<>();
    // ともった漁の灯 → 消える時刻
    private static final Map<BlockPos, Long> LAMP_ON = new HashMap<>();

    private LampIsle() {
    }

    public static FinaleTerrain terrain() {
        return FinaleTerrain.of(NAME);
    }

    public static BlockPos mark(String key) {
        return terrain().mark(key);
    }

    // 島の上 (楕円。橋と主の間を除く)
    public static boolean onIsland(Vec3 p) {
        BlockPos c = mark("center");
        return Math.hypot((p.x - c.getX()) / 40.0, (p.z - (c.getZ() - 8)) / 36.0) < 1.0 && p.y < mark("arena").getY() - 6;
    }

    // ---------------------------------------------------------------- 光の筋
    public static double angle(ServerLevel level) {
        return (level.getGameTime() % PERIOD) / (double) PERIOD * Math.PI * 2;
    }

    // 灯の部屋の外の縁 (筋の向きに 5.5)
    static Vec3 source(double angle) {
        Vec3 c = mark("lamp_room").getCenter();
        return c.add(Math.cos(angle) * 5.5, 0, Math.sin(angle) * 5.5);
    }

    static double wrap(double a) {
        while (a > Math.PI) {
            a -= Math.PI * 2;
        }
        while (a < -Math.PI) {
            a += Math.PI * 2;
        }
        return a;
    }

    // src から angle の向きに半幅 half・range までの筋に、p が入っていて見えているか (灯されていれば陰でも)
    public static boolean inBeam(ServerLevel level, Vec3 src, double angle, double half, double range, ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 to = eye.subtract(src);
        double d = Math.hypot(to.x, to.z);
        if (d > range || d < 0.5 || Math.abs(wrap(Math.atan2(to.z, to.x) - angle)) > half) {
            return false;
        }
        return lit(p) || visible(level, src, eye);
    }

    public static boolean visible(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    public static boolean lit(ServerPlayer p) {
        Long until = LIT.get(p.getUUID());
        return until != null && until > p.serverLevel().getGameTime();
    }

    // 灯蛾に触れられた: ticks のあいだ陰でも筋に見つかる
    public static void light(ServerPlayer p, int ticks) {
        LIT.put(p.getUUID(), p.serverLevel().getGameTime() + ticks);
    }

    // 筋の中か (2 tick ごとに呼ぶ)。中なら重く、hold より長ければ 20 tick ごとに傷 burn (source が null なら魔法の傷)
    public static void gaze(ServerPlayer p, boolean in, int hold, float burn, Entity source) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
        if (!in) {
            if (HELD.remove(p.getUUID()) != null) {
                unheavy(p);
            }
            return;
        }
        int held = HELD.merge(p.getUUID(), 2, Integer::sum);
        if (speed != null && !speed.hasModifier(SLOW)) {
            speed.addTransientModifier(new AttributeModifier(SLOW, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (jump != null && !jump.hasModifier(NOJUMP)) {
            jump.addTransientModifier(new AttributeModifier(NOJUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        ServerLevel level = p.serverLevel();
        if (held == 2) {
            level.playSound(null, p.blockPosition(), CloudMobsSetup.BEAM_CAUGHT.get(), SoundSource.HOSTILE, 0.8f, 1.0f);
            Notice.hint(p, "message.skyabyss.isle2.caught");
        }
        if (held % 6 == 0) {
            level.sendParticles(ParticleTypes.WAX_ON, p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.3, 0.5, 0.3, 0.01);
        }
        if (held > hold && held % 20 == 0 && !p.isCreative()) {
            p.hurt(source != null ? level.damageSources().indirectMagic(source, source) : level.damageSources().magic(), burn);
        }
    }

    static void unheavy(ServerPlayer p) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
        if (speed != null) {
            speed.removeModifier(SLOW);
        }
        if (jump != null) {
            jump.removeModifier(NOJUMP);
        }
    }

    public static boolean heavy(ServerPlayer p) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.hasModifier(SLOW);
    }

    // 筋の光の粒 (灯の部屋から島の地面へ下る線)
    static void drawBeam(ServerLevel level, double a) {
        Vec3 s = source(a);
        double ground = mark("center").getY();
        for (double d = 1; d <= RANGE - 10; d += 1.6) {
            double t = d / (RANGE - 10);
            level.sendParticles(BEAM, s.x + Math.cos(a) * d, s.y + (ground - s.y) * t, s.z + Math.sin(a) * d, 1, 0, 0, 0, 0);
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != FinaleWorld.LEVEL || level.getGameTime() % 2 != 0
                || !FinaleWorld.built(level.getServer()) || FinaleWorld.version(level.getServer()) < 3) {
            return;
        }
        double a = angle(level);
        boolean anyone = false;
        for (ServerPlayer p : level.players()) {
            if (onIsland(p.position())) {
                anyone = true;
            }
            if (p.isSpectator()) {
                continue;
            }
            if (onIsland(p.position())) {
                gaze(p, inBeam(level, source(a), a, HALF_WIDTH, RANGE, p), HOLD, p.getMaxHealth() * BURN, null);
            } else if (!LampArena.box().contains(p.position())) {
                gaze(p, false, HOLD, 0, null);
            }
        }
        if (anyone && level.getGameTime() % 4 == 0) {
            drawBeam(level, a);
        }
        if (level.getGameTime() % 10 == 0) {
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator()) {
                    gate(p);
                }
            }
        }
        if (level.getGameTime() % 20 == 0) {
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator()) {
                    visit(p);
                }
            }
            lampsOut(level);
            LampArena.tick(level);
        }
        if (level.getGameTime() % 40 == 0) {
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator() && !p.isCreative()) {
                    LampArena.spawn(level, p);
                }
            }
        }
    }

    @SubscribeEvent
    static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            HELD.remove(p.getUUID());
            unheavy(p);
        }
    }

    // ---------------------------------------------------------------- 光の橋の関所 (鐘の島の主の間 → 島 2)
    static boolean inGate(Vec3 p) {
        BlockPos g = mark("bridge_gate");
        return Math.abs(p.x - (g.getX() + 0.5)) <= 2.5 && p.z <= g.getZ() + 4 && p.z >= g.getZ() - 12 && Math.abs(p.y - g.getY()) <= 4;
    }

    public static boolean bridgeOpen(ServerPlayer p) {
        QuestLog.Entry e = QuestLog.of(p).entry(BellIsle.QUEST);
        return e != null && e.done;
    }

    static void gate(ServerPlayer p) {
        if (!inGate(p.position()) || bridgeOpen(p)) {
            return;
        }
        BlockPos to = BellIsle.mark("arena_entry");
        p.teleportTo(p.serverLevel(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 180f, 0f);
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            p.moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 180f, 0f);
        }
        p.resetFallDistance();
        Notice.narrate(p, "message.skyabyss.cloudsea.bridge_shut");
    }

    // ---------------------------------------------------------------- 着く所・突堤
    static void visit(ServerPlayer p) {
        BlockPos arrive = mark("arrive");
        if (p.distanceToSqr(arrive.getX() + 0.5, arrive.getY(), arrive.getZ() + 0.5) < ARRIVE_NEAR * ARRIVE_NEAR) {
            Quests.signal(p, "finale_isle2_arrive");
        }
        for (int i = 0; i < LAMPS.length; i++) {
            BlockPos f = mark("frag_" + LAMPS[i]);
            if (p.distanceToSqr(f.getX() + 0.5, f.getY(), f.getZ() + 0.5) < FRAG_NEAR * FRAG_NEAR && Lore.unlock(p, "finale/lamp_echo_" + (i + 1), true)) {
                Notice.narrate(p, "message.skyabyss.isle2.echo." + LAMPS[i]);
            }
        }
    }

    static void lampsOut(ServerLevel level) {
        LAMP_ON.entrySet().removeIf(e -> {
            if (e.getValue() > level.getGameTime()) {
                return false;
            }
            BlockState st = level.getBlockState(e.getKey());
            if (st.getBlock() instanceof RedstoneLampBlock) {
                level.setBlock(e.getKey(), st.setValue(RedstoneLampBlock.LIT, false), 2);
                level.playSound(null, e.getKey(), CloudMobsSetup.LAMP_OUT.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            return true;
        });
    }

    public static void lightLamp(ServerLevel level, String key) {
        BlockPos pos = mark("lamp_" + key);
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof RedstoneLampBlock) {
            level.setBlock(pos, st.setValue(RedstoneLampBlock.LIT, true), 2);
        }
        LAMP_ON.put(pos, level.getGameTime() + LAMP_TICKS);
        level.playSound(null, pos, CloudMobsSetup.LAMP_LIT.get(), SoundSource.BLOCKS, 1.4f, 1.0f);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 20, 0.4, 0.6, 0.4, 0.04);
    }

    // ---------------------------------------------------------------- 右クリック (漁の灯・扉・帰りの台)
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand() != InteractionHand.MAIN_HAND || !FinaleWorld.in(p)
                || FinaleWorld.version(p.server) < 3) {
            return;
        }
        BlockPos pos = event.getPos();
        ServerLevel level = p.serverLevel();
        for (String k : LAMPS) {
            if (pos.equals(mark("lamp_" + k))) {
                event.setCanceled(true);
                lightLamp(level, k);
                List<ServerPlayer> near = new ArrayList<>();
                for (ServerPlayer o : level.players()) {
                    if (o.distanceToSqr(p) < 32 * 32) {
                        near.add(o);
                    }
                }
                Quests.signalWithParty(level, near, "finale_lamp_" + k);
                Notice.narrate(p, "message.skyabyss.isle2.lamp." + k);
                return;
            }
        }
        if (pos.equals(mark("door"))) {
            event.setCanceled(true);
            if (!doorOpen(p)) {
                Notice.narrate(p, "message.skyabyss.isle2.door.shut");
                return;
            }
            BlockPos to = mark("arena_entry");
            gaze(p, false, HOLD, 0, null);
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 180f, 0f);
            Quests.signal(p, "finale_lamp_door");
            LampArena.arrive(level, p);
        } else if (pos.equals(mark("arena_back"))) {
            event.setCanceled(true);
            BlockPos to = mark("door_front");
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0f, 0f);
        }
    }

    // 扉が開くか: 頼みの段 door 以降 (三つの灯をともした)・頼みを終えた人
    public static boolean doorOpen(ServerPlayer p) {
        QuestLog.Entry e = QuestLog.of(p).entry(QUEST);
        if (e == null) {
            return false;
        }
        if (e.done) {
            return true;
        }
        QuestDef def = QuestManager.quest(QUEST);
        for (int i = 0; def != null && i < def.stages().size(); i++) {
            if (def.stages().get(i).id().equals("door")) {
                return e.stage >= i;
            }
        }
        return false;
    }

    // 自己テスト向け
    static void reset(ServerPlayer p) {
        HELD.remove(p.getUUID());
        LIT.remove(p.getUUID());
        unheavy(p);
    }
}
