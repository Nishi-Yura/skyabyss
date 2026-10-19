#version 150

// 上から落ちる影を、画面の画素ごとに「その画素の世界の位置」で掛ける。
// 深度から世界の位置を戻し、影の濃さの地図 (Field。R = 濃さ、G = 影を落とす物の高さ) を引いて、色を乗算で暗くする。
// 屋根の下・木の下・屋内・人や動物も、影の輪郭の中ならまとめて暗くなる (地面に貼る板ではないので、屋根の縁で継ぎ目が出ない)。

uniform sampler2D Depth;
uniform sampler2D Field;

uniform mat4 InvViewProj;
// xyz = カメラの世界の位置、w = 高さの符号化の基準 (G = 0 の高さ)
uniform vec4 CamPos;
// xy = 地図の左上の世界の x, z、z = 1 / 地図の一辺 (ブロック)
uniform vec4 FieldRect;
// rgb = 影の色 (濃さ 1 のとき画面の色に掛ける)
uniform vec4 Tint;

in vec2 uv;

out vec4 fragColor;

void main() {
    float depth = texture(Depth, uv).r;
    if (depth >= 0.99999) {
        discard;
    }
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 rel = InvViewProj * clip;
    vec3 world = rel.xyz / rel.w + CamPos.xyz;
    vec2 f = (world.xz - FieldRect.xy) * FieldRect.z;
    if (f.x <= 0.0 || f.y <= 0.0 || f.x >= 1.0 || f.y >= 1.0) {
        discard;
    }
    vec4 s = texture(Field, f);
    float edge = min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y));
    float casterY = CamPos.w + s.g * 512.0;
    float below = 1.0 - smoothstep(casterY - 4.0, casterY, world.y);
    float d = s.r * below * smoothstep(0.0, 0.06, edge);
    if (d <= 0.002) {
        discard;
    }
    fragColor = vec4(mix(vec3(1.0), Tint.rgb, d), 1.0);
}
