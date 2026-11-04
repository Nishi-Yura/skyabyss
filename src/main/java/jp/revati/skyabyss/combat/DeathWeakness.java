package jp.revati.skyabyss.combat;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 倒れたときの軽い損「弱り」(docs/design/47 の 2.7。2026-11-04 の表 5-1)。持ち物も経験値も失わない。
//   倒れて目覚めたとき (復活) DURATION のあいだ: その人が与える傷 ×DEALT、受ける傷 ×TAKEN。効き目の欄に絵と残りの時間
//   宿で休むと (FacilityActions.innRest) 消える。仲間に起こされたとき (倒れずに済んだ) は付かない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DeathWeakness {
    public static final int DURATION = 2400;
    public static final float DEALT = 0.85f;
    public static final float TAKEN = 1.15f;

    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, SkyAbyss.MODID);
    public static final DeferredHolder<MobEffect, MobEffect> WEAKENED = EFFECTS.register("weakened", () -> new MobEffect(MobEffectCategory.HARMFUL, 0x7D6A8A) {
    });

    private DeathWeakness() {
    }

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }

    public static boolean weak(Player p) {
        return p.hasEffect(WEAKENED);
    }

    // 弱りを付ける (目覚めたとき)
    public static void apply(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(WEAKENED, DURATION, 0, false, false, true));
    }

    // 宿で休んだ。弱っていれば消して知らせる
    public static void rest(ServerPlayer p) {
        if (weak(p)) {
            p.removeEffect(WEAKENED);
            Notice.gain(p, "message.skyabyss.weakened.rested");
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || p.isCreative() || p.isSpectator()) {
            return;
        }
        apply(p);
        Notice.hint(p, "message.skyabyss.weakened.applied");
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncoming(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Player target && weak(target)) {
            event.setAmount(event.getAmount() * TAKEN);
        }
        if (event.getSource().getEntity() instanceof Player from && from != event.getEntity() && weak(from)) {
            event.setAmount(event.getAmount() * DEALT);
        }
    }
}
