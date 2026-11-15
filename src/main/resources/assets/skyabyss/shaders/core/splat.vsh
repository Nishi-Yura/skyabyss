#version 150

#moj_import <fog.glsl>

// 点の模型 (client/splat/SplatGpu)。点一つが一つのインスタンスで、四角の角は gl_VertexID (三角形の帯の 0..3) から作る。
// 点ごとの中身 (NEW_ENTITY の枠の名前を借りる): Position = 骨の空間の点 (ブロック)、Color = 色、UV0.x = 四角の半分の大きさ、
// UV1 = (骨 A, 骨 B)、UV2 = (骨 A の重み 0..255, 印)、Normal = 骨の空間の法線 (0 = 向きなし)。
// 四角はカメラ (ModelViewMat の行) を向く。世界・GUI・一人称のどれでも同じ式でよい
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform vec2 LightUV;
uniform vec2 OverlayUV;
uniform vec4 Tint;
uniform float CullCos;
// 遠くで間引いたぶん点を大きくする倍率
uniform float Grow;
// 光る点の明るさ (1 = いつも最大、0 = 世界の明るさのまま。眠る巨神の光が消えていく)
uniform float GlowFade;
// 0 以上: 全部の点をこの骨 (Bone0) だけで動かす (骨一本ずつ描く呼び出し用)
uniform int ForceBone;
uniform mat4 Bone0;
uniform mat4 Bone1;
uniform mat4 Bone2;
uniform mat4 Bone3;
uniform mat4 Bone4;
uniform mat4 Bone5;
uniform mat4 Bone6;
uniform mat4 Bone7;
uniform mat4 Bone8;
uniform mat4 Bone9;
uniform mat4 Bone10;
uniform mat4 Bone11;
uniform mat4 Bone12;
uniform mat4 Bone13;
uniform mat4 Bone14;
uniform mat4 Bone15;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;

const vec2 CORNERS[4] = vec2[4](vec2(-1.0, -1.0), vec2(1.0, -1.0), vec2(-1.0, 1.0), vec2(1.0, 1.0));

void main() {
    mat4 bones[16] = mat4[16](Bone0, Bone1, Bone2, Bone3, Bone4, Bone5, Bone6, Bone7, Bone8, Bone9, Bone10, Bone11, Bone12, Bone13, Bone14, Bone15);
    bool force = ForceBone >= 0;
    mat4 ma = force ? Bone0 : bones[clamp(UV1.x, 0, 15)];
    mat4 mb = bones[clamp(UV1.y, 0, 15)];
    float sa = length(ma[0].xyz);
    if (sa < 1e-6) {
        gl_Position = vec4(0.0, 0.0, -2.0, 1.0);
        return;
    }
    vec3 p = (ma * vec4(Position, 1.0)).xyz;
    float wa = float(UV2.x) / 255.0;
    if (!force && wa < 0.999 && length(mb[0].xyz) > 1e-6) {
        p = mix(p, (mb * vec4(Position, 1.0)).xyz, 1.0 - wa);
    }
    vec4 vp = ModelViewMat * vec4(p, 1.0);
    bool ortho = ProjMat[3][3] > 0.5;
    // 裏を向いた点は捨てる (透視のときだけ。GUI の正射影は持ち物の画面の人のように奥行きを反転して描くことがあるので捨てない)
    if (!ortho && dot(Normal, Normal) > 0.01) {
        vec3 n = normalize(mat3(ModelViewMat) * (mat3(ma) * Normal));
        if (dot(n, normalize(vp.xyz)) > CullCos) {
            gl_Position = vec4(0.0, 0.0, -2.0, 1.0);
            return;
        }
    }
    // 四角はカメラの空間で広げる (大きさは骨の行列の倍率ぶん)
    vec2 c = CORNERS[gl_VertexID & 3] * UV0.x * Grow * sa * length(ModelViewMat[0].xyz);
    vp.xy += c;
    gl_Position = ProjMat * vp;
    vertexDistance = fog_distance(p, FogShape);
    vertexColor = Color * Tint;
    vec2 l = (UV2.y & 1) != 0 ? mix(LightUV, vec2(240.0), GlowFade) : LightUV;
    lightMapColor = texture(Sampler2, clamp(l / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
    overlayColor = texelFetch(Sampler1, ivec2(OverlayUV), 0);
}
