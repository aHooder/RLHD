#version 330

struct Material
{
    int colorMap;
    int normalMap;
    int displacementMap;
    int roughnessMap;
    int ambientOcclusionMap;
    int flowMap;
    int flags;
    float brightness;
    float displacementScale;
    float specularStrength;
    float specularGloss;
    float flowMapStrength;
    vec2 flowMapDuration;
    vec2 scrollDuration;
    vec2 textureScale;
    vec2 pad;
};

layout(std140) uniform MaterialUniforms
{
    Material MaterialArray[326];
} _492;

uniform float fogDepth;
uniform int expandedMapLoadingChunks;
uniform vec3 cameraPos;
uniform int drawDistance;
uniform int useFog;

layout(location = 0) in ivec4 vPosition;
layout(location = 1) in vec4 vUv;
layout(location = 2) in vec4 vNormal;
out vec3 gPosition;
out vec3 gUv;
out vec3 gNormal;
out vec4 gColor;
out float gFogAmount;
out int gMaterialData;
out int gTerrainData;

void main()
{
    vec3 _394 = vec3(vPosition.xyz);
    float _546 = float((vPosition.w >> 10) & 63) * 0.015625 + 0.0078125;
    float _555 = float(vPosition.w & 127);
    float _577 = (1.0 - abs(_555 * 0.015625 + (-1.0))) * (float((vPosition.w >> 7) & 7) * 0.125 + 0.0625);
    vec3 _609 = (vec3(clamp(abs(_546 * 6.0 + (-3.0)) - 1.0, 0.0, 1.0), clamp(2.0 - abs(_546 * 6.0 + (-2.0)), 0.0, 1.0), clamp(2.0 - abs(_546 * 6.0 + (-4.0)), 0.0, 1.0)) * _577) + vec3(_555 * 0.0078125 + (_577 * (-0.5)));
    float _408 = (-float((vPosition.w >> 24) & 255)) * 0.0039215688593685626983642578125 + 1.0;
    vec2 _422 = abs(floor(_394.xz * vec2(0.0078125)) - floor(cameraPos.xz * vec2(0.0078125)));
    float _432 = float(drawDistance);
    float _840 = 0.0;
    if ((max(_422.x, _422.y) * 128.0) > _432)
    {
        _840 = _408 * (-256.0);
    }
    else
    {
        _840 = _408;
    }
    gPosition = _394;
    gUv = vec3(vUv.xyz);
    gNormal = vNormal.xyz;
    gColor = vec4(mix(_609 * vec3(0.077399380505084991455078125), pow((_609 + vec3(0.054999999701976776123046875)) * vec3(0.947867333889007568359375), vec3(2.400000095367431640625)), step(vec3(0.040449999272823333740234375), _609)), _840);
    float _841 = 0.0;
    do
    {
        if (fogDepth == 0.0)
        {
            _841 = 0.0;
            break;
        }
        float _660 = float(((expandedMapLoadingChunks * (-8)) + 1) * 128);
        float _672 = float(((expandedMapLoadingChunks * 8) + 103) * 128);
        float _713 = min(gPosition.x - max(_660, cameraPos.x - _432), min(_672, (cameraPos.x + _432) - 128.0) - gPosition.x);
        float _722 = min(gPosition.z - max(_660, cameraPos.z - _432), min(_672, (cameraPos.z + _432) - 128.0) - gPosition.z);
        float _725 = min(_713, _722);
        float _863 = -_432;
        float _763 = float(min(drawDistance, 11520));
        float _766 = fogDepth * _763;
        float _869 = -((-clamp(_766 * 7.8124998253770172595977783203125e-05, 0.0, 1000.0)) * 0.0003000000142492353916168212890625 + 0.300000011920928955078125);
        _841 = max(max(clamp((_863 * 0.85000002384185791015625 + length(cameraPos.xz - gPosition.xz)) / (_863 * 0.85000002384185791015625 + _432), 0.0, 1.0), 1.0 - clamp(exp((-(max(_869 * _763 + length(cameraPos - vec3(gPosition.x, (gPosition.y + cameraPos.y) * 0.5, gPosition.z)), 0.0) / max(_869 * _763 + _763, 1.0))) * (_766 * 7.8124998026396497152745723724365e-07)), 0.0, 1.0)), (1.0 - clamp(((-192.0) * max(0.0, (_725 + 2.25) / (max(_713, _722) + 2.25)) + _725) * 0.001562500023283064365386962890625, 0.0, 1.0)) * float(useFog));
        break;
    } while(false);
    gFogAmount = _841;
    gMaterialData = int(vUv.w);
    gTerrainData = int(vNormal.w);
}

