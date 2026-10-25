#version 150

uniform float U_GameTime;
uniform vec2 ScreenSize;
uniform sampler2D MainDepthSampler;
uniform sampler2D MainColorSampler;
uniform mat4 U_InverseProjectionMatrix;
uniform mat4 U_InverseViewMatrix;
uniform vec3 U_CameraPosition;

uniform float U_SunSpeed;
uniform float U_ScanSpeed;
uniform float U_LoopEnabled;
uniform float U_ScanDuration;
uniform float U_SkyEnabled;
uniform float U_GroundEnabled;
uniform float U_RainSpeed;
uniform float U_RainDensity;
uniform float U_ColorGrade;
uniform float U_SurfaceDensity;
uniform float U_SurfaceFade;
uniform float U_SurfaceSpeed;
uniform float U_EntityBoost;

in vec2 texCoord;
out vec4 fragColor;

// ---- 黑客帝国调色板 ----
// 雨滴双色取自 LB 主题 backgrounds/background.frag:190
const vec3 M_HEAD   = vec3(0.67, 1.00, 0.82);     // 雨滴头部：亮青白
const vec3 M_BODY   = vec3(0.25, 0.80, 0.40);     // 雨滴主体：主题绿
const vec3 M_DEEP   = vec3(0.020, 0.075, 0.032);  // 深绿背景
const vec3 M_SHADOW = vec3(0.008, 0.026, 0.012);  // 暗部绿（不用纯黑）
const vec3 M_HIGH   = vec3(0.62, 1.00, 0.72);     // 高光青白绿

// ---- hash（Dave Hoskins 无三角函数版）----
float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec4 hash43(vec3 p) {
    vec4 p4 = fract(vec4(p.xyzx) * vec4(0.1031, 0.1030, 0.0973, 0.1099));
    p4 += dot(p4, p4.wzxy + 33.33);
    return fract((p4.xxyz + p4.yzzw) * p4.zywx);
}

// ---- 程序化符文（改编自 LB 主题的 "runes"，不依赖字体纹理）----
float runeLine(vec2 p, vec2 a, vec2 b) {
    p -= a;
    b -= a;
    float h = clamp(dot(p, b) / max(dot(b, b), 1e-5), 0.0, 1.0);
    return length(p - b * h);
}

float rune(vec2 U, vec2 seed, float w) {
    float d = 1e5;
    for (int i = 0; i < 4; i++) {
        vec4 pos = hash43(vec3(seed, float(i)));

        // 四条笔画分别贴住方框的一条边
        if (i == 0) pos.y = 0.0;
        if (i == 1) pos.x = 0.999;
        if (i == 2) pos.x = 0.0;
        if (i == 3) pos.y = 0.999;

        // 端点吸附到 2x3 网格，避免看起来像纯噪声
        vec4 snaps = vec4(2.0, 3.0, 2.0, 3.0);
        pos = (floor(pos * snaps) + 0.5) / snaps;

        // 起点终点重合会退化成点，丢掉
        if (pos.x != pos.z || pos.y != pos.w) {
            d = min(d, runeLine(U, pos.xy, pos.zw + 0.001));
        }
    }
    // 笔画宽度按屏幕像素解析计算，不用 fwidth：
    // digitalRain 只在 sky 分支里调用，属于非一致控制流，fwidth 在那里是未定义行为。
    // 注意 smoothstep 的 edge0 必须小于 edge1，否则行为未定义。
    return 1.0 - smoothstep(w * 0.35, w, d);
}

