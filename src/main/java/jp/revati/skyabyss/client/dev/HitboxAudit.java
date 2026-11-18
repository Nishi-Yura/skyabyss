package jp.revati.skyabyss.client.dev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.client.lowpoly.LowPolyMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

// 当たり判定の大きさと、描く低ポリの体の大きさを比べる (2026-11-15 の声「大きいモブの当たり判定大丈夫か。杖や弓で狙うときに重要」)。
// 撮影の台本の hitboxaudit <ファイル名> が呼ぶ。mod の生き物の型ごとに、クライアントの世界に一体作って (足さない)、
// 描き手が LowPolyGeoRenderer なら、休めの姿のメッシュの縦横 × 描く倍率と、当たりの箱 (幅・高さ) を並べる。
// 比 (当たり ÷ 見た目) が 0.6 未満の行に「小さい」、1.6 を超える行に「大きい」を付ける。大きい順 (見た目の高さ) に並べる
public final class HitboxAudit {
    private HitboxAudit() {
    }

    record Row(String id, float bbW, float bbH, float visW, float visH, float lowY, float highY, String note) {
    }

    public static String write(Path file) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return "no level";
        }
        List<Row> rows = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (!id.getNamespace().equals(SkyAbyss.MODID)) {
                continue;
            }
            Entity e;
            try {
                e = type.create(mc.level);
            } catch (RuntimeException ex) {
                continue;
            }
            if (!(e instanceof LivingEntity le)) {
                continue;
            }
            EntityRenderer<? super Entity> r;
            try {
                r = mc.getEntityRenderDispatcher().getRenderer(e);
            } catch (RuntimeException ex) {
                continue;
            }
            if (!(r instanceof LowPolyGeoRenderer<?> lp)) {
                continue;
            }
            LowPolyMesh mesh;
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                LowPolyMesh m = ((LowPolyGeoRenderer) lp).meshOf(e);
                mesh = m;
            } catch (RuntimeException ex) {
                mesh = null;
            }
            if (mesh == null) {
                continue;
            }
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (LowPolyMesh.Bone b : mesh.bones.values()) {
                for (int i = 0; i + 2 < b.pos.length; i += 3) {
                    minX = Math.min(minX, b.pos[i]);
                    maxX = Math.max(maxX, b.pos[i]);
                    minY = Math.min(minY, b.pos[i + 1]);
                    maxY = Math.max(maxY, b.pos[i + 1]);
                    minZ = Math.min(minZ, b.pos[i + 2]);
                    maxZ = Math.max(maxZ, b.pos[i + 2]);
                }
            }
            if (minX > maxX) {
                continue;
            }
            float s = lp.visualScale() * le.getScale();
            float visW = Math.max(maxX - minX, maxZ - minZ) * s;
            float visH = (maxY - minY) * s;
            // 体の長い主: 当たりの幅は、本体の箱と部品 (entity/BodyPart) を合わせた前後・左右の広がり
            float bbW = e.getBbWidth();
            float bbH = e.getBbHeight();
            if (e.isMultipartEntity() && e.getParts() != null) {
                double f0 = -bbW / 2.0, f1 = bbW / 2.0, s0 = -bbW / 2.0, s1 = bbW / 2.0;
                for (net.neoforged.neoforge.entity.PartEntity<?> part : e.getParts()) {
                    if (part instanceof jp.revati.skyabyss.entity.BodyPart bp) {
                        double w = bp.getBbWidth() / 2.0;
                        f0 = Math.min(f0, bp.forward() - w);
                        f1 = Math.max(f1, bp.forward() + w);
                        s0 = Math.min(s0, bp.side() - w);
                        s1 = Math.max(s1, bp.side() + w);
                        bbH = Math.max(bbH, bp.getBbHeight());
                    }
                }
                bbW = (float) Math.max(f1 - f0, s1 - s0);
            }
            float rw = bbW / Math.max(0.01f, visW);
            float rh = bbH / Math.max(0.01f, visH);
            // 描く体の上下 (足もとからのブロック) と、そのうち当たりの箱の高さに入っている割合
            float lowY = minY * s;
            float highY = maxY * s;
            float inside = Math.max(0f, Math.min(highY, bbH) - Math.max(lowY, 0f)) / Math.max(0.01f, highY - lowY);
            String note = inside < 0.6f ? "ずれ" : (rw < 0.6f || rh < 0.6f) ? "小さい" : (rw > 1.6f || rh > 1.6f) ? "大きい" : "";
            rows.add(new Row(id.getPath() + (e.isMultipartEntity() ? "+parts" : ""), bbW, bbH, visW, visH, lowY, highY, note));
            e.discard();
        }
        rows.sort((a, b) -> Float.compare(b.visH * b.visW, a.visH * a.visW));
        StringBuilder sb = new StringBuilder("# 当たり判定と見た目 (ブロック)。id\t当たり 幅x高\t見た目 幅x高\t比 幅/高\t見た目の上下\t印 (ずれ = 体の 6 割未満しか当たりの高さに入っていない)\n");
        int flagged = 0;
        for (Row r : rows) {
            sb.append(String.format("%s\t%.2fx%.2f\t%.2fx%.2f\t%.2f/%.2f\t%.1f..%.1f\t%s%n", r.id, r.bbW, r.bbH, r.visW, r.visH,
                    r.bbW / Math.max(0.01f, r.visW), r.bbH / Math.max(0.01f, r.visH), r.lowY, r.highY, r.note));
            if (!r.note.isEmpty()) {
                flagged++;
            }
        }
        try {
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "write failed: " + ex;
        }
        return rows.size() + " mobs, " + flagged + " flagged -> " + file.getFileName();
    }
}
