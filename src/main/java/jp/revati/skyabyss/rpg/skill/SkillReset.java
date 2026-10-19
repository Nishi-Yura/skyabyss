package jp.revati.skyabyss.rpg.skill;

import net.minecraft.server.level.ServerPlayer;

// 技の記録のまとめた初期化の口。転生 (rebirth パッケージ) はここの forRebirth を呼ぶ。docs/design/31 の 2 節
public final class SkillReset {
    private SkillReset() {
    }

    // 転生: 技の点 (覚えた技・刻印・キーストーン・追加の点)、天職、構え、連鎖、武器ごとの枠、マナとクールダウンを 0 に戻す。
    // 残すもの: 型の保存 (4 つ。覚えていない技は読み込むときに外れる)、熟練 (転生しても残る)、これまでの記録の回数
    public static void forRebirth(ServerPlayer player) {
        SkillData d = SkillManager.get(player);
        d.clearSkills();
        d.bonusPoints = 0;
        d.calling = "";
        d.calling2 = "";
        d.stance = 0;
        d.stanceCd = 0;
        d.bar = 0;
        d.respecs = 0;
        d.runeSwaps = 0;
        d.notifiedPoints = 0;
        d.cooldowns.clear();
        d.globalCooldown = 0;
        d.mana = -1;
        d.chainSkill = "";
        d.lastCastSlot = -1;
        // 紋の入手の記録は空に戻る (越え直す試練でまた紋が出る)。変異・持っている紋・盤の紋は残す
        d.glyphClaims.clear();
        d.echoUntil = 0;
        SkillManager.settleMana(player);
        SkillManager.sync(player);
        SkillManager.applyModifiers(player);
        SkillCalling.sendState(player);
    }
}
