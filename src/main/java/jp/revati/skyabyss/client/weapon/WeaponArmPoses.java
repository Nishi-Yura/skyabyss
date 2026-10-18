package jp.revati.skyabyss.client.weapon;

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
// 構えは「普段 = バニラの片手持ち」+「振っている間 = 種類ごとの動き (SwingMove)」。振りの間はバニラの腕振り・胴ひねりを止めて、
// 同じ進み具合 (model.attackTime) から動きを作る。利き腕でない手で振った (ブロックを置くなど) ときは触らない
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
        return new EnumProxy<>(HumanoidModel.ArmPose.class, false, (IArmPoseTransformer) (model, entity, arm) -> apply(kind, model, entity, arm));
    }

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

    // この描画で使う振りの進み (0 = 振っていない)。バニラの腕振りは止める
    private static float progress(HumanoidModel<?> model, LivingEntity entity) {
        float forced = WeaponClient.forcedSwing;
        if (forced >= 0 && entity == Minecraft.getInstance().player) {
            model.attackTime = 0;
            return forced;
        }
        // バニラの腕振りは常に止める (長い振りの間にバニラの 6 tick の動きが混ざらないように)。利き手以外で振ったときだけ残す
        if (entity.swingingArm == InteractionHand.MAIN_HAND) {
            model.attackTime = 0;
        }
        return SwingClock.progress(entity, SwingClock.partial());
    }

    private static void apply(WeaponKind kind, HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        boolean right = arm == HumanoidArm.RIGHT;
        float sg = right ? 1f : -1f;
        ModelPart main = right ? model.rightArm : model.leftArm;
        ModelPart off = right ? model.leftArm : model.rightArm;
        SwingMove mv = kind.move;
        // 普段の構え: 種類ごとの構え、なければバニラの片手持ち
        if (Float.isNaN(mv.idleMX)) {
            main.xRot = main.xRot * 0.5f - 0.31415927f;
            main.yRot = 0f;
        } else {
            main.xRot = mv.idleMX;
            main.yRot = sg * mv.idleMY;
        }
        // 反対の手は、何も持っていないときだけ添える
        boolean offFree = entity.getOffhandItem().isEmpty();
        if (offFree && !Float.isNaN(mv.idleOX)) {
            off.xRot = mv.idleOX;
            off.yRot = sg * mv.idleOY;
        }
        float t = progress(model, entity);
        if (t <= 0f) {
            return;
        }
        float w = mv.weight(t);
        main.xRot = Mth.lerp(w, main.xRot, mv.get(SwingMove.MX, t, main.xRot));
        main.yRot = Mth.lerp(w, main.yRot, sg * mv.get(SwingMove.MY, t, main.yRot * sg));
        main.zRot = Mth.lerp(w, main.zRot, sg * mv.get(SwingMove.MZ, t, main.zRot * sg));
        if (offFree) {
            off.xRot = Mth.lerp(w, off.xRot, mv.get(SwingMove.OX, t, off.xRot));
            off.yRot = Mth.lerp(w, off.yRot, sg * mv.get(SwingMove.OY, t, off.yRot * sg));
            off.zRot = Mth.lerp(w, off.zRot, sg * mv.get(SwingMove.OZ, t, off.zRot * sg));
        }
        // 胴のひねりに、両腕をつなげる (バニラの腕振りと同じ付け方)
        float twist = sg * mv.get(SwingMove.BY, t, 0f) * w;
        model.body.yRot = twist;
        model.rightArm.z = Mth.sin(twist) * 5f;
        model.rightArm.x = -Mth.cos(twist) * 5f;
        model.leftArm.z = -Mth.sin(twist) * 5f;
        model.leftArm.x = Mth.cos(twist) * 5f;
        model.rightArm.yRot += twist;
        model.leftArm.yRot += twist;
        // 突くとき、利き腕の肩ごと前へ出す
        main.z += mv.get(SwingMove.PZ, t, 0f) * w;
    }

    private static void applyBow(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        float sg = arm == HumanoidArm.RIGHT ? 1f : -1f;
        ModelPart main = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        ModelPart off = arm == HumanoidArm.RIGHT ? model.leftArm : model.rightArm;
        // 普段の構え: バニラの片手持ち
        main.xRot = main.xRot * 0.5f - 0.31415927f;
        main.yRot = 0f;
        float r = BowRecoil.amount(entity);
        if (r <= 0f) {
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
