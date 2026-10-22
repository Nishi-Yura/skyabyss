package jp.revati.skyabyss.story.ch5;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

// SELFTEST abyss relics: 前の私の落とし物 (docs/design/36 の 9 章・決定 257)。
//   墓 12 それぞれに遺品が一つ (品・絵・二つの言語の文)、墓を弔うと一度だけ渡る (前に弔った墓も次の弔いで受け取る)、
//   種類の数で効果が 2・4・6 から効く、十二の墓で伝承 ch5/graves_all が記される
public final class AbyssRelicsSelfTest {
    private AbyssRelicsSelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            ServerLevel level = server.overworld();
            check(f, AbyssRelics.RELICS.size() == AbyssFinds.LAYERS.length * AbyssFinds.GRAVES, "the relic table does not have 12 entries");
            for (AbyssRelics.Relic r : AbyssRelics.RELICS) {
                String id = "abyss_relic_" + r.id();
                check(f, AbyssRelics.relicOf(r.layer(), r.n()) == r, "relic " + r.id() + " is not found by its grave");
                check(f, AbyssRelicsSelfTest.class.getResource("/assets/skyabyss/textures/item/" + id + ".png") != null, id + " has no texture");
                check(f, AbyssRelicsSelfTest.class.getResource("/assets/skyabyss/models/item/" + id + ".json") != null, id + " has no item model");
                for (String lang : new String[] {"ja_jp", "en_us"}) {
                    check(f, hasKey("/assets/skyabyss_abyss_rl/lang/" + lang + ".json", "item.skyabyss." + id), id + " has no " + lang + " name");
                    check(f, hasKey("/assets/skyabyss_abyss_rl/lang/" + lang + ".json", "item.skyabyss." + id + ".desc"), id + " has no " + lang + " tooltip");
                }
            }
            for (String lang : new String[] {"ja_jp", "en_us"}) {
                check(f, hasKey("/assets/skyabyss_abyss_rl/lang/" + lang + ".json", "message.skyabyss.abyss.relic.got"), "no " + lang + " grant line");
                check(f, hasKey("/assets/skyabyss_abyss_rl/lang/" + lang + ".json", "message.skyabyss.abyss.relic.all"), "no " + lang + " chapter six clue");
                check(f, hasKey("/assets/skyabyss_abyss_rl/lang/" + lang + ".json", "item.skyabyss.abyss_relic.set_hint"), "no " + lang + " set hint");
                for (AbyssRelics.Bonus b : AbyssRelics.Bonus.values()) {
                    check(f, hasKey("/assets/skyabyss_abyss_rl/lang/" + lang + ".json", "item.skyabyss.abyss_relic.bonus." + b.needed), "no " + lang + " line for the " + b.needed + " set");
                }
            }

            // 一人目: 一つ目の墓を二回弔う。遺品は一つだけ
            FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_relics"));
            AbyssFinds.find(p, "grave", "bone", 1);
            AbyssFinds.find(p, "grave", "bone", 1);
            check(f, count(p, "abyss_relic_bone_1") == 1, "a grave gave its relic " + count(p, "abyss_relic_bone_1") + " times");
            check(f, AbyssRelics.setCount(p) == 1, "one relic did not count as one");

            // 前に弔った墓 (記録だけがあって遺品はまだ) も、次の弔いで受け取る
            AbyssFinds.finds(p).putBoolean("grave_salt_2", true);
            AbyssFinds.find(p, "grave", "salt", 2);
            check(f, count(p, "abyss_relic_salt_2") == 1, "an old grave gave no relic on the next visit");

            // 種類の数: 二つで夜目、四つで足音の警告、六つで軽い足
            FakePlayer q = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_relics_set"));
            check(f, !AbyssRelics.bonus(q, AbyssRelics.Bonus.SIGHT), "a bonus worked with no relics");
            for (int i = 0; i < 6; i++) {
                q.getInventory().add(new ItemStack(AbyssRelics.RELICS.get(i).item().get()));
                // 同じ品をもう一つ持っても数えない
                if (i == 0) {
                    q.getInventory().add(new ItemStack(AbyssRelics.RELICS.get(0).item().get()));
                }
                int count = i + 1;
                check(f, AbyssRelics.setCount(q) == count, "set count is " + AbyssRelics.setCount(q) + " for " + count);
                check(f, AbyssRelics.bonus(q, AbyssRelics.Bonus.SIGHT) == (count >= 2), "sight is wrong at " + count);
                check(f, AbyssRelics.bonus(q, AbyssRelics.Bonus.FOOTSTEPS) == (count >= 4), "footsteps is wrong at " + count);
                check(f, AbyssRelics.bonus(q, AbyssRelics.Bonus.LIGHT_STEP) == (count >= 6), "light step is wrong at " + count);
                check(f, AbyssRelics.voiceFactor(q) == (count >= 2 ? 0.75 : 1.0), "voice factor is wrong at " + count);
            }

            // 十二の墓で伝承。十一まではまだ
            FakePlayer all = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_relics_all"));
            int done = 0;
            for (String layer : AbyssFinds.LAYERS) {
                for (int i = 1; i <= AbyssFinds.GRAVES; i++) {
                    done++;
                    if (done < 12) {
                        AbyssFinds.find(all, "grave", layer, i);
                    }
                }
            }
            check(f, !Lore.lock(all, AbyssRelics.LORE_ALL), "the chapter six clue opened before all twelve graves");
            AbyssFinds.find(all, "grave", AbyssFinds.LAYERS[AbyssFinds.LAYERS.length - 1], AbyssFinds.GRAVES);
            check(f, Lore.lock(all, AbyssRelics.LORE_ALL), "twelve graves did not open the chapter six clue");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST abyss relics crashed", e);
            f.add("crashed: " + e);
        }
        report(f);
    }

    // 持ち物にある、この墓の遺品の数
    private static int count(FakePlayer p, String id) {
        Item item = AbyssRelics.RELICS.stream().filter(r -> ("abyss_relic_" + r.id()).equals(id)).findFirst().orElseThrow().item().get();
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() == item) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static boolean hasKey(String path, String key) {
        try (Reader r = new InputStreamReader(AbyssRelicsSelfTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            return o.has(key);
        } catch (Exception e) {
            return false;
        }
    }

    private static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss relics PASSED (12 relics with textures and two languages, granted once, set counts at 2/4/6, the chapter six clue after all 12 graves)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST abyss relics FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}
