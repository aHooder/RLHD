#pragma once

layout(std140) uniform SkyUniforms {
    mat4 projectionMatrix;
    ivec2 viewportDimensions;
    float colorBlindnessIntensity;
    vec3 lightDir;
    vec3 lightColor;
    float gammaCorrection;
};
