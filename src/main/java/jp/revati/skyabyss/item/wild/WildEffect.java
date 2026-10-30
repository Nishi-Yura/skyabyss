package jp.revati.skyabyss.item.wild;

// 野の装備の能力 (docs/design/51)。表 (WildGearTable) は能力を (種類, 数...) で持つ。数の並びは種類ごとにここに書く。
// tick の数は 20 = 1 秒、割合は 0.1 = 10%、半径はブロック。手当ては WildGearEvents (武器) と WildArmorEvents (防具の一式)
public final class WildEffect {
    private WildEffect() {
    }

    public enum Kind {
        // ---------------------------------------------------------------- 品の数値 (手に持つか一式で効く。属性の修飾子か、受ける傷の倍率)
        MAX_HEALTH,        // 体力の上限 +a
        MAX_MANA,          // マナの上限 +a
        KNOCKBACK,         // 押し返し +a
        REACH,             // 間合い +a
        MOVE_SPEED,        // 移動の速さ +a (割合)
        TOUGHNESS,         // 強靭 +a
        MANA_REGEN,        // マナの戻り +a (割合)
        COOLDOWN,          // 技の待ち −a (割合)
        REVIVE_SPEED,      // 倒れた仲間を起こす速さ ×a
        ARROW_SPEED,       // 矢の速さ +a (割合)
        FIRE_TAKEN,        // 火の傷 ×(1+a) (a は負)
        FALL_TAKEN,        // 落下の傷 ×(1+a)
        LIGHTNING_TAKEN,   // 雷の傷 ×(1+a)
        STEALTH,           // 敵に気づかれる距離 ×(1−a)
        REFLECT,           // 受けた傷の a を相手へ返す
        // ---------------------------------------------------------------- 与える傷の倍率 (条件つき)
        AURA_DAMAGE,       // まわり b の仲間 (自分も) の与える傷 +a (重ならない)
        SPRINT_BONUS,      // 走りながら +a
        SLAYER_BEAST,      // 獣に +a
        BACKSTAB,          // 背から +a
        FIRST_STRIKE,      // 体力が満ちた相手に +a
        EXECUTE,           // 体力 a 未満の相手に +b
        BERSERK,           // 自分の体力 a 未満で +b
        DARK_BONUS,        // 明るさ a 以下で +b
        FAR_BONUS,         // 相手が a ブロックより遠いと +b
        AIR_BONUS,         // 空中で +a
        WEATHER,           // 雨か嵐で +a、晴れはマナの戻り +b
        CRIT,              // a の確率で +b
        RAMP,              // 同じ相手を続けて打つと +a ずつ (最大 b、c tick 空くと戻る)
        ARMOR_PIERCE,      // 相手の防具を a 無視 (傷 +a × 相手の防具の効き)
        OPENER,            // 体力 a より多い相手に +b、倒すと c tick 攻撃の速さ +d
        // ---------------------------------------------------------------- 打った相手へ
        POISON,            // 毒 (a tick・段 b)
        SLOW,              // 遅く (a tick・段 b)
        WEAKEN,            // 与える傷 −b (a tick)
        BLEED,             // 出血: 当たった傷の a を b tick かけて
        BLEED_STACK,       // 出血を重ねる (最大 a、1 秒ごとに重ね × b)
        ARMOR_SHRED,       // 防具 −a (b まで重ねる、c tick)
        ARMOR_DOWN,        // 防具 −a (b tick、重ならない)
        MARK,              // 印: だれの傷も +a (b tick、光る)
        FREEZE_STACK,      // 凍え: 遅く I、a 度目ごとに遅く III (b tick)
        ROOT,              // 縛る (a tick。主は遅く II)、待ち b tick
        LINK,              // 近くの敵と結ぶ (a tick): 片方の傷の b をもう一方も (半径 c)
        HOOK_PULL,         // 待ち a tick ごとに引き寄せ、次の一撃 +b
        HOOK,              // a ブロック引き寄せ、次の一撃 +b
        PULL,              // 弓: a ブロック引き寄せ、b tick 遅く
        PULL_AOE,          // a ブロック引き寄せ、まわり c に b
        SLOW_BONUS,        // 遅く (a tick・段 b)、遅い相手に +c
        BURN_BONUS,        // 燃やす a tick、燃えている相手に +b
        STUN_CHANCE,       // a の確率で b tick しびれ
        SNUFF,             // 背から ×(1+a) と灯を消す (相手は b tick 狙いを失い遅く。主は c tick)
        HANG,              // 弓: 満ちた矢で a tick 吊る (主は遅く III)、吊った相手に +b
        // ---------------------------------------------------------------- まわりへ
        CLEAVE,            // まわり b の敵にも a
        PIERCE_LINE,       // 前の 2 体目にも a
        CHAIN,             // 近くの b 体へ a (半径 c)
        EVERY_N_BOLT,      // a 度目ごとに雷 (相手とまわり c に b)
        EVERY_N_BITE,      // a 度目ごとに追撃 b と燃え c tick
        EVERY_N_QUAKE,     // a 度目ごとにまわり c に b
        EVERY_N_ASHRAIN,   // a 度目ごとにまわり c に b、目くらみ d tick
        EVERY_N_SPIT,      // a 度目ごとに前の扇 c に b
        EVERY_N_SPARKS,    // a 度目ごとにまわり c に b、燃やす d tick
        HEAT_STACK,        // 熱 (最大 a)、満ちると次の一撃がまわり c に b
        DELAYED_BOLT,      // a tick 後に雷 (攻撃力の b、まわり c)
        EMBERBUG,          // 熾り蟲: b tick 後にいちばん近い敵で弾ける (攻撃力の a、燃やす c tick)
        FEATHERS,          // a の確率で雷の羽 b 本 (まわり d に各 c)
        PROC_BOLT,         // a の確率で雷 (攻撃力の b)
        BACK_THRUST,       // a 度目ごとに後ろ c に b
        WIND_SLASH,        // 振るたびに前へ a ブロックの風の刃 (通った敵に b)
        CONSTELLATION,     // a 度目ごとにまわり c に b
        PRISM,             // 打つたびに七色を順に (a 色)
        BACK_VENOM,        // 背からの一撃に毒 (b tick で攻撃力の a)
        MIRROR,            // 背からの一撃を a tick 後にもう一度 (b)
        KILL_BLAST,        // 倒した敵が爆ぜる (まわり b に a)
        // ---------------------------------------------------------------- 自分と仲間へ
        LIFESTEAL,         // 傷の a を体力に
        HEARTBEAT,         // 傷の a を体力に、満ちているときは鼓動を溜め (最大 b)、満ちると次の一撃 ×2
        LEECH_LOWEST,      // 傷の a を、まわり b でいちばん体力の低い仲間へ
        KILL_WARD,         // 倒すと膜 a (b tick)
        WARD_STACK,        // 打つたびに膜 1 (最大 a、b tick)、膜があると +c
        HASTE_RAMP,        // 続けて打つたび攻撃の速さ +a (最大 b、c tick で戻る)
        MOVE_RAMP,         // 打つたびに移動 +a (最大 b、c tick)、倒すと +d
        VANISH,            // a の確率で b tick 姿を消し、次の一撃 ×(1+c)
        PLUNGE,            // 落ちながら打つと ×(1+a)、まわり c に b
        TEAM_HEAL_ON_HIT,  // 打つたびにまわり b の仲間 (自分も) の体力 +a
        TEAM_HASTE,        // 打つと c tick、まわり d の仲間の移動 +a・傷 +b
        TEAM_GUARD,        // 打つと b tick、まわり c の仲間の受ける傷 −a
        TEAM_GUARD_AURA,   // 常に まわり b の仲間の受ける傷 −a
        TEAM_REGEN,        // 打つと a tick、まわり c の仲間に再生 (段 b)
        TEAM_MANA,         // 打つと b tick、まわり c の仲間のマナの戻り +a
        TEAM_HEALTH_AURA,  // 常に まわり b の仲間の体力の上限 +a
        DRUM,              // a 度目ごとに太鼓: まわり b の敵を c tick すくませ、仲間の体力 +d
        LULLABY,           // a 度目ごとに子守唄: まわり b の敵を c tick 眠らせ (主は d tick)、仲間の体力 +e
        STAR_FRUIT,        // a 度目ごとに星の実: まわり e の仲間の体力 +b、d tick 傷 +c
        HYMN,              // a の確率で祝詞: まわり d の仲間に c tick 受ける傷 −b・再生
        SMOKE,             // 打つたびに煙 (a tick・半径 b): 中の仲間の受ける傷 −c、敵は遅く
        PROTECT,           // まわり c の仲間の受ける傷の a を肩代わり、自分の受ける傷 −b
        // ---------------------------------------------------------------- 弓の矢
        PIERCE,            // 矢が a 体貫く、貫くたび +b
        RICOCHET,          // 当たった矢が近くの敵 (半径 b) へ一度跳ねる (a)
        ARROW_BURST,       // 満ちた矢が爆ぜる (まわり b に a、燃やす c tick)
        SPLIT_EVERY,       // a 本目ごとに b 本に分かれる (各 c)
        HOLD_BONUS,        // 満ちて a tick 保つと +b
        HOMING,            // 矢が a ブロック内の敵へ曲がる、+b
        BEAM,              // 満ちて a tick 保つと光の矢 (直線 b の全部の敵に ×c)
        AIR_SPLIT,         // 空中で放つ矢は a 本に分かれ (各 b)、+c
        // ---------------------------------------------------------------- 防具の一式
        SWIM,              // 水の中の移動 +a
        BREATH,            // 水の中の息が長い
        NIGHT_VISION,      // 暗い所で暗視
        LIGHT,             // まわりが明るい
        NIGHT_MANA,        // 夜はマナの戻り +a、傷 +b
        NIGHT_DAMAGE,      // 夜は与える傷 +a
        BURN_BONUS_SET,    // 燃えている相手に +a
        LAST_STAND,        // 体力 a 未満で b tick 耐性 II (待ち c tick)
        THORN_BOLT,        // 打たれると a の確率で相手に雷 (b)
        THORNS,            // 打たれると a の確率で相手に b
        BULWARK,           // 止まっていると 1 秒ごとに受ける傷 −a (最大 b)
        NOOSE,             // 倒れる傷を体力 1 で耐え、a ブロック後ろへ跳ぶ (待ち b tick)
        SNEAK_STEALTH,     // しゃがむと気づかれる距離 ×(1−a)
        GRIT,              // 受ける傷 −a、体力が半分未満で −b
        CLOUD_WARD,        // a tick ごとに膜 b、膜があるあいだ移動 +c
    }

    public record Fx(Kind kind, double[] p) {
        public double p(int i) {
            return i < p.length ? p[i] : 0;
        }

        public int ticks(int i) {
            return (int) Math.round(p(i));
        }
    }

    public static Fx fx(Kind kind, double... p) {
        return new Fx(kind, p);
    }
}
