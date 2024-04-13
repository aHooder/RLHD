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
    WaterType waterType = getWaterType(waterTypeIndex);

    // Make the color appear like wet sand/dirt to start off with
    outputColor *= vec3(.84, 1.2, 1.2);

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
//    extinctionCoefficients += vec3(
//        0.0275,
//        0.0175,
//        0.005
//    );

    // Attempt #1 at picking some numbers which look nice
//    extinctionCoefficients = vec3(
//        .1,
//        .1,
//        .00922
//    );

    // Try to match water.glsl appearance
    extinctionCoefficients = vec3(
        .309,
        .3,
        .1548
    ) * .35;

    switch (waterTypeIndex) {
        case WATER_TYPE_SWAMP_WATER:
        case WATER_TYPE_BLOOD:
        case WATER_TYPE_MUDDY_WATER:
            // TODO: should just make these opaque
            extinctionCoefficients = vec3(100); // basically opaque
            break;
        case WATER_TYPE_POISON_WASTE:
            extinctionCoefficients = vec3(
                .309,
                .3,
                .1548
            ) * .35;
            extinctionCoefficients += vec3(
                0.0275,
                0.0175,
                0.005
            ) * 20;
            break;
        case WATER_TYPE_SCAR_SLUDGE:
            extinctionCoefficients = vec3(
                .309,
                .3,
                .1548
            ) * .35;
            extinctionCoefficients += vec3(
                0.0275,
                0.0175,
                0.005
            ) * 20;
            break;
    }

    // Convert extinction coefficients to in-game units
    extinctionCoefficients /= 128.f;

    // Convert to XYZ at the end https://en.wikipedia.org/wiki/CIE_1931_color_space#Color_matching_functions
    mat3 bandsToXyz = transpose(mat3(
        1.062200000000, 0.631000000000, 0.000800000000, // 600 nm
        0.433449900000, 0.994950100000, 0.008749999000, // 550 nm
        0.336200000000, 0.038000000000, 1.772110000000  // 450 nm
    ));
    mat3 xyzToBands = inverse(bandsToXyz);

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
    // TODO: support viewing underwater geometry from below in waterfalls properly
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
    if (underwaterCaustics) {
        vec2 causticsUv = worldUvs(3.333);
        const vec2 direction = vec2(1, -2);
        vec2 flow1 = causticsUv + animationFrame(13) * direction;
        vec2 flow2 = causticsUv * 1.5 + animationFrame(17) * -direction;
        // TODO: Chromatic abberation can make it appear like caustics are adding shadows
         vec3 caustics = sampleCaustics(flow1, flow2, depth * .00003);
//        vec3 caustics = sampleCaustics(flow1, flow2);

        // Apply caustics color based on the environment
        // Usually this falls back to directional lighting
        caustics *= underwaterCausticsColor * underwaterCausticsStrength;

        // Fade caustics out too close to the shoreline
        caustics *= min(1, smoothstep(0, 1, depth / 32));

        // Fade caustics out with depth, since they should decay sharply due to focus
        caustics *= max(0, 1 - smoothstep(0, 1, depth / 768));

        // Artificially boost strength
        caustics *= 7.5;

        directionalLight += caustics;
    }

    // Disable shadows for flat water, as it needs more work
    if (waterTransparency && !waterType.isFlat) {
        // For shadows, we can take refraction into account, since sunlight is parallel
        vec3 surfaceSunPos = fragPos - refractedSunDir * sunToFragDist;
        surfaceSunPos += refractedSunDir * 32; // Push the position a short distance below the surface
        vec2 distortion = vec2(0);
        {
            vec2 flowMapUv = worldUvs(15) + animationFrame(50 * waterType.duration);
            float flowMapStrength = 0.025;
            vec2 uvFlow = textureBicubic(textureArray, vec3(flowMapUv, waterType.flowMap)).xy;
            distortion = uvFlow * .0015 * (1 - exp(-depth));
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
    ambientLight *= -underwaterNormal.y;

    // Scale colors to safe ranges for conversion to XYZ
    float intensityFactor = max(
        max(max(directionalLight.r, directionalLight.g), directionalLight.b),
        max(max(ambientLight.r, ambientLight.g), ambientLight.b)
    );
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
    float specularGloss = waterType.specularGloss;
    float specularStrength = waterType.specularStrength;

    specularStrength *= .4; // reduce intensity TODO: fresnel maybe?

    vec3 N;
    #ifdef IDENTICAL_WAVES
        float waveHeight = waterWaveSize;
        float speed = .024;

        switch (waterTypeIndex) {
            case WATER_TYPE_BLOOD:
                waveHeight = .75;
                break;
            case WATER_TYPE_ICE:
            case WATER_TYPE_ICE_FLAT:
                waveHeight = .1;
                speed = 0.00000001;
                break;
            case WATER_TYPE_MUDDY_WATER:
                waveHeight = .3;
                speed = .08;
                break;
            case WATER_TYPE_ABYSS_BILE:
                waveHeight = .7;
                break;
        }

        speed *= waterWaveSpeed;
        vec2 uv1 = worldUvs(26) - animationFrame(sqrt(11.) / speed * waterType.duration / vec2(-1, 4));
        vec2 uv2 = worldUvs(6) - animationFrame(sqrt(3.) / speed * waterType.duration * 1.5 /vec2(2, -1));

        // get diffuse textures
        vec3 n1 = linearToSrgb(texture(textureArray, vec3(uv1, MAT_WATER_NORMAL_MAP_1.colorMap)).xyz);
        vec3 n2 = linearToSrgb(texture(textureArray, vec3(uv2, MAT_WATER_NORMAL_MAP_2.colorMap)).xyz);

        // Normalize
        n1.xy = (n1.xy * 2 - 1);
        n2.xy = (n2.xy * 2 - 1);
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
        float waveHeight = waterWaveSize * .5;
        float scale1 = 26;
        float scale2 = 6;
        vec2 dir1 = vec2(2, 1);
        vec2 dir2 = vec2(-1, 4);
        float speed1 = .01 * length(dir1) * waterWaveSpeed;
        float speed2 = .008 * length(dir2) * waterWaveSpeed;
        vec2 uv1 = worldUvs(scale1) + dir1 * speed1 * elapsedTime;
        vec2 uv2 = worldUvs(scale2) + dir2 * speed2 * elapsedTime;

        // get diffuse textures
    //    vec3 n1 = linearToSrgb(texture(textureArray, vec3(uv1, MAT_WATER_NORMAL_MAP_1.colorMap), 1).xyz);
    //    vec3 n2 = linearToSrgb(texture(textureArray, vec3(uv2, MAT_WATER_NORMAL_MAP_2.colorMap), 1).xyz);
    //    vec3 n1 = linearToSrgb(textureBicubic(textureArray, vec3(uv1, MAT_WATER_NORMAL_MAP_1.colorMap)).xyz);
    //    vec3 n2 = linearToSrgb(textureBicubic(textureArray, vec3(uv2, MAT_WATER_NORMAL_MAP_2.colorMap)).xyz);
        vec3 n1 = textureBicubicSrgb(textureArray, vec3(uv1, MAT_WATER_NORMAL_MAP_1.colorMap)).xyz;
        vec3 n2 = textureBicubicSrgb(textureArray, vec3(uv2, MAT_WATER_NORMAL_MAP_2.colorMap)).xyz;
    //    vec3 n1 = textureFxaaSrgb(textureArray, vec3(uv1, MAT_WATER_NORMAL_MAP_1.colorMap)).xyz;
    //    vec3 n2 = textureFxaaSrgb(textureArray, vec3(uv2, MAT_WATER_NORMAL_MAP_2.colorMap)).xyz;

        // Normalize
        n1.xy = (n1.xy * 2 - 1);
        n2.xy = (n2.xy * 2 - 1);
        // Tangent space to world
        n1.z *= -1;
        n2.z *= -1;
        n1.xyz = n1.xzy;
        n2.xyz = n2.xzy;
        n1 = normalize(vec3(1, 1 / waveHeight, 1) * n1);
        n2 = normalize(vec3(1, 1 / waveHeight, 1) * n2);
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

    vec3 ambientLight = ambientColor * ambientStrength;
    vec3 directionalLight = lightColor * lightStrength;

    // Scattering approximation
    // float k_1 = 0; // This doesn't work for our normal map-based waves unfortunately
    float k_2 = .001;
    float k_3 = .002;
    float k_4 = .00001;
    vec3 C_ss = srgbToLinear(vec3(0.332, .708, .728));
    vec3 C_f = vec3(1); // air bubble color

    // From water.glsl
    C_ss = vec3(0, .28, .32); // directional scatter color
    C_f = vec3(1); // ambient scatter color
    k_2 = 0.01; // straight on sun scatter (C_ss)
    k_3 = 0.008; // directional sun scatter (C_ss)
    k_4 = 0.0001;  // ambient scatter (C_f)

    switch (waterTypeIndex) {
        case WATER_TYPE_SWAMP_WATER:
            C_f = srgbToLinear(vec3(.382, .539, .432));
            k_2 = .001;
            k_3 = .002;
            k_4 = .15;
            break;
        case WATER_TYPE_POISON_WASTE:
            C_f = srgbToLinear(vec3(.234, .266, .184));
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
            C_ss = C_f = unpackSrgb(0x684e22);
            k_2 = 0;
            k_3 = .1;
            k_4 = .1;
            break;
        case WATER_TYPE_SCAR_SLUDGE:
            C_f = srgbToLinear(vec3(.234, .266, .184));
            k_2 = .001;
            k_3 = .002;
            k_4 = .25;
            break;
        case WATER_TYPE_ABYSS_BILE:
            C_f = unpackSrgb(0x728963);
            k_2 = .001;
            k_3 = .002;
            k_4 = .1;
            break;
        case WATER_TYPE_DARK_BLUE_WATER:
            C_f = unpackSrgb(0x00515e);
            k_2 = .1;
            k_3 = .01;
            k_4 = .02;
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

    vec3 sunSpecular = pow(max(0, dot(N, omega_h)), specularGloss) * lightStrength * lightColor * specularStrength;
    additionalLight += sunSpecular;

    // Point lights
    vec3 pointLightsSpecular = vec3(0);
    float fragToCamDist = length(IN.position - cameraPos);
    // TODO: optimize by precomputing falloff radius
    for (int i = 0; i < pointLightsCount; i++) {
        vec4 pos = PointLightArray[i].position;
        vec3 fragToLight = pos.xyz - IN.position;
        float fragToLightDist = length(fragToLight);
        float distSq = fragToLightDist + fragToCamDist;
        distSq *= distSq;
        float radiusSquared = pos.w;

        vec3 pointLightColor = PointLightArray[i].color;
        vec3 pointLightDir = fragToLight / fragToLightDist;

        pointLightColor *= 1 / (1 + distSq) * 1e5;

        vec3 halfway = normalize(omega_o + pointLightDir);
        pointLightsSpecular += pointLightColor * pow(max(0, dot(halfway, N)), specularGloss) * specularStrength;
    }
    additionalLight += pointLightsSpecular;

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
    // TODO: make abyssal bile flat, and maybe change hard-coded depth to per environment, tile or water type
    if (waterType.isFlat || !waterTransparency || waterTypeIndex == WATER_TYPE_ABYSS_BILE) {
        // Computed from packedHslToSrgb(6676)
        const vec3 underwaterColor = vec3(0.04856183, 0.025971446, 0.005794384);
        int depth = 600;

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
        vec3 foamColor = vec3(0.5);
        foamColor = srgbToLinear(foamColor) * foamMask * (ambientColor * ambientStrength + lightColor * lightStrength);
        foamAmount = clamp(pow(1.0 - ((1.0 - foamAmount) / foamDistance), 3), 0.0, 1.0) * waterType.hasFoam;
        foamAmount *= waterFoamAmount;
        foamAmount *= 0.12; // rescale foam so that 100% is a good default amount
        vec4 foam = vec4(foamColor, foamAmount);

        switch (waterTypeIndex) {
            case WATER_TYPE_SWAMP_WATER:
            case WATER_TYPE_SWAMP_WATER_FLAT:
                foam.rgb *= vec3(1.3, 1.3, 0.4);
                break;
            case WATER_TYPE_POISON_WASTE:
                foam.rgb *= vec3(0.7, 0.7, 0.7);
                foam.a *= .25;
                break;
            case WATER_TYPE_BLACK_TAR_FLAT:
                foam.rgb *= vec3(1.0, 1.0, 1.0);
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
                foam.rgb *= vec3(1.0, 0.5, 0.5);
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