// ---- 世界空间数字雨（球投影 / 经纬网格）----
// 坐标来自「世界方向」而不是屏幕 uv，所以和太阳一样锁在天上：
// 转头时雨幕从身边滑过，而不是整片雨跟着屏幕平移。
//
// 列 = 世界方位角，行 = 仰角。
// 注：经纬网格的所有经线在天顶必然交汇，抬头会看到放射状，这是球投影的固有性质。
vec3 digitalRain(vec3 dir, float t) {
    // 列数决定横向密度；行数按仰角范围配比，让格子接近正方形
    float colCount = max(U_RainDensity, 32.0);
    float rowCount = colCount * 0.5;

    // ---- 列：同一世界方位永远是同一列 ----
    float az = atan(dir.z, dir.x);                     // [-PI, PI]
    float colF     = (az / 6.2831853 + 0.5) * colCount;
    float col      = floor(colF);
    float colLocal = fract(colF);

    // ---- 行：按仰角均匀排布 ----
    float el = asin(clamp(dir.y, -1.0, 1.0));          // [-PI/2, PI/2]
    float v        = (el / 3.14159265 + 0.5) * rowCount;
    float row      = floor(v);
    float rowLocal = fract(v);

    float r1 = hash11(col * 1.31 + 0.7);
    float r2 = hash11(col * 2.71 + 9.3);
    float r3 = hash11(col * 4.13 + 21.1);

    float speed = (0.55 + r1 * 1.65) * max(U_RainSpeed, 0.0);
    float phase = r2 * 53.0;

    // 头部位置：t 增大 → head 减小 → 雨滴向下落
    float head = fract(phase - t * speed * 0.22) * rowCount;

    float d     = mod(v - head, rowCount);
    float dCell = floor(d);

    // 拖尾按整列比例给，视觉长度与密度无关
    float trailFrac  = 0.10 + r3 * 0.22;
    float trailCells = max(trailFrac * rowCount, 1.0);

    float t01 = clamp(dCell / trailCells, 0.0, 1.0);
    float bright = (1.0 - t01);
    bright = bright * bright * bright;
    if (bright < 0.01) return vec3(0.0);

    // ---- 字符 ----
    float charRate = 1.5 + r1 * 4.0;
    float charSlot = mod(floor(t * charRate), 512.0);

    float present = step(0.06, hash21(vec2(col * 11.3 + 5.0, row * 7.9 + charSlot)));
    if (present < 0.5) return vec3(0.0);

    float seedA = hash21(vec2(col * 3.7 + 1.0, row * 5.1 + 2.0));
    vec2  seed  = vec2(seedA * 97.0, charSlot + col * 0.37);

    // 字符在格子里只占 86%，留出间隙
    vec2  g     = vec2(colLocal, rowLocal) * 0.86 + 0.07;
    float glyph = rune(g, seed, 0.09);

    float headness = 1.0 - smoothstep(0.0, trailCells * 0.30, dCell);
    vec3  rainCol  = mix(M_BODY, M_HEAD, headness);
    float leadGlow = 1.0 - smoothstep(0.0, 1.2, dCell);
    rainCol += M_HIGH * leadGlow * 0.9;

    return rainCol * glyph * bright;
}

// ---- 字符雨组成的太阳（数据核心）----
// 在一个圆盘内用更亮、更密的字符雨填充，边缘柔和衰减。
vec3 matrixSun(vec3 dir, vec3 sunDir, float t) {
    // 把方向投影到以太阳为中心的正交平面上
    vec3 sunRight = normalize(cross(sunDir, vec3(0.0, 1.0, 0.0)));
    vec3 sunUp    = cross(sunRight, sunDir);

    float facing = dot(dir, sunDir);
    if (facing <= 0.0) return vec3(0.0);

    // 投影到太阳平面，按 facing 归一化得到角度偏移
    vec2 sp = vec2(dot(dir, sunRight), dot(dir, sunUp)) / max(facing, 0.05);

    // 太阳半径用「角度」定：0.20 约等于 11.3 度半角，视觉上像太阳而不是铺满屏幕
    float radius = length(sp) / 0.20;
    if (radius > 1.6) return vec3(0.0);

    // 圆盘边缘柔和
    float disc = 1.0 - smoothstep(0.85, 1.35, radius);
    if (disc < 0.01) return vec3(0.0);

    // 圆盘内的字符雨：把 sp 归一化到 [-1,1]，再铺格子
    vec2 sn = sp / 0.20;

    float colCount = 44.0;
    float rowCount = 44.0;

    float colF = (sn.x * 0.5 + 0.5) * colCount;
    float col  = floor(colF);
    float colLocal = fract(colF);

    float v = (sn.y * 0.5 + 0.5) * rowCount;
    float row = floor(v);
    float rowLocal = fract(v);

    float r1 = hash11(col * 2.13 + 31.0);
    float r2 = hash11(col * 3.71 + 7.7);

    // 太阳上的字符流得慢一些，更有"核心"感
    float speed = (0.35 + r1 * 0.7) * max(U_SunSpeed, 0.0);
    float head = fract(r2 * 31.0 - t * speed * 0.30) * rowCount;

    float d = mod(v - head, rowCount);
    float dCell = floor(d);
    float trailCells = max((0.35 + r1 * 0.35) * rowCount, 1.0);

    float bright = 1.0 - clamp(dCell / trailCells, 0.0, 1.0);
    bright = bright * bright;
    if (bright < 0.01) return vec3(0.0);

    float charSlot = mod(floor(t * (2.0 + r1 * 3.0)), 512.0);
    float present = step(0.03, hash21(vec2(col * 7.3 + 2.0, row * 5.9 + charSlot)));
    if (present < 0.5) return vec3(0.0);

    float seedA = hash21(vec2(col * 3.1 + 4.0, row * 2.7 + 6.0));
    vec2  seed  = vec2(seedA * 83.0, charSlot + col * 0.29);

    vec2  g = vec2(colLocal, rowLocal) * 0.86 + 0.07;
    float glyph = rune(g, seed, 0.09);

    // 太阳整体比天空雨亮：偏青白，中心更亮
    float centerBoost = 1.0 - smoothstep(0.0, 1.1, radius);
    vec3  sunCol = mix(M_HEAD, M_HIGH, centerBoost * 0.7);
    float intensity = (1.6 + centerBoost * 1.2) * disc;

    return sunCol * glyph * bright * intensity;
}

