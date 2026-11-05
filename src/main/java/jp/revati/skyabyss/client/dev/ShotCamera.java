package jp.revati.skyabyss.client.dev;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;

// 撮影の台本の「別のカメラ」(映像用。開発だけ)。自分の体とは別の見る所を置き、自分 (人) が歩く・走る・跳ぶ・技を出すのを外から撮る。
// 見る所は手元だけの目印 (Marker。世界には足さない) で、Minecraft.setCameraEntity で絵の視点をそこへ移す。人は今までどおりキーで動く。
// カメラの動き (置く・滑らせる・追う・人を見る) は手順を止めずに毎 tick 進むので、そのあいだに人を動かす手順を並べられる。
//   cam <x> <y> <z> <yaw> <pitch>            : そこへ置く (どの数も ~ で今のカメラのまま、~n で +n。初めてなら人の目の所が基準)
//   cam player <右> <上> <前>                 : 人の向きから見たずれの所へ置き、人を見る (前が負なら後ろ)
//   camdolly <tick> <x> <y> <z> [<yaw> <pitch>] [linear] : 今の所から <tick> かけて滑らせる (~ は今のカメラ基準)。向きを省くと、人を見るかは camlook のまま
//   camlook on|off                           : 人 (の胸の高さ) をいつも見る
//   camfollow <右> <上> <前> [<なめらかさ 0〜1>] | off : 人の向きから見たずれの所へ、毎 tick 寄っていく (既定 0.12。1 でぴったり)
//   camoff                                    : 視点を自分の体へ戻す
//   hold <forward|back|left|right|jump|sprint|sneak|attack|use> on|off : キーを押し続ける / 放す (KeyMapping。OS の入力ではない)
//   aim <tick> <yaw> <pitch>                  : 人の向きだけを <tick> かけて回す (~n で今から。キーで歩きながら向きを変える)
final class ShotCamera {
    private static Marker cam;
    private static double[] from;
    private static double[] to;
    private static int ticks;
    private static int t;
    private static boolean linear;
    private static boolean dollyAngles;
    private static boolean look;
    private static Vec3 follow;
    private static double followSmooth = 0.12;
    // aim: 人の向き
    private static float[] aimFrom;
    private static float[] aimTo;
    private static int aimTicks;

    private ShotCamera() {
    }

    static boolean handles(String op) {
        return switch (op) {
            case "cam", "camdolly", "camlook", "camfollow", "camoff", "hold", "aim" -> true;
            default -> false;
        };
    }

