package jp.revati.skyabyss.abyss.mobs;

// 深淵の敵 A の 9 種 (docs/design/36 の 4・4.1)。骨の層 3・塩の層 3・雷の層 3。
// 元の層 (native) は、その敵が「もとの姿」で出る層 (0 骨・1 塩・2 雷)。下の層へ行くと歪み・崩れの姿で出る (AbyssScaling.form)。
// base* は縦穴の上端で湧いたときの数字。湧いた高さによる伸びは AbyssScaling (A と B で同じ決まり)。
public enum AbyssMobKind {
    BONE_PICKER("bone_picker", "骨拾い", "Bone Picker", 0, 6, 3, 0.32, 2, 0.5f, 0.45f, Mode.SWARM),
    RIB_GUARD("rib_guard", "肋の番", "Rib Guard", 0, 30, 5, 0.2, 12, 0.7f, 1.95f, Mode.SHIELD),
    BONE_FLUTE("bone_flute", "骨笛", "Bone Flute", 0, 14, 2, 0.12, 8, 0.6f, 0.6f, Mode.FLUTE),
    SALT_HUSK("salt_husk", "塩の抜け殻", "Salt Husk", 1, 26, 6, 0.3, 10, 0.6f, 1.9f, Mode.AMBUSH),
    TIDE_HANDS("tide_hands", "潮だまりの手", "Tide Hands", 1, 18, 4, 0.0, 8, 1.0f, 0.5f, Mode.SNARE),
    WHITE_SHELL_KEEPER("white_shell_keeper", "白い貝守", "White Shell Keeper", 1, 40, 5, 0.14, 14, 0.9f, 0.9f, Mode.SHELL),
    CHARGED_MOTH("charged_moth", "帯電の羽虫", "Charged Moth", 2, 5, 2, 0.35, 2, 0.4f, 0.35f, Mode.SWARM_FLY),
    LIGHTNING_IDOL("lightning_idol", "避雷の像", "Lightning Idol", 2, 24, 7, 0.0, 12, 0.7f, 2.2f, Mode.STRIKE),
    SCORCHED_BIRD("scorched_bird", "焦げた鳥の影", "Scorched Bird", 2, 16, 5, 0.5, 8, 0.8f, 0.6f, Mode.DIVE);

    // 行動の型 (AbyssMob が使う)
    public enum Mode {
        SWARM,      // 群れで寄る (骨拾い)
        SHIELD,     // 盾の前からの攻撃をほとんど通さない (肋の番)
        FLUTE,      // 浮いて、予告のあと骨拾いを呼ぶ (骨笛)
        AMBUSH,     // 固まった姿で待ち、近づくと割れて動く (塩の抜け殻)
        SNARE,     // 床から伸びる手。予告の輪のなかの人を縛る (潮だまりの手)
        SHELL,      // 殻にこもる。開いている間だけ傷が通る (白い貝守)
        SWARM_FLY,  // 光る群れで人のまわりを飛ぶ (帯電の羽虫)
        STRIKE,     // 雷を集めて落とす。予告の輪 (避雷の像)
        DIVE        // 上から影が差し、急降下して掴んで少し持ち上げる (焦げた鳥の影)
    }

    public final String id;
    public final String ja;
    public final String en;
    public final int native_;
    public final float baseHp;
    public final float baseDamage;
    public final double baseSpeed;
    public final int xp;
    public final float width;
    public final float height;
    public final Mode mode;

    AbyssMobKind(String id, String ja, String en, int nativeLayer, float baseHp, float baseDamage, double baseSpeed, int xp,
            float width, float height, Mode mode) {
        this.id = id;
        this.ja = ja;
        this.en = en;
        this.native_ = nativeLayer;
        this.baseHp = baseHp;
        this.baseDamage = baseDamage;
        this.baseSpeed = baseSpeed;
        this.xp = xp;
        this.width = width;
        this.height = height;
        this.mode = mode;
    }

    public static AbyssMobKind byId(String id) {
        for (AbyssMobKind k : values()) {
            if (k.id.equals(id)) {
                return k;
            }
        }
        return null;
    }

    // その層で出る敵 (元の層が一致するもの)
    public static java.util.List<AbyssMobKind> nativeTo(int layer) {
        java.util.List<AbyssMobKind> out = new java.util.ArrayList<>();
        for (AbyssMobKind k : values()) {
            if (k.native_ == layer) {
                out.add(k);
            }
        }
        return out;
    }
}
