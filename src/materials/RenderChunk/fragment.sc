$input v_color0, v_color1, v_fog, v_refl, v_texcoord0, v_lightmapUV, v_extra

#include <bgfx_shader.sh>
#include <newb/main.sh>

SAMPLER2D_AUTOREG(s_MatTexture);
SAMPLER2D_AUTOREG(s_SeasonsTexture);
SAMPLER2D_AUTOREG(s_LightMapTexture);

void main() {
  #if defined(DEPTH_ONLY_OPAQUE) || defined(DEPTH_ONLY) || defined(INSTANCING)
    gl_FragColor = vec4(1.0,1.0,1.0,1.0);
    return;
  #endif

  vec4 diffuse = texture2D(s_MatTexture, v_texcoord0);
  vec4 color = v_color0;

  #ifdef ALPHA_TEST
    if (diffuse.a < 0.6) {
      discard;
    }
  #endif

  #if defined(SEASONS) && (defined(OPAQUE) || defined(ALPHA_TEST))
    diffuse.rgb *= mix(vec3(1.0,1.0,1.0), texture2D(s_SeasonsTexture, v_color1.xy).rgb * 2.0, v_color1.z);
  #endif

  vec3 glow = nlGlow(s_MatTexture, v_texcoord0, v_extra.a);

// Author: devendrn
// Title: Newb Shader 3D Moon
// License: CC-BY-SHA 4.0

#ifdef GL_ES
precision mediump float;
#endif

uniform vec2 u_resolution;
uniform vec2 u_mouse;
uniform float u_time;

vec3 render(vec2 uv, float t) {
    t *= 1.5;

    float a0 = 0.1*t;
    float a1 = 0.1*t;
    float a2 = 0.6*t;
    mat3 r = mat3(1, 0, 0, 0, cos(a0), -sin(a0), 0, sin(a0), cos(a0));
    r *= mat3(cos(a1), 0, sin(a1), 0, 1, 0, -sin(a1), 0, cos(a1) );
    r *= mat3(cos(a2), -sin(a2), 0, sin(a2), cos(a2), 0, 0, 0, 1);

    vec3 v = vec3(0.0,0.0,-1.0)*r;
    vec3 p = vec3(uv, 0.0)*r - 1.0*v;
    vec3 ldtmp = normalize(vec3(9.0,8.0,1.0));
    vec3 ld = ldtmp*r;
    
    vec3 c = vec3(0.0);
    
    float st = max(sin(20.0*uv.x+0.3*t)*sin(20.0*uv.y+4.0*sin(5.0*uv.x))*sin(9.0*uv.x*uv.y),0.0);
    st = 0.1*pow(st,32.0) + 2.0*pow(st,180.0);
    vec3 stc = vec3(sin(20.0*uv.x),sin(30.0*uv.x+0.8),sin(40.0*uv.y));
    c += st*(0.5 + 0.5*stc*stc);
    
    float dp = 0.0;
    float g = 2.0;
    for (int i = 0; i<32; i++) {
        vec3 pe = p + dp*v;
        vec3 q = abs(pe) - 0.5;
        float dt = length(max(q,0.0)) + min(max(q.x,max(q.y,q.z)),0.0);
        //dt -= 0.1;
        dp += dt;

        g = min(g,dt);

        if (dp > 2.0) {
            break;
        }

        if (dt < 0.01) {
            vec3 n = normalize(pe*pow(abs(2.0*pe),vec3(6.0)));

            vec3 h = floor(7.81*pe);
            float j = 0.4 + 0.6*(fract(176.728*sin(dot(h,vec3(24.06,12.75,172.3)))));
            c = vec3(0.7,0.7,0.8)*j;

            c *= 0.2+0.8*max(dot(ld,n),0.0);

            g = 0.0;
            break;
        }
  }

  g = 4.0/(1.0+5.0*g);
  c = 0.9*c + 0.15*g*smoothstep(2.0,0.2,length(uv-ldtmp.xy));
    
  c *= mix(vec3(1.0,0.0,0.1), vec3(1.0,1.0,0.2), c);

  return c;
}

void main(void) {
    vec2 uv = gl_FragCoord.xy / u_resolution.x;
    uv = (uv - 0.5)*3.5;
    vec3 col = render(uv, u_time);
    gl_FragColor = vec4(col, 1.0);
}

  diffuse.rgb *= diffuse.rgb;

  vec3 lightTint = texture2D(s_LightMapTexture, v_lightmapUV).rgb;
  lightTint = mix(lightTint.bbb, lightTint*lightTint, 0.35 + 0.65*v_lightmapUV.y*v_lightmapUV.y*v_lightmapUV.y);

  color.rgb *= lightTint;

  #if defined(TRANSPARENT) && !(defined(SEASONS) || defined(RENDER_AS_BILLBOARDS))
    if (v_extra.b > 0.9) {
      diffuse.rgb = vec3_splat(1.0 - NL_WATER_TEX_OPACITY*(1.0 - diffuse.b*1.8));
      diffuse.a = color.a;
    }
  #else
    diffuse.a = 1.0;
  #endif

  diffuse.rgb *= color.rgb;
  diffuse.rgb += glow;

  if (v_extra.b > 0.9) {
    diffuse.rgb += v_refl.rgb*v_refl.a;
  } else if (v_refl.a > 0.0) {
    // reflective effect - only on xz plane
    float dy = abs(dFdy(v_extra.g));
    if (dy < 0.0002) {
      float mask = v_refl.a*(clamp(v_extra.r*10.0,8.2,8.8)-7.8);
      diffuse.rgb *= 1.0 - 0.6*mask;
      diffuse.rgb += v_refl.rgb*mask;
    }
  }

  diffuse.rgb = mix(diffuse.rgb, v_fog.rgb, v_fog.a);

  diffuse.rgb = colorCorrection(diffuse.rgb);

  gl_FragColor = diffuse;
}
