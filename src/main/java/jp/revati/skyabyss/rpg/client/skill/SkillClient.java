package jp.revati.skyabyss.rpg.client.skill;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import jp.revati.skyabyss.rpg.skill.SkillStance;
import jp.revati.skyabyss.rpg.skill.net.SkillStancePayload;
import net.minecraft.network.chat.Component;
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
    public static int manaFlash;
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

    // ヒロイック以上の武器の説明に、書き換わる技を一行添える
    @SubscribeEvent
    static void onTooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).getPath();
        for (jp.revati.skyabyss.rpg.skill.Rune r : jp.revati.skyabyss.rpg.skill.Rune.values()) {
            if (r.legendary() && r.weapon.equals(path)) {
                event.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.skyabyss.legend_rewrite",
                        net.minecraft.network.chat.Component.translatable(r.skillNameKey()), net.minecraft.network.chat.Component.translatable(r.nameKey()))
                        .withColor(0xF2D78A));
                return;
            }
        }
    }

    // 天職を選べる (サーバーが知らせた) 段。0 = まだ。K の画面のボタンを出す
    public static int callingReady;

    public static void onCallingOpen(jp.revati.skyabyss.rpg.skill.net.CallingOpenPayload payload) {
        callingReady = payload.which();
        Minecraft mc = Minecraft.getInstance();
        if (payload.open() && mc.player != null && !(mc.screen instanceof CallingScreen)) {
            mc.setScreen(new CallingScreen(payload.which()));
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
            if (s != null && d.mana + 1e-4 < SkillManager.manaCostFor(mc.player, s)) {
                manaFlash = 14;
            }
        }
    }

    private static void request(Minecraft mc, int slot) {
        if (jp.revati.skyabyss.party.client.DownedClient.selfDowned()) {
            // 倒れている間は送らない
            slotFlash[slot] = -4;
            return;
        }
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
        while (SkillClientSetup.STANCE_KEY.consumeClick()) {
            if (mc.screen == null && mc.player.isAlive() && !mc.player.isSpectator()) {
                SkillData sd = SkillManager.get(mc.player);
                if (sd.stanceCd > 0 && !mc.player.isCreative()) {
                    mc.player.displayClientMessage(ModText.deny("message.skyabyss.skill.stance_cooldown"), true);
                } else {
                    int next = (sd.stance + 1) % SkillStance.values().length;
                    PacketDistributor.sendToServer(new SkillStancePayload(next));
                    mc.player.displayClientMessage(ModText.hint("message.skyabyss.skill.stance_set", Component.translatable(SkillStance.of(next).nameKey())), true);
                }
            }
        }
        for (int i = 0; i < SkillClientSetup.SLOT_KEYS.length; i++) {
            while (SkillClientSetup.SLOT_KEYS[i].consumeClick()) {
                if (mc.screen == null && mc.player.isAlive() && !mc.player.isSpectator()) {
                    request(mc, i);
                }
            }
        }
        // クールダウンは手元でも数える (サーバーからは使ったときにだけ届く)。一人遊びで止めている間は数えない
        if (!mc.isPaused()) {
            SkillManager.get(mc.player).tickCooldowns();
        }
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
