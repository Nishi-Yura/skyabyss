package jp.revati.skyabyss.titan.tide.chapter;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.TitanWorldData;
import jp.revati.skyabyss.titan.tide.TideKeeper;
import jp.revati.skyabyss.titan.tide.TideOcean;
import jp.revati.skyabyss.titan.tide.TideShellEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 潮見の貝 (潮を見る貝。08 の 2.6節)。海辺で巨神を探す手間を減らす道具。
// - 手に持つ: 一秒ごとに画面下の帯 (Notice.bar) へ、巨神の向き (八方位)、距離の三段 (遠い・近い・すぐそこ)、次の干潮の刻まで (分)
// - 吹く (右クリック): 海から 32 ブロック以内で、刻の 1 分前から刻の途中までに吹くと、その刻は自分のそばへ寄るよう頼める (一つの刻に一人)。
//   巨神が頼みを受けると TideShellEntity.request が休む場所を決めて向かう。ほかに貝を持つ人には「○○が呼んだ」と知らせる
// - 入手: 二章の小要素「漂着の荷」で一人に一つ。作業台では作らない
public class TideConchItem extends Item {
    // 距離の三段の境 (ブロック)
    public static final double NEAR = 320;
    public static final double CLOSE = 96;
    // 吹ける海からの距離
    public static final int SEA_RANGE = 32;

    // 読み取り。found = false なら沖に島の気配がない。compass 0=北 1=北東 ... 7=北西、tier 0=遠い 1=近い 2=すぐそこ
    public record Reading(boolean found, int compass, int tier, int minutesToLow, boolean lowNow, String asked) {
        public static final Reading NONE = new Reading(false, 0, 0, 0, false, "");
    }

    public TideConchItem(Properties properties) {
        super(properties);
    }

    // 向きを八方位に (東 +x、南 +z。北 = 0)
    public static int compass(double dx, double dz) {
        double bearing = Math.toDegrees(Math.atan2(dx, -dz));
        return Math.floorMod((int) Math.round(bearing / 45.0), 8);
    }

    public static int tier(double distance) {
        return distance < CLOSE ? 2 : distance < NEAR ? 1 : 0;
    }

    // 純粋な部分 (自己テストが直接呼ぶ)
    public static Reading reading(Vec3 player, Vec3 titan, long dayTime, String asked) {
        double dx = titan.x - player.x;
        double dz = titan.z - player.z;
        long until = TideOcean.ticksUntilLowTide(dayTime);
        return new Reading(true, compass(dx, dz), tier(Math.hypot(dx, dz)), (int) Math.ceil(until / 1200.0), until == 0, asked);
    }

    // この人の今の読み取り。オーバーワールドの外、巨神がまだ現れていなければ NONE
    public static Reading read(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        TitanWorldData.Slot slot = TideKeeper.KEEPER.slot(overworld);
        if (player.level() != overworld || !slot.spawned) {
            return Reading.NONE;
        }
        TideShellEntity titan = TideKeeper.KEEPER.current(overworld);
        Vec3 at = titan != null ? titan.position() : slot.lastPosition();
        if (at == null) {
            return Reading.NONE;
        }
        return reading(player.position(), at, overworld.getDayTime(), titan != null ? titan.requesterNow() : "");
    }

    public static Component describe(Reading r) {
        if (!r.found()) {
            return Component.translatable("message.skyabyss.ch2.conch.none");
        }
        Component dir = Component.translatable("message.skyabyss.ch2.conch.dir." + r.compass());
        Component tier = Component.translatable("message.skyabyss.ch2.conch.tier." + r.tier());
        Component line = r.lowNow()
                ? Component.translatable("message.skyabyss.ch2.conch.reading_low", dir, tier)
                : Component.translatable("message.skyabyss.ch2.conch.reading", dir, tier, r.minutesToLow());
        return r.asked().isEmpty() ? line : Component.empty().append(line).append(Component.translatable("message.skyabyss.ch2.conch.asked_suffix", r.asked()));
    }

    public static boolean holds(Player player) {
        return player.getMainHandItem().getItem() instanceof TideConchItem || player.getOffhandItem().getItem() instanceof TideConchItem;
    }

    // 水 (海面) が range ブロック以内にあるか (8 方向を 4 ブロックごとに見る)
    public static boolean nearWater(ServerLevel level, double x, double z, int range) {
        if (TideOcean.waterAtSurface(level, x, z)) {
            return true;
        }
        for (int dir = 0; dir < 8; dir++) {
            double ax = Math.cos(Math.PI / 4 * dir);
            double az = Math.sin(Math.PI / 4 * dir);
            for (int s = 4; s <= range; s += 4) {
                if (TideOcean.waterAtSurface(level, x + ax * s, z + az * s)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)) {
            return InteractionResultHolder.success(stack);
        }
        player.getCooldowns().addCooldown(this, 40);
        blow(sp, server);
        return InteractionResultHolder.consume(stack);
    }

    // 吹く。結果を返す (自己テストが使う)。巨神がいない・海から遠い場合は null
    @Nullable
    static TideShellEntity.RequestResult blow(ServerPlayer sp, ServerLevel server) {
        TideShellEntity titan = server.dimension() == Level.OVERWORLD ? TideKeeper.KEEPER.current(server) : null;
        if (titan == null) {
            Notice.bar(sp, Component.translatable("message.skyabyss.ch2.conch.none"));
            return null;
        }
        if (!nearWater(server, sp.getX(), sp.getZ(), SEA_RANGE)) {
            Notice.bar(sp, Component.translatable("message.skyabyss.ch2.conch.far_from_sea"));
            return null;
        }
        TideShellEntity.RequestResult r = titan.request(sp);
        switch (r) {
            case OK -> {
                server.playSound(null, sp.getX(), sp.getY(), sp.getZ(), TideChapterSetup.CONCH_FAR.get(), SoundSource.PLAYERS, 1.6f, 1.0f);
                Notice.narrate(sp, "message.skyabyss.ch2.conch.asked");
                for (ServerPlayer other : server.players()) {
                    if (other != sp && holds(other)) {
                        Notice.hint(other, "message.skyabyss.ch2.conch.asked_other", sp.getDisplayName());
                    }
                }
            }
            case NOT_NOW -> Notice.bar(sp, Component.translatable("message.skyabyss.ch2.conch.not_now"));
            case TAKEN -> Notice.bar(sp, Component.translatable("message.skyabyss.ch2.conch.taken", titan.requesterNow()));
            case NO_SPOT -> Notice.bar(sp, Component.translatable("message.skyabyss.ch2.conch.no_spot"));
        }
        return r;
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID)
    static final class Events {
        private Events() {
        }

        @SubscribeEvent
        static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
                return;
            }
            if (player.tickCount % 20 == 11 && holds(player)) {
                Notice.bar(player, describe(read(player)));
            }
        }
    }
}
