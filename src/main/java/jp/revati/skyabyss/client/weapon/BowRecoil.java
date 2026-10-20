package jp.revati.skyabyss.client.weapon;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// 雷翼の弓を引いて放した「反動」(クライアントだけ。ほかのプレイヤーにも同じに見える)。
// 引いている間はバニラの構え (腕を前へ・弦を引く) のまま。放した瞬間から数 tick、弓の手が跳ね上がり、弦を引いていた手が外へ開き、
// そこから普段の構えへ戻る。強く引いたほど大きく跳ねる。矢が実際に飛んだかどうかは見ない (引いてやめても同じ動き)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class BowRecoil {
    // 反動の長さ (tick)
    static final float LENGTH = 7f;

    private static final class State {
        boolean wasDrawing;
        int drawTicks;
        long releasedAt = Long.MIN_VALUE / 2;
        float power;
        // 放したあとの返しの長さ (tick。MoveSet の fixedDuration)
        int fixed;
    }

    private static final Int2ObjectOpenHashMap<State> STATES = new Int2ObjectOpenHashMap<>();

    private BowRecoil() {
    }

    // 雷翼の弓・深淵の雷弓を引いているか
    static boolean drawing(LivingEntity entity) {
        return entity.isUsingItem() && isBow(entity.getUseItem().getItem());
    }

    static boolean isBow(net.minecraft.world.item.Item item) {
        return item == jp.revati.skyabyss.item.storm.StormGearSetup.BOW.get() || item == jp.revati.skyabyss.abyss.AbyssSetup.STORM_BOW_ITEM.get();
    }

    // 放したあとの返し (MoveSet の steps[0]。一人称) の進み 0..1。なければ -1。長さは MoveSet の fixedDuration
    static float flourish(LivingEntity entity) {
        float forced = WeaponClient.forcedSwing;
        if (forced >= 0f && entity == Minecraft.getInstance().player) {
            return forced;
        }
        State s = STATES.get(entity.getId());
        if (s == null || drawing(entity) || s.fixed <= 0) {
            return -1f;
        }
        float age = entity.level().getGameTime() - s.releasedAt + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        return age < 0f || age >= s.fixed ? -1f : age / s.fixed;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            STATES.clear();
            return;
        }
        for (Player player : level.players()) {
            State s = STATES.computeIfAbsent(player.getId(), id -> new State());
            boolean drawing = drawing(player);
            if (drawing) {
                s.drawTicks = player.getTicksUsingItem();
                var mv = jp.revati.skyabyss.weapon.MoveSets.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getUseItem().getItem()).getPath());
                s.fixed = mv == null ? 0 : mv.fixedDuration;
            } else if (s.wasDrawing && s.drawTicks >= 3) {
                s.releasedAt = level.getGameTime();
                s.power = Math.min(1f, s.drawTicks / 20f);
            }
            s.wasDrawing = drawing;
        }
        if (level.getGameTime() % 200 == 0) {
            STATES.int2ObjectEntrySet().removeIf(e -> level.getEntity(e.getIntKey()) == null);
        }
    }

    // 今の反動の強さ (0 = なし)。放した直後が最大で、LENGTH tick かけて 0 へ
    public static float amount(LivingEntity entity) {
        float forced = WeaponClient.forcedSwing;
        if (forced >= 0f && entity == Minecraft.getInstance().player) {
            // 開発用 (台本 swingpose): 進み 0 = 放した直後の最大、1 = 戻りきった
            return (1f - forced) * (1f - forced);
        }
        State s = STATES.get(entity.getId());
        if (s == null || drawing(entity)) {
            return 0f;
        }
        float age = entity.level().getGameTime() - s.releasedAt + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        if (age < 0f || age >= LENGTH) {
            return 0f;
        }
        float k = 1f - age / LENGTH;
        // 放した瞬間に最大、そこからなめらかに戻る
        return s.power * k * k;
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
    }
}
