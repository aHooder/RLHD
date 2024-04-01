/*
 * Copyright (c) 2021, 117 <https://twitter.com/117scape>
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
#include utils/misc.glsl
#include utils/texture_tiling.glsl

//#define OLD
#ifdef OLD
vec4 sampleWater(int waterTypeIndex, vec3 viewDir) {
    WaterType waterType = getWaterType(waterTypeIndex);

    vec2 baseUv = vUv[0].xy * IN.texBlend.x + vUv[1].xy * IN.texBlend.y + vUv[2].xy * IN.texBlend.z;
    vec2 uv3 = baseUv;

    vec2 uv2 = worldUvs(3) + animationFrame(24 * waterType.duration);
    vec2 uv1 = worldUvs(3).yx - animationFrame(28 * waterType.duration);

    vec2 flowMapUv = worldUvs(15) + animationFrame(50 * waterType.duration);
    float flowMapStrength = 0.025;

    vec2 uvFlow = texture(textureArray, vec3(flowMapUv, waterType.flowMap)).xy;
    uv1 += uvFlow * flowMapStrength;
    uv2 += uvFlow * flowMapStrength;
    uv3 += uvFlow * flowMapStrength;

    // get diffuse textures
    vec3 n1 = texture(textureArray, vec3(uv1, waterType.normalMap)).xyz;
    vec3 n2 = texture(textureArray, vec3(uv2, waterType.normalMap)).xyz;
    float foamMask = texture(textureArray, vec3(uv3, waterType.foamMap)).r;

    // normals
    n1 = -vec3((n1.x * 2 - 1) * waterType.normalStrength, n1.z, (n1.y * 2 - 1) * waterType.normalStrength);
    n2 = -vec3((n2.x * 2 - 1) * waterType.normalStrength, n2.z, (n2.y * 2 - 1) * waterType.normalStrength);
    vec3 normals = normalize(n1 + n2);

    float lightDotNormals = dot(normals, lightDir);
    float downDotNormals = -normals.y;
    float viewDotNormals = dot(viewDir, normals);

    vec2 distortion = uvFlow * .00075;
    float shadow = sampleShadowMap(IN.position, waterTypeIndex, distortion, lightDotNormals);
    float inverseShadow = 1 - shadow;

    vec3 vSpecularStrength = vec3(waterType.specularStrength);
    vec3 vSpecularGloss = vec3(waterType.specularGloss);
    float combinedSpecularStrength = waterType.specularStrength;

    // calculate lighting

    // ambient light
    vec3 ambientLightOut = ambientColor * ambientStrength;

    // directional light
    vec3 dirLightColor = lightColor * lightStrength;

    // apply shadows
    dirLightColor *= inverseShadow;

    vec3 lightColor = dirLightColor;
    vec3 lightOut = max(lightDotNormals, 0.0) * lightColor;

    // directional light specular
    vec3 lightReflectDir = reflect(-lightDir, normals);
    vec3 lightSpecularOut = lightColor * specular(viewDir, lightReflectDir, vSpecularGloss, vSpecularStrength);

    // point lights
    vec3 pointLightsOut = vec3(0);
    vec3 pointLightsSpecularOut = vec3(0);
    for (int i = 0; i < pointLightsCount; i++) {
        vec4 pos = PointLightArray[i].position;
        vec3 lightToFrag = pos.xyz - IN.position;
        float distanceSquared = dot(lightToFrag, lightToFrag);
        float radiusSquared = pos.w;
        if (distanceSquared <= radiusSquared) {
            vec3 pointLightColor = PointLightArray[i].color;
            vec3 pointLightDir = normalize(lightToFrag);

            float attenuation = 1 - min(distanceSquared / radiusSquared, 1);
            pointLightColor *= attenuation * attenuation;

            float pointLightDotNormals = max(dot(normals, pointLightDir), 0);
            pointLightsOut += pointLightColor * pointLightDotNormals;

            vec3 pointLightReflectDir = reflect(-pointLightDir, normals);
            pointLightsSpecularOut += pointLightColor * specular(viewDir, pointLightReflectDir, vSpecularGloss, vSpecularStrength);
        }
    }


    // sky light
    vec3 skyLightColor = fogColor.rgb;
    float skyLightStrength = 0.0;
    float skyDotNormals = downDotNormals;
    vec3 skyLightOut = max(skyDotNormals, 0.0) * skyLightColor * skyLightStrength;


    // lightning
    vec3 lightningColor = vec3(1.0, 1.0, 1.0);
    float lightningStrength = lightningBrightness;
    float lightningDotNormals = downDotNormals;
    vec3 lightningOut = max(lightningDotNormals, 0.0) * lightningColor * lightningStrength;


    // underglow
    vec3 underglowOut = underglowColor * max(normals.y, 0) * underglowStrength;


    // fresnel reflection
    float baseOpacity = 0.4;
    float fresnel = 1.0 - clamp(viewDotNormals, 0.0, 1.0);
    vec3 surfaceColor = vec3(0);

    // add sky gradient
    if (fresnel < 0.5) {
        surfaceColor = mix(waterColorDark, waterColorMid, fresnel * 2);
    } else {
        vec3 I = viewDir; // incident
        vec3 N = normals.xyz; // normal

        // TODO: use actual viewport size here
        ivec2 screenSize = textureSize(waterReflectionMap, 0);
        vec2 uv = gl_FragCoord.xy / vec2(screenSize);
        uv.y = 1 - uv.y;
        vec3 norm1 = n1 * 2 - 1;
        vec3 norm2 = n2 * 2 - 1;
        vec3 distortion = normalize((norm1 - norm2) * waterType.normalStrength);
        uv += distortion.xz / 1000;
        uv = clamp(uv, 0, 1);

        vec3 c = waterColorLight;

        if (waterReflectionEnabled && distance(waterHeight, IN.position.y) < 48)
            c = texture(waterReflectionMap, uv).rgb;

        surfaceColor = mix(waterColorMid, c, (fresnel - 0.5) * 2);

        shadow *= (1 - fresnel);
        inverseShadow = (1 - shadow);  // this does nothing? the reference to this that used to be after is now gone :(
    }

    vec3 surfaceColorOut = surfaceColor * max(combinedSpecularStrength, 0.2);


    // apply lighting
    vec3 compositeLight = ambientLightOut + lightOut + lightSpecularOut + skyLightOut + lightningOut +
    underglowOut + pointLightsOut + pointLightsSpecularOut + surfaceColorOut;

    vec3 baseColor = waterType.surfaceColor * compositeLight;
    baseColor = mix(baseColor, surfaceColor, waterType.fresnelAmount);
    float shoreLineMask = 1 - dot(IN.texBlend, vec3(vColor[0].x, vColor[1].x, vColor[2].x));
    float maxFoamAmount = 0.0;
    float foamAmount = min(shoreLineMask, maxFoamAmount);
    float foamDistance = 0.7;
    vec3 foamColor = waterType.foamColor;
    foamColor = foamColor * foamMask * compositeLight;
    foamAmount = clamp(pow(1.0 - ((1.0 - foamAmount) / foamDistance), 3), 0.0, 1.0) * waterType.hasFoam;
    foamAmount *= foamColor.r;
    baseColor = mix(baseColor, foamColor, foamAmount);
    vec3 specularComposite = mix(lightSpecularOut, vec3(0.0), foamAmount);
    float flatFresnel = (1.0 - dot(viewDir, vec3(0, -1, 0))) * 1.0;
    fresnel = max(fresnel, flatFresnel);
    fresnel -= fresnel * shadow * 0.2;
    baseColor += pointLightsSpecularOut + lightSpecularOut / 3;

    float alpha = max(waterType.baseOpacity, max(foamAmount, max(fresnel, length(specularComposite / 3))));

    if (waterType.isFlat) {
        baseColor = mix(waterType.depthColor, baseColor, alpha);
        alpha = 1;
    }

    return vec4(baseColor, alpha);
}

void sampleUnderwater(inout vec3 outputColor, WaterType waterType, float depth, float lightDotNormals) {
    // underwater terrain
    float lowestColorLevel = 500;
    float midColorLevel = 150;
    float surfaceLevel = IN.position.y - depth; // e.g. -1600

    if (depth < midColorLevel) {
        outputColor *= mix(vec3(1), waterType.depthColor, translateRange(0, midColorLevel, depth));
    } else if (depth < lowestColorLevel) {
        outputColor *= mix(waterType.depthColor, vec3(0), translateRange(midColorLevel, lowestColorLevel, depth));
    } else {
        outputColor = vec3(0);
    }

    if (shorelineCaustics) {
        const float scale = 1.75;
        const float maxCausticsDepth = 128 * 4;

        vec2 causticsUv = worldUvs(scale);

        float depthMultiplier = (IN.position.y - surfaceLevel - maxCausticsDepth) / -maxCausticsDepth;
        depthMultiplier *= depthMultiplier;

        causticsUv *= .75;

        const ivec2 direction = ivec2(1, -2);
        vec2 flow1 = causticsUv + animationFrame(17) * direction;
        vec2 flow2 = causticsUv * 1.5 + animationFrame(23) * -direction;
        vec3 caustics = sampleCaustics(flow1, flow2, .005);

        vec3 causticsColor = underwaterCausticsColor * underwaterCausticsStrength;
        // This also got multiplied by (1 - fresnel) from sampleWater in Aeryn's old branch, but that isn't accessible here
        outputColor.rgb *= 1 + caustics * causticsColor * depthMultiplier * lightDotNormals * lightStrength;
    }
}

#else

float calculateFresnel(const vec3 I, const vec3 N, const float ior) {
    float cosi = dot(I, N);
    float etai = ior, etat = 1;
    if (cosi < 0) {
        etai = 1;
        etat = ior;
        return 1; // hide artifacts
    }

    float R0 = (etai - etat) / (etai + etat);
    R0 *= R0;
    return R0 + (1 - R0) * pow(1 - cosi, 5);
}

void sampleUnderwater(inout vec3 outputColor, WaterType waterType, float depth, float lightDotNormals);

vec4 sampleWater(int waterTypeIndex, vec3 viewDir) {

    waterTypeIndex = 13; // DEVELOPMENT OVERRIDE - ALSO SET IN SAMPLEUNDERWATER //TODO look here for water
    // 1 = water
    // 2 = flat water
    // 3 = swamp water
    // 4 = swamp water flat
    // 5 = poison waste
    // 6 = black tar flat
    // 7 = blood water
    // 8 = ice
    // 9 = ice flat
    // 10 = muddy water
    // 11 = scar sludge
    // 12 = abyss bile
    // 13 = plain flat water --- #2 is color-matched to model-water in caves etc, while this one isn't

    WaterType waterType = getWaterType(waterTypeIndex);

//    vec2 baseUv = vUv[0].xy * IN.texBlend.x + vUv[1].xy * IN.texBlend.y + vUv[2].xy * IN.texBlend.z;
//    vec2 uv3 = baseUv;
//
//    vec2 uv2 = worldUvs(3) + animationFrame(24 * waterType.duration);
//    vec2 uv1 = worldUvs(3).yx - animationFrame(28 * waterType.duration);
//
//    vec2 flowMapUv = worldUvs(15) + animationFrame(50 * waterType.duration);
//    float flowMapStrength = 0.025;
//
//    vec2 uvFlow = texture(textureArray, vec3(flowMapUv, waterType.flowMap)).xy;
//    uv1 += uvFlow * flowMapStrength;
//    uv2 += uvFlow * flowMapStrength;
//    uv3 += uvFlow * flowMapStrength;
//
//    // get diffuse textures
//    vec3 diffuse1 = texture(textureArray, vec3(uv1, waterType.normalMap)).xyz;
//    vec3 diffuse2 = texture(textureArray, vec3(uv2, waterType.normalMap)).xyz;
//    float foamMask = texture(textureArray, vec3(uv3, waterType.foamMap)).r;
//
//    // normals
//    vec3 n1 = -vec3((diffuse1.x * 2 - 1) * waterType.normalStrength, diffuse1.z, (diffuse1.y * 2 - 1) * waterType.normalStrength);
//    vec3 n2 = -vec3((diffuse2.x * 2 - 1) * waterType.normalStrength, diffuse2.z, (diffuse2.y * 2 - 1) * waterType.normalStrength);
//    vec3 normals = normalize(n1 + n2);
//
//    float lightDotNormals = dot(normals, lightDir);
//    float downDotNormals = -normals.y;
//    float viewDotNormals = dot(viewDir, normals);
//
//    vec2 distortion = uvFlow * .00075;
//    float shadow = sampleShadowMap(IN.position, waterTypeIndex, distortion, lightDotNormals);
//    float inverseShadow = 1 - shadow;
//
//    vec3 vSpecularStrength = vec3(waterType.specularStrength);
//    vec3 vSpecularGloss = vec3(waterType.specularGloss);
//    float combinedSpecularStrength = waterType.specularStrength;
//
//    // calculate lighting
//
//    // ambient light
//    vec3 ambientLightOut = ambientColor * ambientStrength;
//
//    // directional light
//    vec3 dirLightColor = lightColor * lightStrength;
//
//    // apply shadows
//    dirLightColor *= inverseShadow;
//
//    vec3 lightColor = dirLightColor;
//    vec3 lightOut = max(lightDotNormals, 0.0) * lightColor;
//
//    // directional light specular
//    vec3 lightReflectDir = reflect(-lightDir, normals);
//    vec3 lightSpecularOut = lightColor * specular(viewDir, lightReflectDir, vSpecularGloss, vSpecularStrength);
//
//    // point lights
//    vec3 pointLightsOut = vec3(0);
//    vec3 pointLightsSpecularOut = vec3(0);
//    for (int i = 0; i < pointLightsCount; i++) {
//        vec4 pos = PointLightArray[i].position;
//        vec3 lightToFrag = pos.xyz - IN.position;
//        float distanceSquared = dot(lightToFrag, lightToFrag);
//        float radiusSquared = pos.w;
//        if (distanceSquared <= radiusSquared) {
//            vec3 pointLightColor = PointLightArray[i].color;
//            vec3 pointLightDir = normalize(lightToFrag);
//
//            float attenuation = 1 - min(distanceSquared / radiusSquared, 1);
//            pointLightColor *= attenuation * attenuation;
//
//            float pointLightDotNormals = max(dot(normals, pointLightDir), 0);
//            pointLightsOut += pointLightColor * pointLightDotNormals;
//
//            vec3 pointLightReflectDir = reflect(-pointLightDir, normals);
//            pointLightsSpecularOut += pointLightColor * specular(viewDir, pointLightReflectDir, vSpecularGloss, vSpecularStrength);
//        }
//    }
//
//
//    // sky light
//    vec3 skyLightColor = fogColor.rgb;
//    float skyLightStrength = 0;
//    float skyDotNormals = downDotNormals;
//    vec3 skyLightOut = max(skyDotNormals, 0.0) * skyLightColor * skyLightStrength;
//
//
//    // lightning
//    vec3 lightningColor = vec3(1.0, 1.0, 1.0);
//    float lightningStrength = lightningBrightness;
//    float lightningDotNormals = downDotNormals;
//    vec3 lightningOut = max(lightningDotNormals, 0.0) * lightningColor * lightningStrength;
//
//
//    // underglow
//    vec3 underglowOut = underglowColor * max(normals.y, 0) * underglowStrength;


    float speed = .024;
    if(waterTypeIndex == 8 || waterTypeIndex == 9) // ice
    {
        speed = 0.00000001; // 0 speed bugs out the normals code and prevents rendering, glacier speed is fine
    }
    else
    speed = 0.024;
    float waveSizeConfig = waterWaveSizeConfig / 100.f;
    float waveSpeedConfig = waterWaveSpeedConfig / 100.f;
    speed *= waveSpeedConfig;
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
    if(waterTypeIndex == 6 || waterTypeIndex == 8 || waterTypeIndex == 9 || waterTypeIndex == 12) // black tar, ice, ice flat, abyss bile
    {
        n1.y /= 0.3;
    }
    n1.y /= waveSizeConfig;
    n1 = normalize(n1);
    n2.y /= 0.8; // scale normals
    if(waterTypeIndex == 6 || waterTypeIndex == 8 || waterTypeIndex == 9 || waterTypeIndex == 12) // black tar, ice, ice flat, abyss bile
    {
        n2.y /= 0.3;
    }
    n2.y /= waveSizeConfig;
    n2 = normalize(n2);
    vec3 normals = normalize(n1+n2);
    normals = normalize(vec3(n1.xy + n2.xy, n1.z + n2.z));
    vec3 normalScatter = normals;

    vec3 fragToCam = viewDir;

    // fresnel for fake sky reflection and real planar reflection
    float fresnel = calculateFresnel(normals, fragToCam, 1.333);

    vec3 c = srgbToLinear(fogColor);
    vec4 d = vec4(0);

    if (waterReflectionEnabled && abs(IN.position.y - waterHeight) < 32) { //only render reflection on water within a quarter-tile height of correct for the reflection texture
        vec3 I = -viewDir; // incident
        vec3 N = normals; // normal
        vec3 R = reflect(I, N);

        // for now, assume the water is level
        vec3 flatR = reflect(I, vec3(0, -1, 0));

        // TODO: use actual viewport size here
        vec2 screenSize = vec2(textureSize(waterReflectionMap, 0));
        screenSize /= PLANAR_REFLECTION_RESOLUTION;
        vec2 texelSize = 1 / screenSize;
        vec2 uv = gl_FragCoord.xy / screenSize;
        uv.y = 1 - uv.y;

        vec3 flatRxz = normalize(flatR - vec3(0, flatR.y, 0));
        vec3 uvY = normalize(flatR - flatRxz);
        vec3 uvX = normalize(cross(flatR, uvY));

        float dist = length(IN.position - cameraPos);
        float distortionFactor = 1 - exp(-dist * .0004);

        float x = dot(R, uvX);
        float y = dot(R, uvY);
        int waterDepth = vTerrainData[0] >> 8 & 0x7FF;
        vec2 distortion = vec2(x, y) * 50 * distortionFactor;
        // TODO: Don't distort too close to the shore
        float shoreLineMask = 1.0 - dot(IN.texBlend, vec3(vColor[0].x, vColor[1].x, vColor[2].x));
        distortion *= 1.4 - (shoreLineMask *1.54); // safety factor to remove artifacts
        uv += texelSize * distortion;

        uv = clamp(uv, texelSize, 1 - texelSize);

        // This will be linear or sRGB depending on the linear alpha blending setting
        c = texture(waterReflectionMap, uv, -1).rgb;
        #if !LINEAR_ALPHA_BLENDING
        // When linear alpha blending is on, the texture is in sRGB, and OpenGL will automatically convert it to linear
        c = srgbToLinear(c);
        #endif
    }

    // ALWAYS RETURN IN sRGB FROM THIS FUNCTION (been burned by this a couple times)

    float alpha = fresnel;
    vec3 foam = vec3(0);

    #include WATER_FOAM
    #if WATER_FOAM
        vec2 flowMapUv = worldUvs(15) + animationFrame(50 * waterType.duration);
        float flowMapStrength = 0.025;
        vec2 uvFlow = texture(textureArray, vec3(flowMapUv, waterType.flowMap)).xy;
        vec2 uv3 = vUv[0].xy * IN.texBlend.x + vUv[1].xy * IN.texBlend.y + vUv[2].xy * IN.texBlend.z + uvFlow * flowMapStrength;
        float foamMask = texture(textureArray, vec3(uv3, waterType.foamMap)).r;
        float foamAmount = 1 - dot(IN.texBlend, vec3(vColor[0].x, vColor[1].x, vColor[2].x));
        float foamDistance = 1;
        vec3 foamColor = waterType.foamColor;
        if(waterTypeIndex == 13)
        {
            foamColor = vec3(0.5);
        }
        foamColor = srgbToLinear(foamColor) * foamMask * (ambientColor * ambientStrength + lightColor * lightStrength);
        foamAmount = clamp(pow(1.0 - ((1.0 - foamAmount) / foamDistance), 3), 0.0, 1.0) * waterType.hasFoam;
        foamAmount *= 0.08;
        foam.rgb = foamColor * foamAmount * (1 - foamAmount) * (waterFoamAmountConfig /100.f);
        alpha = foamAmount + alpha * (1 - foamAmount);

        if(waterTypeIndex == 3 || waterTypeIndex == 4) // swamp water + swamp water flat
        {
            foam.rgb *= vec3(1.3, 1.3, 0.4);
        }

        if(waterTypeIndex == 5) // toxic waste
        {
            foam.rgb *= vec3(0.7, 0.7, 0.7);
        }

        if(waterTypeIndex == 6) // black tar
        {
            foam.rgb *= vec3(1.0, 1.0, 1.0);
        }

        if(waterTypeIndex == 7) // blood
        {
            foam.rgb *= vec3(1.6, 0.7, 0.7);
        }

        if(waterTypeIndex == 8) // ice
        {
            foam.rgb *= vec3(0.5, 0.5, 0.5);
        }

        if(waterTypeIndex == 9) // ice flat
        {
            foam.rgb *= vec3(0.5, 0.5, 0.5);
        }

        if(waterTypeIndex == 10) // muddy water
        {
            foam.rgb *= vec3(1.0, 0.5, 0.5);
        }

        if(waterTypeIndex == 11) // scar sludge
        {
            foam.rgb *= vec3(0.9, 1.2, 0.9);
        }

        if(waterTypeIndex == 12) // abyss bile
        {
            foam.rgb *= vec3(1.0, 0.7, 0.3);
        }

        if(waterTypeIndex == 13) // plain water flat
        {
            foam.rgb *= vec3(1);
        }

    #endif

    #include WATER_LIGHT_SCATTERING
    #if WATER_LIGHT_SCATTERING
        if(waterTypeIndex == 1 || waterTypeIndex == 2 || waterTypeIndex == 3 || waterTypeIndex == 4 || waterTypeIndex == 5 || waterTypeIndex == 13)
        {

            vec3 waterTypeExtinction = vec3(1);

            if(waterTypeIndex == 1 || waterTypeIndex == 2 || waterTypeIndex == 13)
            {
                waterTypeExtinction = vec3(1);
            }

            if(waterTypeIndex == 3 || waterTypeIndex == 4 || waterTypeIndex == 5)
            {
                waterTypeExtinction = vec3(2, 2, 2); // Light absorption for swamp water and toxic waste
            }

            if(waterTypeIndex == 7)
            {
                waterTypeExtinction = vec3(0.6, 30, 30); // Light absorption for blood
            }

            if(waterTypeIndex == 10)
            {
                waterTypeExtinction = vec3(2, 2, 5); // Light absorption for muddy water
            }

            if(waterTypeIndex == 11)
            {
                waterTypeExtinction = vec3(2, 2, 2); // Light absorption for scar sludge
            }
            if(waterTypeIndex == 12)
            {
                waterTypeExtinction = vec3(1, 1, 1); // Light absorption for abyss bile
            }

            float scatterStrength = 3;
            vec3 scatterExtinction = vec3(0);
            float scatterDepth = 128 * 2 * 1;
            scatterExtinction.r = exp(-scatterDepth * 0.003090 * waterTypeExtinction.r);
            scatterExtinction.g = exp(-scatterDepth * 0.001981 * waterTypeExtinction.g);
            scatterExtinction.b = exp(-scatterDepth * 0.001548 * waterTypeExtinction.b);

            //return vec4(scatterExtinction, 1);

            float waveStrength = -normalScatter.y;
            waveStrength = 1 - waveStrength;
            waveStrength = pow(waveStrength, 1 / 1.4f);
            waveStrength *=0.3;
            //return vec4(vec3(waveStrength), 1);

            d.r = (scatterStrength * scatterExtinction.r * waveStrength);
            d.g = (scatterStrength * scatterExtinction.g * waveStrength);
            d.b = (scatterStrength * scatterExtinction.b * waveStrength);
        }
    #endif

    vec4 reflection = vec4(c, fresnel);
    vec4 scattering = vec4(d.rgb, 0.5);

    vec4 dst = vec4(0);

    if (waterTransparencyType == 1 || waterTypeIndex == 2 || waterTypeIndex == 4 || waterTypeIndex == 6 || waterTypeIndex == 9 || waterTypeIndex == 13) // Opaque setting or flat water
    {
        if(waterTypeIndex == 2) // Flat cave water
        {
            vec3 caveWaterFlatSrgb = vec3(0.22, 0.51, 0.6);
            c = 0 + (srgbToLinear(caveWaterFlatSrgb) * (1 - alpha));
            alpha = 1;
            dst = vec4(c.rgb, alpha);
            reflection.rgb *= 1;
            dst = reflection * reflection.a + dst * (1 - reflection.a); // blend in reflection
            dst += vec4(foam, 0); // add foam on top
            dst.rgb = linearToSrgb(dst.rgb);
            return vec4(dst.rgb, 1); // cave water flat
        }

        if(waterTypeIndex == 3 || waterTypeIndex == 4) // Swamp Water Flat
        {
            vec3 swampFlatSrgb = vec3(0.275, 0.275, 0.1375);
            c = 0 + (srgbToLinear(swampFlatSrgb) * (1 - alpha));
            alpha = 1;
            dst = vec4(c.rgb, alpha);
            reflection.rgb *= 0.25;
            dst = reflection * reflection.a + dst * (1 - reflection.a); // blend in reflection
            dst += vec4(foam, 0); // add foam on top
            dst.rgb = linearToSrgb(dst.rgb);
            return vec4(dst.rgb, 1); // swamp water flat
        }

        if(waterTypeIndex == 5) // toxic waste flat
        {
            vec3 wasteFlatSrgb = vec3(0.2, 0.2, 0.2);
            c = 0 + (srgbToLinear(wasteFlatSrgb) * (1 - alpha));
            alpha = 1;
            dst = vec4(c.rgb, alpha);
            reflection.rgb *= 0.3;
            dst = reflection * reflection.a + dst * (1 - reflection.a); // blend in reflection
            dst += vec4(foam, 0); // add foam on top
            dst.rgb = linearToSrgb(dst.rgb);
            return vec4(dst.rgb, 1); // black tar flat
        }

        if(waterTypeIndex == 6) // black tar flat
        {
            vec3 tarFlatSrgb = vec3(0, 0, 0);
            c = 0 + (srgbToLinear(tarFlatSrgb) * (1 - alpha));
            alpha = 1;
            dst = vec4(c.rgb, alpha);
            reflection.rgb *= 0.3;
            dst = reflection * reflection.a + dst * (1 - reflection.a); // blend in reflection
            dst += vec4(foam, 0); // add foam on top
            dst.rgb = linearToSrgb(dst.rgb);
            return vec4(dst.rgb, 1); // black tar flat
        }

        if(waterTypeIndex == 7) // Blood flat
        {
            vec3 bloodSrgb = vec3(0.3, 0, 0);
            c = 0 + (srgbToLinear(bloodSrgb) * (1 - alpha));
            alpha = 1;
            dst = vec4(c.rgb, alpha);
            reflection.rgb *= 0.75;
            dst = reflection * reflection.a + dst * (1 - reflection.a); // blend in reflection
            dst += vec4(foam, 0); // add foam on top
            dst.rgb = linearToSrgb(dst.rgb);
            return vec4(dst.rgb, 1); // blood flat water
        }

        if(waterTypeIndex == 8 || waterTypeIndex == 9) // Ice flat
        {
            vec3 iceFlatSrgb = vec3(0.25, 0.25, 0.28);
            c = 0 + (srgbToLinear(iceFlatSrgb) * (1 - alpha));
            alpha = 1;
            dst = vec4(c.rgb, alpha);
            reflection.rgb *= 0.8;
            dst = reflection * reflection.a + dst * (1 - reflection.a); // blend in reflection
            dst += vec4(foam, 0); // add foam on top
            dst.rgb = linearToSrgb(dst.rgb);
            return vec4(dst.rgb, 1); // flat ice
        }

        if(waterTypeIndex == 10) // Muddy water flat
        {
            vec3 mudFlatSrgb = vec3(0.28, 0.18, 0);
            c = 0 + (srgbToLinear(mudFlatSrgb) * (1 - alpha));
            alpha = 1;
            dst = vec4(c.rgb, alpha);
            reflection.rgb *= 0.4;
            dst = reflection * reflection.a + dst * (1 - reflection.a); // blend in reflection
            dst += vec4(foam, 0); // add foam on top
            dst.rgb = linearToSrgb(dst.rgb);
            return vec4(dst.rgb, 1); // flat mud
        }

        if(waterTypeIndex == 11) // Scar Sludge flat
        {
            vec3 sludgeFlatSrgb = vec3(0.45, 0.49, 0.43);
            c = 0 + (srgbToLinear(sludgeFlatSrgb) * (1 - alpha));
            alpha = 1;
            dst = vec4(c.rgb, alpha);
            reflection.rgb *= 0.9;
            dst = reflection * reflection.a + dst * (1 - reflection.a); // blend in reflection
            dst += vec4(foam, 0); // add foam on top
            dst.rgb = linearToSrgb(dst.rgb);
            return vec4(dst.rgb, 1); // flat scar sludge
        }

        if(waterTypeIndex == 12) // abyss bile flat
        {
            vec3 abyssBileFlatSrgb = vec3(0.52, 0.43, 0.18);
            c = 0 + (srgbToLinear(abyssBileFlatSrgb) * (1 - alpha));
            alpha = 1;
            dst = vec4(c.rgb, alpha);
            reflection.rgb *= 0.9;
            dst = reflection * reflection.a + dst * (1 - reflection.a); // blend in reflection
            dst += vec4(foam, 0); // add foam on top
            dst.rgb = linearToSrgb(dst.rgb);
            return vec4(dst.rgb, 1); // flat abyss bile
        }

        if(waterTypeIndex == 13 || waterTypeIndex == 1) // plain water flat
        {
            vec3 plainWaterFlatSrgb = vec3(0.05, 0.1, 0.1);
            c = 0 + (srgbToLinear(plainWaterFlatSrgb) * (1 - alpha));
            alpha = 1;
            dst = vec4(c.rgb, alpha);
            reflection.rgb *= 1;
            dst = reflection * reflection.a + dst * (1 - reflection.a); // blend in reflection
            dst += vec4(foam, 0); // add foam on top
            dst.rgb = linearToSrgb(dst.rgb);
            return vec4(dst.rgb, 1); // flat plain water
        }

        float flatWaterTileDepth = 3;
        float depth = 128 * 2 * flatWaterTileDepth; // tile depth, *2 for round trip, * for number of tiles
        vec3 underwaterExtinction = vec3(0);
        underwaterExtinction.r = exp(-depth * 0.003090);
        underwaterExtinction.g = exp(-depth * 0.001981);
        underwaterExtinction.b = exp(-depth * 0.001548);

        vec3 underwaterLinear = vec3(lightStrength) * 0.1 * underwaterExtinction;
        vec3 underwaterSrgb = linearToSrgb(underwaterLinear);

        sampleUnderwater(underwaterSrgb, waterType, depth, dot(lightDir, normals));
        c = c * alpha + srgbToLinear(underwaterSrgb) * (1 - alpha);
        alpha = 1;

        dst = scattering * scattering.a + vec4(c.rgb, alpha) * (1 - scattering.a); // blend in scattering
        dst = reflection * reflection.a + dst * (1 - reflection.a); // blend in reflection
        dst += vec4(foam, 0); // add foam on top

        dst.rgb = linearToSrgb(dst.rgb);
        return vec4(dst.rgb, 1); // flat water
    }

    if(waterTypeIndex == 3) // swamp water
    {
        reflection.rgb *= 0.3; // dim reflection
        dst.rgb += vec3(0.1, 0.1, 0.05); // inject color

    }

    if(waterTypeIndex == 5) // toxic waste
    {
        reflection.rgb *= 0.3; // dim reflection
        dst.rgb += vec3(0.05, 0.05, 0.05); // inject color

    }

     if(waterTypeIndex == 7) // blood
    {
        reflection.rgb *= 0.75; // dim reflection
        dst.rgb += vec3(0.16, 0, 0); // inject color

    }

    if(waterTypeIndex == 8) // ice
    {
        reflection.rgb *= 0.8; // dim reflection
        dst.rgb += vec3(0.07, 0.07, 0.1); // inject color
    }

    if(waterTypeIndex == 10) // muddy water
    {
        reflection.rgb *= 0.4; // dim reflection
        dst.rgb += vec3(0.13, 0.06, 0); // inject color
    }

    if(waterTypeIndex == 11) // scar sludge
    {
        reflection.rgb *= 0.9;
        dst.rgb += vec3(0.3, 0.37, 0.3); // inject color
    }

    if(waterTypeIndex == 12) // abyss bile
    {
        reflection.rgb *= 0.9;
        dst.rgb += vec3(0.42, 0.29, 0.075); // inject color
    }


    dst = scattering * scattering.a + dst * (1 - scattering.a); // blend in scattering
    dst = reflection * reflection.a + dst * (1 - reflection.a); // blend in reflection
    foam.rgb *= 1.5; // foam otherwise looks disproportionately weak on transparent water due to reduced alpha

    dst += vec4(foam, 0); // add foam on top

    dst.rgb /= (dst.a);
    dst.rgb = linearToSrgb(dst.rgb);
    return dst; // transparent water
}

void sampleUnderwater(inout vec3 outputColor, WaterType waterType, float depth, float lightDotNormals) {
    // underwater terrain
    outputColor = srgbToLinear(outputColor);
    outputColor.r *=0.7; // dirt texture looks unnaturally dry/bright/red in shallow water, remove some before further blending

    vec3 camToFrag = normalize(IN.position - cameraPos);
    float distanceToSurface = depth / camToFrag.y;
    float totalDistance = depth + distanceToSurface;
    int waterTypeIndex = vTerrainData[0] >> 3 & 0x1F;

    //TODO water types are here
    waterTypeIndex = 13; // DEVELOPMENT OVERRIDE - ALSO SET IN SAMPLEWATER
    // 1 = water
    // 2 = flat water
    // 3 = swamp water
    // 4 = swamp water flat
    // 5 = poison waste
    // 6 = black tar flat
    // 7 = blood water
    // 8 = ice
    // 9 = ice flat
    // 10 = muddy water
    // 11 = scar sludge
    // 12 = abyss bile
    // 13 = plain flat water --- #2 is color-matched to model-water in caves etc, while this one isn't

    float lightPenetration = 0.5 + (waterTransparencyConfig / 44.444); // Scale from a range of 0% = 0.5, 100% = 2.75, 130% = 3.425

    // Exponential falloff of light intensity when penetrating water, different for each color
    vec3 extinctionColors = vec3(0);
    vec3 waterTypeExtinction = vec3(0);

    if(waterTypeIndex == 1 || waterTypeIndex == 2 || waterTypeIndex == 8 || waterTypeIndex == 13)
    {
        waterTypeExtinction = vec3(1);
    }

    if(waterTypeIndex == 3 || waterTypeIndex == 4)
    {
        waterTypeExtinction = vec3(2, 2, 2); // Light absorption for swamp water
    }

    if(waterTypeIndex == 5)
    {
        waterTypeExtinction = vec3(3, 3, 3); // Light absorption for toxic waste
    }

    if(waterTypeIndex == 7)
    {
        waterTypeExtinction = vec3(0.6, 30, 30); // Light absorption for blood
    }

    if(waterTypeIndex == 10)
    {
        waterTypeExtinction = vec3(1.5, 3, 6); // Light absorption for muddy water
    }

    if(waterTypeIndex == 11)
    {
        waterTypeExtinction = vec3(0.75, 1.125, 1.5); // Light absorption for scar sludge
    }

    if(waterTypeIndex == 12)
    {
        waterTypeExtinction = vec3(1, 1, 1); // Light absorption for abyss bile
    }

    extinctionColors.r = exp(-totalDistance * (0.003090 / lightPenetration) * waterTypeExtinction.r);
    extinctionColors.g = exp(-totalDistance * (0.001981 / lightPenetration) * waterTypeExtinction.g);
    extinctionColors.b = exp(-totalDistance * (0.001548 / lightPenetration) * waterTypeExtinction.b);

    if (underwaterCaustics && (waterTransparencyType ==0 || depth <=500)) {
        const float scale = 2.5;
        vec2 causticsUv = worldUvs(scale);
        causticsUv *= 0.75;
        const ivec2 direction = ivec2(1, -2);
        vec2 flow1 = causticsUv + animationFrame(17) * direction;
        vec2 flow2 = causticsUv * 1.5 + animationFrame(23) * -direction;
        vec3 caustics = sampleCaustics(flow1, flow2, .005);
        vec3 causticsColor = underwaterCausticsColor * underwaterCausticsStrength;
        if(waterTransparencyType ==1 && depth <=500) // reduce caustics brightness for shallow opaque water
        {
            causticsColor *= 0.5;
        }
        if(waterTypeIndex == 8 || waterTypeIndex == 9) // ice
        {
            causticsColor *= 0;
        }
        outputColor *= 1 + caustics * causticsColor * extinctionColors * lightDotNormals * lightStrength * (waterCausticsStrengthConfig / 100.f);
    }

    outputColor = mix(vec3(0), outputColor, extinctionColors);
    outputColor = linearToSrgb(outputColor);
}
#endif