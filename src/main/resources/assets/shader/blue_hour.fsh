#version 150

uniform float U_GameTime;
uniform vec2 ScreenSize;
uniform sampler2D MainDepthSampler;
uniform sampler2D MainColorSampler;
uniform mat4 U_ProjectionMatrix;
uniform mat4 U_InverseProjectionMatrix;
uniform mat4 U_InverseViewMatrix;
uniform vec3 U_CameraPosition;

uniform float U_MoodIntensity;
uniform float U_CustomBlueTone;
uniform float U_BlueTone;
uniform float U_Contrast;
uniform float U_Wetness;
uniform float U_IntroSpeed;
uniform float U_LoopIntro;
uniform float U_IntroDuration;

uniform float U_CloudStyle;
uniform float U_CloudSteps;
uniform float U_CloudCoverage;
uniform float U_CloudThickness;
uniform float U_CloudSpeed;
uniform float U_PolygonStrength;

uniform float U_WorldRain;
uniform float U_ScreenRain;
uniform float U_RainDensity;
uniform float U_RainSpeed;
uniform float U_RainOpacity;
uniform float U_DropletRefraction;
uniform float U_GlassFogEnabled;
uniform float U_GlassFogStrength;
uniform float U_GlassFogBlur;
uniform float U_WiperEnabled;
uniform float U_WiperInterval;
uniform float U_WiperDuration;
uniform float U_WiperWidth;
uniform float U_WiperRecovery;

uniform float U_DispersionEnabled;
uniform float U_DispersionStrength;
uniform float U_DispersionRadius;
uniform float U_DispersionEdgeThreshold;
uniform float U_HighlightThreshold;
uniform float U_EdgeBias;
uniform float U_BloomEnabled;
uniform float U_BloomStrength;
uniform float U_BloomRadius;
uniform float U_IndependentLightColorEnabled;
uniform float U_LightColorIsolationStrength;
uniform float U_CustomLightTemperature;
uniform float U_LightTemperature;
uniform float U_TyndallEnabled;
uniform float U_TyndallStrength;
uniform float U_WorldDayTime;
uniform float U_AOEnabled;
uniform float U_AOStrength;
uniform float U_LowPolyLighting;
uniform float U_SkyEnabled;
uniform float U_GroundEnabled;

in vec2 texCoord;
out vec4 fragColor;

const vec3 SKY_TOP = vec3(0.025, 0.075, 0.16);
const vec3 SKY_HORIZON = vec3(0.25, 0.46, 0.68);
const vec3 CLOUD_DARK = vec3(0.055, 0.11, 0.22);
const vec3 CLOUD_LIGHT = vec3(0.32, 0.49, 0.66);
const vec3 FOG_BLUE = vec3(0.105, 0.20, 0.32);

vec3 clipToView(vec2 uv, float depth) {
    vec4 clipPos = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = U_InverseProjectionMatrix * clipPos;
    return viewPos.xyz / viewPos.w;
}

bool isSkyDepth(float depth) {
    return depth >= 0.9999;
}

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

