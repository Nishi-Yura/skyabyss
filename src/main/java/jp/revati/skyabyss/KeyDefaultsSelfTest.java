package jp.revati.skyabyss;

import java.util.List;

import net.minecraft.server.MinecraftServer;

// キーの既定の自己テスト: この mod の操作どうしで既定が重ならず、バニラの既定とも重ならない (KeyDefaults)。
// 構えと護符 (H)、技の枠 5 と控え帳 (B) が重なっていた不具合の再発止め
public final class KeyDefaultsSelfTest {
    private KeyDefaultsSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = KeyDefaults.conflicts();
        // 22 = 19 + 深淵術の三つ (光・重さ・鼓動。docs/design/36 の 5)
        if (KeyDefaults.all().size() != 22) {
            f.add("the key list has " + KeyDefaults.all().size() + " entries (expected 22); update KeyDefaults.all() when a key is added");
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST keys PASSED ({} default keys, no duplicates, none on a vanilla default)", KeyDefaults.all().size());
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST keys FAILED: {}", m));
        }
    }
}
