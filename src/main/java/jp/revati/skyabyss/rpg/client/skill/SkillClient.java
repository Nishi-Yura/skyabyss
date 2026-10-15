package jp.revati.skyabyss.rpg.client.skill;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import jp.revati.skyabyss.rpg.skill.net.ManaPayload;
import jp.revati.skyabyss.rpg.skill.net.SkillCastPayload;
import jp.revati.skyabyss.rpg.skill.net.SkillCastResultPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 技のクライアント側: キーで技を頼む (決めるのはサーバー)、クールダウンを手元でも数える、枠とマナのバーを光らせる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class SkillClient {
    // 枠ごとの光る残り時間 (正 = 使えた、負 = 使えなかった)
    static final int[] slotFlash = new int[SkillRules.SLOTS];
    // マナのバーが赤紫に光る残り時間 (マナが足りなかった)
    static int manaFlash;
    // 同じ枠の連打で何度も頼まない (サーバーの返事を待つ間)
    private static final int[] pending = new int[SkillRules.SLOTS];

    private SkillClient() {
    }

    public static void onMana(ManaPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            SkillManager.get(mc.player).mana = payload.mana();
        }
    }

    public static void onCastResult(SkillCastResultPayload payload) {
        int i = payload.slot();
        if (i < 0 || i >= slotFlash.length) {
            return;
        }
        pending[i] = 0;
        slotFlash[i] = payload.ok() ? 8 : -10;
        Minecraft mc = Minecraft.getInstance();
        if (!payload.ok() && mc.player != null) {
            SkillData d = SkillManager.get(mc.player);
            Skill s = d.slot(i);
            if (s != null && d.mana + 1e-4 < SkillRules.manaCost(s, d.rank(s))) {
                manaFlash = 14;
            }
        }
    }

    private static void request(Minecraft mc, int slot) {
        SkillData d = SkillManager.get(mc.player);
        Skill s = d.slot(slot);
        if (s == null) {
            mc.player.displayClientMessage(ModText.hint("message.skyabyss.skill.empty_slot",
                    SkillClientSetup.SLOT_KEYS[slot].getTranslatedKeyMessage(), SkillClientSetup.SKILLS_KEY.getTranslatedKeyMessage()), true);
            slotFlash[slot] = -6;
            return;
        }
        if (d.cooldown(s) > 0 || d.globalCooldown > 0 || pending[slot] > 0) {
            // まだ使えない: 何も送らず、枠だけ小さく揺らす
            slotFlash[slot] = -4;
            return;
        }
        pending[slot] = 10;
        PacketDistributor.sendToServer(new SkillCastPayload(slot));
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        while (SkillClientSetup.SKILLS_KEY.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new SkillScreen());
            }
        }
        for (int i = 0; i < SkillClientSetup.SLOT_KEYS.length; i++) {
            while (SkillClientSetup.SLOT_KEYS[i].consumeClick()) {
                if (mc.screen == null && mc.player.isAlive() && !mc.player.isSpectator()) {
                    request(mc, i);
                }
            }
        }
        // クールダウンは手元でも数える (サーバーからは使ったときにだけ届く)
        SkillManager.get(mc.player).tickCooldowns();
        for (int i = 0; i < slotFlash.length; i++) {
            if (slotFlash[i] > 0) {
                slotFlash[i]--;
            } else if (slotFlash[i] < 0) {
                slotFlash[i]++;
            }
            if (pending[i] > 0) {
                pending[i]--;
            }
        }
        if (manaFlash > 0) {
            manaFlash--;
        }
    }
}