// Optional blue-hour hue control. The transform is intentionally limited to
// atmospheric/cool grading components instead of the final framebuffer so
// warm local lights keep their own physical color temperature. Luminance is
// normalized after the chroma shift to avoid turning the hue slider into a
// hidden exposure control.
vec3 applyBlueHourTone(vec3 color) {
    if (U_CustomBlueTone < 0.5) return color;

    float tone = clamp(U_BlueTone, 0.0, 1.0);
    float signedBias = (tone - 0.5) * 2.0;
    vec3 tint = vec3(1.0);
    if (signedBias < 0.0) {
        // Cyan/green-biased blue hour: more green-cyan, slightly less red and
        // deep blue. Kept restrained so foliage does not become neon green.
        tint = mix(vec3(1.0), vec3(0.87, 1.085, 0.955), -signedBias);
    } else {
        // Blue-biased blue hour: cleaner cobalt/deep-blue response without a
        // strong magenta cast.
        tint = mix(vec3(1.0), vec3(0.925, 0.965, 1.105), signedBias);
    }

    vec3 shifted = max(color * tint, vec3(0.0));
    float beforeY = max(luminance(color), 0.0005);
    float afterY = max(luminance(shifted), 0.0005);
    return shifted * (beforeY / afterY);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    float n = hash12(p);
    return vec2(n, hash12(p + n + 19.19));
}

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float valueNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float n000 = hash31(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash31(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash31(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash31(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash31(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash31(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash31(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash31(i + vec3(1.0, 1.0, 1.0));

    float x00 = mix(n000, n100, f.x);
    float x10 = mix(n010, n110, f.x);
    float x01 = mix(n001, n101, f.x);
    float x11 = mix(n011, n111, f.x);
    return mix(mix(x00, x10, f.y), mix(x01, x11, f.y), f.z);
}

float fbm(vec3 p) {
    float sum = 0.0;
    float amplitude = 0.55;
    for (int i = 0; i < 4; i++) {
        sum += valueNoise(p) * amplitude;
        p = p * 2.03 + vec3(7.1, 13.7, 3.4);
        amplitude *= 0.48;
    }
    return sum;
}

float fbm3(vec3 p) {
    float sum = 0.0;
    float amplitude = 0.58;
    for (int i = 0; i < 3; i++) {
        sum += valueNoise(p) * amplitude;
        p = p * 2.01 + vec3(5.7, 11.9, 2.8);
        amplitude *= 0.46;
    }
    return sum;
}

float cloudDensity(vec3 worldPos, float bottom, float top) {
    vec3 drift = vec3(U_GameTime * U_CloudSpeed, 0.0, U_GameTime * U_CloudSpeed * 0.42);
    vec3 samplePos = worldPos * vec3(0.010, 0.028, 0.010) + drift;
    float shape = fbm(samplePos);
    float detail = valueNoise(samplePos * 3.7 + 4.2) * 0.16;
    float threshold = mix(0.74, 0.39, U_CloudCoverage);
    float density = smoothstep(threshold, threshold + 0.19, shape + detail);
    float height01 = clamp((worldPos.y - bottom) / max(top - bottom, 1.0), 0.0, 1.0);
    float vertical = smoothstep(0.0, 0.16, height01) * (1.0 - smoothstep(0.72, 1.0, height01));
    return density * vertical;
}

vec4 renderClouds(vec3 rayDirection) {
    // The cloud slab remains at the original low altitude. We only fix the
    // intersection so it can be seen from both below and above (e.g. Y=319).
    if (abs(rayDirection.y) <= 0.008) return vec4(0.0);

    float bottom = 118.0;
    float top = bottom + mix(34.0, 92.0, U_CloudThickness);
    float tBottom = (bottom - U_CameraPosition.y) / rayDirection.y;
    float tTop = (top - U_CameraPosition.y) / rayDirection.y;
    float slabNear = min(tBottom, tTop);
    float slabFar = max(tBottom, tTop);
    if (slabFar <= 0.0) return vec4(0.0);

    float startDistance = max(slabNear, 0.0);
    float endDistance = min(slabFar, startDistance + 1100.0);
    if (endDistance <= startDistance || startDistance > 1450.0) return vec4(0.0);

    float stepLength = (endDistance - startDistance) / max(U_CloudSteps, 1.0);
    float jitter = hash12(gl_FragCoord.xy) * stepLength;
    vec3 accumulated = vec3(0.0);
    float alpha = 0.0;

    for (int i = 0; i < 12; i++) {
        if (float(i) >= U_CloudSteps || alpha > 0.96) break;
        float distanceAlongRay = startDistance + (float(i) + 0.35) * stepLength + jitter * 0.28;
        vec3 worldPos = U_CameraPosition + rayDirection * distanceAlongRay;
        float density = cloudDensity(worldPos, bottom, top);

        float cellShade = hash31(floor(worldPos * vec3(0.035, 0.055, 0.035)));
        float facetedShade = floor((cellShade * 0.55 + density * 0.75) * 4.0) / 4.0;
        float softShade = clamp((worldPos.y - bottom) / max(top - bottom, 1.0) * 0.55 + density * 0.35, 0.0, 1.0);
        float polygonMix = U_CloudStyle < 0.5 ? 0.0 : (U_CloudStyle < 1.5 ? 1.0 : 0.55);
        polygonMix *= U_PolygonStrength;
        float shade = mix(softShade, facetedShade, polygonMix);
        vec3 cloudColor = applyBlueHourTone(mix(CLOUD_DARK, CLOUD_LIGHT, shade));

        float sampleAlpha = density * clamp(stepLength * 0.021, 0.04, 0.42);
        accumulated += (1.0 - alpha) * cloudColor * sampleAlpha;
        alpha += (1.0 - alpha) * sampleAlpha;
    }

    return vec4(accumulated, alpha);
}

vec3 reconstructNormal(vec3 centerPos, vec2 uv, float centerDepth, out float confidence) {
    if (isSkyDepth(centerDepth)) {
        confidence = 0.0;
        return normalize(-centerPos);
    }

    // Use quad derivatives instead of four extra depth fetches + four inverse
    // projection transforms. Discontinuities are rejected with a confidence
    // term so silhouettes do not create bright/black normal artifacts.
    vec3 dx = dFdx(centerPos);
    vec3 dy = dFdy(centerPos);
    vec3 normal = cross(dx, dy);
    float normalLength = length(normal);
    if (normalLength <= 0.000001) {
        confidence = 0.0;
        return normalize(-centerPos);
    }

    normal /= normalLength;
    if (dot(normal, -centerPos) < 0.0) normal = -normal;

    float relativeEdge = max(length(dx), length(dy)) / max(abs(centerPos.z), 1.0);
    confidence = 1.0 - smoothstep(0.055, 0.22, relativeEdge);
    return normal;
}

float rainPresence(float randomValue) {
    // Density remains a probability rather than an opacity multiplier, but the
    // lens needs enough active sites to feel like it is continuously being hit
    // by rain. Heavy rain therefore raises occupancy without filling every cell.
    float probability = mix(0.11, 0.70, clamp(U_RainDensity, 0.0, 1.0));
    return 1.0 - smoothstep(probability, min(probability + 0.075, 0.985), randomValue);
}

float noise1D(float x, float seed) {
    float i = floor(x);
    float f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(vec2(i, seed));
    float b = hash12(vec2(i + 1.0, seed));
    return mix(a, b, f) * 2.0 - 1.0;
}

vec2 rotate2D(vec2 p, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

float irregularDropletMask(vec2 p, vec2 radii, float seed, float gravityBias) {
    // Small drops are close to spherical caps; larger drops become asymmetric
    // because gravity pulls the leading edge down while the rear contact line
    // stays pinned. Multiple low-amplitude harmonics break the CG-perfect circle.
    radii = max(radii, vec2(0.0001));
    float angle = atan(p.y, p.x);
    float contour = 1.0
        + sin(angle * 3.0 + seed * 1.37) * 0.060
        + sin(angle * 5.0 - seed * 0.91) * 0.033
        + sin(angle * 7.0 + seed * 0.43) * 0.017;

    float vertical = clamp(-p.y / radii.y, -1.0, 1.0);
    float lowerBulb = smoothstep(-0.65, 0.72, vertical);
    float widthBias = mix(1.0, mix(0.82, 1.17, lowerBulb), clamp(gravityBias, 0.0, 1.0));

    vec2 q = vec2(p.x / (radii.x * contour * widthBias), p.y / radii.y);
    float d = length(q);

    // Keep water optically crisp. The previous 0.80 -> 1.10 transition made
    // every bead look defocused before the glass-fog pass even ran. fwidth
    // gives a roughly one-pixel antialiased boundary at any resolution.
    float aa = clamp(fwidth(d) * 0.85, 0.010, 0.085);
    return 1.0 - smoothstep(0.965 - aa, 1.015 + aa, d);
}

float rivuletPathX(float absoluteY, float baseX, vec2 random, float seed) {
    // Glass roughness/contact-line pinning produces a slowly meandering path,
    // not a clean sine-wave track. Two interpolated noise bands keep the bend
    // continuous while avoiding obvious periodicity.
    float columnSeed = seed * 11.7 + random.x * 37.1;
    float broad = noise1D(absoluteY * 0.78 + random.y * 7.0, columnSeed);
    float detail = noise1D(absoluteY * 2.65 - random.x * 5.0, columnSeed + 19.4);
    return baseX + broad * 0.060 + detail * 0.020;
}

float rivuletDrop(vec2 gridUv, vec2 cell, float seed, float columns, float verticalCells) {
    vec2 local = gridUv - cell - 0.5;
    vec2 random = hash22(cell + vec2(seed, seed * 1.73));
    float sizeRandom = hash12(cell + vec2(seed * 2.31, 8.17));
    float lifeRandom = hash12(cell + vec2(seed * 4.73, 23.11));

    // Contact-angle hysteresis: most droplets remain pinned. Only the largest
    // fraction occasionally overcomes the retention force and slides.
    float cycleSpeed = mix(0.026, 0.064, random.x) * U_RainSpeed;
    float phase = fract(random.y + U_GameTime * cycleSpeed);
    float holdEnd = mix(0.64, 0.83, lifeRandom);
    float slideDuration = mix(0.075, 0.140, sizeRandom);
    float slideT = clamp((phase - holdEnd) / max(slideDuration, 0.001), 0.0, 1.0);
    float canSlide = step(mix(0.89, 0.70, U_RainDensity), sizeRandom);

    // Release is rapid, then decelerates as the drop loses mass to its wet trail.
    float release = 1.0 - pow(1.0 - slideT, 2.35);
    release *= 1.0 - 0.07 * sin(release * 3.14159265);
    float progress = release * canSlide;

    float fadeAfter = 1.0 - smoothstep(
        min(holdEnd + slideDuration, 0.94),
        min(holdEnd + slideDuration + 0.12, 0.995),
        phase);
    float staticFade = 1.0 - smoothstep(0.91, 0.998, phase);
    float fadeIn = smoothstep(0.0, 0.070, phase);
    float visibility = fadeIn * mix(staticFade, fadeAfter, canSlide);

    float parkedY = mix(-0.28, 0.30, hash12(cell + seed * 9.1));
    float slideDistance = mix(0.30, 0.78, sizeRandom);
    float headY = parkedY - progress * slideDistance;
    float baseX = (random.x - 0.5) * 0.62;

    float absoluteHeadY = cell.y + 0.5 + headY;
    float headX = rivuletPathX(absoluteHeadY, baseX, random, seed + cell.x * 3.7);
    vec2 headDelta = vec2(local.x - headX, local.y - headY);

    // Cap large droplets aggressively. The old layer produced oversized round
    // blobs; here even a critical sliding drop remains a modest pear/ellipse.
    float radiusX = mix(0.044, 0.094, pow(sizeRandom, 1.38));
    float movingStretch = smoothstep(0.04, 0.42, slideT) * (1.0 - smoothstep(0.72, 1.0, slideT));
    float radiusY = radiusX * mix(0.90, 1.34, sizeRandom) * (1.0 + movingStretch * 0.24);
    float head = irregularDropletMask(
        headDelta,
        vec2(radiusX, radiusY),
        seed + random.y * 17.0,
        mix(0.16, 0.82, sizeRandom));

    float trailAge = smoothstep(holdEnd, holdEnd + 0.020, phase)
        * (1.0 - smoothstep(min(holdEnd + slideDuration + 0.08, 0.94), 0.998, phase))
        * canSlide;
    float trailLength = max(progress * slideDistance, 0.055);
    float axial = local.y - headY; // positive = wet track behind a downward-moving drop
    float clampedAxial = clamp(axial, 0.0, trailLength);
    float absoluteTrailY = absoluteHeadY + clampedAxial;
    float trailCenterX = rivuletPathX(absoluteTrailY, baseX, random, seed + cell.x * 3.7);

    float along01 = clampedAxial / max(trailLength, 0.001);
    float widthNoise = 0.74 + 0.26 * noise1D(
        absoluteTrailY * 5.8 + random.x * 9.0,
        seed + cell.x * 13.1 + 27.0);
    float trailWidth = radiusX * mix(0.34, 0.15, along01) * widthNoise;

    // Rounded end caps eliminate the old rectangular streak/drop artifact.
    float endDistance = max(max(-axial, axial - trailLength), 0.0);
    float curvedDistance = length(vec2(local.x - trailCenterX, endDistance));
    float stream = 1.0 - smoothstep(trailWidth * 0.78, trailWidth * 1.45, curvedDistance);
    float wetVariation = mix(0.58, 1.0, 0.5 + 0.5 * noise1D(
        absoluteTrailY * 3.35 - random.y * 4.0,
        seed + cell.x * 5.9 + 61.0));
    float trail = stream * wetVariation * trailAge;

    // Detached micro-beads are discrete irregular droplets, not sinusoidal bars.
    float beadSpacing = 0.115;
    float beadIndex = floor(clampedAxial / beadSpacing + 0.35);
    float beadRandom = hash12(vec2(beadIndex + cell.y * 9.7, seed + cell.x * 21.3));
    float beadAxial = (beadIndex + mix(0.22, 0.78, beadRandom)) * beadSpacing;
    float beadGate = step(0.0, axial) * step(axial, trailLength)
        * step(beadAxial, trailLength) * step(0.67, beadRandom);
    float beadAbsY = absoluteHeadY + beadAxial;
    float beadPathX = rivuletPathX(beadAbsY, baseX, random, seed + cell.x * 3.7)
        + (hash12(vec2(beadIndex, seed + 101.0)) - 0.5) * radiusX * 0.70;
    float beadRadius = radiusX * mix(0.17, 0.31, beadRandom);
    float wakeBead = irregularDropletMask(
        vec2(local.x - beadPathX, axial - beadAxial),
        vec2(beadRadius, beadRadius * mix(0.78, 1.20, beadRandom)),
        seed + beadIndex * 2.7,
        0.20) * beadGate * trailAge;

    float rivuletProbability = mix(0.17, 0.39, U_RainDensity);
    float rareRivulet = 1.0 - smoothstep(
        rivuletProbability,
        min(rivuletProbability + 0.075, 0.95),
        hash12(cell + vec2(seed * 7.1, 47.3)));

    return max(head * visibility, max(trail * 0.63, wakeBead * 0.48))
        * rainPresence(hash12(cell + vec2(seed * 1.3, 3.1)))
        * rareRivulet;
}

float rivuletLayer(vec2 uv, float columns, float verticalCells, float seed) {
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    vec2 centered = vec2((uv.x - 0.5) * aspect, uv.y - 0.5);
    vec2 gridUv = centered * vec2(columns, verticalCells);
    vec2 baseCell = floor(gridUv);
    float field = 0.0;
    for (int offsetY = -1; offsetY <= 1; offsetY++) {
        field = max(field, rivuletDrop(
            gridUv, baseCell + vec2(0.0, float(offsetY)), seed, columns, verticalCells));
    }
    return field;
}

float glassBeadLayer(vec2 uv, float frequency, float sizeScale, float seed, float occupancyScale) {
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    vec2 beadUv = vec2((uv.x - 0.5) * aspect, uv.y - 0.5) * frequency;
    vec2 cell = floor(beadUv);
    vec2 random = hash22(cell + seed);
    vec2 local = fract(beadUv) - 0.5 - (random - 0.5) * 0.58;

    float shapeRandom = hash12(cell + vec2(seed * 2.7, 12.4));
    float angle = (hash12(cell + vec2(4.1, seed * 8.3)) - 0.5) * 0.52;
    local = rotate2D(local, angle);

    // V4: common beads are intentionally larger than V3. At 1080p the main
    // population is now typically several pixels across instead of 1-2 pixels.
    float radius = mix(0.050, 0.118, pow(shapeRandom, 1.58)) * sizeScale;
    vec2 radii = vec2(
        radius * mix(0.78, 1.08, random.x),
        radius * mix(0.84, 1.30, random.y));

    // Pinned beads remain for a long time. They are the accumulated wet layer,
    // while freshImpactLayer below supplies the continuous incoming rain.
    float phase = fract(random.y + U_GameTime * mix(0.005, 0.012, random.x));
    float life = smoothstep(0.0, 0.055, phase) * (1.0 - smoothstep(0.91, 0.998, phase));
    float bead = irregularDropletMask(local, radii, seed + shapeRandom * 29.0, shapeRandom * 0.42);

    float occupancy = rainPresence(hash12(cell + vec2(seed * 5.1, 31.7))) * occupancyScale;
    return bead * occupancy * life;
}

float freshImpactLayer(vec2 uv, float frequency, float seed, float occupancyScale) {
    // Incoming drops continuously strike the lens. Each cell owns a staggered
    // cycle; generation is part of the hash, so a replacement drop appears at a
    // different position/shape instead of respawning at the same coordinates.
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    vec2 impactUv = vec2((uv.x - 0.5) * aspect, uv.y - 0.5) * frequency;
    vec2 cell = floor(impactUv);

    float baseRandom = hash12(cell + vec2(seed * 3.7, seed * 0.41));
    float cycleDuration = mix(5.0, 12.0, baseRandom) / max(U_RainSpeed, 0.15);
    float clock = U_GameTime / cycleDuration + baseRandom * 7.0;
    float generation = floor(clock);
    float phase = fract(clock);

    vec2 generationSeed = cell + vec2(generation * 0.73 + seed, generation * 1.31 - seed);
    vec2 random = hash22(generationSeed);
    float shapeRandom = hash12(generationSeed + vec2(17.4, 63.8));

    vec2 local = fract(impactUv) - 0.5 - (random - 0.5) * 0.62;
    float angle = (hash12(generationSeed + vec2(5.9, 27.1)) - 0.5) * 0.60;
    local = rotate2D(local, angle);

    // A fresh impact appears quickly, spreads slightly under surface tension,
    // then remains pinned before slowly losing visibility or joining a stream.
    float grow = mix(0.72, 1.0, smoothstep(0.0, 0.085, phase));
    float radius = mix(0.060, 0.138, pow(shapeRandom, 1.45)) * grow;
    vec2 radii = vec2(
        radius * mix(0.78, 1.08, random.x),
        radius * mix(0.86, 1.27, random.y));

    float birth = smoothstep(0.0, 0.028, phase);
    float decayStart = mix(0.68, 0.88, random.x);
    float life = birth * (1.0 - smoothstep(decayStart, 0.995, phase));
    float occupancy = rainPresence(hash12(generationSeed + vec2(91.2, 14.7))) * occupancyScale;

    return irregularDropletMask(local, radii, seed + generation * 2.11, shapeRandom * 0.48)
        * occupancy * life;
}

float glassBeads(vec2 uv) {
    // The lens now has three simultaneous populations: accumulated pinned beads,
    // newly arriving medium beads, and sparse larger impacts. This keeps motion
    // continuous without turning every droplet into a downward-moving stream.
    float tiny = glassBeadLayer(uv, 36.0, 0.92, 41.7, 0.82);
    float small = glassBeadLayer(uv + vec2(0.003, -0.002), 24.0, 0.96, 73.2, 0.70);
    float medium = glassBeadLayer(uv + vec2(-0.006, 0.004), 15.0, 0.90, 119.4, 0.30);
    float impacts = freshImpactLayer(uv + vec2(0.011, -0.008), 20.0, 151.3, 0.58);
    float largeImpacts = freshImpactLayer(uv + vec2(-0.019, 0.015), 12.0, 207.9, 0.16);
    return max(max(tiny, small), max(max(medium, impacts), largeImpacts));
}

float glassFlowField(vec2 uv) {
    // Rivulets are deliberately rare. Most visible water is pinned condensation,
    // matching real glass instead of filling the frame with parallel streams.
    float broadStreams = rivuletLayer(uv, 7.0, 2.85, 3.7) * 0.88;
    float fineStreams = rivuletLayer(uv + vec2(0.041, -0.013), 11.0, 4.35, 17.2) * 0.60;
    float beads = glassBeads(uv);
    float merged = max(beads, max(broadStreams, fineStreams));
    merged += min(broadStreams, fineStreams) * 0.08;
    // Preserve a firm optical surface instead of washing the mask over a very
    // wide range. This gives refraction/rim lighting a defined droplet boundary.
    return smoothstep(0.035, 0.58, clamp(merged, 0.0, 1.10));
}

float glassFogField(vec2 uv) {
    if (U_GlassFogEnabled < 0.5 || U_GlassFogStrength <= 0.0) return 0.0;
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    vec2 p = vec2((uv.x - 0.5) * aspect, uv.y - 0.5);
    float slowTime = U_GameTime * 0.010;
    float broad = fbm3(vec3(p * 2.45 + vec2(slowTime * 0.15, -slowTime * 0.08), 8.3));
    float fine = valueNoise(vec3(p * 6.2 - vec2(slowTime * 0.09, slowTime * 0.04), 19.4));
    float condensation = smoothstep(0.25, 0.75, broad * 0.80 + fine * 0.26);
    float edgeHumidity = smoothstep(0.22, 0.82, length(p * vec2(0.82, 1.0)));
    return clamp((condensation * 0.80 + edgeHumidity * 0.20) * U_GlassFogStrength, 0.0, 1.0);
}

float wiperClearMask(vec2 uv, out float bladeMask) {
    bladeMask = 0.0;
    if (U_WiperEnabled < 0.5) return 0.0;

    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    vec2 pivot = vec2(0.0, -0.10);
    vec2 p = vec2((uv.x - 0.5) * aspect, uv.y) - pivot;
    float radius = length(p);
    float angle = atan(p.x, p.y);
    float minimumAngle = -1.12;
    float maximumAngle = 1.12;
    float angle01 = clamp((angle - minimumAngle) / (maximumAngle - minimumAngle), 0.0, 1.0);
    float arcMask = smoothstep(0.12, 0.18, radius)
        * (1.0 - smoothstep(1.00, 1.08, radius))
        * step(minimumAngle, angle) * step(angle, maximumAngle);

    float duration = max(U_WiperDuration, 0.05);
    float interval = max(U_WiperInterval, duration + 0.15);
    float cycleTime = mod(U_GameTime, interval);
    float sweep = smoothstep(0.0, 1.0, clamp(cycleTime / duration, 0.0, 1.0));
    float bladeAngle = mix(minimumAngle, maximumAngle, sweep);
    float bladeDistance = abs(sin(angle - bladeAngle)) * radius;
    if (cycleTime <= duration) {
        bladeMask = (1.0 - smoothstep(U_WiperWidth * 0.45, U_WiperWidth * 1.35, bladeDistance)) * arcMask;
    }

    float passedAt = angle01 * duration;
    float age = cycleTime - passedAt;
    if (age < 0.0) age += interval;
    float recentlyCleared = 1.0 - smoothstep(0.0, max(U_WiperRecovery, 0.05), age);
    return recentlyCleared * arcMask;
}

float screenDroplets(vec2 uv, out vec2 refractionOffset) {
    float field = glassFlowField(uv);

    // Fragment derivatives give the water-surface gradient without recomputing
    // the entire procedural field twice more.
    vec2 gradient = vec2(dFdx(field), dFdy(field));
    refractionOffset = gradient * 1.40 + vec2(0.0, -field * 0.008);
    return field;
}

vec3 worldRainFallDirection() {
    // Gravity dominates. Wind only adds a small, slowly varying horizontal drift.
    float gust = sin(U_GameTime * 0.19) * 0.018 + sin(U_GameTime * 0.071 + 1.7) * 0.012;
    return normalize(vec3(0.075 + gust, -1.0, -0.035 + gust * 0.35));
}

float worldRainSegmentLayer(
    vec3 rayDirection,
    float sceneDistance,
    float bandCenter,
    float bandHalfWidth,
    float cellSize,
    float segmentSpacing,
    float seed,
    float layerWeight)
{
    float bandNear = max(2.25, bandCenter - bandHalfWidth);
    float bandFar = min(sceneDistance, bandCenter + bandHalfWidth);
    if (bandFar <= bandNear) return 0.0;

    vec3 fallDirection = worldRainFallDirection();
    vec3 basisA = cross(fallDirection, vec3(0.0, 0.0, 1.0));
    if (length(basisA) < 0.08) basisA = cross(fallDirection, vec3(1.0, 0.0, 0.0));
    basisA = normalize(basisA);
    vec3 basisB = normalize(cross(fallDirection, basisA));

    // Select one persistent world-space rain filament near this ray band. The
    // transverse coordinates live in a plane perpendicular to gravity/wind, so
    // camera rotation cannot turn the rain field into screen-space rings.
    float sampleT = clamp(bandCenter, bandNear, bandFar);
    vec3 sampleWorld = U_CameraPosition + rayDirection * sampleT;
    vec2 transverse = vec2(dot(sampleWorld, basisA), dot(sampleWorld, basisB));
    vec2 cell = floor(transverse / cellSize);
    vec2 random = hash22(cell + vec2(seed * 11.3, seed * 23.7));

    // Keep the filament well inside its transverse cell so neighboring pixels
    // do not clip its sides when they select the adjacent procedural cell.
    vec2 filamentUv = vec2(
        mix(0.18, 0.82, random.x),
        mix(0.18, 0.82, random.y));
    vec2 filamentTransverse = (cell + filamentUv) * cellSize;

    float travel = U_GameTime * U_RainSpeed * 18.0;
    float sampleAlong = dot(sampleWorld, fallDirection);
    float alongCell = floor((sampleAlong - travel) / segmentSpacing);
    float alongRandom = hash12(vec2(
        alongCell + seed * 17.9,
        cell.x * 0.73 + cell.y * 1.37 + seed));
    float segmentCenterAlong = (alongCell + mix(0.22, 0.78, alongRandom)) * segmentSpacing + travel;

    vec3 segmentCenter = basisA * filamentTransverse.x
        + basisB * filamentTransverse.y
        + fallDirection * segmentCenterAlong;

    float lengthRandom = hash12(cell + vec2(alongCell * 0.61 + seed, 67.4));
    float halfLength = mix(0.48, 1.28, lengthRandom) * mix(0.90, 1.12, clamp(U_RainSpeed / 2.0, 0.0, 1.0));

    // Closest points between the camera ray and the finite 3D rain segment.
    // This is the key V3 change: the rain is actual world-space line geometry
    // evaluated analytically, not samples on concentric view-distance shells.
    vec3 fromSegment = U_CameraPosition - segmentCenter;
    float b = dot(rayDirection, fallDirection);
    float d = dot(rayDirection, fromSegment);
    float e = dot(fallDirection, fromSegment);
    float denominator = 1.0 - b * b;

    float rayT;
    if (denominator > 0.0015) {
        rayT = (b * e - d) / denominator;
    } else {
        // Looking almost parallel to the rain: projected streaks collapse toward
        // a vanishing point, so use the segment-center depth as the stable seed.
        rayT = dot(segmentCenter - U_CameraPosition, rayDirection);
    }
    rayT = clamp(rayT, bandNear, bandFar);

    float segmentT = clamp(
        dot(U_CameraPosition + rayDirection * rayT - segmentCenter, fallDirection),
        -halfLength,
        halfLength);
    vec3 closestSegment = segmentCenter + fallDirection * segmentT;

    // One refinement after clamping gives a stable ray/segment capsule distance.
    rayT = clamp(dot(closestSegment - U_CameraPosition, rayDirection), bandNear, bandFar);
    segmentT = clamp(
        dot(U_CameraPosition + rayDirection * rayT - segmentCenter, fallDirection),
        -halfLength,
        halfLength);
    closestSegment = segmentCenter + fallDirection * segmentT;
    vec3 closestRay = U_CameraPosition + rayDirection * rayT;
    float distanceToStreak = length(closestRay - closestSegment);

    // Approximate one screen pixel in world units at this distance. This keeps
    // distant rain anti-aliased instead of flickering or disappearing entirely.
    float projectionY = max(abs(U_ProjectionMatrix[1][1]), 0.25);
    float pixelWorld = max(0.006, rayT * 2.0 / (max(ScreenSize.y, 1.0) * projectionY));
    float physicalRadius = mix(0.010, 0.017, lengthRandom);
    float visibleRadius = max(physicalRadius, pixelWorld * 0.62);
    float edgeSoftness = max(pixelWorld * 1.25, 0.009);
    float streak = 1.0 - smoothstep(visibleRadius, visibleRadius + edgeSoftness, distanceToStreak);

    float cloudTop = 118.0 + mix(34.0, 92.0, U_CloudThickness);
    float belowCloud = 1.0 - smoothstep(cloudTop - 4.0, cloudTop + 8.0, closestSegment.y);
    if (belowCloud <= 0.001) return 0.0;

    float occupancyRandom = hash12(vec2(
        cell.x * 5.3 + alongCell * 0.41 + seed,
        cell.y * 9.7 - alongCell * 0.67 + 31.0));
    float occupancy = rainPresence(occupancyRandom);
    float intensity = mix(0.48, 1.0, hash12(cell + vec2(alongCell * 1.7, seed * 8.1)));

    // Broad gusts modulate density, not streak direction. This avoids wavy or
    // magnetic-looking motion while keeping rainfall from feeling mechanical.
    float gustBand = 0.72 + 0.28 * noise1D(
        dot(closestSegment.xz, vec2(0.013, 0.009)) + U_GameTime * 0.17,
        seed + 44.0);
    gustBand = smoothstep(0.16, 0.98, gustBand);

    float distanceFade = mix(1.0, 0.72, clamp(rayT / 118.0, 0.0, 1.0));
    return streak * occupancy * intensity * gustBand * belowCloud * distanceFade * layerWeight;
}

float worldRain(vec2 uv, vec3 rayDirection, float maximumDistance) {
    if (maximumDistance <= 3.0) return 0.0;

    // Non-overlapping-ish distance bands provide density without raymarch shell
    // aliasing. Every visible streak itself is a finite line segment in world
    // space and therefore obeys perspective automatically at any camera pitch.
    float rain = 0.0;
    float nearLayer = worldRainSegmentLayer(rayDirection, maximumDistance, 8.0, 5.0, 0.82, 4.8, 2.1, 0.96);
    float midLayer = worldRainSegmentLayer(rayDirection, maximumDistance, 22.0, 10.0, 1.05, 5.6, 7.4, 0.80);
    float farLayer = worldRainSegmentLayer(rayDirection, maximumDistance, 48.0, 18.0, 1.36, 6.4, 13.8, 0.58);
    float hazeLayer = worldRainSegmentLayer(rayDirection, maximumDistance, 88.0, 26.0, 1.78, 7.2, 29.2, 0.36);

    rain += nearLayer;
    rain += (1.0 - rain) * midLayer;
    rain += (1.0 - rain) * farLayer;
    rain += (1.0 - rain) * hazeLayer;
    return clamp(rain, 0.0, 1.0);
}


vec3 extractHighlight(vec2 uv) {
    vec3 sampleColor = texture(MainColorSampler, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
    float highlight = smoothstep(U_HighlightThreshold, U_HighlightThreshold + 0.24, luminance(sampleColor));
    return sampleColor * highlight;
}

vec3 renderBloom(vec2 uv) {
    // Five bilinear taps instead of nine. The wider cross keeps the soft halo
    // but cuts texture reads almost in half.
    vec2 pixel = U_BloomRadius / ScreenSize;
    vec3 bloom = extractHighlight(uv) * 0.28;
    bloom += extractHighlight(uv + vec2( pixel.x, 0.0)) * 0.18;
    bloom += extractHighlight(uv + vec2(-pixel.x, 0.0)) * 0.18;
    bloom += extractHighlight(uv + vec2(0.0,  pixel.y)) * 0.18;
    bloom += extractHighlight(uv + vec2(0.0, -pixel.y)) * 0.18;
    return bloom;
}

vec3 lightTemperatureColor(float kelvin) {
    float warmPhase = smoothstep(1800.0, 6500.0, kelvin);
    vec3 warm = vec3(1.0, 0.29, 0.075);
    vec3 neutral = vec3(1.0, 0.955, 0.90);
    vec3 color = mix(warm, neutral, warmPhase);
    float coolPhase = smoothstep(6500.0, 10000.0, kelvin);
    return mix(color, vec3(0.66, 0.80, 1.0), coolPhase);
}

vec3 normalizedLightTemperature(float kelvin) {
    vec3 temperature = lightTemperatureColor(kelvin);
    return temperature / max(luminance(temperature), 0.001);
}

vec3 applyIndependentLightTemperature(vec3 sourceColor) {
    if (U_CustomLightTemperature < 0.5) return sourceColor;

    // Temperature is applied as a white-balance multiplier rather than
    // replacing the surface with a flat orange/blue color. This preserves
    // grass, stone, wood, etc. while changing only the apparent illuminant.
    vec3 temperature = normalizedLightTemperature(U_LightTemperature);
    vec3 whiteBalance = mix(vec3(1.0), temperature, 0.72);
    vec3 tinted = max(sourceColor * whiteBalance, vec3(0.0));

    // Color temperature is not an exposure control.
    float sourceY = max(luminance(sourceColor), 0.0005);
    float tintedY = max(luminance(tinted), 0.0005);
    return tinted * (sourceY / tintedY);
}

vec3 independentLightChromaticity(vec3 gradedColor, vec3 nativeColor) {
    // "Independent light color" must not become an exposure/original-scene
    // restore switch. Transfer only chromaticity / white balance and preserve
    // the luminance already established by the blue-hour grade.
    vec3 candidate = applyIndependentLightTemperature(nativeColor);
    float gradedY = max(luminance(gradedColor), 0.0005);
    float candidateY = max(luminance(candidate), 0.0005);
    return max(candidate * (gradedY / candidateY), vec3(0.0));
}

vec3 gradeEnvironmentHighlight(vec3 sourceColor) {
    // Bloom/highlights that are not associated with a real nearby block light
    // should remain part of the blue-hour environment grade.
    float gray = luminance(sourceColor);
    vec3 cooled = mix(sourceColor, vec3(gray), 0.26) * vec3(0.72, 0.88, 1.11);
    cooled = applyBlueHourTone(cooled);
    return mix(sourceColor, cooled, U_MoodIntensity);
}

// Local native-light separation --------------------------------------------------
float daylightCompetition();

// V10 never classifies a pixel as "artificial light" just because it is warm,
// green, bright, or saturated. Those absolute tests were the reason V9 could
// influence an entire biome/terrain tint. Instead we estimate a same-surface
// neighborhood reference and only recover the *local illumination residual* --
// the part that rises above that surrounding reference.

float sceneLumaAt(vec2 uv) {
    return luminance(texture(MainColorSampler, clamp(uv, vec2(0.001), vec2(0.999))).rgb);
}

float sameSurfaceWeight(float centerDepth, float sampleDepth) {
    bool centerSky = isSkyDepth(centerDepth);
    bool sampleSky = isSkyDepth(sampleDepth);
    if (centerSky != sampleSky) return 0.0;
    if (centerSky) return 1.0;

    // Relative depth tolerance is much more stable than an absolute depth
    // cutoff over Minecraft's perspective depth buffer.
    float perspectiveScale = max(1.0 - centerDepth, 0.00018);
    float relativeDelta = abs(sampleDepth - centerDepth) / perspectiveScale;
    return 1.0 - smoothstep(0.018, 0.16, relativeDelta);
}

vec3 localSurfaceReference(vec2 uv, float centerDepth, vec3 centerColor, out float confidence) {
    // A fairly broad radius measures the unlit/local-average surface rather
    // than individual texture texels. Depth-aware weights stop silhouettes
    // from borrowing color from the object behind them. Kept explicitly
    // unrolled for conservative GLSL 1.50 driver compatibility.
    vec2 r = vec2(22.0) / ScreenSize;
    vec3 sumColor = vec3(0.0);
    float sumWeight = 0.0;

    vec2 suv = clamp(uv + vec2(r.x, 0.0), vec2(0.001), vec2(0.999));
    float sd = texture(MainDepthSampler, suv).r;
    float w = sameSurfaceWeight(centerDepth, sd);
    sumColor += texture(MainColorSampler, suv).rgb * w;
    sumWeight += w;

    suv = clamp(uv - vec2(r.x, 0.0), vec2(0.001), vec2(0.999));
    sd = texture(MainDepthSampler, suv).r;
    w = sameSurfaceWeight(centerDepth, sd);
    sumColor += texture(MainColorSampler, suv).rgb * w;
    sumWeight += w;

    suv = clamp(uv + vec2(0.0, r.y), vec2(0.001), vec2(0.999));
    sd = texture(MainDepthSampler, suv).r;
    w = sameSurfaceWeight(centerDepth, sd);
    sumColor += texture(MainColorSampler, suv).rgb * w;
    sumWeight += w;

    suv = clamp(uv - vec2(0.0, r.y), vec2(0.001), vec2(0.999));
    sd = texture(MainDepthSampler, suv).r;
    w = sameSurfaceWeight(centerDepth, sd);
    sumColor += texture(MainColorSampler, suv).rgb * w;
    sumWeight += w;

    confidence = smoothstep(0.70, 2.5, sumWeight);
    if (sumWeight < 0.20) return centerColor;
    return sumColor / sumWeight;
}

float globalNativeBlockLight(vec2 uv, float rawDepth, vec3 sourceScene) {
    if (U_IndependentLightColorEnabled < 0.5 || isSkyDepth(rawDepth)) return 0.0;

    float referenceConfidence = 0.0;
    vec3 reference = localSurfaceReference(uv, rawDepth, sourceScene, referenceConfidence);
    float sceneY = luminance(sourceScene);
    float referenceY = luminance(reference);

    // Real local illumination raises a surface relative to its own nearby
    // baseline. A globally warm/green/bright world has near-zero relative lift
    // and therefore cannot be globally exempted from the blue-hour grade.
    float relativeLift = max(sceneY - referenceY, 0.0) / max(referenceY, 0.075);
    float lumaEvidence = smoothstep(0.055, 0.44, relativeLift);

    // Artificial light often also changes chromaticity. Compare normalized
    // chroma to the *local surface reference*, never to an absolute warm axis.
    // Requiring some luminance lift prevents naturally colored blocks from
    // being mistaken for lights simply because their albedo is red/yellow.
    vec3 sourceChrom = sourceScene / max(sceneY, 0.055);
    vec3 referenceChrom = reference / max(referenceY, 0.055);
    float chromaDelta = length(sourceChrom - referenceChrom);
    float chromaEvidence = smoothstep(0.10, 0.52, chromaDelta)
        * smoothstep(0.018, 0.20, relativeLift);

    // Luminous block faces themselves are allowed a stronger response, but
    // only when they are also a local peak. Flat snow/sky/daylit stone no
    // longer satisfy this condition across large areas.
    float peak = max(max(sourceScene.r, sourceScene.g), sourceScene.b);
    float emitterCore = smoothstep(0.72, 1.08, peak)
        * smoothstep(0.075, 0.34, relativeLift);

    // Strong texture/geometric edges can create false local peaks when one tap
    // lands on a different texel/material. Reduce confidence there instead of
    // turning the entire neighboring block into a protected color patch.
    float colorEdge = max(
        length(dFdx(sourceScene)),
        length(dFdy(sourceScene))
    );
    float edgeConfidence = 1.0 - smoothstep(0.10, 0.42, colorEdge);

    float localEvidence = max(lumaEvidence, chromaEvidence * 0.78);
    localEvidence *= mix(0.42, 1.0, edgeConfidence);
    localEvidence *= mix(0.55, 1.0, referenceConfidence);

    // Daylight/skylight competes with block light. We suppress recovered
    // *surface* illumination strongly in daylight while preserving only very
    // obvious luminous cores. This keeps the blue-hour environment coherent.
    // World time alone is not enough: a cave/interior can be dark at noon.
    // Only let daylight compete strongly when the local reference surface is
    // itself bright enough to plausibly receive skylight/daylight.
    float daylight = daylightCompetition() * smoothstep(0.10, 0.46, referenceY);
    float surfaceCompetition = mix(1.0, 0.085, daylight);
    float coreCompetition = mix(1.0, 0.38, daylight);

    float surfaceMask = localEvidence * surfaceCompetition;
    float coreMask = emitterCore * coreCompetition;
    float mask = 1.0 - (1.0 - clamp(surfaceMask, 0.0, 1.0))
                     * (1.0 - clamp(coreMask, 0.0, 1.0));
    return clamp(mask * U_LightColorIsolationStrength, 0.0, 1.0);
}

vec3 sunDirectionWorld() {
    float angle = U_WorldDayTime * 6.28318530718;
    return normalize(vec3(cos(angle) * 0.18, sin(angle), -cos(angle)));
}

float daylightCompetition() {
    float sunHeight = max(sunDirectionWorld().y, 0.0);
    return smoothstep(0.055, 0.62, sunHeight);
}

vec3 renderTyndall(vec2 uv, vec3 worldRay) {
    if (U_TyndallEnabled < 0.5 || U_TyndallStrength <= 0.0) return vec3(0.0);

    vec3 sunDir = sunDirectionWorld();
    if (sunDir.y <= 0.025) return vec3(0.0);

    vec3 sunView = transpose(mat3(U_InverseViewMatrix)) * sunDir;
    vec4 sunClip = U_ProjectionMatrix * vec4(sunView * 800.0, 1.0);
    if (sunClip.w <= 0.001) return vec3(0.0);

    vec2 sunUv = sunClip.xy / sunClip.w * 0.5 + 0.5;
    if (any(lessThan(sunUv, vec2(-0.55))) || any(greaterThan(sunUv, vec2(1.55)))) return vec3(0.0);

    vec2 delta = (sunUv - uv) / 6.0;
    vec2 sampleUv = uv;
    float scatter = 0.0;
    float weight = 1.0;
    float weightSum = 0.0;

    for (int i = 0; i < 6; i++) {
        sampleUv = clamp(sampleUv + delta, vec2(0.001), vec2(0.999));
        float depth = texture(MainDepthSampler, sampleUv).r;
        float openSky = smoothstep(0.995, 0.99995, depth);
        scatter += openSky * weight;
        weightSum += weight;
        weight *= 0.84;
    }
    scatter /= max(weightSum, 0.001);

    float radial = 1.0 - smoothstep(0.08, 1.18, length(uv - sunUv));
    float forward = pow(max(dot(worldRay, sunDir), 0.0), 0.42);
    float moisture = mix(0.58, 1.0, U_RainDensity);
    float horizon = 1.0 - smoothstep(0.02, 0.72, sunDir.y);
    vec3 shaftColor = applyBlueHourTone(mix(vec3(0.42, 0.55, 0.67), vec3(0.31, 0.49, 0.70), 0.62 + 0.28 * horizon));

    return shaftColor * scatter * radial * (0.24 + 0.76 * forward)
        * moisture * U_TyndallStrength * 0.62;
}

float dispersionEdge(vec2 uv, float rawDepth, out vec2 edgeDirection) {
    vec2 px = 1.0 / ScreenSize;
    vec2 sx = vec2(px.x, 0.0);
    vec2 sy = vec2(0.0, px.y);

    vec3 cL = texture(MainColorSampler, clamp(uv - sx, vec2(0.001), vec2(0.999))).rgb;
    vec3 cR = texture(MainColorSampler, clamp(uv + sx, vec2(0.001), vec2(0.999))).rgb;
    vec3 cD = texture(MainColorSampler, clamp(uv - sy, vec2(0.001), vec2(0.999))).rgb;
    vec3 cU = texture(MainColorSampler, clamp(uv + sy, vec2(0.001), vec2(0.999))).rgb;

    // Color/albedo edges catch boundaries between adjacent blocks even when
    // they are coplanar and therefore have almost identical depth.
    float lumaDx = luminance(cR) - luminance(cL);
    float lumaDy = luminance(cU) - luminance(cD);
    float rgbDx = length(cR - cL);
    float rgbDy = length(cU - cD);
    float colorMetric = max(length(vec2(lumaDx, lumaDy)) * 1.35, max(rgbDx, rgbDy) * 0.58);

    // Depth discontinuities catch silhouettes and true 3-D block/entity edges.
    float dL = texture(MainDepthSampler, clamp(uv - sx, vec2(0.001), vec2(0.999))).r;
    float dR = texture(MainDepthSampler, clamp(uv + sx, vec2(0.001), vec2(0.999))).r;
    float dD = texture(MainDepthSampler, clamp(uv - sy, vec2(0.001), vec2(0.999))).r;
    float dU = texture(MainDepthSampler, clamp(uv + sy, vec2(0.001), vec2(0.999))).r;
    vec2 depthGradient = vec2(dR - dL, dU - dD);
    float perspectiveScale = max(1.0 - rawDepth, 0.00018);
    float relativeDepthEdge = length(depthGradient) / perspectiveScale;
    float depthMetric = smoothstep(0.008, 0.12, relativeDepthEdge);

    vec2 colorGradient = vec2(lumaDx, lumaDy);
    vec2 combinedGradient = colorGradient * 1.7 + depthGradient / perspectiveScale * 0.22;
    if (dot(combinedGradient, combinedGradient) > 0.000001) {
        edgeDirection = normalize(combinedGradient);
    } else {
        vec2 radial = uv - 0.5;
        edgeDirection = length(radial) > 0.001 ? normalize(radial) : vec2(1.0, 0.0);
    }

    // U_DispersionEdgeThreshold is a sensitivity threshold, not a brightness
    // threshold. Every material can trigger it when a real edge is present.
    float colorEdge = smoothstep(U_DispersionEdgeThreshold * 0.55,
                                 U_DispersionEdgeThreshold * 1.75,
                                 colorMetric);
    return max(colorEdge, depthMetric);
}

vec3 applyLightEffects(vec3 color, vec2 uv, float rawDepth, float dropletMask, float nativeLightInfluence) {
    vec3 rawBloom = renderBloom(uv);
    vec3 environmentBloom = gradeEnvironmentHighlight(rawBloom);
    vec3 independentBloom = applyIndependentLightTemperature(rawBloom);

    // Bloom can still preserve a local artificial-light tint, but V10's
    // nativeLightInfluence is local-residual based and cannot tint the world.
    float lightBloomMask = U_IndependentLightColorEnabled > 0.5
        ? clamp(nativeLightInfluence * 1.18, 0.0, 1.0)
        : 0.0;
    vec3 bloom = mix(environmentBloom, independentBloom, lightBloomMask);
    if (U_LowPolyLighting > 0.5) {
        float bloomLuma = luminance(bloom);
        float quantized = floor(bloomLuma * 7.0 + 0.5) / 7.0;
        bloom *= quantized / max(bloomLuma, 0.0001);
    }

    if (U_BloomEnabled > 0.5) color += bloom * U_BloomStrength;
    if (U_DispersionEnabled < 0.5 || U_DispersionStrength <= 0.0) return color;

    // V10 RGB dispersion is an edge effect, not a highlight effect. It works
    // on dark blocks, unlit entities, silhouettes and ordinary material edges.
    vec2 direction = vec2(1.0, 0.0);
    float edgeMask = dispersionEdge(uv, rawDepth, direction);
    if (edgeMask <= 0.001) return color;

    vec2 fromCenter = uv - 0.5;
    float screenEdge = clamp(length(fromCenter) * 2.0, 0.0, 1.0);
    float radius = U_DispersionRadius * (1.0 + screenEdge * 1.8 * U_EdgeBias);
    radius *= 1.0 + dropletMask * U_DropletRefraction * 0.55;
    vec2 offset = direction * radius / ScreenSize;

    vec2 uvPos = clamp(uv + offset, vec2(0.001), vec2(0.999));
    vec2 uvNeg = clamp(uv - offset, vec2(0.001), vec2(0.999));
    vec3 rawCenter = texture(MainColorSampler, uv).rgb;
    vec3 positive = texture(MainColorSampler, uvPos).rgb;
    vec3 negative = texture(MainColorSampler, uvNeg).rgb;

    // Add only channel displacement relative to the center sample. This keeps
    // the underlying blue-hour grade intact instead of copying ungraded raw
    // RGB back over the scene.
    vec3 channelDelta = vec3(
        positive.r - rawCenter.r,
        0.0,
        negative.b - rawCenter.b
    );
    float strength = U_DispersionStrength * edgeMask * (0.82 + dropletMask * 0.18);
    color += channelDelta * strength * 1.35;
    return color;
}

float aoTap(vec3 centerPos, vec3 normal, vec2 uv, vec2 pixelOffset, float maximumDistance) {
    vec2 sampleUv = clamp(uv + pixelOffset / ScreenSize, vec2(0.0), vec2(1.0));
    float sampleDepth = texture(MainDepthSampler, sampleUv).r;
    if (isSkyDepth(sampleDepth)) return 0.0;
    vec3 samplePos = clipToView(sampleUv, sampleDepth);
    vec3 delta = samplePos - centerPos;
    float distanceToSample = length(delta);
    if (distanceToSample <= 0.0001 || distanceToSample >= maximumDistance) return 0.0;
    float horizon = max(dot(normal, delta / distanceToSample) - 0.16, 0.0);
    return horizon * (1.0 - smoothstep(0.0, maximumDistance, distanceToSample));
}

float screenSpaceOcclusion(vec3 centerPos, vec3 normal, vec2 uv) {
    float radius = mix(3.0, 6.5, clamp(abs(centerPos.z) / 80.0, 0.0, 1.0));
    float maximumDistance = max(abs(centerPos.z) * 0.10, 1.2);
    float occlusion = 0.0;

    // Four diagonal taps are enough for the soft blue-hour AO style and halve
    // the previous depth-sampling cost.
    float diagonal = radius * 0.78;
    occlusion += aoTap(centerPos, normal, uv, vec2( diagonal,  diagonal), maximumDistance);
    occlusion += aoTap(centerPos, normal, uv, vec2(-diagonal,  diagonal), maximumDistance);
    occlusion += aoTap(centerPos, normal, uv, vec2( diagonal, -diagonal), maximumDistance);
    occlusion += aoTap(centerPos, normal, uv, vec2(-diagonal, -diagonal), maximumDistance);
    occlusion = clamp(occlusion * 0.58 * U_AOStrength, 0.0, 0.70);
    if (U_LowPolyLighting > 0.5) occlusion = floor(occlusion * 5.0 + 0.5) / 5.0;
    return occlusion;
}

void main() {
    float rawDepth = texture(MainDepthSampler, texCoord).r;
    bool sky = isSkyDepth(rawDepth);
    if ((sky && U_SkyEnabled < 0.5) || (!sky && U_GroundEnabled < 0.5)) {
        fragColor = texture(MainColorSampler, texCoord);
        return;
    }

    vec3 viewRay = normalize(clipToView(texCoord, 1.0));
    vec3 worldRay = normalize(mat3(U_InverseViewMatrix) * viewRay);

    float dropletMask = 0.0;
    float fogMask = 0.0;
    float wiperBlade = 0.0;
    vec2 dropletOffset = vec2(0.0);
    if (U_ScreenRain > 0.5 && U_RainDensity > 0.0) {
        dropletMask = screenDroplets(texCoord, dropletOffset) * U_RainOpacity;
        fogMask = glassFogField(texCoord);
        float cleared = wiperClearMask(texCoord, wiperBlade);
        dropletMask *= 1.0 - cleared * 0.98;
        fogMask *= 1.0 - cleared * 0.97;
        dropletOffset *= 1.0 - cleared;
    }
    vec2 sourceUv = clamp(texCoord + dropletOffset * (0.0105 * U_DropletRefraction), vec2(0.001), vec2(0.999));
    vec3 sceneColor = texture(MainColorSampler, sourceUv).rgb;

    // V4 separates condensation blur from water droplets. A droplet is a small
    // refractive lens with a sharp contact line; it should distort the scene,
    // not be covered by the same Gaussian blur as fogged glass.
    float glassBlurMask = clamp(fogMask + dropletMask * 0.035, 0.0, 1.0);
    if (glassBlurMask > 0.002) {
        vec2 blurPixel = (0.55 + U_GlassFogBlur * fogMask * 0.88 + dropletMask * 0.20) / ScreenSize;
        vec2 diagonal = blurPixel * 0.72;
        vec3 rainBlur = sceneColor * 0.32;
        rainBlur += texture(MainColorSampler, clamp(sourceUv + vec2( diagonal.x,  diagonal.y), vec2(0.0), vec2(1.0))).rgb * 0.17;
        rainBlur += texture(MainColorSampler, clamp(sourceUv + vec2(-diagonal.x,  diagonal.y), vec2(0.0), vec2(1.0))).rgb * 0.17;
        rainBlur += texture(MainColorSampler, clamp(sourceUv + vec2( diagonal.x, -diagonal.y), vec2(0.0), vec2(1.0))).rgb * 0.17;
        rainBlur += texture(MainColorSampler, clamp(sourceUv + vec2(-diagonal.x, -diagonal.y), vec2(0.0), vec2(1.0))).rgb * 0.17;
        float blurMix = clamp(fogMask * 0.64 + dropletMask * 0.055, 0.0, 0.72);
        sceneColor = mix(sceneColor, rainBlur, blurMix);
        vec3 condensationTint = applyBlueHourTone(vec3(0.91, 0.96, 1.025));
        vec3 condensationLift = applyBlueHourTone(vec3(0.014, 0.022, 0.030));
        sceneColor = mix(sceneColor, sceneColor * condensationTint + condensationLift, fogMask * 0.24);
    }
    vec3 effect;
    float nativeLightInfluence = 0.0;

    if (sky) {
        float horizon = pow(clamp(worldRay.y * 0.5 + 0.5, 0.0, 1.0), 0.65);
        vec3 skyHorizon = applyBlueHourTone(SKY_HORIZON);
        vec3 skyTop = applyBlueHourTone(SKY_TOP);
        effect = mix(skyHorizon, skyTop, horizon);
        float horizonGlow = exp(-abs(worldRay.y + 0.015) * 7.0);
        effect += applyBlueHourTone(vec3(0.08, 0.15, 0.22)) * horizonGlow;
        vec4 clouds = renderClouds(worldRay);
        effect = effect * (1.0 - clouds.a) + clouds.rgb;
        effect = mix(sceneColor, effect, U_MoodIntensity);
    } else {
        vec3 viewPos = clipToView(texCoord, rawDepth);
        float distanceToSurface = length(viewPos);
        float gray = luminance(sceneColor);
        vec3 coolColor = mix(sceneColor, vec3(gray), 0.30) * vec3(0.68, 0.86, 1.15);
        coolColor = applyBlueHourTone(coolColor);
        coolColor = (coolColor - 0.5) * U_Contrast + 0.5;

        float wetHighlight = smoothstep(0.42, 0.92, gray) * U_Wetness;
        coolColor += applyBlueHourTone(vec3(0.035, 0.070, 0.105)) * wetHighlight;

        float normalConfidence = 0.0;
        vec3 normal = normalize(-viewPos);
        if (U_AOEnabled > 0.5 && U_AOStrength > 0.0) {
            normal = reconstructNormal(viewPos, texCoord, rawDepth, normalConfidence);
        }

        if (U_AOEnabled > 0.5 && U_AOStrength > 0.0) {
            float occlusion = screenSpaceOcclusion(viewPos, normal, texCoord) * normalConfidence;
            coolColor *= mix(vec3(1.0), applyBlueHourTone(vec3(0.52, 0.67, 0.82)), occlusion);
        }

        float fog = 1.0 - exp(-distanceToSurface * 0.0105);
        vec3 blueWorld = mix(coolColor, applyBlueHourTone(FOG_BLUE), clamp(fog, 0.0, 0.86));
        effect = mix(sceneColor, blueWorld, U_MoodIntensity);

        // V10: only local illumination residuals are separated from the environment.
        // Absolute warm/green/bright colors can no longer globally bypass the blue grade;
        // there is still no emitter scan, radius, distance or maximum-light list.
        if (U_IndependentLightColorEnabled > 0.5) {
            nativeLightInfluence = globalNativeBlockLight(texCoord, rawDepth, sceneColor);

            if (nativeLightInfluence > 0.001) {
                vec3 independentLightScene = independentLightChromaticity(effect, sceneColor);
                effect = mix(effect, independentLightScene, nativeLightInfluence);
            }
        }
    }

    if (U_WorldRain > 0.5 && U_RainDensity > 0.0) {
        float maximumDistance = sky ? 118.0 : min(length(clipToView(texCoord, rawDepth)), 118.0);
        float rainStreak = worldRain(texCoord, worldRay, maximumDistance) * U_RainOpacity;
        // Airborne rain is mostly transparent scattering/refraction, not blue
        // emissive lines. Keep the blue-hour tint subtle and scene-dependent.
        vec3 rainColor = applyBlueHourTone(vec3(0.56, 0.68, 0.76));
        effect = mix(effect, effect * 0.90 + rainColor * 0.10, rainStreak * 0.22);
        effect += rainColor * rainStreak * 0.052;
    }

    effect += renderTyndall(texCoord, worldRay);

    if (dropletMask > 0.0) {
        float slope = length(dropletOffset);
        float rim = smoothstep(0.015, 0.22, slope) * (1.0 - smoothstep(0.52, 0.95, slope));
        vec3 waterNormal = normalize(vec3(-dropletOffset * 4.0, 1.0));
        float specular = pow(max(dot(waterNormal, normalize(vec3(-0.42, 0.58, 1.0))), 0.0), 22.0);
        effect += applyBlueHourTone(vec3(0.14, 0.23, 0.32)) * rim * dropletMask * U_RainOpacity * 0.72;
        effect += applyBlueHourTone(vec3(0.62, 0.76, 0.88)) * specular * dropletMask * 0.22;
    }
    if (wiperBlade > 0.0) {
        effect = mix(effect, effect * applyBlueHourTone(vec3(0.48, 0.55, 0.62)), wiperBlade * 0.62);
        effect += applyBlueHourTone(vec3(0.20, 0.30, 0.39)) * wiperBlade * 0.18;
    }

    effect = applyLightEffects(effect, texCoord, rawDepth, dropletMask, nativeLightInfluence);

    float introTime = U_GameTime * U_IntroSpeed;
    if (U_LoopIntro > 0.5) introTime = mod(U_GameTime, U_IntroDuration) * U_IntroSpeed;
    float revealRadius = pow(max(introTime, 0.0), 4.0);
    float revealMetric = sky ? worldRay.y : length(clipToView(texCoord, rawDepth));
    float revealThreshold = sky ? mix(-0.95, 1.1, smoothstep(0.0, 700.0, revealRadius)) : revealRadius;
    float revealSoftness = sky ? 0.12 : 9.0;
    float reveal = 1.0 - smoothstep(revealThreshold, revealThreshold + revealSoftness, revealMetric);
    float scanLine = smoothstep(revealThreshold, revealThreshold + revealSoftness, revealMetric)
        * (1.0 - smoothstep(revealThreshold + revealSoftness, revealThreshold + revealSoftness * 2.2, revealMetric));

    vec3 finalColor = mix(texture(MainColorSampler, texCoord).rgb, effect, reveal);
    finalColor += applyBlueHourTone(vec3(0.22, 0.52, 0.78)) * scanLine * 0.65;
    fragColor = vec4(max(finalColor, vec3(0.0)), 1.0);
}
