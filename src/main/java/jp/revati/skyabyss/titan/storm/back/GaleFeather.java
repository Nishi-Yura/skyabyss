package jp.revati.skyabyss.titan.storm.back;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.storm.StormTravel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 風切りの羽根 (12 の 4.1節)。西の試練の肩の門で手に入る試練の品。持っていると、空中でしゃがむ間だけ滑空する
// (落ちる速さ 0.08、前へ 0.35)。持ち物にあればよい (手に持たなくてよい)。試練の外へ出る・倒れると消える (StormWest)。
// 動きはプレイヤーの側 (クライアント) で付ける (しゃがむ入力と同じ所で動かすので、遅れて揺れない)。サーバーは落下の距離を消すだけ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public class GaleFeather extends Item {
    public static final double FALL = 0.08;
    public static final double FORWARD = 0.35;

    public GaleFeather(Properties p) {
        super(p);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.skyabyss.gale_feather.desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.skyabyss.gale_feather.desc2").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    public static boolean holds(Player p) {
        return p.getInventory().hasAnyMatching(s -> s.getItem() instanceof GaleFeather);
    }

    // 滑空しているか (空中・しゃがむ・下へ落ちている・羽根を持つ・飛んでいない)
    public static boolean gliding(Player p) {
        return p.level().dimension() == StormTravel.STORM_BACK && !p.onGround() && p.isShiftKeyDown() && !p.getAbilities().flying
                && !p.isInWater() && p.getDeltaMovement().y < 0 && holds(p);
    }

    // 1 tick の滑空の動き (前へ 0.35 まで寄せ、落ちる速さは 0.08 まで)
    public static Vec3 glide(Entity e, Vec3 v) {
        Vec3 look = e.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-4) {
            flat = new Vec3(0, 0, 0);
        } else {
            flat = flat.normalize().scale(FORWARD);
        }
        double vx = v.x + (flat.x - v.x) * 0.25;
        double vz = v.z + (flat.z - v.z) * 0.25;
        return new Vec3(vx, Math.max(v.y, -FALL), vz);
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player p = event.getEntity();
        if (!gliding(p)) {
            return;
        }
        p.fallDistance = 0;
        Level level = p.level();
        if (level.isClientSide && p.isLocalPlayer()) {
            p.setDeltaMovement(glide(p, p.getDeltaMovement()));
        }
    }
}
