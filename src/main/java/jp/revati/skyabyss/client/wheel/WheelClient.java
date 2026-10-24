package jp.revati.skyabyss.client.wheel;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

// 輪の選び画面 (2026-10-25 の試遊: 原神のように、一つのキーを長押しして出す画面を選びたい)。
// 左 Alt (KeyDefaults.WHEEL) を押しているあいだ輪が出て、マウスを向けた項目を、離したときに開く。
// 開くのは、その画面のキーを押したのと同じ (KeyMapping.click)。キーを割り当て直しても、輪からはそのまま開ける
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class WheelClient {
    public static final KeyMapping KEY = new KeyMapping("key.skyabyss.wheel", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, jp.revati.skyabyss.KeyDefaults.WHEEL, "key.categories.skyabyss");

    // 輪の項目 (上から時計回り)。name は gui.skyabyss.wheel.<id>
    public record Entry(String id, KeyMapping key, ItemStack icon) {
    }

    private static List<Entry> entries;

    private WheelClient() {
    }

    static List<Entry> entries() {
        if (entries == null) {
            entries = List.of(
                    new Entry("quest", jp.revati.skyabyss.client.quest.QuestClient.LOG_KEY, new ItemStack(Items.WRITABLE_BOOK)),
                    new Entry("pouch", jp.revati.skyabyss.client.pouch.PouchClient.OPEN_KEY, new ItemStack(Items.BUNDLE)),
                    new Entry("skills", jp.revati.skyabyss.rpg.client.skill.SkillClientSetup.SKILLS_KEY, new ItemStack(Items.ENCHANTED_BOOK)),
                    new Entry("status", jp.revati.skyabyss.rpg.client.GrowthClientSetup.STATUS_KEY, new ItemStack(Items.IRON_CHESTPLATE)),
                    new Entry("map", jp.revati.skyabyss.client.map.MapClient.MAP_KEY, new ItemStack(Items.FILLED_MAP)),
                    new Entry("record", jp.revati.skyabyss.client.record.RecordClient.OPEN_KEY, new ItemStack(Items.BOOK)),
                    new Entry("party", jp.revati.skyabyss.party.client.PartyClient.PARTY_KEY, new ItemStack(Items.PLAYER_HEAD)),
                    new Entry("notice", jp.revati.skyabyss.client.notice.NoticeClient.LOG_KEY, new ItemStack(Items.BELL)));
        }
        return entries;
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(KEY);
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new WheelScreen());
            }
        }
    }

    // 輪のキーがいま押されているか (画面が開いている間は KeyMapping が動かないので、窓のキーの状態を見る)
    static boolean held() {
        Minecraft mc = Minecraft.getInstance();
        InputConstants.Key key = KEY.getKey();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), key.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }
        return key.getValue() >= 0 && InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
    }

    // 選んだ項目を開く: 画面を閉じてから、その画面のキーを押したことにする (次の tick にその係が開く)
    static void open(Entry e) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);
        KeyMapping.click(e.key().getKey());
    }
}
