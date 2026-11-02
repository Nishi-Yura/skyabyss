package jp.revati.skyabyss.story.ch6.rift;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 下弦の鏡で覚える自分の動き (docs/design/46 の 4.6「自分の残像」)。一人に一つ。
//   触れる (待っていない)   覚え始める。8 秒 (RECORD) か、鏡にもう一度触れるか、淵へ落ちる (RiftTicker.fell) と覚え終わる
//   覚え終わる              残像は鏡の中で待つ (60 秒。鏡が光る)
//   触れる (待っている)     残像が覚えた動きを、覚えた所から 8 秒なぞる (EchoShade.replay)。前の残像は消える
//   淵を離れる・倒れる      覚えかけと待つものは捨てる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RiftEcho {
    public static final int RECORD = 160;
    public static final int WAIT = 1200;
    private static final Map<UUID, Take> TAKES = new HashMap<>();

    static final class Take {
        final BlockPos mirror;
        final List<EchoShade.Frame> frames = new ArrayList<>();
        boolean recording = true;
        long waitUntil;
        int shadeId = -1;

        Take(BlockPos mirror) {
            this.mirror = mirror;
        }
    }

    private RiftEcho() {
    }

    public static boolean recording(ServerPlayer p) {
        Take t = TAKES.get(p.getUUID());
        return t != null && t.recording;
    }

    public static boolean waiting(ServerPlayer p) {
        Take t = TAKES.get(p.getUUID());
        return t != null && !t.recording && t.waitUntil > 0;
    }

    public static int frames(ServerPlayer p) {
        Take t = TAKES.get(p.getUUID());
        return t == null ? 0 : t.frames.size();
    }

    // 鏡に触れた
    public static void touch(ServerPlayer p, BlockPos mirror) {
        ServerLevel level = p.serverLevel();
        Take t = TAKES.get(p.getUUID());
        if (t != null && t.recording) {
            stop(p, "message.skyabyss.moon.echo.stored");
            return;
        }
        if (t != null && t.waitUntil > 0 && !t.frames.isEmpty()) {
            release(p, t);
            return;
        }
        // 覚え始める (前の残像は消す)
        clearShade(level, t);
        Take n = new Take(mirror.immutable());
        TAKES.put(p.getUUID(), n);
        setMode(level, mirror, EchoMirrorBlock.Mode.RECORDING);
        level.playSound(null, mirror, RiftBlocks.ECHO_RECORD.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.END_ROD, mirror.getX() + 0.5, mirror.getY() + 0.8, mirror.getZ() + 0.5, 20, 0.4, 0.5, 0.4, 0.03);
        Notice.narrate(p, "message.skyabyss.moon.echo.recording");
    }

    // 覚え終わる (8 秒・もう一度触れた・淵へ落ちた)。鏡の中で待つ
    public static void stop(ServerPlayer p, String msg) {
        Take t = TAKES.get(p.getUUID());
        if (t == null || !t.recording) {
            return;
        }
        ServerLevel level = p.serverLevel();
        t.recording = false;
        t.waitUntil = level.getGameTime() + WAIT;
        setMode(level, t.mirror, EchoMirrorBlock.Mode.WAITING);
        level.playSound(null, t.mirror, RiftBlocks.ECHO_STORED.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        if (msg != null) {
            Notice.narrate(p, msg);
        }
    }

    // 待っている残像を放つ
    static EchoShade release(ServerPlayer p, Take t) {
        ServerLevel level = p.serverLevel();
        clearShade(level, t);
        EchoShade e = EchoShade.replay(level, p, t.frames);
        t.waitUntil = level.getGameTime() + WAIT;
        if (e != null) {
            t.shadeId = e.getId();
            level.playSound(null, t.mirror, RiftBlocks.ECHO_RELEASE.get(), SoundSource.BLOCKS, 1.2f, 1.0f);
        }
        return e;
    }

    // 開発用と自己テスト: 待っている残像を放つ
    public static EchoShade releaseFor(ServerPlayer p) {
        Take t = TAKES.get(p.getUUID());
        return t == null || t.recording || t.frames.isEmpty() ? null : release(p, t);
    }

    // 自己テスト: 覚えた動きを直に入れる
    public static void storeForTest(ServerPlayer p, BlockPos mirror, List<EchoShade.Frame> frames) {
        Take t = new Take(mirror.immutable());
        t.frames.addAll(frames);
        t.recording = false;
        t.waitUntil = p.serverLevel().getGameTime() + WAIT;
        TAKES.put(p.getUUID(), t);
    }

    public static void forget(ServerPlayer p) {
        Take t = TAKES.remove(p.getUUID());
        if (t != null) {
            clearShade(p.serverLevel(), t);
            setMode(p.serverLevel(), t.mirror, EchoMirrorBlock.Mode.IDLE);
        }
    }

    private static void clearShade(ServerLevel level, Take t) {
        if (t != null && t.shadeId >= 0 && level.getEntity(t.shadeId) instanceof EchoShade e) {
            e.discard();
        }
    }

    private static void setMode(ServerLevel level, BlockPos mirror, EchoMirrorBlock.Mode mode) {
        BlockState st = level.getBlockState(mirror);
        if (st.getBlock() instanceof EchoMirrorBlock && st.getValue(EchoMirrorBlock.MODE) != mode) {
            level.setBlock(mirror, st.setValue(EchoMirrorBlock.MODE, mode), 3);
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || TAKES.isEmpty()) {
            return;
        }
        Take t = TAKES.get(p.getUUID());
        if (t == null) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (level.dimension() != MoonWorld.LEVEL || !p.isAlive() || !MoonRift.inside(p.blockPosition())) {
            forget(p);
            return;
        }
        if (t.recording) {
            t.frames.add(new EchoShade.Frame(p.position(), p.getYRot(), p.getXRot()));
            if (t.frames.size() % 20 == 0) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), RiftBlocks.ECHO_TICK.get(), SoundSource.PLAYERS, 0.4f, 1.0f + t.frames.size() / 200f);
            }
            if (t.frames.size() >= RECORD) {
                stop(p, "message.skyabyss.moon.echo.stored");
            }
            return;
        }
        if (t.waitUntil > 0 && level.getGameTime() > t.waitUntil && !(t.shadeId >= 0 && level.getEntity(t.shadeId) instanceof EchoShade)) {
            TAKES.remove(p.getUUID());
            setMode(level, t.mirror, EchoMirrorBlock.Mode.IDLE);
        }
    }

    // 淵の外へ出た人・いなくなった人の片付け (RiftTicker から 20 tick ごと)
    static void sweep(ServerLevel level) {
        for (Iterator<Map.Entry<UUID, Take>> it = TAKES.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Take> e = it.next();
            if (level.getServer().getPlayerList().getPlayer(e.getKey()) == null) {
                setMode(level, e.getValue().mirror, EchoMirrorBlock.Mode.IDLE);
                it.remove();
            }
        }
    }
}
