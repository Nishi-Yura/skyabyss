package jp.revati.skyabyss.titan.storm;

import java.util.List;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.TitanKeeper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

// 風見鶏 (12 の 2.6節)。鯨追いのイサナが「古い地図」の報酬で渡す。
// 手に持つ: 画面の下に、次の夕立の刻までの時間と、雷翼までの距離 (遠い・近い・すぐそこ) を一行。
// 掲げる (右クリック): 空が見え、まわり 16 ブロックでいちばん高い所 (または y 100 以上) で掲げると、風見鶏に雷が落ち (演出だけ)、
// 雷翼がその人の近くの峰へ降りて 3 分止まる。ワールドで 10 分に一度まで。ほかの人には「○○が嵐を呼んだ」。
// 鶏の向きが雷翼を指す手の中のモデル (回る) は、品の絵 (ラフ 3 案) と一緒に入れる。今は避雷針の形を借りている (00 に書いた)
public class WeathercockItem extends Item {
    static final int CALL_COOLDOWN = 12000;   // 10 分
    static final int CALL_REST = 3600;        // 3 分止まる
    static final int HIGH_Y = 100;
    static final int HIGH_RADIUS = 16;
    static final double NEAR = 400;
    static final double CLOSE = 120;

    // ワールドで最後に呼んだ時刻 (ゲームの時刻)。再起動で忘れる (10 分の待ちが消えるだけ)
    private static long lastCall = Long.MIN_VALUE / 2;

    public WeathercockItem(Properties p) {
        super(p);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.skyabyss.weathercock.desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.skyabyss.weathercock.desc2").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.success(stack);
        }
        player.getCooldowns().addCooldown(this, 40);
        BlockPos pos = sp.blockPosition();
        if (level.dimension() != Level.OVERWORLD || !level.canSeeSky(pos.above())) {
            sp.displayClientMessage(ModText.deny("message.skyabyss.weathercock.no_sky"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!high(sl, pos)) {
            sp.displayClientMessage(ModText.deny("message.skyabyss.weathercock.too_low"), true);
            return InteractionResultHolder.fail(stack);
        }
        long now = sl.getGameTime();
        if (now - lastCall < CALL_COOLDOWN) {
            long minutes = (CALL_COOLDOWN - (now - lastCall) + 1199) / 1200;
            sp.displayClientMessage(ModText.deny("message.skyabyss.weathercock.wait", minutes), true);
            return InteractionResultHolder.fail(stack);
        }
        StormWingEntity titan = StormKeeper.KEEPER.current(sl);
        if (titan == null || !titan.comeRest(sp, CALL_REST)) {
            sp.displayClientMessage(ModText.hint("message.skyabyss.weathercock.absent"), true);
            return InteractionResultHolder.fail(stack);
        }
        lastCall = now;
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
        if (bolt != null) {
            bolt.moveTo(sp.getX(), sp.getY() + 1.5, sp.getZ());
            bolt.setVisualOnly(true);
            sl.addFreshEntity(bolt);
        }
        sp.sendSystemMessage(ModText.lore("message.skyabyss.weathercock.called"));
        for (ServerPlayer other : sl.players()) {
            if (other != sp && holds(other)) {
                other.displayClientMessage(ModText.hint("message.skyabyss.weathercock.called_by", sp.getDisplayName()), true);
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    // y 100 以上か、まわり 16 ブロックの地表がどこも足もとより高くない
    static boolean high(ServerLevel level, BlockPos pos) {
        if (pos.getY() >= HIGH_Y) {
            return true;
        }
        for (int dx = -HIGH_RADIUS; dx <= HIGH_RADIUS; dx += 2) {
            for (int dz = -HIGH_RADIUS; dz <= HIGH_RADIUS; dz += 2) {
                if (dx * dx + dz * dz > HIGH_RADIUS * HIGH_RADIUS) {
                    continue;
                }
                if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX() + dx, pos.getZ() + dz) > pos.getY()) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean holds(Player p) {
        return p.getMainHandItem().getItem() instanceof WeathercockItem || p.getOffhandItem().getItem() instanceof WeathercockItem;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level instanceof ServerLevel sl && entity instanceof ServerPlayer sp && sp.tickCount % 20 == 0
                && (selected || sp.getOffhandItem() == stack)) {
            sp.displayClientMessage(status(sl, sp), true);
        }
    }

    // 「次の夕立の刻まで 2:30 · 近い」
    static MutableComponent status(ServerLevel level, ServerPlayer player) {
        StormWingEntity titan = level.dimension() == Level.OVERWORLD ? StormKeeper.KEEPER.current(level) : null;
        if (titan == null || !TitanKeeper.eligible(player)) {
            return ModText.hint("message.skyabyss.weathercock.absent");
        }
        long dayTime = level.getDayTime();
        MutableComponent when;
        if (StormWingEntity.inWindow(dayTime) || titan.isPerched()) {
            when = Component.translatable("message.skyabyss.weathercock.now");
        } else {
            long t = Math.floorMod(dayTime, 24000L);
            long left = Math.floorMod(StormWingEntity.WINDOW_START - t, 24000L) / 20;
            when = Component.translatable("message.skyabyss.weathercock.next", String.format("%d:%02d", left / 60, left % 60));
        }
        double d = Math.hypot(titan.getX() - player.getX(), titan.getZ() - player.getZ());
        String dist = d > NEAR ? "far" : d > CLOSE ? "near" : "close";
        return Component.empty().append(when).append("  ·  ").append(Component.translatable("message.skyabyss.weathercock." + dist))
                .withStyle(ChatFormatting.GRAY);
    }
}
