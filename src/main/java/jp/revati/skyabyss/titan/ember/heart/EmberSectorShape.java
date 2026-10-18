package jp.revati.skyabyss.titan.ember.heart;

import jp.revati.skyabyss.combat.TelegraphShape;

// 熾の底の区画 k (六つの扇。中心から 4〜15) の形の予告。当たりは熾の底が区画を数える式 (EmberHeartArena.sectorAt) そのもの、
// 印は区画の縁 (内側と外側の弧・両脇の線) と、内から外へ満ちていく弧。崩れる床の予告に使う (一つの区画 ≒ 床の 1 割強)
public record EmberSectorShape(int k) implements TelegraphShape {
    private static final double CX = EmberHeartArena.CX + 0.5;
    private static final double CZ = EmberHeartArena.CZ + 0.5;

    private double from() {
        return Math.toRadians(EmberHeartArena.sectorAngle(k) - 30);
    }

    private double to() {
        return Math.toRadians(EmberHeartArena.sectorAngle(k) + 30);
    }

    @Override
    public boolean contains(double x, double z) {
        return EmberHeartArena.sectorAt(x, z) == k;
    }

    private void arc(PointSink sink, double r, double spacing) {
        int n = Math.max(2, (int) Math.ceil(Math.abs(to() - from()) * r / spacing));
        for (int i = 0; i <= n; i++) {
            double a = from() + (to() - from()) * i / n;
            sink.point(CX + Math.cos(a) * r, CZ + Math.sin(a) * r);
        }
    }

    private void spoke(PointSink sink, double angle, double spacing) {
        double r0 = EmberHeartArena.PLATFORM_R + 0.4;
        double r1 = EmberHeartArena.SECTION_R - 0.4;
        int n = Math.max(1, (int) Math.ceil((r1 - r0) / spacing));
        for (int i = 0; i <= n; i++) {
            double r = r0 + (r1 - r0) * i / n;
            sink.point(CX + Math.cos(angle) * r, CZ + Math.sin(angle) * r);
        }
    }

    @Override
    public void outline(PointSink sink, double spacing) {
        arc(sink, EmberHeartArena.PLATFORM_R + 0.4, spacing);
        arc(sink, EmberHeartArena.SECTION_R - 0.4, spacing);
        spoke(sink, from() + 0.03, spacing);
        spoke(sink, to() - 0.03, spacing);
    }

    @Override
    public void edge(double progress, PointSink sink, double spacing) {
        double r0 = EmberHeartArena.PLATFORM_R + 0.4;
        double r1 = EmberHeartArena.SECTION_R - 0.4;
        double r = r0 + (r1 - r0) * Math.max(0.05, Math.min(1, progress));
        arc(sink, r, spacing * 1.3);
    }

    @Override
    public double[] bounds() {
        double r = EmberHeartArena.SECTION_R;
        return new double[] {CX - r, CZ - r, CX + r, CZ + r};
    }

    @Override
    public double centerX() {
        return CX + Math.cos((from() + to()) / 2) * 9.5;
    }

    @Override
    public double centerZ() {
        return CZ + Math.sin((from() + to()) / 2) * 9.5;
    }
}
