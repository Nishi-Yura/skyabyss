package jp.revati.skyabyss.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.grave.GraveMarkerHud;
import jp.revati.skyabyss.client.notice.NoticeClient;
import jp.revati.skyabyss.client.quest.ObjectiveStrip;
import jp.revati.skyabyss.client.quest.QuestClient;
import jp.revati.skyabyss.client.quest.QuestHud;
import jp.revati.skyabyss.client.titan.BossAttackCue;
import jp.revati.skyabyss.client.tutorial.TutorialClient;
import jp.revati.skyabyss.client.vow.VowHud;
import jp.revati.skyabyss.grave.GraveMarkers;
import jp.revati.skyabyss.hud.HudLayout;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.notice.NoticePayload;
import jp.revati.skyabyss.party.DownedPackets;
import jp.revati.skyabyss.party.Ping;
import jp.revati.skyabyss.party.PartyLocator;
import jp.revati.skyabyss.party.PartyNetwork;
import jp.revati.skyabyss.party.client.DownedClient;
import jp.revati.skyabyss.party.client.PartyClient;
import jp.revati.skyabyss.party.client.PartyMarkers;
import jp.revati.skyabyss.party.client.PingClient;
import jp.revati.skyabyss.rpg.client.GrowthClient;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.titan.tide.back.TideBackNetwork;
import jp.revati.skyabyss.titan.tide.back.client.TideGaugeHud;
import jp.revati.skyabyss.titan.vow.VowNetwork;
import jp.revati.skyabyss.tutorial.TutorialHints;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