    // 手順を一つ進める (aim だけは回り終わるまで待つ。ほかはすぐ次へ)
    static boolean step(Minecraft mc, ShotScript.Line line, boolean first, int stepTicks) {
        switch (line.op()) {
            case "cam" -> {
                ensure(mc);
                if (line.arg(0).equals("player")) {
                    Vec3 p = local(mc, line.doubleArg(1), line.doubleArg(2), line.doubleArg(3));
                    place(p.x, p.y, p.z, cam.getYRot(), cam.getXRot());
                    look = true;
                    follow = null;
                    lookAtPlayer(mc, true);
                } else {
                    double[] c = now();
                    double[] v = new double[5];
                    for (int k = 0; k < 5; k++) {
                        v[k] = rel(line.arg(k), c[k]);
                    }
                    place(v[0], v[1], v[2], (float) v[3], (float) v[4]);
                    follow = null;
                }
                ticks = 0;
                return true;
            }
            case "camdolly" -> {
                ensure(mc);
                double[] c = now();
                int n = line.args().length;
                boolean lin = line.arg(n - 1).equalsIgnoreCase("linear");
                int m = lin ? n - 1 : n;
                to = new double[5];
                for (int k = 0; k < 3; k++) {
                    to[k] = rel(line.arg(k + 1), c[k]);
                }
                dollyAngles = m >= 6;
                to[3] = dollyAngles ? rel(line.arg(4), c[3]) : c[3];
                to[4] = dollyAngles ? rel(line.arg(5), c[4]) : c[4];
                to[3] = c[3] + Mth.wrapDegrees(to[3] - c[3]);
                from = c;
                ticks = Math.max(1, line.intArg(0));
                t = 0;
                linear = lin;
                follow = null;
                return true;
            }
            case "camlook" -> {
                look = line.arg(0).equalsIgnoreCase("on");
                return true;
            }
            case "camfollow" -> {
                ensure(mc);
                if (line.arg(0).equalsIgnoreCase("off")) {
                    follow = null;
                } else {
                    follow = new Vec3(line.doubleArg(0), line.doubleArg(1), line.doubleArg(2));
                    followSmooth = line.args().length > 3 ? line.doubleArg(3) : 0.12;
                    look = true;
                    ticks = 0;
                }
                return true;
            }
            case "camoff" -> {
                off(mc);
                return true;
            }
            case "hold" -> {
                KeyMapping k = switch (line.arg(0)) {
                    case "forward" -> mc.options.keyUp;
                    case "back" -> mc.options.keyDown;
                    case "left" -> mc.options.keyLeft;
                    case "right" -> mc.options.keyRight;
                    case "jump" -> mc.options.keyJump;
                    case "sprint" -> mc.options.keySprint;
                    case "sneak" -> mc.options.keyShift;
                    case "attack" -> mc.options.keyAttack;
                    case "use" -> mc.options.keyUse;
                    default -> throw new IllegalArgumentException("hold: unknown key " + line.arg(0));
                };
                boolean on = line.arg(1).equalsIgnoreCase("on");
                k.setDown(on);
                if (on && (k == mc.options.keyAttack || k == mc.options.keyUse)) {
                    // 押した一回を数えさせる (振り・使うの始まり)
                    KeyMapping.click(k.getKey());
                }
                return true;
            }
            case "aim" -> {
                var p = mc.player;
                if (first) {
                    aimFrom = new float[]{p.getYRot(), p.getXRot()};
                    float y = (float) rel(line.arg(1), aimFrom[0]);
                    float x = (float) rel(line.arg(2), aimFrom[1]);
                    aimTo = new float[]{aimFrom[0] + Mth.wrapDegrees(y - aimFrom[0]), x};
                    aimTicks = Math.max(1, line.intArg(0));
                }
                double e = ease(Math.min(1.0, (stepTicks + 1) / (double) aimTicks));
                p.setYRot((float) (aimFrom[0] + (aimTo[0] - aimFrom[0]) * e));
                p.setXRot((float) (aimFrom[1] + (aimTo[1] - aimFrom[1]) * e));
                return stepTicks + 1 >= aimTicks;
            }
            default -> throw new IllegalArgumentException(line.op());
        }
    }

    // 毎 tick (手順より前): カメラを進める。前の tick の所を xo に残すので、描く絵は tick のあいだも滑らか
    static void tick(Minecraft mc) {
        if (cam == null || mc.player == null || mc.level == null) {
            return;
        }
        if (cam.level() != mc.level) {
            // 次元を移った: 目印を作り直す
            double[] c = now();
            cam = null;
            ensure(mc);
            place(c[0], c[1], c[2], (float) c[3], (float) c[4]);
        }
        cam.xo = cam.xOld = cam.getX();
        cam.yo = cam.yOld = cam.getY();
        cam.zo = cam.zOld = cam.getZ();
        cam.yRotO = cam.getYRot();
        cam.xRotO = cam.getXRot();
        if (follow != null) {
            Vec3 goal = local(mc, follow.x, follow.y, follow.z);
            double a = followSmooth;
            cam.setPos(cam.getX() + (goal.x - cam.getX()) * a, cam.getY() + (goal.y - cam.getY()) * a, cam.getZ() + (goal.z - cam.getZ()) * a);
        } else if (to != null && t < ticks) {
            t++;
            double e = linear ? t / (double) ticks : ease(t / (double) ticks);
            cam.setPos(from[0] + (to[0] - from[0]) * e, from[1] + (to[1] - from[1]) * e, from[2] + (to[2] - from[2]) * e);
            if (!look || dollyAngles) {
                cam.setYRot((float) (from[3] + (to[3] - from[3]) * e));
                cam.setXRot((float) (from[4] + (to[4] - from[4]) * e));
            }
        }
        if (look && !(to != null && t < ticks && dollyAngles)) {
            lookAtPlayer(mc, false);
        }
        if (mc.getCameraEntity() != cam) {
            mc.setCameraEntity(cam);
        }
    }

