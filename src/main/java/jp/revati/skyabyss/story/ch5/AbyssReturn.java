package jp.revati.skyabyss.story.ch5;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

// 地上へ帰る磁石石の祠 (骨の野営の南西。印 exit。2026-10-23 の試遊「深淵から帰れるロードストーン。派手にしてもいいかもな。これで帰れたんだってなる」)。
//   祠: 白い石の壇・柱の先の灯り・奥の壁の空色の光 (tools/abyss/gen_abyss_layers.py の build_return_shrine)
//   見回り (0.5 秒ごと、20 以内に人がいるとき): 鎖にそって上る白い光・壇のまわりの空色の粒、4 秒ごとに鐘のような低い音
//   帰る: 磁石石か鎖を右クリック → 体がふわりと浮き、光が集まり、画面が白く明るくなる (AbyssNetwork.Flash) → 白の中で都の下の門の前へ
//         → 着いた所に光と鐘の音と「地上へ戻った」の一言。2 秒のあいだに二度押しても一度だけ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssReturn {
    static final int RISE = 26;          // 浮き始めから運ぶまで
    static final int FLASH_IN = 18;
    static final int FLASH_HOLD = 12;
    static final int FLASH_OUT = 34;
    static final int SKY = 0xFFF6E6;
    static final DustParticleOptions SKY_DUST = new DustParticleOptions(new Vector3f(0.65f, 0.88f, 1f), 1.4f);

    private static final Map<UUID, Long> GOING = new HashMap<>();

    private AbyssReturn() {
    }

    public static boolean going(ServerPlayer p) {
        return GOING.containsKey(p.getUUID());
    }

    // 帰り始める (move = 偽なら運ばない。自己テスト用)
    public static boolean begin(ServerPlayer p, boolean move) {
        if (GOING.containsKey(p.getUUID())) {
            return false;
        }
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        GOING.put(p.getUUID(), now);
        BlockPos exit = AbyssBuild.mark("exit");
        double cx = exit != null ? exit.getX() + 0.5 : p.getX();
        double cy = exit != null ? exit.getY() : p.getY();
        double cz = exit != null ? exit.getZ() + 0.5 : p.getZ();
        level.playSound(null, cx, cy, cz, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.6f, 1.1f);
        level.playSound(null, cx, cy, cz, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.4f, 0.8f);
        level.sendParticles(ParticleTypes.END_ROD, cx, cy + 1.2, cz, 40, 0.6, 1.2, 0.6, 0.04);
        level.sendParticles(SKY_DUST, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.9, 0.5, 0);
        p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, RISE + 6, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, RISE + 60, 0, false, false));
        Notice.bar(p, net.minecraft.network.chat.Component.translatable("message.skyabyss.abyss_v3.return.rise"));
        if (p.connection != null && !(p instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            PacketDistributor.sendToPlayer(p, new AbyssNetwork.Flash(FLASH_IN, FLASH_HOLD, FLASH_OUT, SKY));
        }
        // 光が集まる (浮いている間)
        for (int k = 1; k <= 3; k++) {
            int at = k * 7;
            later(p.server, at, () -> {
                if (p.level() == level) {
                    level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 16, 0.7, 0.8, 0.7, 0.02);
                    level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.8f + 0.2f * at / 7f);
                }
            });
        }
        later(p.server, FLASH_IN + 4, () -> {
            GOING.remove(p.getUUID());
            if (!move) {
                return;
            }
            p.removeEffect(MobEffects.LEVITATION);
            p.fallDistance = 0;
            AbyssGate.up(p);
            arrived(p);
        });
        return true;
    }

    // 着いた所 (都の下の門の前): 光と鐘と一言
    static void arrived(ServerPlayer p) {
        ServerLevel ow = p.serverLevel();
        ow.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 50, 0.6, 1.4, 0.6, 0.05);
        ow.sendParticles(SKY_DUST, p.getX(), p.getY() + 0.5, p.getZ(), 40, 1.2, 0.3, 1.2, 0);
        ow.playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.2f, 0.7f);
        ow.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8f, 1.4f);
        later(p.server, 30, () -> Notice.place(p, net.minecraft.network.chat.Component.translatable("message.skyabyss.abyss_v3.return.title"),
                net.minecraft.network.chat.Component.translatable("message.skyabyss.abyss_v3.return.sub")));
    }

    // ---------------------------------------------------------------- 見回り (祠の光と音)
    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        runLater(server);
        if (server.getTickCount() % 10 != 6) {
            return;
        }
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null || level.players().isEmpty() || !AbyssBuild.built(server)) {
            return;
        }
        BlockPos exit = AbyssBuild.mark("exit");
        if (exit == null || !level.isLoaded(exit)) {
            return;
        }
        boolean near = false;
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().closerThan(exit, 20)) {
                near = true;
                break;
            }
        }
        if (!near) {
            return;
        }
        double x = exit.getX() + 0.5, y = exit.getY(), z = exit.getZ() + 0.5;
        level.sendParticles(ParticleTypes.END_ROD, x, y + 1.5 + level.random.nextDouble() * 4, z, 2, 0.08, 0.6, 0.08, 0.01);
        double a = (server.getTickCount() / 10 % 12) * Math.PI / 6;
        level.sendParticles(SKY_DUST, x + Math.cos(a) * 1.4, y + 0.3, z + Math.sin(a) * 1.4, 2, 0.05, 0.05, 0.05, 0);
        level.sendParticles(SKY_DUST, x - Math.cos(a) * 1.4, y + 0.3, z - Math.sin(a) * 1.4, 2, 0.05, 0.05, 0.05, 0);
        if (server.getTickCount() % 80 == 6) {
            level.playSound(null, exit, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.0f, 1.2f);
        }
    }

    private record Pending(int at, Runnable run) {
    }

    private static final List<Pending> LATER = new java.util.ArrayList<>();

    static void later(MinecraftServer server, int ticks, Runnable r) {
        LATER.add(new Pending(server.getTickCount() + ticks, r));
    }

    private static void runLater(MinecraftServer server) {
        if (LATER.isEmpty()) {
            return;
        }
        int now = server.getTickCount();
        List<Pending> due = new java.util.ArrayList<>();
        LATER.removeIf(e -> e.at() <= now && due.add(e));
        due.forEach(e -> e.run().run());
    }

    // 自己テスト: 待ちを今すぐ流す
    static void flushForTest(MinecraftServer server) {
        List<Pending> due = List.copyOf(LATER);
        LATER.clear();
        due.forEach(e -> e.run().run());
    }
}
