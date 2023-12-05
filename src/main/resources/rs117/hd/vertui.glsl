#version 330

uniform ivec2 sourceDimensions;
uniform ivec2 targetDimensions;

layout(location = 0) in vec3 aPos;
out vec2 TexCoord;
layout(location = 1) in vec2 aTexCoord;

void main()
{
    gl_Position = vec4(aPos, 1.0);
    TexCoord = aTexCoord;
}