// 開発用: 画面の部品を全部いっぺんに出す (スクリーンショットと「かぶらないか」の目視用)。docs/design/18 の 9.5 節。
//   /skyabyss_hud demo [all|opening|back|boss|off]  : 見本のデータを各層へ入れる (サーバーのデータには触れない。表示だけ)。台本では `huddemo <all|opening|back|boss|off>`
//   /skyabyss_hud layout         : いまの置き場の長方形と、出さなかった部品を、チャットへ出す
// ボスバーと効き目の絵はバニラの部品なので、台本から `cmd bossbar …`・`cmd effect give …` で足す (tools/shots/hud_layout.txt)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class HudDemo {
    // 見本の部品 (場面ごとに組み合わせる。all = 全部)
    public enum Part {
        TUTORIAL, HINTS, NARRATE, BANNER, LEVELUP, TOASTS, OBJECTIVE, QUEST, GROWTH, SKILLS, QUICKCARD, VOW, GAUGE, PARTY, MARKERS, CUE
    }

    private static final java.util.EnumSet<Part> PARTS = java.util.EnumSet.noneOf(Part.class);
    private static boolean on;
    private static int tick;
    private static int counter;
    private static SkillData savedSkills;
    private static double savedSpeed = Double.NaN;

    private HudDemo() {
    }

    public static boolean on() {
        return on;
    }

    public static boolean has(Part part) {
        return on && PARTS.contains(part);
    }

    // 場面の名前から部品を選ぶ: all (既定)・opening (序盤: 手引きとキーの手がかり)・back (背中: 目盛りと目的の板・誓い・仲間)・boss (ボス戦)
    public static void set(String mode) {
        if (mode.equalsIgnoreCase("off")) {
            set(false);
            return;
        }
        set(false);
        PARTS.clear();
        switch (mode.toLowerCase(java.util.Locale.ROOT)) {
            case "opening" -> PARTS.addAll(java.util.EnumSet.of(Part.TUTORIAL, Part.HINTS, Part.NARRATE, Part.OBJECTIVE, Part.GROWTH, Part.QUICKCARD, Part.SKILLS));
            case "back" -> PARTS.addAll(java.util.EnumSet.of(Part.GAUGE, Part.VOW, Part.PARTY, Part.OBJECTIVE, Part.QUEST, Part.GROWTH, Part.HINTS, Part.TOASTS,
                    Part.SKILLS, Part.QUICKCARD));
            case "boss" -> PARTS.addAll(java.util.EnumSet.of(Part.PARTY, Part.MARKERS, Part.TUTORIAL, Part.HINTS, Part.NARRATE, Part.OBJECTIVE, Part.VOW, Part.GAUGE,
                    Part.SKILLS, Part.QUICKCARD, Part.CUE, Part.GROWTH, Part.TOASTS, Part.BANNER));
            default -> PARTS.addAll(java.util.EnumSet.allOf(Part.class));
        }
        set(true);
    }

    public static void set(boolean value) {
        if (on == value) {
            return;
        }
        on = value;
        tick = 0;
        Minecraft mc = Minecraft.getInstance();
        ObjectiveStrip.demo = value && PARTS.contains(Part.OBJECTIVE);
        QuestHud.demo = value && PARTS.contains(Part.QUEST);
        if (value) {
            // 撮りやすいよう、知らせをゆっくり (半分の速さ) 出す
            savedSpeed = jp.revati.skyabyss.Config.NOTICE_SPEED.get();
            jp.revati.skyabyss.Config.NOTICE_SPEED.set(0.5);
            refresh(mc, true);
        } else {
            if (!Double.isNaN(savedSpeed)) {
                jp.revati.skyabyss.Config.NOTICE_SPEED.set(savedSpeed);
                savedSpeed = Double.NaN;
            }
            TutorialClient.receive(new TutorialHints.Sync(List.of(), List.of()));
            VowHud.receive(new VowNetwork.Sync("", List.of(), 0, -1));
            PartyClient.receive(new PartyNetwork.Sync(0, 15, 0, "", 70, List.of(), ""));
            PartyMarkers.clear();
            DownedClient.clear();
            PingClient.clear();
            GraveMarkerHud.clear();
            GraveMarkerHud.forceShow(false);
            BossAttackCue.clear();
            NoticeClient.clear();
            if (mc.player != null && savedSkills != null) {
                SkillData d = SkillManager.get(mc.player);
                d.ranks.clear();
                d.ranks.putAll(savedSkills.ranks);
                System.arraycopy(savedSkills.slots, 0, d.slots, 0, d.slots.length);
                d.mana = savedSkills.mana;
                savedSkills = null;
            }
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (!on) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        tick++;
        // 待ちが空いた列へ見本を足す (毎 tick 見る)。位置のある見本は 1 秒ごとに更新
        refresh(mc, tick % 20 == 1);
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        on = false;
        PARTS.clear();
        ObjectiveStrip.demo = false;
        QuestHud.demo = false;
        savedSkills = null;
    }

    private static void notice(Notice.Kind kind, String text, String extra) {
        NoticeClient.receive(new NoticePayload(kind, Component.literal(text), Component.literal(extra)));
    }

    private static String fresh(String text) {
        // 同じ手がかりは 3 度までなので、見えない文字で別の文にする
        return text + "​".repeat(++counter);
    }

    private static void refresh(Minecraft mc, boolean slow) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        // ---- 手引き (初回の案内。ホットバーの上) と、キーの手がかり
        if (slow && has(Part.TUTORIAL)) {
            TutorialClient.receive(new TutorialHints.Sync(List.of(TutorialHints.POUCH, TutorialHints.RECORD, TutorialHints.PING),
                    tick % 400 == 1 ? List.of("demo" + counter) : List.of()));
            counter++;
        }
        if ((has(Part.HINTS) || has(Part.NARRATE)) && NoticeClient.idle(Notice.Channel.CAPTION)) {
            if (has(Part.HINTS) && (counter % 2 == 0 || !has(Part.NARRATE))) {
                notice(Notice.Kind.HINT, fresh(Component.translatable("message.skyabyss.growth.open_hint", Component.keybind("key.skyabyss.status")).getString()), "");
            } else if (has(Part.NARRATE)) {
                notice(Notice.Kind.NARRATE, "背骨に沿って、古い石の門と折れた柱が半ば埋もれている。頭はずっと北、胸びれの先は雲の上だ" + "​".repeat(counter), "");
            }
            if (has(Part.HINTS) && NoticeClient.idle(Notice.Channel.CAPTION)) {
                notice(Notice.Kind.HINT, fresh(Component.translatable("message.skyabyss.quest.open_hint", Component.keybind("key.skyabyss.quest_log")).getString()), "");
            }
        }
        if (has(Part.LEVELUP) && NoticeClient.idle(Notice.Channel.CENTER)) {
            notice(Notice.Kind.LEVEL_UP, "技の点が 1 増えた", "");
        }
        if (has(Part.BANNER) && NoticeClient.idle(Notice.Channel.BANNER)) {
            notice(Notice.Kind.PLACE, "岩鯨の背", "空を泳ぐものの、いちばん高い所");
            notice(Notice.Kind.ALERT, "⚔ 波が来る", "");
        }
        if (slow && has(Part.TOASTS) && tick % 160 == 1) {
            notice(Notice.Kind.GAIN, "風切りの羽 ×2", "");
            notice(Notice.Kind.UNLOCK, "封印が解けた。石が鳴っている", "");
            QuestClient.receiveNotice((byte) 0, "demo");
        }
        if (slow && has(Part.GROWTH) && tick % 100 == 1) {
            GrowthClient.demo();
        }
        // ---- 右上: 誓いの帯、背の目盛りと目的の板、仲間
        if (slow && has(Part.VOW)) {
            VowHud.receive(new VowNetwork.Sync("tide", List.of("hardy", "hasty"), 3, 1800 - tick / 20));
        }
        if (slow && has(Part.GAUGE)) {
            TideGaugeHud.receive(new TideBackNetwork.Gauge(1, 1200, 0.6f, false, 2, "east.go", 1, 3, 1, 2, 3));
        }
        String a = "00000000-0000-4000-8000-00000000000a";
        String b = "00000000-0000-4000-8000-00000000000b";
        String c = "00000000-0000-4000-8000-00000000000c";
        if (slow && has(Part.PARTY)) {
            PartyClient.receive(new PartyNetwork.Sync(1, 15, 1, "tide", 70, List.of(
                    new PartyNetwork.Member(mc.player.getUUID().toString(), "あなた", 9, 20f, 20f, true, true, false),
                    new PartyNetwork.Member(a, "イサナ", 12, 18f, 20f, true, true, true),
                    new PartyNetwork.Member(b, "カイ", 8, 7f, 20f, true, true, false),
                    new PartyNetwork.Member(c, "ミオ", 10, 20f, 20f, true, false, false)), ""));
        }
        // 仲間・倒れた仲間・「ここ」の印・墓の標を、向きの違う所へ (札と画面の外の矢印)
        if (slow && has(Part.MARKERS)) {
            Vec3 p = mc.player.position();
            Vec3 look = mc.player.getLookAngle().multiply(1, 0, 1).normalize();
            Vec3 right = new Vec3(-look.z, 0, look.x);
            String dim = mc.level.dimension().location().toString();
            PartyMarkers.receive(new PartyLocator.Pos(List.of(
                    entry(a, "イサナ", dim, p.add(look.scale(12)).add(right.scale(3)), 90),
                    entry(b, "カイ", dim, p.add(look.scale(-14)).add(right.scale(-8)), 35),
                    entry(c, "ミオ", dim, p.add(right.scale(30)), 100))));
            DownedClient.receive(new DownedPackets.Sync(List.of(new DownedPackets.Entry("00000000-0000-4000-8000-00000000000d", "ハル", dim,
                    (int) (p.x + look.x * 9 - right.x * 7), (int) p.y, (int) (p.z + look.z * 9 - right.z * 7), 400, 0))));
            PingClient.receive(new Ping.Show(a, "イサナ", dim, p.x + look.x * 20 + right.x * 10, p.y + 2, p.z + look.z * 20 + right.z * 10, -1, false, 100));
            GraveMarkerHud.forceShow(true);
            GraveMarkerHud.receive(new GraveMarkers.Pos(List.of(new GraveMarkers.Mark(dim, (int) (p.x - look.x * 40), (int) p.y, (int) (p.z - look.z * 40)))));
        }
        // 技の枠とマナ
        if (slow && has(Part.SKILLS)) {
            SkillData d = SkillManager.get(mc.player);
            if (savedSkills == null) {
                savedSkills = new SkillData();
                savedSkills.ranks.putAll(d.ranks);
                System.arraycopy(d.slots, 0, savedSkills.slots, 0, d.slots.length);
                savedSkills.mana = d.mana;
            }
            Skill[] skills = {Skill.TITAN_CLEAVE, Skill.RIPOSTE, Skill.CLOUD_PIERCE, Skill.FAR_SHOT};
            for (int i = 0; i < skills.length && i < d.slots.length; i++) {
                d.ranks.put(skills[i].id, 1);
                d.slots[i] = skills[i].id;
            }
            d.mana = (float) (SkillManager.maxMana(mc.player) * 0.6);
        }
        // ---- ボスの技の名前
        if (slow && has(Part.CUE) && tick % 40 == 1) {
            BossAttackCue.show(null, Component.literal("雷の網"), 40);
            BossAttackCue.show(null, Component.literal("翼の払い"), 36);
        }
    }

    private static PartyLocator.Entry entry(String id, String name, String dim, Vec3 at, int hp) {
        return new PartyLocator.Entry(id, name, dim, (int) at.x, (int) at.y, (int) at.z, hp);
    }

    // ------------------------------------------------------------ コマンド
    @SubscribeEvent
    static void onClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyabyss_hud")
                .then(Commands.literal("demo")
                        .executes(ctx -> {
                            if (on) {
                                set(false);
                            } else {
                                set("all");
                            }
                            return 1;
                        })
                        .then(Commands.argument("mode", StringArgumentType.word()).executes(ctx -> {
                            set(StringArgumentType.getString(ctx, "mode"));
                            return 1;
                        })))
                .then(Commands.literal("layout").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.gui.getChat().addMessage(Component.literal(describe()));
                    return 1;
                })));
    }

    // いまの置き場の一覧 (開発用)
    public static String describe() {
        HudLayout.Result r = HudManager.result();
        List<String> lines = new ArrayList<>();
        r.placed.forEach((id, rect) -> lines.add(id + " " + rect.x() + "," + rect.y() + " " + rect.w() + "x" + rect.h()));
        return "gui " + r.scene.w() + "x" + r.scene.h() + " | " + String.join(" | ", lines) + " | dropped " + r.dropped;
    }
}
