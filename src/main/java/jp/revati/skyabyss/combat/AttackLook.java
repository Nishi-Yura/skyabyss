package jp.revati.skyabyss.combat;

// 心臓の技・領域の決まりの「見た目の言葉」(2026-10-23 の試遊「巨神ボスの攻撃をもっとわかりやすく、もっと派手に」)。
// 床の赤い予告 (Telegraph の印) はそのまま。これはその上に足す粒だけで、判定・傷・予告の長さには触れない。
// 一つの技に一つ。Telegraph.Builder.look(...) で付け、床の印のパケットに番号で乗る。描くのはクライアント (client/combat/AttackFx)。
// 技ごとの違いは、属性 (粒の種類と色) と、四つの段の組み合わせで出す:
//   溜め (Windup)  : 予告のあいだ。技の元 (心臓) か床に、力が集まる・湧き上がる・ひびが走る・空から落ちてくる兆し
//   運び (Travel)  : 当たる直前・最中。飛んでいく弾の軌跡、帯・扇の走る縁、広がる波の頭、空から落ちる雷・殻
//   当たり (Impact): 当たった瞬間。砕ける・水柱・雷の閃光・噴き上がる炎
//   残り (Linger)  : 当たったあと少し。舞う砂ぼこり・泡・残る静電気・くすぶる煙・広がる衝撃の輪
// 数は client 側で 1 tick の上限 (と画面の粒の設定) に合わせて間引く。
public enum AttackLook {
    NONE(Element.STONE, Windup.NONE, Travel.NONE, Impact.NONE, Linger.NONE, 1f),

    // ---- 岩鯨 (石・骨)
    WHALE_ROAR(Element.STONE, Windup.ORBIT, Travel.WAVE, Impact.NONE, Linger.NONE, 0.6f),
    WHALE_THRUST(Element.STONE, Windup.GATHER, Travel.BEAM, Impact.PIERCE, Linger.DUST, 1f),
    WHALE_SWEEP(Element.STONE, Windup.CREEP, Travel.NONE, Impact.BURST, Linger.DUST, 0.9f),
    WHALE_SPIKE(Element.STONE, Windup.CRACK, Travel.NONE, Impact.COLUMN, Linger.DUST, 0.8f),
    WHALE_LEAP(Element.STONE, Windup.RISE, Travel.FALL, Impact.BURST, Linger.SHOCK, 1.3f),
    WHALE_LEAP_WAVE(Element.STONE, Windup.NONE, Travel.WAVE, Impact.RING, Linger.DUST, 1f),
    WHALE_CRACK(Element.STONE, Windup.CRACK, Travel.NONE, Impact.FISSURE, Linger.SMOLDER, 1f),
    WHALE_VOLLEY(Element.STONE, Windup.GATHER, Travel.SHOTS, Impact.PIERCE, Linger.DUST, 0.8f),
    WHALE_BEAM(Element.STONE, Windup.GATHER, Travel.SWEEP, Impact.NONE, Linger.DUST, 1.1f),
    WHALE_CHAIN(Element.STONE, Windup.CREEP, Travel.SWEEP, Impact.NONE, Linger.DUST, 0.9f),
    WHALE_SHELL_DROP(Element.STONE, Windup.SKY, Travel.FALL, Impact.BURST, Linger.SHOCK, 1.1f),

    // ---- 潮殻 (水・珊瑚・泡)
    TIDE_ROAR(Element.WATER, Windup.ORBIT, Travel.NONE, Impact.RING, Linger.NONE, 0.6f),
    TIDE_RIPPLE(Element.WATER, Windup.NONE, Travel.WAVE, Impact.RING, Linger.FOAM, 1f),
    TIDE_BREATH(Element.WATER, Windup.GATHER, Travel.BEAM, Impact.PIERCE, Linger.FOAM, 1.1f),
    TIDE_SHELL_STRIKE(Element.WATER, Windup.RISE, Travel.NONE, Impact.BURST, Linger.SHOCK, 1.2f),
    TIDE_WHIRL(Element.WATER, Windup.SPIRAL, Travel.NONE, Impact.BURST, Linger.FOAM, 1.1f),
    TIDE_SPEAR(Element.WATER, Windup.RISE, Travel.NONE, Impact.COLUMN, Linger.FOAM, 0.8f),
    TIDE_EBB(Element.WATER, Windup.GATHER, Travel.NONE, Impact.BURST, Linger.FOAM, 1.2f),
    TIDE_MOON_WAVE(Element.WATER, Windup.CREEP, Travel.SWEEP, Impact.NONE, Linger.FOAM, 1.1f),
    TIDE_SPRING_TIDE(Element.WATER, Windup.SPIRAL, Travel.NONE, Impact.COLUMN, Linger.SHOCK, 1.4f),

