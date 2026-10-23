#version 150

// 信号故障 / RGB 色散后处理（用于 TargetHud 的面板与透视标记的入场、出场动画）
//
// 采样已渲染好的场景拷贝，在圆角矩形范围内叠加：
//   * 水平 RGB 通道分离（色散）
//   * 按行随机水平撕裂（block tear）
//   * 扫描线
// 强度为 0 时完全透明，不影响画面。

uniform sampler2D InputSampler;
uniform vec2 InputResolution;
uniform vec2 uLocation;
uniform vec2 uSize;
uniform float radius;

uniform float uTime;
uniform float uGlitch;   // 0..1 总强度
uniform float uRgbSplit; // 色散像素
uniform float uScanline; // 扫描线强度
uniform float uTear;     // 撕裂强度

out vec4 fragColor;

float roundedBoxSDF(vec2 center, vec2 size, float r) {
    return length(max(abs(center) - size + r, 0.0)) - r;
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    vec2 frag = gl_FragCoord.xy;

    // ── 圆角矩形遮罩（★ 必须最先算）──
    //
    // ⚠️ 性能关键：这个着色器是在【整屏四边形】上跑的（见 Shader2DUtil.drawFullscreen），
    //   片元着色器会对整屏每个像素执行。若把遮罩计算放在后面，
    //   那么矩形外的片元（占 99%+）也会先付出「3 次纹理采样 + 撕裂/扫描线计算」的成本。
    //
    //   所以照同目录 blur_rect.fsh:58 的写法：先算 mask、先 discard，
    //   只有真正落在矩形内的片元才继续付出采样成本。
    //   discard 不改变结果 —— 矩形外的 alpha 本来就是 0。
    vec2 halfSize = uSize * 0.5;
    float d = roundedBoxSDF(frag - uLocation - halfSize, halfSize, radius);
    float mask = 1.0 - smoothstep(0.0, 1.0, d);

    float outAlpha = mask * uGlitch;
    if (outAlpha <= 0.001) discard;

    vec2 uv = frag / InputResolution;

    // ── 块状撕裂：按 4px 行高随机决定该行是否水平位移 ──
    float row = floor(frag.y / 4.0);
    float n = hash(vec2(row, floor(uTime * 18.0)));
    float doTear = step(1.0 - uTear * uGlitch * 0.55, n);
    uv.x += doTear * (n - 0.5) * 36.0 * uGlitch / InputResolution.x;

    // ── RGB 色散：R 右移、B 左移 ──
    float split = uRgbSplit * uGlitch / InputResolution.x;
    float rr = texture(InputSampler, uv + vec2(split, 0.0)).r;
    float gg = texture(InputSampler, uv).g;
    float bb = texture(InputSampler, uv - vec2(split, 0.0)).b;
    vec3 col = vec3(rr, gg, bb);

    // ── 扫描线 ──
    col *= 1.0 - uScanline * uGlitch * (0.45 + 0.45 * sin(frag.y * 3.14159));

    fragColor = vec4(col, outAlpha);
}
