package jp.revati.skyabyss.rpg.skill;

// 装飾・防具の品が実装すると、身に付けているあいだそのタグが 1 つ数えられる (装備側の担当が付ける。docs/design/31 の A03・A07)
public interface SkillTagged {
    SkillTag skillTag();
}
