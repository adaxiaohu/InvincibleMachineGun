#version 150

uniform float U_GameTime;
uniform vec2 ScreenSize;
uniform sampler2D MainDepthSampler;
uniform sampler2D MainColorSampler;
uniform mat4 U_InverseProjectionMatrix;
uniform mat4 U_InverseViewMatrix;

uniform vec3 U_SkyTop;
uniform vec3 U_SkyBottom;
uniform vec3 U_FogColor;
uniform vec3 U_MoodColor;
uniform float U_MoodIntensity;
uniform float U_Wetness;

in vec2 texCoord;
out vec4 fragColor;

vec3 clipToView(vec2 uv, float depth) {
    vec4 clipPos = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = U_InverseProjectionMatrix * clipPos;
    return viewPos.xyz / viewPos.w;
}

void main() {
    float rawDepth = texture(MainDepthSampler, texCoord).r;
    vec4 sceneColor = texture(MainColorSampler, texCoord);
    
    float gray = dot(sceneColor.rgb, vec3(0.299, 0.587, 0.114));
    vec3 blueMoody = mix(sceneColor.rgb, vec3(gray), 0.5) * U_MoodColor;

    vec3 effect;
    if (rawDepth >= 1.0) {
        effect = mix(U_SkyBottom, U_SkyTop, texCoord.y);
    } else {
        vec3 viewPos = clipToView(texCoord, rawDepth);
        float dist = length(viewPos);
        
        vec2 off = 1.0 / ScreenSize;
        float dL = texture(MainDepthSampler, texCoord - vec2(off.x, 0.0)).r;
        float dR = texture(MainDepthSampler, texCoord + vec2(off.x, 0.0)).r;
        float flatness = 1.0 - smoothstep(0.0, 0.0001, abs(dL - dR));
        
        vec3 groundColor = mix(blueMoody, blueMoody * 1.3, flatness * U_Wetness);
        effect = mix(U_FogColor, groundColor, clamp(exp(-dist * 0.012), 0.0, 1.0));
    }

    float vignette = smoothstep(1.0, 0.3, length(texCoord - 0.5));
    fragColor = vec4(mix(sceneColor.rgb, effect * vignette, U_MoodIntensity), 1.0);
}