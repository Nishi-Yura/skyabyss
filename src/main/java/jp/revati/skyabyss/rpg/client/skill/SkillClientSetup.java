package jp.revati.skyabyss.rpg.client.skill;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import jp.revati.skyabyss.rpg.skill.Skills;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

// 技の画面まわりの登録 (Mod のイベントバス)。
// キー: 技の画面 K、技の枠 R・G・V・Z (5 つ目は B。バニラで使っていないキー。数字はホットバー、Q・E・F・C・X はバニラが使う)。キー設定で変えられる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class SkillClientSetup {
    public static final KeyMapping SKILLS_KEY = new KeyMapping("key.skyabyss.skills", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, jp.revati.skyabyss.KeyDefaults.SKILLS, "key.categories.skyabyss");
    // 構えを順に切り替える (なし → 守 → 疾 → 歌 → なし)
    public static final KeyMapping STANCE_KEY = new KeyMapping("key.skyabyss.skill_stance", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, jp.revati.skyabyss.KeyDefaults.STANCE, "key.categories.skyabyss");
    private static final int[] SLOT_DEFAULTS = jp.revati.skyabyss.KeyDefaults.SLOTS;
    public static final KeyMapping[] SLOT_KEYS = new KeyMapping[SkillRules.SLOTS];
    static {
        for (int i = 0; i < SLOT_KEYS.length; i++) {
            SLOT_KEYS[i] = new KeyMapping("key.skyabyss.skill_slot_" + (i + 1), KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, SLOT_DEFAULTS[i], "key.categories.skyabyss");
        }
    }

    private SkillClientSetup() {
    }

    @SubscribeEvent
    static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(SKILLS_KEY);
        event.register(STANCE_KEY);
        for (KeyMapping k : SLOT_KEYS) {
            event.register(k);
        }
    }

    @SubscribeEvent
    static void onLayers(RegisterGuiLayersEvent event) {
        // マナのバーは成長の表示 (左上) の下。技の枠はホットバーの右。チャットより下に描く
        event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "skill_hud"), SkillHud::render);
    }

    @SubscribeEvent
    static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(Skills.RUNE_SPARK.get(), SkillParticles.RuneSparkProvider::new);
        event.registerSpriteSet(Skills.SKY_MOTE.get(), SkillParticles.SkyMoteProvider::new);
        event.registerSpriteSet(Skills.STORM_FEATHER.get(), jp.revati.skyabyss.client.combat.StormFeatherParticle.Provider::new);
    }
}
