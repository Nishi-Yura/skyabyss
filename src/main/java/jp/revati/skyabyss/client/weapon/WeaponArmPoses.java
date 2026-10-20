package jp.revati.skyabyss.client.weapon;

import jp.revati.skyabyss.weapon.MoveSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

// 武器ごとの腕の構え (三人称・ほかのプレイヤーから見た姿)。HumanoidModel.ArmPose を NeoForge の拡張 enum で足す。
// 登録は META-INF/enumextensions.json (名前 SKYABYSS_*、各フィールドをここで EnumProxy として渡す)。
// 構えは「普段 = 品ごとの構え (MoveSet の idle。なければバニラの片手持ち) + 待機のゆれ」+「振っている間・点検の間 = 品ごとの動き (MoveSet の段)」。
// どの品も同じ変換を通り、持っている品の MoveSet を引いて動かす。動きの間はバニラの腕振り・胴ひねりを止めて、
// 同じ時計 (SwingClock) の進みから作る。利き腕でない手で振った (ブロックを置くなど) ときは触らない
public final class WeaponArmPoses {
    public static final EnumProxy<HumanoidModel.ArmPose> GREATSWORD = proxy(WeaponKind.GREATSWORD);
    public static final EnumProxy<HumanoidModel.ArmPose> PIKE = proxy(WeaponKind.PIKE);
    public static final EnumProxy<HumanoidModel.ArmPose> GLAIVE = proxy(WeaponKind.GLAIVE);
    public static final EnumProxy<HumanoidModel.ArmPose> FANG = proxy(WeaponKind.FANG);
    public static final EnumProxy<HumanoidModel.ArmPose> CLAW = proxy(WeaponKind.CLAW);
    public static final EnumProxy<HumanoidModel.ArmPose> AXE = proxy(WeaponKind.AXE);
    public static final EnumProxy<HumanoidModel.ArmPose> HAMMER = proxy(WeaponKind.HAMMER);
    public static final EnumProxy<HumanoidModel.ArmPose> POKER = proxy(WeaponKind.POKER);

    // 雷翼の弓: 放したあとの反動 (BowRecoil)。引いている間はバニラの構え
    public static final EnumProxy<HumanoidModel.ArmPose> BOW = new EnumProxy<>(HumanoidModel.ArmPose.class, false,
            (IArmPoseTransformer) WeaponArmPoses::applyBow);

    private WeaponArmPoses() {
    }

    private static EnumProxy<HumanoidModel.ArmPose> proxy(WeaponKind kind) {
        return new EnumProxy<>(HumanoidModel.ArmPose.class, false, (IArmPoseTransformer) WeaponArmPoses::apply);
    }

    // 腕の構えの種類 (enum) は登録の名前のためだけに残してあり、変換はどれも同じ
    static HumanoidModel.ArmPose of(WeaponKind kind) {
        return switch (kind) {
            case GREATSWORD -> GREATSWORD.getValue();
            case PIKE -> PIKE.getValue();
            case GLAIVE -> GLAIVE.getValue();
            case FANG -> FANG.getValue();
            case CLAW -> CLAW.getValue();
            case AXE -> AXE.getValue();
            case HAMMER -> HAMMER.getValue();
            case POKER -> POKER.getValue();
        };
    }

    // 盾 (主の手で殴る) も、同じ変換を使う
    static HumanoidModel.ArmPose generic() {
        return POKER.getValue();
    }

    // 角度 (度) を -180..180 に。全回転 (360 度など) に混ぜ具合をかけても、巻き戻って見えないようにする
    static float wrap(float degrees) {
        float d = degrees % 360f;
        return d > 180f ? d - 360f : d <= -180f ? d + 360f : d;
    }

    // バニラの腕振りは常に止める (長い振りの間にバニラの 6 tick の動きが混ざらないように)。利き手以外で振ったときだけ残す
    private static void stopVanillaSwing(HumanoidModel<?> model, LivingEntity entity) {
        if (entity.swingingArm == InteractionHand.MAIN_HAND || (WeaponClient.forcedSwing >= 0 && entity == Minecraft.getInstance().player)) {
            model.attackTime = 0;
        }
    }

