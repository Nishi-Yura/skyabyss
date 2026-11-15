#version 150

#moj_import <fog.glsl>

// 点の模型 (client/splat/SplatGpu)。点ごとの中身は最初に一度だけ VBO に入れ、ここで骨のポーズ・カメラ向きの四角・裏向きの間引きをする。
// 入れ方 (NEW_ENTITY の枠を借りる): Position = 骨の空間の点 (ブロック)、Color = 色、UV0 = 四角の角の向き x 半分の大きさ、
// UV1 = (骨 A, 骨 B)、UV2 = (骨 A の重み 0..255, 印)、Normal = 骨の空間の法線 (0 = 向きなし)
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
uniform vec3 CamLeft;
uniform vec3 CamUp;
uniform vec2 LightUV;
uniform vec2 OverlayUV;
uniform vec4 Tint;
uniform float CullCos;
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

void main() {
    mat4 bones[16] = mat4[16](Bone0, Bone1, Bone2, Bone3, Bone4, Bone5, Bone6, Bone7, Bone8, Bone9, Bone10, Bone11, Bone12, Bone13, Bone14, Bone15);
    mat4 ma = bones[clamp(UV1.x, 0, 15)];
    mat4 mb = bones[clamp(UV1.y, 0, 15)];
    float sa = length(ma[0].xyz);
    if (sa < 1e-6) {
        // 隠れた骨 (行列 0) の点は描かない
        gl_Position = vec4(0.0, 0.0, -2.0, 1.0);
        return;
    }
    vec3 p = (ma * vec4(Position, 1.0)).xyz;
    float wa = float(UV2.x) / 255.0;
    if (wa < 0.999 && length(mb[0].xyz) > 1e-6) {
        p = mix(p, (mb * vec4(Position, 1.0)).xyz, 1.0 - wa);
    }
    // 裏を向いた点は捨てる (カメラは原点)
    if (dot(Normal, Normal) > 0.01) {
        vec3 n = normalize(mat3(ma) * Normal);
        if (dot(n, normalize(p)) > CullCos) {
            gl_Position = vec4(0.0, 0.0, -2.0, 1.0);
            return;
        }
    }
    vec3 world = p + (CamLeft * UV0.x + CamUp * UV0.y) * sa;
    gl_Position = ProjMat * ModelViewMat * vec4(world, 1.0);
    vertexDistance = fog_distance(world, FogShape);
    vertexColor = Color * Tint;
    vec2 l = (UV2.y & 1) != 0 ? vec2(240.0) : LightUV;
    lightMapColor = texture(Sampler2, clamp(l / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
    overlayColor = texelFetch(Sampler1, ivec2(OverlayUV), 0);
}
