/*
 * Copyright (c) 2024, Aeryn <https://github.com/xxEzri>
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
#include utils/misc.glsl
#include utils/water_reflection.glsl
#include utils/texture_bicubic.glsl

#define WATER_TYPE_NONE              0
#define WATER_TYPE_WATER             1
#define WATER_TYPE_WATER_FLAT        2
#define WATER_TYPE_SWAMP_WATER       3
#define WATER_TYPE_SWAMP_WATER_FLAT  4
#define WATER_TYPE_POISON_WASTE      5
#define WATER_TYPE_BLACK_TAR_FLAT    6
#define WATER_TYPE_BLOOD             7
#define WATER_TYPE_ICE               8
#define WATER_TYPE_ICE_FLAT          9
#define WATER_TYPE_MUDDY_WATER      10
#define WATER_TYPE_SCAR_SLUDGE      11
#define WATER_TYPE_ABYSS_BILE       12
#define WATER_TYPE_PLAIN_WATER      13 // #2 is color-matched to model-water in caves etc, while this one isn't
#define WATER_TYPE_DARK_BLUE_WATER  14
#define WATER_TYPE_CYAN_WATER  15

// DEVELOPMENT OVERRIDE
//#define DEVELOPMENT_WATER_TYPE WATER_TYPE_BLOOD

// TEMPORARY DEV
#include WATER_STYLE
#define WATER_STYLE_AERYN 0
#define WATER_STYLE_HOODER 1
#define WATER_STYLE_LEGACY 2

#if WATER_STYLE == WATER_STYLE_AERYN
    #undef LEGACY_WATER
    #define LEGACY_WATER 0
#elif WATER_STYLE == WATER_STYLE_HOODER
    #define LEGACY_WATER 0
    #define HOODER_WATER
#elif WATER_STYLE == WATER_STYLE_LEGACY && LEGACY_WATER == 0
    #undef LEGACY_WATER
    #define LEGACY_WATER 1
#endif
// TEMPORARY DEV

#if LEGACY_WATER > 0
#include utils/legacy_water.glsl
#elif defined HOODER_WATER
#include utils/hooder_water.glsl
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

void sampleUnderwater(inout vec3 outputColor, int waterTypeIndex, float depth);

vec4 sampleWater(int waterTypeIndex, vec3 viewDir)
{
    WaterType waterType = getWaterType(waterTypeIndex);

    // VARIABLES
    bool isOpaque = !waterTransparency || waterType.isFlat;
    vec3 fragToCam = viewDir;
    vec4 d = vec4(0);
    vec3 I = -viewDir; // incident




















    // NORMALS STUFF
    float speed = .024;
    if(waterTypeIndex == 8 || waterTypeIndex == 9) // ice
        speed = 0.00000001; // 0 speed bugs out the normals code, glacier speed is fine
    speed *= waterWaveSpeed;
    vec2 uv1 = worldUvs(26) - animationFrame(sqrt(11.) / speed * waterType.duration / vec2(-1, 4));
    vec2 uv2 = worldUvs(6) - animationFrame(sqrt(3.) / speed * waterType.duration * 1.5 /vec2(2, -1));

    // Flip UVs horizontally, since our water normal maps aren't oriented like vanilla textures
    uv1.x = 1 - uv1.x;
    uv2.x = 1 - uv2.x;
    vec3 n1 = textureBicubic(waterNormalMaps, vec3(uv1, 0)).xyz;
    vec3 n2 = textureBicubic(waterNormalMaps, vec3(uv2, 1)).xyz;

    // Normalize
    n1.xy = (n1.xy * 2 - 1);
    n2.xy = (n2.xy * 2 - 1);
    // Tangent space to world
    n1.z *= -1;
    n2.z *= -1;
    n1.xyz = n1.xzy;
    n2.xyz = n2.xzy;
    n1.y /= 0.225; // scale normals

    //black tar
    if(waterTypeIndex == WATER_TYPE_BLACK_TAR_FLAT)
    {
        n1.y /= 0.1;
    }

    //ice, ice flat, abyss bile
    if(waterTypeIndex == 8 || waterTypeIndex == 9 || waterTypeIndex == 12)
    {
        n1.y /= 0.3;
    }

    n1.y /= waterWaveSize;
    n1 = normalize(n1);
    n2.y /= 0.8; // scale normals

    //black tar
    if(waterTypeIndex == WATER_TYPE_BLACK_TAR_FLAT)
    {
        n2.y /= 0.1;
    }

    //ice, ice flat, abyss bile
    if( waterTypeIndex == 8 || waterTypeIndex == 9 || waterTypeIndex == 12)
    {
        n2.y /= 0.3;
    }

    n2.y /= waterWaveSize;
    n2 = normalize(n2);
    vec3 N = normalize(n1 + n2);
    N = normalize(vec3(n1.xy + n2.xy, n1.z + n2.z));















    // REFLECTIONS STUFF
    // Assume the water is level
    vec3 flatR = reflect(I, vec3(0, -1, 0));
    vec3 R = reflect(I, N);
    float distortionFactor = 50;

    // Initialize the reflection with a fake sky reflection
    vec4 reflection = vec4(
        sampleWaterReflection(flatR, R, distortionFactor),
        calculateFresnel(fragToCam, N, IOR_WATER)
    );












    // FOAM STUFF
    vec4 foam = vec4(0);
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
        foam = vec4(foamColor, foamAmount);

        switch (waterTypeIndex) {
            case 1:
            case 13:
            case 14:
            if(isOpaque)
            {
                foam.a *=2;
            }
            break;
            case WATER_TYPE_SWAMP_WATER:
            case WATER_TYPE_SWAMP_WATER_FLAT:
                foam.rgb *= vec3(1.3, 1.3, 0.4);
                foam.a *= .25;
                break;
            case WATER_TYPE_POISON_WASTE:
                foam.rgb *= vec3(0.7, 0.7, 0.7);
                break;
            case WATER_TYPE_BLACK_TAR_FLAT:
                foam.rgb *= vec3(0.4);
                foam.a *= 0.5;
                break;
            case 7: // blood
                foam.rgb *= vec3(1.6, 0.7, 0.7);
                foam.a *= 0.5;
                break;
            case 8: // ice
                foam.rgb *= vec3(0.5, 0.5, 0.5);
                break;
            case 9: // ice flat
                foam.rgb *= vec3(0.5, 0.5, 0.5);
                break;
            case 10: // muddy water
                foam.rgb *= vec3(1.0, 0.5, 0.5);
                break;
            case 11: // scar sludge
                foam.rgb *= vec3(0.9, 1.2, 0.9);
                break;
            case 12: // abyss bile
                foam.rgb *= vec3(1.0, 0.7, 0.3);
                break;
        }
    #endif



















    // SCATTERING STUFF

    vec3 C_ss = vec3(0.06, .28, .32); // water scatter color
    vec3 C_f = vec3(1); // air bubble color
    float k_2 = 0.01; // ~refraction scatter
    float k_3 = 0.008; // ~ambient scatter
    float k_4 = 0.1;  // ~air bubble scatter
    float P_f = .01; // density of air bubbles
    float brightnessFactor = 1;

    switch (waterTypeIndex)
    {
        case 1: // standard water
        case 2: // standard flat water
        case 13:
        case 14:
        if(!isOpaque)
        {
            C_ss = vec3(0.06, .26, .32); // water scatter color
            C_f = vec3(1); // air bubble color
            k_2 = 0.01; // ~refraction scatter
            k_3 = 0.008; // ~ambient scatter
            k_4 = 0.1;  // ~air bubble scatter
            P_f = .01; // density of air bubbles
            brightnessFactor = 1;
            reflection.rgb *= 1;
        }
        else
        {
            C_ss = vec3(0.06, .26, .32); // water scatter color
            C_f = vec3(1); // air bubble color
            k_2 = 0.04; // ~refraction scatter
            k_3 = 0.04; // ~ambient scatter
            k_4 = 0.1;  // ~air bubble scatter
            P_f = .01; // density of air bubbles
            brightnessFactor = 1;
            reflection.rgb *= 1;
        }
        break;


        case 15: // turquiose water, non-flat option which has a higher focus on surface scattering coloration
        {
                if(!waterTransparency)
                {
                    C_ss = vec3(0.026, .45, .8); // water scatter color
                    C_f = vec3(1); // air bubble color
                    k_2 = 0.15; // ~refraction scatter
                    k_3 = 0.2; // ~ambient scatter
                    k_4 = 0.2;  // ~air bubble scatter
                    P_f = .05; // density of air bubbles
                    //brightnessFactor = 25;
                    reflection.rgb *= 8;
                }
                else
                {
                    C_ss = vec3(0.026, .45, .8); // water scatter color
                    C_f = vec3(1); // air bubble color
                    k_2 = 0.15; // ~refraction scatter
                    k_3 = 0.2; // ~ambient scatter
                    k_4 = 0.2;  // ~air bubble scatter
                    P_f = .05; // density of air bubbles
                    brightnessFactor = 0.5;
                    reflection.rgb *= 8;
                }
        }
        break;


        case WATER_TYPE_SWAMP_WATER:
        case WATER_TYPE_SWAMP_WATER_FLAT:
            C_ss = vec3(0, .6792933, 1);
            C_f = vec3(.12060062, .25193095, .15640968);
            k_2 = .0015;
            k_3 = .0015;
            k_4 = .015;
            P_f = 1;
            break;


        case WATER_TYPE_POISON_WASTE:
            C_ss = vec3(0, .6792933, 1);
            C_f = vec3(.04470426, .05751832, .028336687);
            k_2 = .001;
            k_3 = .002;
            k_4 = .15;
            P_f = 1;
            break;


        case WATER_TYPE_BLACK_TAR_FLAT:
        {
            C_ss = vec3(.0, .0, .0); // water scatter color
            C_f = vec3(1); // air bubble color
            k_2 = 0.01; // ~refraction scatter
            k_3 = 0.008; // ~ambient scatter
            k_4 = 0.1;  // ~air bubble scatter
            P_f = .01; // density of air bubbles
            brightnessFactor = 1;
            reflection.rgb *= 1;
        }
        break;


        case 7: // blood
        {
            C_ss = vec3(0.3, .0, .0); // water scatter color
            C_f = vec3(1); // air bubble color
            k_2 = 0.1; // ~refraction scatter
            k_3 = 0.1; // ~ambient scatter
            k_4 = 0.1;  // ~air bubble scatter
            P_f = .01; // density of air bubbles
            brightnessFactor = 1;
            reflection.rgb *= 1;
        }
        break;


        case 8: // ice
        if(!isOpaque)
        {
            C_ss = vec3(0.42, .5, .5); // water scatter color
            C_f = vec3(1); // air bubble color
            k_2 = 0.01; // ~refraction scatter
            k_3 = 0.02; // ~ambient scatter
            k_4 = 0.1;  // ~air bubble scatter
            P_f = .01; // density of air bubbles
            brightnessFactor = 1;
            reflection.rgb *= 1;
        }
        else
        {
            C_ss = vec3(.35, .5, .6); // water scatter color
            C_f = vec3(1); // air bubble color
            k_2 = 0.15; // ~refraction scatter
            k_3 = 0.15; // ~ambient scatter
            k_4 = 0.1;  // ~air bubble scatter
            P_f = .01; // density of air bubbles
            brightnessFactor = 1;
            reflection.rgb *= 1;
        }
        break;


        case 9: // ice flat
        {
            C_ss = vec3(0.06, .28, .32); // water scatter color
            C_f = vec3(1); // air bubble color
            k_2 = 0.01; // ~refraction scatter
            k_3 = 0.008; // ~ambient scatter
            k_4 = 0.1;  // ~air bubble scatter
            P_f = .01; // density of air bubbles
            brightnessFactor = 1;
            reflection.rgb *= 1;
        }
        break;


        case 10: // muddy water
        if(!isOpaque)
        {
            C_ss = vec3(0.3, .18, .0); // water scatter color
            C_f = vec3(1); // air bubble color
            k_2 = 0.02; // ~refraction scatter
            k_3 = 0.016; // ~ambient scatter
            k_4 = 0.1;  // ~air bubble scatter
            P_f = .01; // density of air bubbles
            brightnessFactor = 1;
            reflection.rgb *= 0.2;
        }
        else
        {
            C_ss = vec3(0.3, .18, .0); // water scatter color
            C_f = vec3(1); // air bubble color
            k_2 = 0.02; // ~refraction scatter
            k_3 = 0.016; // ~ambient scatter
            k_4 = 0.1;  // ~air bubble scatter
            P_f = .01; // density of air bubbles
            brightnessFactor = 1;
            reflection.rgb *= 0.2;
        }
        break;


        case 11: // scar sludge
        if(!isOpaque)
        {
            C_ss = vec3(0.45, .49, .43); // water scatter color
            C_f = vec3(1); // air bubble color
            k_2 = 0.05; // ~refraction scatter
            k_3 = 0.05; // ~ambient scatter
            k_4 = 0.1;  // ~air bubble scatter
            P_f = .01; // density of air bubbles
            brightnessFactor = 1;
            reflection.rgb *= 1;
        }
        else
        {
            C_ss = vec3(0.45, .49, .43); // water scatter color
            C_f = vec3(1); // air bubble color
            k_2 = 0.05; // ~refraction scatter
            k_3 = 0.05; // ~ambient scatter
            k_4 = 0.1;  // ~air bubble scatter
            P_f = .01; // density of air bubbles
            brightnessFactor = 1;
            reflection.rgb *= 1;
        }
        break;


        case 12: // abyss bile flat
        {
            C_ss = vec3(0.68, .975, .48); // water scatter color
            C_f = vec3(1); // air bubble color
            k_2 = 0.02; // ~refraction scatter
            k_3 = 0.017; // ~ambient scatter
            k_4 = 0.1;  // ~air bubble scatter
            P_f = .01; // density of air bubbles
            brightnessFactor = 1;
            reflection.rgb *= 1;
        }
        break;
    }

    float cosUp = -N.y;
    vec3 omega_i = lightDir; // incoming = sun to frag
    vec3 omega_o = viewDir; // outgoing = frag to camera
    vec3 omega_h = normalize(omega_o - omega_i); // half-way between incoming and outgoing
    vec3 omega_n = N; // wave normal

    vec3 L_sun = max(lightColor * lightStrength, lightColor * 3);
    vec3 L_scatter = (
//        k_1*H*pow(max(0, dot(omega_i, -omega_o)), 4.f) * pow(.5 - .5*dot(omega_i, omega_n), 3.f)
        + k_2*pow(max(0, dot(omega_o, omega_n)), 2.f)
    ) * C_ss*L_sun;
    L_scatter += k_3*max(0, dot(omega_i, omega_n))*C_ss*L_sun + k_4*P_f*C_f*L_sun;
    L_scatter *= brightnessFactor;













    // SPECULAR STUFF
    vec3 specular = vec3(0);
    float specularGloss = waterType.specularGloss;
    float specularStrength = waterType.specularStrength * waterSpecularStrength;

    // Sun specular
    #if WATER_SPECULAR_MODE == 1 || WATER_SPECULAR_MODE == 3 // sun or sun & lights
        specular += pow(max(0, dot(R, lightDir)), specularGloss) * lightStrength * lightColor * specularStrength * 0.25;
    #endif

    // Point lights specular
    #if WATER_SPECULAR_MODE >= 2 // lights or sun & lights
        #define PHYSICAL_LIGHT_FALLOFF
        #ifdef PHYSICAL_LIGHT_FALLOFF
        vec3 pointLightsSpecular = vec3(0);
        float fragToCamDist = length(IN.position - cameraPos);
        for (int i = 0; i < pointLightsCount; i++) {
            vec4 pos = PointLightArray[i].position;
            vec3 lightToFrag = pos.xyz - IN.position;
            float distSq = length(lightToFrag) + fragToCamDist;
            distSq *= distSq;
            float radiusSquared = pos.w;

            vec3 pointLightColor = PointLightArray[i].color;
            vec3 pointLightDir = normalize(lightToFrag);

            pointLightColor *= 1 / (1 + distSq) * 2e5; // arbitrary multiplier

            vec3 pointLightReflectDir = reflect(-pointLightDir, N);
            pointLightsSpecular += pointLightColor * pow(max(0, dot(pointLightReflectDir, viewDir)), specularGloss) * specularStrength;
        }
        #else
        vec3 pointLightsSpecular = vec3(0);
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

                vec3 pointLightReflectDir = reflect(-pointLightDir, N);
                pointLightsSpecular += pointLightColor * pow(max(0, dot(pointLightReflectDir, viewDir)), specularGloss) * specularStrength;
            }
        }
        #endif
        specular += pointLightsSpecular;
    #endif


















    // PUTTING IT ALL TOGETHER...

    // Begin with the reflection as a base
    vec4 dst = reflection;

    // Refraction makes up the remaining portion if we exclude specular highlights,
    // but with transparent water, we still want the underwater geometry to be visible.
    // Refraction + reflection = 1, so modulate scattering by 1 - reflection.a
    vec3 refraction = L_scatter * (1 - reflection.a);

    // Neither refraction nor specular make sense to blend in using alpha blending,
    // so we need a special way to blend in light additively, without unnecessarily
    // obscuring the underwater geometry.
    vec3 additionalLight = refraction + specular;

    // In theory, we could just add the light and be done with it, but since the color
    // will be multiplied by alpha during alpha blending, we need to divide by alpha to
    // end up with our target amount of additional light after alpha blending
    dst.rgb += additionalLight / dst.a;

    // The issue now is that or color may exceed 100% brightness, and get clipped.
    // To work around this, we can adjust the alpha component to let more of the light through,
    // and adjust our color accordingly. This necessarily causes the surface to become more opaque,
    // but since we're adding lots of light, this should have minimal impact on the final picture.
    float brightestColor = max(max(dst.r, dst.g), dst.b);
    // If the color would get clipped
    if (brightestColor > 1) {
        // Bring the brightest color back down to 1
        dst.rgb /= brightestColor;
        // And bump up the alpha to increase brightness instead
        dst.a *= brightestColor;
        // While not strictly necessary, we might as well clamp the alpha component in case it exceeds 1
        dst.a = min(1, dst.a);
    }

    // If the water should be opaque, premultiply alpha and set it to 1
    if (isOpaque) {
        dst.rgb *= dst.a;
        dst.a = 1;
    }

    // Blend in foam at the very end as an overlay
    dst.rgb = foam.rgb * foam.a + dst.rgb * dst.a * (1 - foam.a);
    dst.a = foam.a + dst.a * (1 - foam.a);
    dst.rgb /= dst.a;

    return dst;
}

void sampleUnderwater(inout vec3 outputColor, int waterTypeIndex, float depth, float lightDotNormals) {
    outputColor *= vec3(0.57, 0.85, 1) * 2; // tune underwater terrain color

    // Sanitize underwater terrain lighting so that it doesn't deviate too much
    float underWaterLightStrength;
    if(lightStrength < 4)
    {
        underWaterLightStrength = 4;
    }
    else
    {
        underWaterLightStrength = lightStrength;
    }
    outputColor *= (underWaterLightStrength / 4);


    vec3 camToFrag = normalize(IN.position - cameraPos);
    float distanceToSurface = abs(depth / camToFrag.y); // abs = hack for viewing underwater geometry from below in waterfalls
    float totalDistance = depth + distanceToSurface;

    // Scale from a range of 0% = 0.5, 100% = 2.75, 130% = 3.425
    float lightPenetration = 0.5 + 2.25 * waterTransparencyAmount / 1.35;
    // divide by tuning factor during testing

    // Exponential falloff of light intensity when penetrating water, different for each color
    vec3 extinctionColors = vec3(0);
    vec3 waterTypeExtinction = vec3(1);
    switch (waterTypeIndex) {
        case 1:
        case 2:
        case 13:
        case 14:
        case 15:
            waterTypeExtinction = vec3(1);
            break;
        case WATER_TYPE_SWAMP_WATER:
        case WATER_TYPE_SWAMP_WATER_FLAT:
            waterTypeExtinction = vec3(10); // Light absorption for swamp water
            outputColor *= vec3(0.6, 0.8, 0); // Browner mud rather than sand
            break;
        case WATER_TYPE_POISON_WASTE:
            waterTypeExtinction = vec3(1.654, 3.64, 4.83344);
            outputColor *= vec3(0.5, 0.35, .35); // Browner mud rather than sand
            break;
        case 7:
            waterTypeExtinction = vec3(1, 4, 4); // Light absorption for blood
            outputColor *= vec3(0.4, 0.1, 0.1); // Browner mud rather than sand
            break;
        case 8:
            waterTypeExtinction = vec3(1, 1, 1);
            break;
        case 10:
            waterTypeExtinction = vec3(0.6, 1, 1.5); // Light absorption for muddy water
            outputColor *= vec3(0.37, 0.24, 0.24); // Browner mud rather than sand
            break;
        case 11:
            waterTypeExtinction = vec3(1, 1, 1); // Light absorption for scar sludge
            outputColor *= vec3(0.8, 0.8, 0); // Browner mud rather than sand
            break;
        case 12:
            waterTypeExtinction = vec3(1, 1, 1); // Light absorption for abyss bile
            break;
    }

    extinctionColors.r = exp(-totalDistance * (0.003090 / lightPenetration) * waterTypeExtinction.r);
    extinctionColors.g = exp(-totalDistance * (0.001981 / lightPenetration) * waterTypeExtinction.g);
    extinctionColors.b = exp(-totalDistance * (0.001548 / lightPenetration) * waterTypeExtinction.b);

    if (shorelineCaustics && (waterTransparency || depth <= 500)) {
        const float scale = 2.5;
        vec2 causticsUv = worldUvs(scale);
        causticsUv *= 0.75;
        const ivec2 direction = ivec2(1, -2);
        vec2 flow1 = causticsUv + animationFrame(17) * direction;
        vec2 flow2 = causticsUv * 1.5 + animationFrame(23) * -direction;
        vec3 caustics = sampleCaustics(flow1, flow2, .005);
        vec3 causticsColor = underwaterCausticsColor * underwaterCausticsStrength;
        if (!waterTransparency && depth <= 500) // reduce caustics brightness for shallow opaque water
        {
            causticsColor *= 0.5;
        }
        if(waterTypeIndex == 3 || waterTypeIndex == 10 || waterTypeIndex == 11)
        {
            causticsColor *= 0.5;
        }
        if (waterTypeIndex == 5 || waterTypeIndex == 7 || waterTypeIndex == 8 || waterTypeIndex == 9)
        {
            causticsColor *= 0;
        }
        outputColor *= 1 + caustics * causticsColor * extinctionColors * lightDotNormals * lightStrength * waterCausticsStrength;
    }

    outputColor = mix(vec3(0), outputColor, extinctionColors);
}
#endif
