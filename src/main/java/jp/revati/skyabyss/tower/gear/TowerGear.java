package jp.revati.skyabyss.tower.gear;

import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.rpg.skill.Job;

// あいだの塔の報酬の表と数値 (docs/design/54 の 2.6)。
//   塔の装備  職ごとの武器 6・防具 3 式 (4 部位)・装飾 2 (どれもヒロイック・必要 Lv 50)。武器の強さは神器と横並び (1 秒あたりの傷が同じ)、
//             効き方が違う (TowerGearEvents)
//   塔の欠片  主を倒した人に毎回 SHARDS_PER_CLEAR。塔の装備の強化の燃料 (共鳴の炉。由来 tower の図鑑の燃料) と、塔の店の値段
//   塔の箱    主を倒した人に毎回一つ (封切り師で開ける。中は塔の装備。loot/box の box_tower)
// | 品               | 職     | 攻撃 / 速さ | 効き方 |
// | あいだの大剣     | 戦士   | 15.0 / 1.6  | 傾き: 自分の体力が半分より上なら傷 +15%、半分以下なら与えた傷の 10% を体力に |
// | 引き合いの鎚     | 鍛冶師 | 15.2 / 1.6  | 4 度目ごとに引き合いの一撃 (傷 ×1.5、まわり 5 の敵を自分のほうへ引き寄せる) |
// | 落ちる月の杖     | 術士   | 20.0 / 1.2  | 打った相手に月の印 6 秒。印のある相手への 3 打目ごとに小さな月が落ちる (まわり 3 に攻撃力の 120%) |
// | 側の目の弓       | 弓使い | 矢 ×2.3     | 同じ相手に続けて当てるたびに傷 +6% (5 重ねで +30%)。別の相手に当てると数え直し |
// | 追う印の短剣     | 暗殺者 | 10.8 / 2.0  | 打つと 3 秒足が速くなり (+20%)、その間の次の一撃は +35% (3 秒に一度) |
// | 合掌の鈴杖       | 祈祷師 | 18.2 / 1.4  | 打つたびに祈りが一つ。5 つで合掌: まわり 6 の仲間 (自分も) の体力 4 とマナ 6 を戻す |
// | 防具 (4 部位)    | 防御 / 強靭      | 4 部位の効果 |
// | 天秤の甲冑 (重)  | 6/10/8/6 · 5     | 受ける傷 ×0.92。体力が半分以下のあいだ強靭 +10 |
// | 渡りの装束 (軽)  | 4/8/6/4 · 3      | 速さ +8%。走っているあいだ受ける傷 ×0.93 |
// | 光輪の衣 (術)    | 4/7/5/4 · 2      | マナの戻り +30%、技の待ち −6% |
// | 天の環 (装飾)    | —                | 体力が満ちているあいだ与える傷 +6% |
// | 淵の鎖 (装飾)    | —                | 体力が 3 割以下のあいだ受ける傷 ×0.85 |
public final class TowerGear {
    public static final String DUNGEON = "tower";
    public static final int REQUIRED_LEVEL = 50;
    public static final int SHARDS_PER_CLEAR = 10;
    public static final String SHARD = id("tower_shard");
    public static final String SWORD = id("tower_balance_sword");
    public static final String HAMMER = id("tower_pull_hammer");
    public static final String STAFF = id("tower_moon_staff");
    public static final String BOW = id("tower_eye_bow");
    public static final String DAGGER = id("tower_sigil_dagger");
    public static final String BELL = id("tower_clap_bell_staff");
    public static final List<String> WEAPONS = List.of(SWORD, HAMMER, STAFF, BOW, DAGGER, BELL);
    // 防具の一式 (品は <一式>_<helmet|chestplate|leggings|boots>) と装飾
    public static final String SCALE = "tower_scale";
    public static final String BRIDGE = "tower_bridge";
    public static final String HALO = "tower_halo";
    public static final List<String> ARMOR_SETS = List.of(SCALE, BRIDGE, HALO);
    public static final List<String> PIECES = List.of("helmet", "chestplate", "leggings", "boots");
    public static final String RING = id("tower_heaven_ring");
    public static final String CHAIN = id("tower_abyss_chain");

    // ---------------------------------------------------------------- 数値 (神器と同じ 1 秒あたり)
    public static final double SWORD_ATTACK = 15.0;
    public static final double SWORD_SPEED = 1.6;
    public static final double SWORD_HIGH = 0.15;
    public static final double SWORD_DRAIN = 0.10;
    public static final double HAMMER_ATTACK = 15.2;
    public static final double HAMMER_SPEED = 1.6;
    public static final int HAMMER_EVERY = 4;
    public static final double HAMMER_PULL = 1.5;
    public static final double HAMMER_RADIUS = 5;
    public static final double STAFF_ATTACK = 20.0;
    public static final double STAFF_SPEED = 1.2;
    public static final int STAFF_MARK_TICKS = 120;
    public static final int STAFF_EVERY = 3;
    public static final double STAFF_MOON = 1.2;
    public static final double STAFF_RADIUS = 3;
    public static final double BOW_MULTIPLIER = 2.3;
    public static final double BOW_STEP = 0.06;
    public static final int BOW_MAX = 5;
    public static final double DAGGER_ATTACK = 10.8;
    public static final double DAGGER_SPEED = 2.0;
    public static final int DAGGER_TICKS = 60;
    public static final double DAGGER_NEXT = 0.35;
    public static final double DAGGER_SPEED_UP = 0.20;
    public static final double BELL_ATTACK = 18.2;
    public static final double BELL_SPEED = 1.4;
    public static final int BELL_PRAYERS = 5;
    public static final double BELL_RADIUS = 6;
    public static final float BELL_HEAL = 4f;
    public static final double BELL_MANA = 6;

    // 防具 (頭・胴・脚・靴の防御と、部位ごとの強靭)
    public static final int[][] DEFENSE = {{6, 10, 8, 6}, {4, 8, 6, 4}, {4, 7, 5, 4}};
    public static final float[] TOUGHNESS = {5f, 3f, 2f};
    public static final double SCALE_TAKEN = 0.92;
    public static final double SCALE_LOW_TOUGHNESS = 10;
    public static final double BRIDGE_SPEED = 0.08;
    public static final double BRIDGE_SPRINT_TAKEN = 0.93;
    public static final double HALO_REGEN = 0.30;
    public static final double HALO_COOLDOWN = 0.06;
    public static final double RING_BONUS = 0.06;
    public static final double CHAIN_LOW = 0.3;
    public static final double CHAIN_TAKEN = 0.85;

    // 図鑑の形は燃料のためだけ (燃料 = 塔の欠片)。塔の装備は塔の箱と塔の店で手に入る (心核の抽選には入れない)
    public static final TitanGearSet SET = new TitanGearSet(DUNGEON, SHARD, id("tower_none"), List.of());

    static final Map<Job, String> BY_JOB = Map.of(Job.WARRIOR, SWORD, Job.SMITH, HAMMER, Job.MAGE, STAFF, Job.ARCHER, BOW, Job.ASSASSIN, DAGGER,
            Job.SHAMAN, BELL);

    private TowerGear() {
    }

    static String id(String path) {
        return SkyAbyss.MODID + ":" + path;
    }

    public static String forJob(Job job) {
        return job == null ? SWORD : BY_JOB.getOrDefault(job, SWORD);
    }
}
