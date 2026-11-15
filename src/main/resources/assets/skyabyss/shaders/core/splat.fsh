#version 150

#moj_import <fog.glsl>

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;

out vec4 fragColor;

void main() {
    vec4 c = vertexColor * ColorModulator;
    c.rgb = mix(overlayColor.rgb, c.rgb, overlayColor.a);
    c *= lightMapColor;
    fragColor = linear_fog(vec4(c.rgb, 1.0), vertexDistance, FogStart, FogEnd, FogColor);
}
