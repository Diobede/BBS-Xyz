#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2; // Lightmap from VBO
in vec3 Normal;

uniform sampler2D Sampler2; // Lightmap texture

uniform mat4 ModelViewMat;
uniform mat3 NormalMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform int FogShape;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

// New uniform for light override
uniform int UseLightOverride; // 0 = use UV2, 1 = use LightOverrideValue
uniform ivec2 LightOverrideValue;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec2 texCoord0;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = fog_distance(ModelViewMat, IViewRotMat * Position, FogShape);
    vec3 fixNormal = normalize(NormalMat * Normal);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, fixNormal, Color);
    
    ivec2 lightCoord = UV2;
    if (UseLightOverride > 0)
    {
        lightCoord = LightOverrideValue;
    }
    
    lightMapColor = texelFetch(Sampler2, lightCoord / 16, 0);
    texCoord0 = UV0;
}
