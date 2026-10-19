package jp.revati.skyabyss.rpg.skill;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.rpg.skill.net.CallingOpenPayload;
import jp.revati.skyabyss.story.ch1.Tasks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// 天職 (docs/design/27 の 10 節)。一つ目は岩鯨の心臓が止まったあと (心臓の報酬の画面のあと)、二つ目は雷翼が止まったあと。
// 選べるかどうかは、その人の個人の進み (Progression.stage) と記録だけで決める (保存する「申し出」の旗は持たない)。
// 画面を開くのは、心臓の報酬を選び終えたあと・ログインのとき・K の画面のボタン。あとで選んでもよい
public final class SkillCalling {
    // 一つ目: 心臓を 1 体止めた人。二つ目: 3 体止めた人 (雷翼)
    public static final int FIRST_STAGE = 1;
    public static final int SECOND_STAGE = 3;
    public static final int OFFER_DELAY = 60;
    public static final int LOGIN_DELAY = 140;

    public enum Result {
        OK, NOT_READY, BAD_TREE, TAKEN
    }

    private SkillCalling() {
    }

    // いま選べる天職 (0 = なし、1 = 一つ目、2 = 二つ目)。クリエイティブは、まだ選んでいなければいつでも
    public static int which(ServerPlayer player) {
        if (!SkillRules.CALLINGS) {
            return 0;
        }
        SkillData d = SkillManager.get(player);
        int stage = player instanceof FakePlayer ? 0 : Progression.stage(player);
        if (!d.hasCalling()) {
            return stage >= FIRST_STAGE || player.isCreative() ? 1 : 0;
        }
        if (d.calling2.isEmpty()) {
            return stage >= SECOND_STAGE || player.isCreative() ? 2 : 0;
        }
        return 0;
    }

    // 自己テストなどから、進みと関係なく決める
    public static Result choose(ServerPlayer player, String treeId, boolean ignoreStage) {
        SkillTree tree = SkillTree.byId(treeId);
        if (tree == null || !tree.callable()) {
            return Result.BAD_TREE;
        }
        SkillData d = SkillManager.get(player);
        int which = which(player);
        if (which == 0) {
            if (!ignoreStage) {
                return d.hasCalling() && !d.calling2.isEmpty() ? Result.TAKEN : Result.NOT_READY;
            }
            which = !d.hasCalling() ? 1 : d.calling2.isEmpty() ? 2 : 0;
            if (which == 0) {
                return Result.TAKEN;
            }
        }
        if (which == 2 && tree.id.equals(d.calling)) {
            return Result.TAKEN;
        }
        if (which == 1) {
            d.calling = tree.id;
        } else {
            d.calling2 = tree.id;
        }
        d.sanitize(SkillManager.level(player));
        SkillManager.sync(player);
        SkillManager.applyModifiers(player);
        sendState(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.1f);
        Notice.alert(player, which == 1 ? "message.skyabyss.calling.chosen" : "message.skyabyss.calling.chosen2", Component.translatable("calling.skyabyss." + tree.id));
        return Result.OK;
    }

    public static Result choose(ServerPlayer player, String treeId) {
        return choose(player, treeId, false);
    }

    // 画面を開かせる (選べるときだけ)
    public static boolean open(ServerPlayer player) {
        int which = which(player);
        if (which == 0) {
            return false;
        }
        if (!(player instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(player, new CallingOpenPayload(which, true));
        }
        return true;
    }

    // 選べる段だけを知らせる (K の画面の「天職を選ぶ」のボタンを出すため。画面は開かない)
    public static void sendState(ServerPlayer player) {
        if (!(player instanceof FakePlayer) && !player.hasDisconnected()) {
            PacketDistributor.sendToPlayer(player, new CallingOpenPayload(which(player), false));
        }
    }

    // 少しあとに知らせて開く (心臓の報酬を選び終えたあと・ログインのとき)。選べない・待っている報酬があるあいだは開かない
    public static void offerSoon(ServerPlayer player, int delay, boolean force) {
        if (player instanceof FakePlayer) {
            return;
        }
        sendState(player);
        Tasks.later(delay, () -> {
            if (player.hasDisconnected() || player.isRemoved() || !player.isAlive() || which(player) == 0) {
                return;
            }
            // 心臓の報酬の画面が先 (選び終わったあとにここへ来る)
            if (!force && jp.revati.skyabyss.loot.reward.HeartReward.hasPending(player)) {
                return;
            }
            if (jp.revati.skyabyss.cinematic.Cinematics.active(player)) {
                offerSoon(player, 40, force);
                return;
            }
            Notice.hint(player, which(player) == 1 ? "message.skyabyss.calling.offer" : "message.skyabyss.calling.offer2");
            open(player);
        });
    }
}
