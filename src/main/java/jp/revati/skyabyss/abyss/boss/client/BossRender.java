package jp.revati.skyabyss.abyss.boss.client;

import com.mojang.blaze3d.vertex.PoseStack;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.boss.BossLook;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.entity.GuardMob;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

// 層の主・歪んだ王の素材 (BossLook の look が 0 でない深淵の敵)。素材は tools/gen/abyss/gen_abyss_boss_models.py が入れる:
//   lowpoly/<stem>.json・textures/lowpoly/<stem>.png (+ _glowmask)・geo/entity/<stem>_lowpoly(_debug).geo.json・animations/entity/<animStem>.animation.json
// 深淵の敵の二つの描画 (AbyssMobRenderers・AbyssMobsBRenderers) の模型が、look のあるときだけこちらを返す。
public final class BossRender {
    private BossRender() {
    }

    public static int look(Entity e) {
        return e instanceof GuardMob g ? g.bossLook() : 0;
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path);
    }

    public static ResourceLocation geo(int look) {
        String s = BossLook.stem(look);
        return id("geo/entity/" + s + (LowPoly.enabled && !LowPoly.debug ? "_lowpoly" : "_lowpoly_debug") + ".geo.json");
    }

    public static ResourceLocation texture(int look) {
        return id("textures/lowpoly/" + BossLook.stem(look) + ".png");
    }

    public static ResourceLocation animation(int look) {
        return id("animations/entity/" + BossLook.animStem(look) + ".animation.json");
    }

    public static String mesh(int look) {
        return BossLook.stem(look);
    }

    // 模型は「SCALE をかけた後に案の大きさ」で作ってある。描画の SCALE の上で戻す
    public static void undoAuthorScale(PoseStack pose, Entity e) {
        int look = look(e);
        if (look != 0) {
            float k = 1f / BossLook.authorScale(look);
            pose.scale(k, k, k);
        }
    }
}
