#version 330
layout(triangles) in;
layout(max_vertices = 3, triangle_strip) out;

uniform vec3 cameraPos;
uniform mat4 projectionMatrix;
uniform float elapsedTime;

in int gMaterialData[3];
in vec3 gUv[3];
in vec3 gPosition[3];
flat out vec4 vColor[3];
in vec4 gColor[3];
flat out int vMaterialData[3];
flat out int vTerrainData[3];
in int gTerrainData[3];
flat out vec2 vUv[3];
flat out vec3 T;
flat out vec3 B;
out FragmentData
{
    vec3 position;
    vec3 normal;
    vec3 texBlend;
    float fogAmount;
} OUT;

in vec3 gNormal[3];
in float gFogAmount[3];

void main()
{
    for (int _641 = 0; _641 < 3; )
    {
        vColor[_641] = gColor[_641];
        vMaterialData[_641] = gMaterialData[_641];
        vTerrainData[_641] = gTerrainData[_641];
        _641++;
        continue;
    }
    vec2 param[3] = vec2[](vec2(0.0), vec2(0.0), vec2(0.0));
    if (((gMaterialData[0] >> 2) & 1) == 1)
    {
        float _480 = 1.0 / length(gUv[0]);
        vec3 _484 = gUv[0] * _480;
        vec3 _486 = cross(vec3(0.0, 0.0, 1.0), _484);
        vec3 _488 = cross(vec3(0.0, 1.0, 0.0), _484);
        bvec3 _496 = bvec3(length(_486) > length(_488));
        vec3 _498 = normalize(vec3(_496.x ? _486.x : _488.x, _496.y ? _486.y : _488.y, _496.z ? _486.z : _488.z));
        mat3 _517 = mat3(_498, cross(_484, _498), _484);
        for (int _644 = 0; _644 < 3; )
        {
            param[_644] = ((_517 * gPosition[_644]).xy * vec2(0.0078125)) * _480;
            _644++;
            continue;
        }
    }
    else
    {
        if (((gMaterialData[0] >> 1) & 1) == 1)
        {
            vec3 _550 = gUv[1] - gUv[0];
            vec3 _554 = gUv[2] - gUv[0];
            vec3 _557 = cross(_550, _554);
            vec3 _560 = cross(_550, _557);
            vec3 _563 = cross(_554, _557);
            float _567 = 1.0 / dot(_563, _550);
            float _571 = 1.0 / dot(_560, _554);
            for (int _643 = 0; _643 < 3; )
            {
                vec3 _582 = cameraPos - gPosition[_643];
                vec3 _601 = (gPosition[_643] + ((_582 * dot(gUv[_643] - gPosition[_643], _557)) / vec3(dot(_582, _557)))) - gUv[0];
                param[_643] = vec2(dot(_563, _601) * _567, dot(_560, _601) * _571);
                _643++;
                continue;
            }
        }
        else
        {
            for (int _642 = 0; _642 < 3; )
            {
                param[_642] = gUv[_642].xy;
                _642++;
                continue;
            }
        }
    }
    vUv = param;
    mat2 _299 = mat2(vUv[1] - vUv[0], vUv[2] - vUv[0]);
    mat2 _645 = mat2(vec2(0.0), vec2(0.0));
    if (determinant(_299) == 0.0)
    {
        _645 = mat2(vec2(1.0, 0.0), vec2(0.0, 1.0));
    }
    else
    {
        _645 = _299;
    }
    vec3 _320 = gPosition[1] - gPosition[0];
    vec3 _325 = gPosition[2] - gPosition[0];
    mat2x3 _338 = mat2x3(_320, _325) * (inverse(_645) * (-1.0));
    T = _338[0];
    B = _338[1];
    vec3 _352 = normalize(cross(_320, _325));
    for (int _646 = 0; _646 < 3; _646++)
    {
        vec3 _647 = vec3(0.0);
        if (dot(gNormal[_646], gNormal[_646]) == 0.0)
        {
            _647 = _352;
        }
        else
        {
            _647 = normalize(gNormal[_646]);
        }
        OUT.normal = _647;
        OUT.position = gPosition[_646];
        OUT.texBlend = vec3(0.0);
        OUT.texBlend[_646] = 1.0;
        OUT.fogAmount = gFogAmount[_646];
        gl_Position = projectionMatrix * vec4(OUT.position, 1.0);
        EmitVertex();
    }
    EndPrimitive();
}