    private static void apply(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        MoveSet ms = SwingClock.setOf(entity);
        if (ms == null) {
            return;
        }
        boolean right = arm == HumanoidArm.RIGHT;
        float sg = right ? 1f : -1f;
        ModelPart main = right ? model.rightArm : model.leftArm;
        ModelPart off = right ? model.leftArm : model.rightArm;
        // 普段の構え: 品ごとの構え、なければバニラの片手持ち
        if (Float.isNaN(ms.idleMX)) {
            main.xRot = main.xRot * 0.5f - 0.31415927f;
            main.yRot = 0f;
        } else {
            main.xRot = ms.idleMX;
            main.yRot = sg * ms.idleMY;
        }
        // 反対の手は、何も持っていないときだけ添える
        boolean offFree = entity.getOffhandItem().isEmpty();
        if (offFree && !Float.isNaN(ms.idleOX)) {
            off.xRot = ms.idleOX;
            off.yRot = sg * ms.idleOY;
        }
        stopVanillaSwing(model, entity);
        float partial = SwingClock.partial();
        SwingClock.Active a = SwingClock.current(entity, partial);
        // 待機のゆれ: 腕がゆっくり呼吸する (動きの間は混ぜない)
        if (a == null) {
            float ph = (entity.tickCount + partial) / 20f * ms.swayHz * Mth.TWO_PI + entity.getId();
            float s = Mth.sin(ph) * ms.sway;
            main.xRot += s;
            main.zRot += sg * Mth.cos(ph * 0.7f) * ms.sway * 0.5f;
            if (offFree && !Float.isNaN(ms.idleOX)) {
                off.xRot += s * 0.8f;
            }
            return;
        }
        MoveSet.Step st = a.step();
        float t = a.t();
        float w = a.w();
        main.xRot = Mth.lerp(w, main.xRot, st.get(MoveSet.MX, t, main.xRot));
        main.yRot = Mth.lerp(w, main.yRot, sg * st.get(MoveSet.MY, t, main.yRot * sg));
        main.zRot = Mth.lerp(w, main.zRot, sg * st.get(MoveSet.MZ, t, main.zRot * sg));
        if (offFree) {
            off.xRot = Mth.lerp(w, off.xRot, st.get(MoveSet.OX, t, off.xRot));
            off.yRot = Mth.lerp(w, off.yRot, sg * st.get(MoveSet.OY, t, off.yRot * sg));
            off.zRot = Mth.lerp(w, off.zRot, sg * st.get(MoveSet.OZ, t, off.zRot * sg));
        }
        // 胴のひねりに、両腕をつなげる (バニラの腕振りと同じ付け方)
        float twist = sg * st.get(MoveSet.BY, t, 0f) * w;
        model.body.yRot = twist;
        model.rightArm.z = Mth.sin(twist) * 5f;
        model.rightArm.x = -Mth.cos(twist) * 5f;
        model.leftArm.z = -Mth.sin(twist) * 5f;
        model.leftArm.x = Mth.cos(twist) * 5f;
        model.rightArm.yRot += twist;
        model.leftArm.yRot += twist;
        // 突くとき、利き腕の肩ごと前へ出す
        main.z += st.get(MoveSet.PZ, t, 0f) * w;
    }

    private static void applyBow(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        float sg = arm == HumanoidArm.RIGHT ? 1f : -1f;
        ModelPart main = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        ModelPart off = arm == HumanoidArm.RIGHT ? model.leftArm : model.rightArm;
        // 普段の構え: バニラの片手持ち + 待機のゆれ
        main.xRot = main.xRot * 0.5f - 0.31415927f;
        main.yRot = 0f;
        float r = BowRecoil.amount(entity);
        if (r <= 0f) {
            if (!BowRecoil.drawing(entity)) {
                float ph = (entity.tickCount + SwingClock.partial()) / 20f * 0.3f * Mth.TWO_PI + entity.getId();
                main.xRot += Mth.sin(ph) * 0.03f;
                main.zRot += sg * Mth.cos(ph * 0.7f) * 0.015f;
            }
            return;
        }
        // 放した直後は引いていた構えのまま、すぐ普段の構えへ戻る。弓の手は跳ね上がり、弦の手は外へ開く
        float aim = Mth.clamp(r * 2.2f, 0f, 1f);
        float aimX = (float) (-Math.PI / 2) + model.head.xRot;
        main.xRot = Mth.lerp(aim, main.xRot, aimX) - 0.3f * r;
        main.yRot = Mth.lerp(aim, main.yRot, -0.1f * sg + model.head.yRot);
        off.xRot = Mth.lerp(aim, off.xRot, aimX);
        off.yRot = Mth.lerp(aim, off.yRot, 0.5f * sg + model.head.yRot);
        off.zRot -= sg * 0.9f * r * (1f - r);
    }
}
