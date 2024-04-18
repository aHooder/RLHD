/*
 * Copyright (c) 2024, Hooder <ahooder@protonmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#include utils/constants.glsl
#include utils/color_utils.glsl
#include utils/shadows.glsl
#include utils/texture_bicubic.glsl

#define IDENTICAL_WAVES
//#define ALMOST_IDENTICAL_APPEARANCE

// Index of refraction and the cosine of the angle between the normal vector and a vector towards the camera
float calculateFresnel(const float cosi, const float iorFrom, const float iorTo) {
    float R0 = (iorFrom - iorTo) / (iorFrom + iorTo);
    R0 *= R0;
    return R0 + (1 - R0) * pow(1 - cosi, 5);
}

// Air to medium by default
float calculateFresnel(const float cosi, const float ior) {
    return calculateFresnel(cosi, IOR_AIR, ior);
}

// Air to medium by default
float calculateFresnel(const vec3 I, const vec3 N, const float ior) {
    return calculateFresnel(dot(I, N), ior);
}

// Fresnel approximation which accounts for total internal reflection
// Normalized incident and normal vectors, and index of refraction
float calculateFresnelTIR(const vec3 I, const vec3 N, const float ior) {
    float R0 = (ior - 1) / (ior + 1);
    R0 *= R0;
    float cosi = dot(I, N);
    if (ior < 1) {
        // Moving from denser to lighter medium
        float inv_eta = 1 / ior;
        float sintSquared = inv_eta * inv_eta * (1 - cosi * cosi);
        if (sintSquared > 1)
            return 1; // total internal reflection
        cosi = sqrt(1 - sintSquared);
    }
    return R0 + (1 - R0) * pow(1 - cosi, 5);
}

void sampleUnderwater(inout vec3 outputColor, int waterTypeIndex, float depth) {
    // All light is absorbed
    switch (waterTypeIndex) {
        case WATER_TYPE_SWAMP_WATER:
        case WATER_TYPE_BLACK_TAR_FLAT:
        case WATER_TYPE_MUDDY_WATER:
            outputColor = vec3(0);
            return;
    }

    WaterType waterType = getWaterType(waterTypeIndex);

    // Make the color appear like wet sand/dirt to start off with
    outputColor *= vec3(.68, 1.2, 1.2);

    // The idea is to approximate light absorption using 3 frequency bands.
    // To accomplish this, we must transform linear RGB into linear XYZ,
    // then into our chosen 3 frequency bands, do all of the light calculations,
    // then finally transform it back into linear RGB

    // Pure water based on https://en.wikipedia.org/wiki/Electromagnetic_absorption_by_water#/media/File:Absorption_coefficient_of_water.svg
    // Exponential extinction coefficient per meter
    vec3 extinctionCoefficients = vec3(
        0.2224, // ~red   600 nm
        0.0565, // ~green 550 nm
        0.00922 // ~blue  450 nm
    );
    // Approximate some kind of ocean water with phytoplankton absorption based on https://www.oceanopticsbook.info/view/absorption/absorption-by-oceanic-constituents
    extinctionCoefficients += vec3(
        0.005,  // 600 nm
        0.0175, // 550 nm
        0.0275  // 450 nm
    );
    extinctionCoefficients *= 3;

    #ifdef ALMOST_IDENTICAL_APPEARANCE
    // Try to match water.glsl appearance
    extinctionCoefficients = vec3(
        .309,
        .3,
        .1548
    ) * .35;
    #endif

    switch (waterTypeIndex) {
        case WATER_TYPE_POISON_WASTE:
            extinctionCoefficients = vec3(
                .309,
                .3,
                .1548
            ) * .35;
            extinctionCoefficients += vec3(
                0.005,
                0.0175,
                0.0275
            ) * 20;
            break;
        case WATER_TYPE_SCAR_SLUDGE:
            extinctionCoefficients = vec3(
                .309,
                .3,
                .1548
            ) * .35;
            extinctionCoefficients += vec3(
                0.005,
                0.0175,
                0.0275
            ) * 20;
            break;
    }

    // Convert extinction coefficients to in-game units
    extinctionCoefficients /= 128.f;

    // Convert to XYZ at the end https://en.wikipedia.org/wiki/CIE_1931_color_space#Color_matching_functions
    const mat3 bandsToXyz = mat3(
        //      600 nm,         550 nm,         450 nm
        1.062200000000, 0.433449900000, 0.336200000000,
        0.631000000000, 0.994950100000, 0.038000000000,
        0.000800000000, 0.008749999000, 1.772110000000
    );
    const mat3 xyzToBands = mat3(
         1.268775120684124396,     -0.55072870658672442104,  -0.22889916806727973655,
        -0.80479044935729810573,    1.354595097945706297,     0.12363562947671802757,
         0.0034009714580576880391, -0.0064398501149254011414, 0.5637919247113148566
    );

    float absorptionAdjustment = exp(5 * (1 - waterTransparencyAmount));
    extinctionCoefficients *= absorptionAdjustment;

    // Refraction is not precalculated for the underwater position
    vec3 fragPos = IN.position;
    vec3 underwaterNormal = normalize(IN.normal);
    const vec3 surfaceNormal = vec3(0, -1, 0); // Assume a flat surface

    vec3 sunDir = -lightDir; // the light's direction from the sun towards any fragment
    vec3 refractedSunDir = refract(sunDir, surfaceNormal, IOR_AIR_TO_WATER);
    float sunToFragDist = depth / refractedSunDir.y;

    vec3 camToFrag = normalize(fragPos - cameraPos);
    // We ignore refraction effects on the way back up to the surface
    float fragToSurfaceDist = abs(depth / camToFrag.y);

    // Attenuate directional and ambient light by their distances travelled on the way down
    vec3 directionalAttenuation = exp(-extinctionCoefficients * sunToFragDist);
    vec3 ambientAttenuation = exp(-extinctionCoefficients * depth);
    // Attenuate the light on the way back up to the surface
    vec3 upwardAttenuation = exp(-extinctionCoefficients * fragToSurfaceDist);

    // Initialize with linear RGB colors
    vec3 directionalLight = lightColor * lightStrength;
    vec3 ambientLight = ambientColor * ambientStrength;

    // Add underwater caustics as additional directional light
    if (shorelineCaustics) {
        vec2 causticsUv = worldUvs(3.333);
        const vec2 direction = vec2(1, -2);
        vec2 flow1 = causticsUv + animationFrame(13) * direction;
        vec2 flow2 = causticsUv * 1.5 + animationFrame(17) * -direction;
        vec3 caustics = sampleCaustics(flow1, flow2, depth * .00003);

        // Apply caustics color based on the environment
        // Usually this falls back to directional lighting
        caustics *= underwaterCausticsColor * underwaterCausticsStrength;

        // Apply the caustics strength config
        caustics *= waterCausticsStrength;

        // Fade caustics out too close to the shoreline
        caustics *= min(1, smoothstep(0, 1, depth / 32));

        // Fade caustics out with depth, since they should decay sharply due to focus
        caustics *= max(0, 1 - smoothstep(0, 1, depth / 768));

        // Artificially boost strength
        caustics *= 1.5;

        // Add caustics as additional directional light
        directionalLight *= 1 + caustics;
    }

    // Disable shadows for flat water, as it needs more work
    if (waterTransparency && !waterType.isFlat) {
        // For shadows, we can take refraction into account, since sunlight is parallel
        vec3 surfaceSunPos = fragPos - refractedSunDir * sunToFragDist;
        surfaceSunPos += refractedSunDir * 32; // Push the position a short distance below the surface
        vec2 distortion = vec2(0);
        {
            vec2 flowMapUv = worldUvs(26) + animationFrame(26 * waterType.duration);
            float flowMapStrength = 0.025;
            vec2 uvFlow = textureBicubic(textureArray, vec3(flowMapUv, waterType.flowMap)).xy;
            distortion = uvFlow * .001 * (1 - exp(-.01 * depth));
        }
        float shadow = sampleShadowMap(surfaceSunPos, distortion, dot(-sunDir, underwaterNormal));
        // Attenuate directional by shadowing
        directionalLight *= 1 - shadow;
    }

    // Attenuate directional light by fresnel refraction
    directionalLight *= 1 - calculateFresnel(max(0, dot(-sunDir, surfaceNormal)), IOR_WATER);
    // Attenuate ambient light by fresnel refraction travelling straight down
    ambientLight *= 1 - calculateFresnel(1, IOR_WATER);

    // Attenuate based on the direction light hits the underwater surface
    directionalLight *= max(0, dot(-refractedSunDir, underwaterNormal));
    // This is a bit questionable, since ambient comes from all directions, but here we assume it goes down
    ambientLight *= max(0, -underwaterNormal.y);

    // Scale colors to safe ranges for conversion to XYZ
    float intensityFactor = max(
        max(max(directionalLight.r, directionalLight.g), directionalLight.b),
        max(max(ambientLight.r, ambientLight.g), ambientLight.b)
    );
    if (intensityFactor <= .00001) {
        // Exit early if there's no incoming light
        outputColor = vec3(0);
        return;
    }
    directionalLight /= intensityFactor;
    ambientLight /= intensityFactor;

    // Convert light into frequency bands for extinction calculations
    directionalLight = xyzToBands * RGBtoXYZ(directionalLight);
    ambientLight = xyzToBands * RGBtoXYZ(ambientLight);

    // Attenuate light on the way down towards the underwater surface
    directionalLight *= directionalAttenuation;
    ambientLight *= ambientAttenuation;

    // Combine both sources of light upon reaching the fragment
    vec3 light = ambientLight + directionalLight;

    // Attenuate light on the way back up towards the water surface
    light *= upwardAttenuation;

    // Convert the light back into RGB
    light = XYZtoRGB(bandsToXyz * light);

    // Bring the intensity back
    light *= intensityFactor;

    // Light doesn't need to be attenuated on the way back up through the surface, since the surface
    // already handles this with alpha blending. The only remaining issue might be accounting for
    // total internal reflection, which can maybe also be done by the surface, where normals are known

    // Apply the calculated light to the fragment's color
    outputColor *= light;
}

vec4 sampleWater(int waterTypeIndex, vec3 viewDir) {
    WaterType waterType = getWaterType(waterTypeIndex);
    float specularGloss = 500; // Ignore values set per water type, as they don't make a lot of sense
    float specularStrength = waterType.specularStrength * waterSpecularStrength;

    vec3 ambientLight = ambientColor * ambientStrength;
    vec3 directionalLight = lightColor * lightStrength;

    vec3 N;
    #ifdef IDENTICAL_WAVES
        float waveHeight = waterWaveSize;
        float speed = .024;

        switch (waterTypeIndex) {
            case WATER_TYPE_BLACK_TAR_FLAT:
                waveHeight = .1;
                speed = .01;
                break;
            case WATER_TYPE_MUDDY_WATER:
                waveHeight = .1;
                break;
            case WATER_TYPE_BLOOD:
                waveHeight = .75;
                break;
            case WATER_TYPE_ICE:
            case WATER_TYPE_ICE_FLAT:
                waveHeight = .1;
                speed = 0.00000001;
                break;
            case WATER_TYPE_ABYSS_BILE:
                waveHeight = .7;
                break;
        }

        speed *= waterWaveSpeed;
        vec2 uv1 = worldUvs(26) - animationFrame(sqrt(11.) / speed * waterType.duration / vec2(-1, 4));
        vec2 uv2 = worldUvs(6) - animationFrame(sqrt(3.) / speed * waterType.duration * 1.5 /vec2(2, -1));

        // Flip UVs horizontally, since our water normal maps aren't oriented like vanilla textures
        uv1.x = 1 - uv1.x;
        uv2.x = 1 - uv2.x;
        vec3 n1 = textureBicubic(waterNormalMaps, vec3(uv1, 0)).xyz;
        vec3 n2 = textureBicubic(waterNormalMaps, vec3(uv2, 1)).xyz;

        // Normalize
        n1.xy = n1.xy * 2 - 1;
        n2.xy = n2.xy * 2 - 1;
        // Tangent space to world
        n1.z *= -1;
        n2.z *= -1;
        n1.xyz = n1.xzy;
        n2.xyz = n2.xzy;
        n1.y /= 0.225; // scale normals

        n1.y /= waveHeight;
        n1 = normalize(n1);
        n2.y /= 0.8; // scale normals

        n2.y /= waveHeight;
        n2 = normalize(n2);
        N = normalize(n1 + n2);
    #else
        float waveHeight = waterWaveSize;
        float waveSpeed = waterWaveSpeed * .01;
        vec2 uv1 = worldUvs(26) + vec2(1, -4) * waveSpeed * elapsedTime;
        vec2 uv2 = worldUvs(6) + vec2(5, -1) * waveSpeed * elapsedTime;

        // Flip UVs horizontally, since our water normal maps aren't oriented like vanilla textures
        uv1.x = 1 - uv1.x;
        uv2.x = 1 - uv2.x;
        vec3 n1 = textureBicubic(waterNormalMaps, vec3(uv1, 0)).xyz;
        vec3 n2 = textureBicubic(waterNormalMaps, vec3(uv2, 1)).xyz;

        // Normalize
        n1.xy = n1.xy * 2 - 1;
        n2.xy = n2.xy * 2 - 1;
        // Tangent space to world
        n1.z *= -1;
        n2.z *= -1;
        n1.xyz = n1.xzy;
        n2.xyz = n2.xzy;
        // Scale normals
        n1.y /= waveHeight * .4;
        n2.y /= waveHeight;
        n1 = normalize(n1);
        n2 = normalize(n2);
        N = normalize(n1 + n2);
    #endif

    vec3 fragToCam = viewDir;
    vec3 I = -viewDir; // incident

    // Assume the water is level
    vec3 flatR = reflect(I, vec3(0, -1, 0));
    vec3 R = reflect(I, N);
    float distortionFactor = 50;

    // Initialize the reflection with a fake sky reflection
    vec4 reflection = vec4(
        sampleWaterReflection(flatR, R, distortionFactor),
        calculateFresnel(dot(fragToCam, N), IOR_WATER)
    );

    vec3 additionalLight = vec3(0);

    // Scattering approximation
    vec3 C_ss = vec3(0, .6792933, 1);
    vec3 C_f = vec3(.2685256, .9729184, 1);
    // float k_1 = 0; // This doesn't work for our normal map-based waves unfortunately
    float k_2 = .0015;
    float k_3 = .0015;
    float k_4 = .0005;

    switch (waterTypeIndex) {
        case WATER_TYPE_SWAMP_WATER:
            C_f = vec3(.12060062, .25193095, .15640968);
            k_2 = .0015;
            k_3 = .0015;
            k_4 = .015;
            break;
        case WATER_TYPE_POISON_WASTE:
            C_f = vec3(.04470426, .05751832, .028336687);
            k_2 = .001;
            k_3 = .002;
            k_4 = .15;
            break;
        case WATER_TYPE_BLACK_TAR_FLAT:
            k_2 = k_3 = k_4 = 0;
            break;
        case WATER_TYPE_BLOOD:
            C_ss = C_f = vec3(1, 0, 0);
            k_2 = .05;
            k_3 = .001;
            k_4 = .015;
            break;
        case WATER_TYPE_ICE:
        case WATER_TYPE_ICE_FLAT:
            C_f = vec3(1);
            k_4 = .003;
            break;
        case WATER_TYPE_MUDDY_WATER:
            C_ss = C_f = vec3(.13843162, .07618539, .015996294);
            k_2 = .001;
            k_3 = .002;
            k_4 = .11;
            break;
        case WATER_TYPE_SCAR_SLUDGE:
            C_f = vec3(.04470426, .05751832, .028336687);
            k_2 = .001;
            k_3 = .002;
            k_4 = .25;
            break;
        case WATER_TYPE_ABYSS_BILE:
            C_f = vec3(.1682694, .2501583, .12477182);
            k_2 = .001;
            k_3 = .002;
            k_4 = .1;
            break;
        case WATER_TYPE_DARK_BLUE_WATER:
            C_f = vec3(0, .082282715, .111932434);
            k_2 = .1;
            k_3 = .01;
            k_4 = .02;
            break;
        case WATER_TYPE_CYAN_WATER:
            C_ss = vec3(0.026, .45, .8);
            C_f = vec3(1);
            k_2 = .15;
            k_3 = .2;
            k_4 = .01;
            reflection.rgb *= 8;
            break;
    }

//  float H = (1 + N.y) * 50; // wave height
    vec3 omega_i = lightDir; // incoming = frag to sun
    vec3 omega_o = viewDir; // outgoing = frag to camera
    vec3 omega_h = normalize(omega_o + omega_i); // half-way vector
    vec3 omega_n = N; // surface normal

    vec3 L_scatter = (
//      k_1*H*pow(max(0, dot(omega_i, -omega_o)), 4.f) * pow(.5 - .5*dot(omega_i, omega_n), 3.f) +
        k_2 * pow(max(0, dot(omega_o, omega_n)), 2.f) +
        k_3 * max(0, dot(omega_i, omega_n))
    ) * C_ss * directionalLight;
    L_scatter += k_4 * C_f * (ambientLight + directionalLight);
    additionalLight += L_scatter;

    #if WATER_SPECULAR_MODE == 1 || WATER_SPECULAR_MODE == 3 // sun or sun & lights
        vec3 sunSpecular = pow(max(0, dot(N, omega_h)), specularGloss) * lightStrength * lightColor * specularStrength;
        additionalLight += sunSpecular * calculateFresnel(omega_i, omega_n, IOR_WATER);
    #endif

    // Point lights
    #if WATER_SPECULAR_MODE >= 2 // lights or sun & lights
        vec3 pointLightsSpecular = vec3(0);
        float fragToCamDist = length(IN.position - cameraPos);
        for (int i = 0; i < pointLightsCount; i++) {
            vec4 pos = PointLightArray[i].position;
            vec3 fragToLight = pos.xyz - IN.position;
            float fragToLightDist = length(fragToLight);
            float distSq = fragToLightDist + fragToCamDist;
            distSq *= distSq;
            float radiusSquared = pos.w;

            vec3 pointLightColor = PointLightArray[i].color;
            vec3 pointLightDir = fragToLight / fragToLightDist;

            pointLightColor *= 1 / (1 + distSq) * 2e5;

            vec3 halfway = normalize(omega_o + pointLightDir);
            pointLightsSpecular += pointLightColor * pow(max(0, dot(halfway, N)), specularGloss) * specularStrength;
        }
        additionalLight += pointLightsSpecular;
    #endif

    // Begin constructing final output color
    vec4 dst = reflection;

    // In theory, we could just add the light and be done with it, but since the color
    // will be multiplied by alpha during alpha blending, we need to divide by alpha to
    // end up with our target amount of additional light after alpha blending
    dst.rgb += additionalLight / dst.a;

    // The issue now is that or color may exceed 100% brightness, and get clipped.
    // To work around this, we can adjust the alpha component to let more of the light through,
    // and adjust our color accordingly. This necessarily causes the surface to become more opaque,
    // but since we're adding lots of light, this should have minimal impact on the final picture.
    float maxIntensity = max(max(dst.r, dst.g), dst.b);
    // Check if the color would get clipped
    if (maxIntensity > 1) {
        // Bring the brightest color back down to 1
        dst.rgb /= maxIntensity;
        // And bump up the alpha to increase brightness instead
        dst.a *= maxIntensity;
        // While not strictly necessary, we might as well clamp the alpha component in case it exceeds 1
        dst.a = min(1, dst.a);
    }

    // If the water is opaque, blend in a fake underwater surface
    if (waterType.isFlat || !waterTransparency) {
        // Computed from packedHslToSrgb(6676)
        const vec3 underwaterColor = vec3(0.04856183, 0.025971446, 0.005794384);
        int depth = 1200;

        // TODO: add a way for tile overrides to specify water depth
        if (waterTypeIndex == WATER_TYPE_ABYSS_BILE)
            depth = 16;

        vec4 src = dst;
        dst.rgb = underwaterColor;
        sampleUnderwater(dst.rgb, waterTypeIndex, depth);

        dst.rgb = mix(dst.rgb, src.rgb, src.a);
        dst.a = 1;
    }

    #if WATER_FOAM
        vec2 flowMapUv = worldUvs(15) + animationFrame(50 * waterType.duration);
        float flowMapStrength = 0.025;
        vec2 uvFlow = texture(textureArray, vec3(flowMapUv, waterType.flowMap)).xy;
        vec2 uv3 = vUv[0].xy * IN.texBlend.x + vUv[1].xy * IN.texBlend.y + vUv[2].xy * IN.texBlend.z + uvFlow * flowMapStrength;
        float foamMask = texture(textureArray, vec3(uv3, waterType.foamMap)).r;
        float foamAmount = 1 - dot(IN.texBlend, vec3(vColor[0].x, vColor[1].x, vColor[2].x));
        float foamDistance = 1;
        vec3 foamColor = .214 * foamMask * (ambientColor * ambientStrength + lightColor * lightStrength);
        foamAmount = clamp(pow(1.0 - ((1.0 - foamAmount) / foamDistance), 3), 0.0, 1.0) * waterType.hasFoam;
        foamAmount *= waterFoamAmount;
        foamAmount *= 0.12; // rescale foam so that 100% is a good default amount
        vec4 foam = vec4(foamColor, foamAmount);

        switch (waterTypeIndex) {
            case WATER_TYPE_SWAMP_WATER:
            case WATER_TYPE_SWAMP_WATER_FLAT:
                foam.rgb *= vec3(1.3, 1.3, 0.4);
                foam.a *= .25;
                break;
            case WATER_TYPE_POISON_WASTE:
                foam.rgb *= vec3(0.7, 0.7, 0.7);
                foam.a *= .25;
                break;
            case WATER_TYPE_BLACK_TAR_FLAT:
                foam.a *= .2;
                break;
            case WATER_TYPE_BLOOD:
                foam.a = 0;
                break;
            case WATER_TYPE_ICE:
                foam.rgb *= vec3(0.5, 0.5, 0.5);
                break;
            case WATER_TYPE_ICE_FLAT:
                foam.rgb *= vec3(0.5, 0.5, 0.5);
                break;
            case WATER_TYPE_MUDDY_WATER:
                foam.a *= .1;
                break;
            case WATER_TYPE_SCAR_SLUDGE:
                foam.a *= .5;
                break;
            case WATER_TYPE_ABYSS_BILE:
                foam.rgb *= vec3(1.0, 0.7, 0.3);
                break;
            case WATER_TYPE_PLAIN_WATER:
                foam.rgb *= vec3(1);
                break;
        }

        float maxfoam = max(max(foam.r, foam.g), foam.b);
        foam.a *= maxfoam;
        foam.rgb /= maxfoam;

        // Blend in foam at the very end as an overlay
        dst.rgb = foam.rgb * foam.a + dst.rgb * dst.a * (1 - foam.a);
        dst.a = foam.a + dst.a * (1 - foam.a);
        dst.rgb /= dst.a;
    #endif

    return dst;
}
