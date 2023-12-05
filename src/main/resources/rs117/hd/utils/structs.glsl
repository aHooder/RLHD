#pragma once

struct Light {
    int type;
    vec3 position;
    float radius; // squared
    vec3 color;
    float brightness;
    vec3 direction;
    vec3 reflection;
    float ndl; // normal.light
    float distance; // squared
};

Light globalsun;
vec3 globalviewDir;
float globaludn;
float globalvdn;
int globalmaterialData;
vec3 globaltexBlend;
float globalmipBias;
vec3 globalfragPos;
mat3 globalTBN;
vec4 globalalbedo;
vec3 globalnormals;
vec3 globalsmoothness;
vec3 globalreflectivity;
bool globalisWater;
bool globalisUnderwater;
WaterType globalwaterType;
int globalwaterTypeIndex;
float globalwaterDepth;
Material[3] globalmaterials;
vec2[4] globaluvs;