// ---- 电影绿单色分级 ----
vec3 gradeMatrix(vec3 c) {
    float l = clamp(dot(c, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
    vec3 mid = M_BODY * 0.55;
    if (l < 0.5) return mix(M_SHADOW, mid, l * 2.0);
    return mix(mid, M_HIGH, (l - 0.5) * 2.0);
}

vec3 clipToView(vec2 uv, float depth) {
    vec4 clipPos = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = U_InverseProjectionMatrix * clipPos;
    return viewPos.xyz / viewPos.w;
}

bool isSkyDepth(float depth) {
    return depth >= 0.9999;
}

vec3 fetchViewPos(vec2 uv) {
    float rawDepth = texture(MainDepthSampler, uv).r;
    if (isSkyDepth(rawDepth)) return vec3(0.0, 0.0, -1000.0);
    return clipToView(uv, rawDepth);
}

vec3 reconstructNormal(vec3 centerPos, vec2 uv) {
    float EDGE_THRESHOLD = 0.003;
    vec2 offset = 1.0 / ScreenSize;
    vec3 posLeft  = fetchViewPos(uv + vec2(-offset.x, 0.0));
    vec3 posRight = fetchViewPos(uv + vec2( offset.x, 0.0));
    vec3 posUp    = fetchViewPos(uv + vec2(0.0,  offset.y));
    vec3 posDown  = fetchViewPos(uv + vec2(0.0, -offset.y));
    vec3 l = centerPos - posLeft;
    vec3 r = posRight - centerPos;
    vec3 d = centerPos - posDown;
    vec3 u = posUp - centerPos;
    float p = pow(max(abs(centerPos.z), 0.1), 2.0);
    if (abs(l.z / p) > EDGE_THRESHOLD || abs(r.z / p) > EDGE_THRESHOLD ||
        abs(d.z / p) > EDGE_THRESHOLD || abs(u.z / p) > EDGE_THRESHOLD) {
        return vec3(0.0, 1.0, 0.0);
    }
    vec3 hVec = (abs(l.z) < abs(r.z)) ? l : r;
    vec3 vVec = (abs(d.z) < abs(u.z)) ? d : u;
    return normalize(cross(hVec, vVec));
}

// ---- 表面字符雨（三平面投影）----
// 在一个投影平面上铺一列列下落的字符，返回该平面的亮度。
// uv.x = 横向坐标，uv.y = 竖向坐标（随时间变化 → 字符下落）
float surfaceRainPlane(vec2 uv, float t, float seed, float density) {
    // 格子：横向 density 列，纵向同密度（近似正方形）
    float colF = uv.x * density;
    float col  = floor(colF);
    float colLocal = fract(colF);

    // 列属性：同一列永远同一套参数，看起来才像"一条雨"
    float cSeed = col + seed * 71.0;
    float r1 = hash11(cSeed * 1.31 + 0.7);
    float r2 = hash11(cSeed * 2.71 + 9.3);
    float r3 = hash11(cSeed * 4.13 + 21.1);

    // 表面用独立的速度设置，与天空雨分开调
    float speed = (0.55 + r1 * 1.65) * max(U_SurfaceSpeed, 0.0);
    float phase = r2 * 53.0;
    // 拖尾占整列的比例：长拖尾，雨幕更满
    float trailFrac = 0.55 + r3 * 0.40;

    // 竖向格坐标：整列循环。t 增大 → 字符向下流
    // uv 是米，乘 density 换算成「格」
    float vF = (uv.y - t * speed * 0.22 + phase) * density;
    float row = floor(vF);
    float rowLocal = fract(vF);

    // 一整列的格数：周期短一些，雨幕更密
    float colSpan    = max(5.0 * density, 8.0);
    float headCell   = fract(phase - t * speed * 0.22) * colSpan;
    float dCells     = mod(vF - headCell, colSpan);   // 0 = 头部，向上递增
    float trailCells = max(trailFrac * colSpan, 1.0);

    // 二次衰减：尾部更饱满
    float bright = 1.0 - clamp(dCells / trailCells, 0.0, 1.0);
    bright = bright * bright;
    if (bright < 0.01) return 0.0;

    // ---- 字符 ----
    float charRate = 1.5 + r1 * 4.0;
    float charSlot = mod(floor(t * charRate), 512.0);

    // 留空比例：少量留空，雨幕连续
    float present = step(0.02, hash21(vec2(cSeed * 11.3 + 5.0, row * 7.9 + charSlot)));
    if (present < 0.5) return 0.0;

    float seedA = hash21(vec2(cSeed * 3.7 + 1.0, row * 5.1 + 2.0));
    vec2  glyphSeed = vec2(seedA * 97.0, charSlot + col * 0.37);

    // 笔画宽度：偏粗，字符更醒目。
    // 密度 8 以上时 0.55/density 已经小于下限，所以实际粗细由下限决定。
    float strokeW = clamp(0.55 / density, 0.11, 0.16);
    vec2  g = vec2(colLocal, rowLocal) * 0.86 + 0.07;
    float glyph = rune(g, glyphSeed, strokeW);

    return glyph * bright;
}

// 三平面投影：按法线权重混合三个轴上的投影
// 明暗对比：每列/每格有独立亮度系数，头部更亮
vec3 surfaceRain(vec3 worldPos, vec3 worldNormal, float t, float seed) {
    float density = max(U_SurfaceDensity, 0.5);

    // 三个投影平面的坐标
    vec2 uvX = worldPos.zy;   // 朝 ±X 的墙：横向 Z，竖向 Y
    vec2 uvY = worldPos.xz;   // 地面/天花板：横向 X，竖向 Z
    vec2 uvZ = worldPos.xy;   // 朝 ±Z 的墙：横向 X，竖向 Y

    // 权重：轴对齐面只有一项接近 1
    vec3 w = abs(worldNormal);
    w = w / max(w.x + w.y + w.z, 1e-4);

    // 明暗对比：每个平面上的每列有独立亮度
    float bX = surfaceRainPlane(uvX, t, seed + 0.0, density);
    float bY = surfaceRainPlane(uvY, t, seed + 11.0, density);
    float bZ = surfaceRainPlane(uvZ, t, seed + 23.0, density);

    // 按权重混合，曲面自然过渡
    float lum = bX * w.x + bY * w.y + bZ * w.z;

    // 明暗层次：同一列不同格子亮度不同（用世界坐标 hash 出暗部）
    float shade = 0.35 + 0.65 * hash21(floor(worldPos.xz * density * 0.5));
    lum *= shade;

    // 头部偏青白，拖尾偏主题绿；整体提亮让字符在暗部也能看清
    vec3 col = mix(M_BODY, M_HEAD, clamp(lum, 0.0, 1.0));
    return col * lum * 1.5;
}

void main() {
    float rawDepth = texture(MainDepthSampler, texCoord).r;
    vec4 sceneColor = texture(MainColorSampler, texCoord);
    vec3 matrixColor = vec3(0.0);

    // 绿调是全局后处理，独立于天空/地面开关，所以先取出来
    float grade = clamp(U_ColorGrade, 0.0, 1.0);

    bool sky = isSkyDepth(rawDepth);
    bool areaEnabled = sky ? (U_SkyEnabled >= 0.5) : (U_GroundEnabled >= 0.5);
    if (!areaEnabled) {
        // 该区域不套矩阵风格，但绿调仍然作用于原始画面
        vec3 passthrough = mix(sceneColor.rgb, gradeMatrix(sceneColor.rgb), grade);
        fragColor = vec4(passthrough, 1.0);
        return;
    }
    if (sky) {
        // 天空：黑暗代码空间 + 下降字符 + 数据核心
        vec3 viewPos = clipToView(texCoord, 1.0);
        vec3 worldViewDir = normalize(mat3(U_InverseViewMatrix) * normalize(viewPos));

        vec3 sunDir = normalize(vec3(-1.0, 0.45, 0.0));
        // 太阳本身就是一团更亮的字符雨
        vec3 sunCol = matrixSun(worldViewDir, sunDir, U_GameTime);

        // 地平线(uv.y=0)更亮，天顶更暗
        vec3 skyBase = mix(M_SHADOW, M_DEEP * 0.9, 1.0 - texCoord.y);
        // 传入世界方向而不是屏幕 uv：雨锁在天上，不随视角平移
        vec3 rainCol = digitalRain(worldViewDir, U_GameTime);

        // 光核在雨后面，雨滴叠在它之上
        matrixColor = skyBase + sunCol + rainCol;
    } else {
        // 地面：数字网格
        vec3 viewPos = clipToView(texCoord, rawDepth);
        vec3 normal = reconstructNormal(viewPos, texCoord);
        float NdotV = dot(normal, normalize(-viewPos));

        vec3 worldPos = (U_InverseViewMatrix * vec4(viewPos, 1.0)).xyz + U_CameraPosition;
        vec3 worldNormal = normalize(mat3(U_InverseViewMatrix) * normal);

        // 轴对齐面（方块）判定：三个轴里最大分量接近 1
        float axisness = max(max(abs(worldNormal.x), abs(worldNormal.y)), abs(worldNormal.z));
        // 法线越不轴对齐 = 越可能是实体（曲面上法线连续变化）
        float isBlock = smoothstep(0.85, 0.97, axisness);

        // 距离淡出：远处字符小到看不清，按距离衰减避免糊成噪点
        float dist = length(viewPos);
        float fade = exp(-dist * max(U_SurfaceFade, 0.0));

        // 表面字符雨：方块和实体统一处理（三平面投影天然区分朝向）
        vec3 surf = surfaceRain(worldPos, worldNormal, U_GameTime, 0.0);

        // 实体更亮：方块保持原亮度，实体按设置提亮
        float boost = mix(max(U_EntityBoost, 1.0), 1.0, isBlock);
        surf *= fade * boost;

        // 方块面额外加一点底光，实体保持纯字符
        float blockBase = isBlock * 0.5;

        float rim = pow(1.0 - max(NdotV, 0.0), 3.0);

        vec3 base = mix(M_SHADOW, M_DEEP, 0.5) * (1.0 + blockBase);
        matrixColor = base + M_BODY * rim * 0.4 + surf;
    }

    // 雷达扫描：改色
    float timeCycle = U_GameTime * U_ScanSpeed;
    if (U_LoopEnabled > 0.5) timeCycle = mod(timeCycle, U_ScanDuration);
    float mainRadius = pow(max(timeCycle, 0.0), 5.0);

    float scanMetric = sky ? normalize(clipToView(texCoord, 1.0)).y : length(clipToView(texCoord, rawDepth));
    float scanThreshold = sky ? mix(-0.8, 1.1, smoothstep(0.0, 1500.0, mainRadius)) : mainRadius;
    float scanSoftness = sky ? 0.1 : 8.0;

    float alphaMask = 1.0 - smoothstep(scanThreshold, scanThreshold + scanSoftness, scanMetric);
    float scanLine = smoothstep(scanThreshold, scanThreshold + scanSoftness, scanMetric)
        * (1.0 - smoothstep(scanThreshold, scanThreshold + (sky ? 0.1 : 10.0), scanMetric));

    // 最终输出混合
    vec3 finalOutput = mix(sceneColor.rgb, matrixColor, alphaMask) + M_HIGH * scanLine * 1.4;

    // 整体黑客帝国绿调色
    finalOutput = mix(finalOutput, gradeMatrix(finalOutput), grade);

    fragColor = vec4(finalOutput, 1.0);
}