    // 毎 tick の頭: 別のカメラのあいだも、押したキーで人を動かす (LocalPlayer は視点が自分のときしかキーを動きへ写さない。写さないときは前の値を残すので、ここで入れる)
    static void preTick(Minecraft mc) {
        var p = mc.player;
        if (cam == null || p == null) {
            return;
        }
        var o = mc.options;
        p.zza = (o.keyUp.isDown() ? 1.0F : 0.0F) - (o.keyDown.isDown() ? 1.0F : 0.0F);
        p.xxa = (o.keyLeft.isDown() ? 1.0F : 0.0F) - (o.keyRight.isDown() ? 1.0F : 0.0F);
        p.setJumping(o.keyJump.isDown());
        p.setSprinting(o.keySprint.isDown() && p.zza > 0);
    }

    static void off(Minecraft mc) {
        cam = null;
        follow = null;
        to = null;
        look = false;
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
    }

    // ---------------------------------------------------------------- 内側
    private static void ensure(Minecraft mc) {
        if (cam == null) {
            cam = new Marker(EntityType.MARKER, mc.level);
            var p = mc.player;
            cam.setPos(p.getX(), p.getEyeY(), p.getZ());
            cam.setYRot(p.getYRot());
            cam.setXRot(p.getXRot());
            cam.xo = cam.getX();
            cam.yo = cam.getY();
            cam.zo = cam.getZ();
            cam.yRotO = cam.getYRot();
            cam.xRotO = cam.getXRot();
            mc.setCameraEntity(cam);
        }
    }

    private static void place(double x, double y, double z, float yaw, float pitch) {
        cam.setPos(x, y, z);
        cam.setYRot(yaw);
        cam.setXRot(pitch);
        cam.xo = cam.xOld = x;
        cam.yo = cam.yOld = y;
        cam.zo = cam.zOld = z;
        cam.yRotO = yaw;
        cam.xRotO = pitch;
        to = null;
    }

    private static double[] now() {
        return new double[]{cam.getX(), cam.getY(), cam.getZ(), cam.getYRot(), cam.getXRot()};
    }

    private static double rel(String a, double base) {
        return a.startsWith("~") ? base + (a.length() > 1 ? Double.parseDouble(a.substring(1)) : 0) : Double.parseDouble(a);
    }

    // 人の向きから見たずれ (右・上・前) を世界の所へ
    private static Vec3 local(Minecraft mc, double right, double up, double fwd) {
        var p = mc.player;
        double r = Math.toRadians(p.getYRot());
        double fx = -Math.sin(r), fz = Math.cos(r);
        double rx = -fz, rz = fx;
        return new Vec3(p.getX() + fx * fwd + rx * right, p.getY() + 1.2 + up, p.getZ() + fz * fwd + rz * right);
    }

    private static void lookAtPlayer(Minecraft mc, boolean snap) {
        var p = mc.player;
        double dx = p.getX() - cam.getX();
        double dy = p.getY() + 1.1 - cam.getY();
        double dz = p.getZ() - cam.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
        if (snap) {
            cam.setYRot(yaw);
            cam.setXRot(pitch);
            cam.yRotO = yaw;
            cam.xRotO = pitch;
        } else {
            // 少し遅れて追う (手持ちのカメラのように)
            float cy = cam.getYRot();
            cam.setYRot(cy + Mth.wrapDegrees(yaw - cy) * 0.35F);
            cam.setXRot(cam.getXRot() + (pitch - cam.getXRot()) * 0.35F);
        }
    }

    private static double ease(double x) {
        return x * x * (3 - 2 * x);
    }
}