    // ---- 雷翼 (雷・火花・閃光)
    STORM_ROAR(Element.STORM, Windup.ORBIT, Travel.WAVE, Impact.NONE, Linger.NONE, 0.6f),
    STORM_BOLT(Element.STORM, Windup.FLICKER, Travel.BOLT, Impact.FLASH, Linger.STATIC, 1f),
    STORM_BLADE(Element.STORM, Windup.CREEP, Travel.SWEEP, Impact.NONE, Linger.STATIC, 1f),
    STORM_CHAIN(Element.STORM, Windup.FLICKER, Travel.BEAM, Impact.PIERCE, Linger.STATIC, 0.9f),
    STORM_CHAIN_NODE(Element.STORM, Windup.FLICKER, Travel.BOLT, Impact.FLASH, Linger.STATIC, 0.7f),
    STORM_DOWNDRAFT(Element.STORM, Windup.SKY, Travel.NONE, Impact.BURST, Linger.SHOCK, 1.1f),
    STORM_WING_WAVE(Element.STORM, Windup.NONE, Travel.WAVE, Impact.RING, Linger.STATIC, 1f),
    STORM_CHARGE(Element.STORM, Windup.GATHER, Travel.ARC, Impact.FLASH, Linger.SHOCK, 1.3f),
    STORM_FEATHER(Element.STORM, Windup.SKY, Travel.FALL, Impact.BURST, Linger.STATIC, 0.7f),
    STORM_FLASH(Element.STORM, Windup.GATHER, Travel.NONE, Impact.FLASH, Linger.STATIC, 1.2f),
    STORM_WEB(Element.STORM, Windup.FLICKER, Travel.SWEEP, Impact.NONE, Linger.STATIC, 0.9f),
    STORM_CROSS(Element.STORM, Windup.CREEP, Travel.BEAM, Impact.PIERCE, Linger.STATIC, 1f),

    // ---- 余燼 (熾・溶岩・煙)
    EMBER_ROAR(Element.EMBER, Windup.ORBIT, Travel.WAVE, Impact.NONE, Linger.NONE, 0.6f),
    EMBER_BREATH(Element.EMBER, Windup.GATHER, Travel.BEAM, Impact.PIERCE, Linger.SMOLDER, 1.1f),
    EMBER_PULSE(Element.EMBER, Windup.NONE, Travel.WAVE, Impact.RING, Linger.SMOLDER, 1f),
    EMBER_ORB(Element.EMBER, Windup.ORBIT, Travel.ARC, Impact.BURST, Linger.SMOLDER, 1f),
    EMBER_CRACK(Element.EMBER, Windup.CRACK, Travel.NONE, Impact.BURST, Linger.SMOLDER, 1.2f),
    EMBER_GALE(Element.EMBER, Windup.SPIRAL, Travel.NONE, Impact.BURST, Linger.SHOCK, 1.1f),
    EMBER_GALE_WAVE(Element.EMBER, Windup.NONE, Travel.WAVE, Impact.RING, Linger.SMOLDER, 1f),
    EMBER_PELLET(Element.EMBER, Windup.NONE, Travel.ARC, Impact.BURST, Linger.SMOLDER, 0.7f),
    EMBER_FADING(Element.EMBER, Windup.GATHER, Travel.NONE, Impact.BURST, Linger.SHOCK, 1.3f),
    EMBER_FADING_WAVE(Element.EMBER, Windup.NONE, Travel.WAVE, Impact.RING, Linger.SMOLDER, 1f),
    EMBER_FORGE(Element.EMBER, Windup.CREEP, Travel.NONE, Impact.BURST, Linger.SMOLDER, 1f),
    EMBER_FORGE_CORE(Element.EMBER, Windup.RISE, Travel.NONE, Impact.COLUMN, Linger.SHOCK, 1.3f),
    EMBER_BLAZE(Element.EMBER, Windup.RISE, Travel.NONE, Impact.COLUMN, Linger.SMOLDER, 0.9f),
    EMBER_EMBERS(Element.EMBER, Windup.FLICKER, Travel.NONE, Impact.BURST, Linger.SMOLDER, 0.7f),

