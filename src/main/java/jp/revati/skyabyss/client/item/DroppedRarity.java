package jp.revati.skyabyss.client.item;

import org.joml.Vector3f;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.ui.GearTierFrames;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.loot.box.SealedBoxItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

// 地面に落ちた装備 (武器・防具・装飾) と封の箱は、稀さが色で分かる (2026-11-06 の声「ドロップしたとき、色でレア度がわかる様に」)。
//  - 名札: 品の名前を階級の色 (箱は箱の色) で、品の上に出す (数が 2 以上なら「×数」)。名札の見える距離はバニラと同じ (64 ブロック)
//  - 光の粒: アンコモン以上と箱は、階級の色の粒が品から立ちのぼる。英雄より上は粒が多く、高くまで昇る (遠くからでも分かる)
// 色は持ち物の枠・ツールチップの縁 (GearTierFrames) と同じ。クライアントだけで描く (サーバーは何も送らない)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class DroppedRarity {
    private DroppedRarity() {
    }

    // 粒を出す距離 (ブロック) と間隔 (tick)
    static final double PARTICLE_RANGE = 32;
    static final int PARTICLE_EVERY = 3;

    // 品の稀さの色。色を付けない品は -1
    static int colorOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }
        if (stack.getItem() instanceof SealedBoxItem box) {
            return SealedBoxItem.color(box.kind);
        }
        GearTier tier = GearTierFrames.tierOf(stack);
        return tier == null ? -1 : tier.rgb();
    }

    // 粒の強さ: 0 = 出さない、1 = 少し、2 = 多く高く
    static int glowOf(ItemStack stack) {
        if (stack.getItem() instanceof SealedBoxItem) {
            return 1;
        }
        GearTier tier = GearTierFrames.tierOf(stack);
        if (tier == null || tier == GearTier.COMMON) {
            return 0;
        }
        return tier.ordinal() >= GearTier.HEROIC.ordinal() ? 2 : 1;
    }

    @SubscribeEvent
    static void onNameTag(RenderNameTagEvent event) {
        Entity e = event.getEntity();
        if (!(e instanceof ItemEntity item)) {
            return;
        }
        ItemStack stack = item.getItem();
        int rgb = colorOf(stack);
        if (rgb < 0) {
            return;
        }
        MutableComponent name = stack.getHoverName().copy().withStyle(s -> s.withColor(rgb));
        if (stack.getCount() > 1) {
            name.append(Component.literal(" ×" + stack.getCount()).withStyle(s -> s.withColor(rgb)));
        }
        event.setContent(name);
        event.setCanRender(TriState.TRUE);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused() || mc.level.getGameTime() % PARTICLE_EVERY != 0) {
            return;
        }
        RandomSource r = mc.level.random;
        for (ItemEntity item : mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(PARTICLE_RANGE))) {
            ItemStack stack = item.getItem();
            int glow = glowOf(stack);
            if (glow <= 0) {
                continue;
            }
            int rgb = colorOf(stack);
            Vector3f c = new Vector3f((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f);
            DustParticleOptions dust = new DustParticleOptions(c, glow >= 2 ? 1.3f : 0.9f);
            int n = glow >= 2 ? 3 : 1;
            for (int i = 0; i < n; i++) {
                double x = item.getX() + (r.nextDouble() - 0.5) * 0.35;
                double z = item.getZ() + (r.nextDouble() - 0.5) * 0.35;
                double y = item.getY() + 0.2 + r.nextDouble() * (glow >= 2 ? 1.6 : 0.8);
                mc.level.addParticle(dust, x, y, z, 0, glow >= 2 ? 0.06 : 0.03, 0);
            }
        }
    }
}
