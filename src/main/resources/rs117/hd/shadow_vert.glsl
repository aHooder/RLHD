#version 330

layout(location = 1) in vec4 vUv;
layout(location = 2) in vec4 vNormal;
layout(location = 0) in ivec4 vPosition;
flat out vec3 gPosition;
flat out vec3 gUv;
flat out int gMaterialData;
flat out int gCastShadow;
flat out float gOpacity;

void main()
{
    int _19 = int(vUv.w);
    int _24 = int(vNormal.w);
    float _47 = (-float((vPosition.w >> 24) & 255)) * 0.0039215688593685626983642578125 + 1.0;
    float _56 = float((_19 >> 6) & 63) * 0.01587301678955554962158203125;
    gPosition = vec3(vec4(vPosition).xyz);
    gUv = vec3(vUv.xyz);
    gMaterialData = _19;
    gCastShadow = ((((_24 & 15) == 1) || (((_24 >> 3) & 31) > 0)) || (_47 <= ((_56 == 0.0) ? 0.00999999977648258209228515625 : _56))) ? 0 : 1;
    gOpacity = _47;
}

