package jp.revati.skyabyss.client.weapon;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.StaffBolts;
import jp.revati.skyabyss.rpg.skill.Job;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 魔法使いの魔弾 (combat/StaffBolts) の手元: 杖を持って、手の届く敵を指していない左クリックで撃つ合図を送る。
// 手の届く敵を指しているときは、今までどおり杖で打つ (バニラの一撃 → weapon/WeaponStrikes)。押し続けても、続けて撃てる間より早くは送らない
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class StaffBoltClient {
    private static long lastSent;

    private StaffBoltClient() {
    }

    @SubscribeEvent
    static void onInput(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || mc.player == null || mc.level == null || mc.player.isSpectator() || mc.screen != null) {
            return;
        }
        if (Job.of(mc.player.getMainHandItem()) != Job.MAGE) {
            return;
        }
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) {
            return;
        }
        long now = mc.level.getGameTime();
        if (now - lastSent < StaffBolts.MIN_GAP_CLIENT) {
            return;
        }
        lastSent = now;
        PacketDistributor.sendToServer(new StaffBolts.Cast());
    }
}
