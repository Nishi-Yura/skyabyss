package jp.revati.skyabyss.titan.ember;

import java.util.List;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.TitanWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 温石 (おんじゃく、13 の 2.6節。ID は warm_stone、11.2節の HearthStoneItem)。灰の民の焼き物の小石で、余燼の巨神を探す手間を減らす。
// - 手に持つ: 画面に巨神の向き (手の温かさの左右)、距離の三段、眠っている／地の中、次の眠りまで (EmberTitanClient が描く)
// - 握る (右クリック): 日没の 1 分前から眠りの始まりまでに、陸の上で握ると、その夜は自分のそばで眠るよう頼める (一夜に一人)
// - 入手: 自分で雷翼の心臓を止めた人に一つ渡す (雷翼のファイルは触らず、進みの記録を 5 秒ごとに見る)。
//   なくしたら作業台で作り直せる (余燼の欠片 + 石 4 + マグマブロック。data/skyabyss/recipe/warm_stone.json)
public class WarmStoneItem extends Item {
    // 渡したかの印 (人の持続データ)
    static final String GIVEN = "skyabyss_warm_stone_given";
    // 距離の三段の境 (ブロック)
    static final double NEAR = 400;
    static final double CLOSE = 120;

    public WarmStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)) {
            return InteractionResultHolder.success(stack);
        }
        player.getCooldowns().addCooldown(this, 40);
        EmberTitanEntity titan = server.dimension() == Level.OVERWORLD ? EmberKeeper.KEEPER.current(server) : null;
        if (titan == null) {
            sp.displayClientMessage(ModText.hint(EmberIds.LANG_SURFACE + "stone.cold"), true);
            return InteractionResultHolder.consume(stack);
        }
        if (!player.onGround() || player.isInWater()) {
            sp.displayClientMessage(ModText.hint(EmberIds.LANG_SURFACE + "stone.not_land"), true);
            return InteractionResultHolder.consume(stack);
        }
        EmberTitanEntity.RequestResult r = titan.request(sp);
        switch (r) {
            case OK -> {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5f, 0.6f);
                sp.sendSystemMessage(ModText.lore(EmberIds.LANG_SURFACE + "stone.asked"));
                for (ServerPlayer other : server.players()) {
                    if (other != sp && holds(other)) {
                        other.sendSystemMessage(ModText.hint(EmberIds.LANG_SURFACE + "stone.asked_other", sp.getDisplayName()));
                    }
                }
            }
            case NOT_NOW -> sp.displayClientMessage(ModText.hint(EmberIds.LANG_SURFACE + "stone.not_now"), true);
            case TAKEN -> sp.displayClientMessage(ModText.hint(EmberIds.LANG_SURFACE + "stone.taken", titan.requesterTonight()), true);
            case NO_SPOT -> sp.displayClientMessage(ModText.hint(EmberIds.LANG_SURFACE + "stone.no_spot"), true);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(EmberIds.LANG_SURFACE + "stone.tooltip_1").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(EmberIds.LANG_SURFACE + "stone.tooltip_2").withStyle(ChatFormatting.GRAY));
    }

    static boolean holds(Player player) {
        return player.getMainHandItem().getItem() instanceof WarmStoneItem || player.getOffhandItem().getItem() instanceof WarmStoneItem;
    }

    // 手の温かさ (向き): 巨神が右なら 1、左なら -1、前なら 0、後ろなら 2
    public static int side(float playerYaw, double dx, double dz) {
        float to = EmberGround.yawOf(dx, dz);
        float d = Mth.wrapDegrees(to - playerYaw);
        if (Math.abs(d) <= 30f) {
            return 0;
        }
        if (Math.abs(d) >= 150f) {
            return 2;
        }
        // yaw は時計回り (南 0、西 90)。向きの差が正なら右
        return d > 0 ? 1 : -1;
    }

    // 距離の三段: 0 遠い、1 近い、2 すぐそこ
    public static int distanceTier(double d) {
        return d < CLOSE ? 2 : d < NEAR ? 1 : 0;
    }

    // 温石を持つ人へ様子を送る (1 秒ごと)
    static EmberSurfaceNetwork.StoneInfo info(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        long until = EmberGround.ticksUntilSleep(overworld.getDayTime());
        EmberTitanEntity titan = EmberKeeper.KEEPER.current(overworld);
        TitanWorldData.Slot slot = EmberKeeper.KEEPER.slot(overworld);
        Vec3 at = titan != null ? titan.position() : slot.lastPosition();
        if (player.level() != overworld || at == null || !slot.spawned) {
            return new EmberSurfaceNetwork.StoneInfo(false, 0, 0, false, (int) until, "");
        }
        double dx = at.x - player.getX();
        double dz = at.z - player.getZ();
        boolean sleeping = titan != null ? titan.isSurfaced() : slot.state.getInt("Mode") >= EmberTitanEntity.Mode.RISE.ordinal();
        String asked = titan != null ? titan.requesterTonight() : "";
        return new EmberSurfaceNetwork.StoneInfo(true, side(player.getYRot(), dx, dz), distanceTier(Math.hypot(dx, dz)), sleeping,
                (int) until, asked);
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
            if (player.tickCount % 20 == 7 && holds(player)) {
                EmberSurfaceNetwork.send(player, info(player));
            }
            if (player.tickCount % 100 == 31) {
                giveIfEarned(player);
            }
        }
    }

    // 自分で雷翼の心臓を止めた人に、一人一つ渡す (一度だけ。なくしたら作り直す)
    static boolean giveIfEarned(ServerPlayer player) {
        if (player.getPersistentData().getBoolean(GIVEN) || !Progression.stopped(player.server, player.getUUID(), Titans.THUNDER_WING)) {
            return false;
        }
        player.getPersistentData().putBoolean(GIVEN, true);
        ItemStack stone = new ItemStack(EmberTitanSetup.WARM_STONE.get());
        if (!player.getInventory().add(stone)) {
            player.drop(stone, false);
        }
        player.sendSystemMessage(ModText.lore(EmberIds.LANG_SURFACE + "stone.given"));
        return true;
    }
}
