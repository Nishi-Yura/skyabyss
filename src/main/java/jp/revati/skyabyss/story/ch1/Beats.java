package jp.revati.skyabyss.story.ch1;

import java.util.List;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.OpeningDirector;
import jp.revati.skyabyss.registry.ModSounds;
import jp.revati.skyabyss.sky.ChapterSky;
import jp.revati.skyabyss.sky.ChapterTintPayload;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.arc.Beat;
import jp.revati.skyabyss.story.arc.ForeshadowRegisterEvent;
import jp.revati.skyabyss.titan.WhaleKeeper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// 一章の予兆 3 拍 (docs/design/01 の 4.1「予兆 3 拍」。予兆の枠は story/arc/Foreshadow): 岩鯨が現れる前に、痕跡 → 間接 → 一部。
//   ① 痕跡 (昼・空の見える所): 晴れているのに、乾いた石の粉が空からうっすら降る
//   ② 間接 (昼・空の見える所): 雲ひとつないのに、地面の上だけを大きな影が渡る (霧の色が暗くなって戻る。動物は空を見上げて固まる)
//   ③ 一部 (夕方〜夜): 空の高みで鎖が鳴り、石の環が一つ足もとに落ちてくる (拾える。本人だけ)
// どれも、岩鯨がまだ現れていない世界で、静かな状態 (OpeningDirector.quiet) のときだけ。ダメージ・敵・ジャンプスケアなし
public final class Beats {
    static final String DUST = "stone_dust";
    static final String SHADOW = "ground_shadow";
    static final String CHAIN = "chain_link";
    public static final List<String> IDS = List.of(Chapters.WHALE.id() + "/" + DUST, Chapters.WHALE.id() + "/" + SHADOW, Chapters.WHALE.id() + "/" + CHAIN);

    // 一章の空の段 (docs/design/01 の 4.1「空の異変の段」): 四章までで一番ごく弱い。月がほんの少し近く、空に石灰色が薄く (二章 0.02/0.05、三章 0.04/0.07、四章 0.08/0.12 と順に濃くなる)
    public static final ChapterSky.Stage SKY = new ChapterSky.Stage(0.01f, 0x7C838C, 0.03f);

    private Beats() {
    }

    static void register(ForeshadowRegisterEvent event) {
        ChapterSky.setStage(Chapters.WHALE, SKY);
        event.register(Beat.of(Chapters.WHALE, 1, DUST, Beats::dust).time(Beat.Time.DAY).cooldown(2400).when(Beats::ready));
        event.register(Beat.of(Chapters.WHALE, 2, SHADOW, Beats::shadow).time(Beat.Time.DAY).cooldown(4800).when(Beats::ready));
        event.register(Beat.of(Chapters.WHALE, 3, CHAIN, Beats::chain).time(Beat.Time.DUSK).cooldown(4800).when(Beats::ready));
    }

    // 共通の条件: 岩鯨がまだ現れていない・静かな状態
    static boolean ready(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        return !whaleSpawned(level) && OpeningDirector.quiet(level, player);
    }

    static boolean whaleSpawned(ServerLevel level) {
        return WhaleKeeper.everSpawned(level.getServer().overworld());
    }

    private static boolean skyVisible(ServerPlayer player) {
        return player.serverLevel().canSeeSky(player.blockPosition().above());
    }

    // ① 石の粉
    static boolean dust(ServerPlayer player, Beat beat) {
        if (!skyVisible(player)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        BlockParticleOption grit = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.ANDESITE.defaultBlockState());
        for (int i = 0; i < 28; i++) {
            Tasks.later(i * 6, () -> {
                if (player.isRemoved() || player.serverLevel() != level) {
                    return;
                }
                level.sendParticles(player, grit, true, player.getX(), player.getY() + 6.0, player.getZ(), 10, 4.5, 1.5, 4.5, 0.0);
            });
        }
        Notice.narrate(player, "message.skyabyss.ch1.beat.dust");
        return true;
    }

    // ② 地面を渡る影
    static boolean shadow(ServerPlayer player, Beat beat) {
        if (!skyVisible(player) || player.serverLevel().isRaining()) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        int[] ramp = {0, 10, 20, 30, 80, 90, 100, 110};
        float[] strength = {0.08f, 0.18f, 0.28f, 0.35f, 0.35f, 0.25f, 0.12f, 0f};
        for (int i = 0; i < ramp.length; i++) {
            float s = strength[i];
            // その時の本人を名前 (UUID) から引き直す (倒れて起き直すと別の人の物になり、最後の「戻す」が送られず暗いままだった。2026-10-22 の二人の試遊)
            java.util.UUID id = player.getUUID();
            net.minecraft.server.MinecraftServer server = player.server;
            Tasks.later(ramp[i], () -> {
                ServerPlayer cur = server.getPlayerList().getPlayer(id);
                if (cur != null) {
                    tint(cur, s);
                }
            });
        }
        for (int t = 0; t <= 100; t += 10) {
            Tasks.later(t, () -> {
                if (player.isRemoved() || player.serverLevel() != level) {
                    return;
                }
                for (Animal animal : level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(24))) {
                    animal.getNavigation().stop();
                    animal.getLookControl().setLookAt(animal.getX(), animal.getY() + 40.0, animal.getZ());
                    animal.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 6, false, false));
                }
            });
        }
        Notice.narrate(player, "message.skyabyss.ch1.beat.shadow");
        return true;
    }

    // 霧の色を暗い青灰へ s だけ寄せる。0 なら章の段の色味へ戻す
    static void tint(ServerPlayer player, float s) {
        if (player.isRemoved() || player.connection == null) {
            return;
        }
        if (s <= 0f) {
            ChapterSky.Stage stage = ChapterSky.stage(player);
            PacketDistributor.sendToPlayer(player, new ChapterTintPayload(stage.tintRgb(), stage.tintStrength()));
        } else {
            PacketDistributor.sendToPlayer(player, new ChapterTintPayload(0x0E1320, s));
        }
    }

    // ③ 空から鎖の環
    static boolean chain(ServerPlayer player, Beat beat) {
        if (!skyVisible(player)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (player.connection != null) {
            player.connection.send(new ClientboundSoundPacket(ModSounds.TITAN_CHAIN, SoundSource.AMBIENT, player.getX() + 30, player.getY() + 70, player.getZ() - 20,
                    6.0f, 0.7f, level.random.nextLong()));
        }
        Tasks.later(50, () -> {
            if (player.isRemoved() || player.serverLevel() != level) {
                return;
            }
            // その人の頭の上から落とす (目線の先だとブロックの奥に落ちることがあった。2026-10-27 の声)
            Vec3 at = player.position().add(0, 6.0, 0);
            ItemStack link = new ItemStack(Items.CHAIN);
            link.set(DataComponents.CUSTOM_NAME, Component.translatable("item.skyabyss.ch1.whale_link").withStyle(s -> s.withItalic(false)));
            link.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.skyabyss.ch1.whale_link.lore"))));
            ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, link);
            item.setDeltaMovement(0, -0.1, 0);
            item.setUnlimitedLifetime();
            item.setTarget(player.getUUID());
            level.addFreshEntity(item);
            Notice.narrate(player, "message.skyabyss.ch1.beat.chain");
        });
        return true;
    }
}
