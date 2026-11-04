package jp.revati.skyabyss.upgrade;

import java.util.List;
import java.util.function.Consumer;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

// 版が上がったときの、世界のデータの移し替え (2026-11-02 の声「アップデートで、mod の ver が上がって変更しても対応できるようにできるの？」)。
// 世界ごとに「データの版」(保存 skyabyss_world_version) を持ち、サーバーが始まったとき、それより新しい段 (STEPS) を古い順に一度ずつ流す。
// 人ごとのデータは PlayerUpgrade (入ったとき)。
//   - 段は何度流しても同じ結果になるように書く (前の版で途中まで流れた世界・新しい世界で流れても壊さない)
//   - チャンクが要る直し (建物・置き物) は、段で直す印を立て、人が近づいたとき各仕組みが直す (例: world/continent/IslandTrail.clearOldGate、
//     土地の町の建て直し world/continent/ContinentTowns.VERSION_MARK、都の係の人の立ち位置 facility/Attendants.capitalSpots)
//   - 新しい Mod の版で保存した世界を古い版で開くと (版が CURRENT より大きい)、記録に知らせを残し、版は下げない
// 新しい段を足すとき: CURRENT を一つ上げ、STEPS の終わりに new Step(番号, 名前, 中身) を足す (docs/release.md の「版を上げるとき」)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class WorldUpgrade extends SavedData {
    public static final int CURRENT = 2;
    static final String NAME = "skyabyss_world_version";
    private static final Factory<WorldUpgrade> FACTORY = new Factory<>(WorldUpgrade::new, WorldUpgrade::load);

    public record Step(int version, String name, Consumer<MinecraftServer> run) {
    }

    // 段の並び (版の順)
    static final List<Step> STEPS = List.of(
            // 1.0.0: 版の記録を始める。前の版の世界でもう済んでいる移し替え (倒れても落とさない・大陸の縁・町の作りの版・蜂の巣・留め袋) は
            // それぞれの仕組みが自分で見ているので、ここでは何もしない
            new Step(1, "start version tracking", server -> {
            }),
            // 1.1.1: 1.1.0 で大陸の地上を作り直した世界は、語り部・鯨追い・職人が生き物ごと消えたのに「立たせた時刻」が残り、
            // 一日たつまで立ち直らなかった。記録を消して、近づいたときにすぐ立たせる (生きている人は見つけて二人目を立てない)
            new Step(2, "forget npc spawn times", server -> {
                int n = jp.revati.skyabyss.npc.NpcWorldData.get(server.overworld()).forgetAll();
                SkyAbyss.LOGGER.info("World upgrade: forgot the spawn times of {} npc place(s)", n);
            }));

    int version;
    String modVersion = "";

    static WorldUpgrade get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    static WorldUpgrade load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldUpgrade w = new WorldUpgrade();
        w.version = tag.getInt("version");
        w.modVersion = tag.getString("mod");
        return w;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", version);
        tag.putString("mod", modVersion);
        return tag;
    }

    // この世界のデータの版
    public static int version(MinecraftServer server) {
        return get(server).version;
    }

    // 最後にこの世界を開いた Mod の版
    public static String savedWith(MinecraftServer server) {
        String v = get(server).modVersion;
        return v.isEmpty() ? "?" : v;
    }

    public static String modVersion() {
        return ModList.get().getModContainerById(SkyAbyss.MODID).map(c -> c.getModInfo().getVersion().toString()).orElse("?");
    }

    // 新しい Mod の版で保存された世界か (古い版で開いている)
    public static boolean newerThanMod(MinecraftServer server) {
        return get(server).version > CURRENT;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onStarted(ServerStartedEvent event) {
        run(event.getServer());
    }

    // 済んでいない段を流す。流した段の数
    public static int run(MinecraftServer server) {
        WorldUpgrade w = get(server);
        String mod = modVersion();
        if (w.version > CURRENT) {
            SkyAbyss.LOGGER.warn("World upgrade: this world was saved by a newer version of the mod (data version {} > {}, last opened with {}). Opening it with {} may lose data",
                    w.version, CURRENT, w.modVersion, mod);
            return 0;
        }
        int n = 0;
        for (Step s : STEPS) {
            if (s.version() <= w.version) {
                continue;
            }
            try {
                s.run().accept(server);
                SkyAbyss.LOGGER.info("World upgrade: step {} ({}) done", s.version(), s.name());
            } catch (RuntimeException e) {
                // 一つの段がこけても、世界は開ける。版は進めず、次に開いたときにもう一度流す
                SkyAbyss.LOGGER.error("World upgrade: step {} ({}) failed; it will run again next time", s.version(), s.name(), e);
                break;
            }
            w.version = s.version();
            n++;
        }
        if (n > 0 || !mod.equals(w.modVersion)) {
            if (!w.modVersion.isEmpty() && !mod.equals(w.modVersion)) {
                SkyAbyss.LOGGER.info("World upgrade: opened with {} (last {}), data version {}", mod, w.modVersion, w.version);
            }
            w.modVersion = mod;
            w.setDirty();
        }
        return n;
    }
}