    // ---- 領域の決まり
    DOMAIN_STONE_WAVE(Element.STONE, Windup.NONE, Travel.WAVE, Impact.RING, Linger.DUST, 1.2f),
    DOMAIN_STONE_SPIKE(Element.STONE, Windup.CRACK, Travel.NONE, Impact.COLUMN, Linger.DUST, 1f),
    DOMAIN_SHELL_SLAM(Element.WATER, Windup.SKY, Travel.FALL, Impact.BURST, Linger.SHOCK, 1.1f),
    DOMAIN_SHELL_SWEEP(Element.WATER, Windup.CREEP, Travel.SWEEP, Impact.NONE, Linger.FOAM, 1f),
    DOMAIN_THUNDER_LINE(Element.STORM, Windup.FLICKER, Travel.BEAM, Impact.PIERCE, Linger.STATIC, 1f),
    DOMAIN_THUNDER_ROD(Element.STORM, Windup.GATHER, Travel.BOLT, Impact.FLASH, Linger.STATIC, 1.1f),
    DOMAIN_HEARTH_HEAT(Element.EMBER, Windup.RISE, Travel.NONE, Impact.BURST, Linger.SMOLDER, 1f);

    public enum Element {
        STONE, WATER, STORM, EMBER
    }

    // 溜め: GATHER 床から技の元へ吸い込まれる / RISE 床から湧き上がる / CREEP 縁を這う / CRACK 中心からひびが走る /
    //       FLICKER 火花がまたたく / SKY 空から兆しが降る / ORBIT 技の元のまわりを回り締まる / SPIRAL 渦を巻いて中心へ
    public enum Windup {
        NONE, GATHER, RISE, CREEP, CRACK, FLICKER, SKY, ORBIT, SPIRAL
    }

    // 運び: BEAM 元から帯の先までの奔流 / SHOTS 帯を飛ぶ弾 / ARC 元から弧を描いて落ちる弾 / FALL 空から落ちる塊 /
    //       BOLT 空からの稲妻 / SWEEP 振り回しの縁 / WAVE 広がる波の頭
    public enum Travel {
        NONE, BEAM, SHOTS, ARC, FALL, BOLT, SWEEP, WAVE
    }

    // 当たり: BURST 面が弾ける / COLUMN 柱が立つ / PIERCE 帯を貫く / RING 縁が噴く / FLASH 閃光 / FISSURE 裂け目から噴く
    public enum Impact {
        NONE, BURST, COLUMN, PIERCE, RING, FLASH, FISSURE
    }

    // 残り: DUST 砂ぼこり / FOAM 泡 / STATIC 静電気 / SMOLDER くすぶり / SHOCK 広がる衝撃の輪
    public enum Linger {
        NONE, DUST, FOAM, STATIC, SMOLDER, SHOCK
    }

    public final Element element;
    public final Windup windup;
    public final Travel travel;
    public final Impact impact;
    public final Linger linger;
    // 粒の量の重み (大技は多め)
    public final float weight;

    AttackLook(Element element, Windup windup, Travel travel, Impact impact, Linger linger, float weight) {
        this.element = element;
        this.windup = windup;
        this.travel = travel;
        this.impact = impact;
        this.linger = linger;
        this.weight = weight;
    }

    public static AttackLook byId(int id) {
        AttackLook[] all = values();
        return id >= 0 && id < all.length ? all[id] : NONE;
    }
}
