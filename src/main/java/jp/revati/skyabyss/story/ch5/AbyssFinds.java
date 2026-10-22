package jp.revati.skyabyss.story.ch5;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 深淵の寄り道で探すもの (docs/design/36 の 8 章。00 の 254)。設計図の印は tools/abyss/layer_common.py が置く。
//   刻み   印 carve_<層>_<1..4> の彫った石を右クリックで読む。層の四つを読むと伝承「前の私の刻み (<層>)」
//   墓     印 grave_<層>_<1..3> (塚・頭・蝋燭) を右クリックで弔う。層の三つで伝承「前の私の墓 (<層>)」
//          墓は 9 章の「前の私の落とし物」と同じもの (遺品の装備は A8 で渡す)
//   記録   人ごと (死んでも引き継ぐ所。Player.PERSISTED_NBT_TAG の skyabyss_abyss_finds)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssFinds {
    public static final String[] LAYERS = {"bone", "salt", "storm", "ash"};
    public static final int CARVINGS = 4;
    public static final int GRAVES = 3;
    static final String TAG = "skyabyss_abyss_finds";

    private AbyssFinds() {
    }

    static CompoundTag finds(Player p) {
        CompoundTag data = p.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG)) {
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(TAG)) {
            persisted.put(TAG, new CompoundTag());
        }
        return persisted.getCompound(TAG);
    }

    public static boolean found(Player p, String id) {
        return finds(p).getBoolean(id);
    }

    // 刻み・墓の一つを見つけた (初めてなら true)。層のぶんが揃えば伝承を開く
    public static boolean find(ServerPlayer p, String kind, String layer, int n) {
        String id = kind + "_" + layer + "_" + n;
        CompoundTag f = finds(p);
        Notice.narrate(p, "message.skyabyss.abyss." + kind + "." + layer + "_" + n);
        if (kind.equals("grave")) {
            AbyssRelics.onGrave(p, layer, n); // 遺品を一度だけ渡す・十二の墓が揃えば六章の手がかり (前に弔った墓も、次に弔ったとき受け取る)
        }
        if (f.getBoolean(id)) {
            return false;
        }
        f.putBoolean(id, true);
        int total = kind.equals("carve") ? CARVINGS : GRAVES;
        int have = 0;
        for (int i = 1; i <= total; i++) {
            if (f.getBoolean(kind + "_" + layer + "_" + i)) {
                have++;
            }
        }
        if (have == total) {
            Lore.unlock(p, "ch5/" + (kind.equals("carve") ? "carvings_" : "graves_") + layer, true);
        }
        return true;
    }

    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !AbyssWorld.in(p)) {
            return;
        }
        BlockPos at = event.getPos();
        for (String layer : LAYERS) {
            for (int i = 1; i <= CARVINGS; i++) {
                BlockPos m = AbyssBuild.mark("carve_" + layer + "_" + i);
                if (m != null && m.equals(at)) {
                    event.setCanceled(true);
                    find(p, "carve", layer, i);
                    return;
                }
            }
            for (int i = 1; i <= GRAVES; i++) {
                BlockPos m = AbyssBuild.mark("grave_" + layer + "_" + i);
                // 塚・頭 (一つ上)・蝋燭 (東隣) のどれでも
                if (m != null && (m.equals(at) || m.above().equals(at) || m.east().equals(at))) {
                    event.setCanceled(true);
                    if (find(p, "grave", layer, i)) {
                        p.serverLevel().playSound(null, m, SoundEvents.SOUL_ESCAPE.value(), SoundSource.AMBIENT, 1f, 0.7f);
                    }
                    return;
                }
            }
        }
    }
}
