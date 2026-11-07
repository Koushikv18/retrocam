'use strict';
const VS = `attribute vec2 p;varying vec2 uv;void main(){uv=p*0.5+0.5;gl_Position=vec4(p,0.0,1.0);}`;
const FS = `
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 uv;
uniform sampler2D tex;
uniform vec2 res; uniform vec2 vres;
uniform float time, mirror, zoom;
uniform float exposure,contrast,sat,temp,tint,fade,grain,vignette,halation,aberration,bloom,leak,scan,toneAmt;
uniform vec3 tone;
float hash(vec2 s){return fract(sin(dot(s,vec2(12.9898,78.233)))*43758.5453);}
vec2 cover(vec2 u){
  float ca=res.x/res.y; float va=vres.x/vres.y; vec2 s=vec2(1.0);
  if(va>ca) s.x=ca/va; else s.y=va/ca;
  u=(u-0.5)/zoom*s+0.5;
  if(mirror>0.5) u.x=1.0-u.x;
  return u;
}
vec3 samp(vec2 u){return texture2D(tex,cover(u)).rgb;}
void main(){
  float ca=res.x/res.y;
  vec2 c=uv-0.5; float r2=dot(c,c);
  vec2 d=c*r2*aberration*8.0;
  vec3 col=vec3(samp(uv-d).r,samp(uv).g,samp(uv+d).b);
  vec3 acc=vec3(0.0);
  for(int i=0;i<12;i++){
    float a=float(i)*0.5236;
    float rr=(mod(float(i),2.0)<1.0)?0.008:0.018;
    vec3 s=samp(uv+vec2(cos(a)/ca,sin(a))*rr);
    float l=dot(s,vec3(0.299,0.587,0.114));
    acc+=s*smoothstep(0.62,1.0,l);
  }
  acc/=12.0;
  col+=acc*vec3(1.0,0.35,0.2)*halation*2.2+acc*bloom*1.6;
  col*=pow(2.0,exposure*2.0);
  col+=vec3(temp,tint*0.5,-temp);
  float l=dot(col,vec3(0.299,0.587,0.114));
  col=mix(vec3(l),col,sat);
  col=(col-0.5)*contrast+0.5;
  col=mix(col,col*tone,clamp(toneAmt*(1.0-l)*3.0,0.0,1.0));
  col=col*(1.0-fade)+fade;
  float lk=smoothstep(0.55,1.0,uv.x)*smoothstep(0.2,0.9,uv.y)*leak;
  col+=vec3(1.0,0.4,0.15)*lk*(0.6+0.4*sin(time*0.5));
  col*=1.0-scan*0.5*(0.5+0.5*sin(uv.y*2200.0));
  vec2 gp=floor(uv*vec2(1100.0*ca,1100.0));
  float g=hash(gp+time*61.0)-0.5;
  col+=g*grain*(1.0-l*0.5);
  col*=1.0-vignette*smoothstep(0.15,0.75,r2*2.0);
  gl_FragColor=vec4(clamp(col,0.0,1.0),1.0);
}`;
const UNIFORMS = ['tex','res','vres','time','mirror','zoom','exposure','contrast','sat','temp','tint','fade','grain','vignette','halation','aberration','bloom','leak','scan','toneAmt','tone'];
const PARAMS = ['exposure','contrast','sat','temp','tint','fade','grain','vignette','halation','aberration','bloom','leak','scan','toneAmt'];

class Renderer {
  constructor(canvas, opts) {
    this.canvas = canvas; this.opts = opts || {}; this.ok = false; this.lost = false;
    canvas.addEventListener('webglcontextlost', e => { e.preventDefault(); this.lost = true; });
    canvas.addEventListener('webglcontextrestored', () => { this.init(); this.lost = false; });
    this.init();
  }
  compile(type, src) {
    const gl = this.gl, s = gl.createShader(type);
    gl.shaderSource(s, src); gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error('Shader: ' + gl.getShaderInfoLog(s));
    return s;
  }
  init() {
    const gl = this.gl = this.canvas.getContext('webgl', {
      antialias: false, alpha: false, preserveDrawingBuffer: !!this.opts.keep, powerPreference: 'high-performance'
    });
    if (!gl) throw new Error('WebGL not available');
    const prog = this.prog = gl.createProgram();
    gl.attachShader(prog, this.compile(gl.VERTEX_SHADER, VS));
    gl.attachShader(prog, this.compile(gl.FRAGMENT_SHADER, FS));
    gl.linkProgram(prog);
    if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) throw new Error('Link: ' + gl.getProgramInfoLog(prog));
    gl.useProgram(prog);
    const buf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1, 1,-1, -1,1, 1,1]), gl.STATIC_DRAW);
    const loc = gl.getAttribLocation(prog, 'p');
    gl.enableVertexAttribArray(loc); gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
    this.tex = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, this.tex);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
    this.U = {}; UNIFORMS.forEach(n => this.U[n] = gl.getUniformLocation(prog, n));
    this.maxTex = gl.getParameter(gl.MAX_TEXTURE_SIZE) || 2048;
    this.uploaded = false; this.ok = true;
  }
  clear() { if (!this.ok || this.lost) return; this.gl.clearColor(0, 0, 0, 1); this.gl.clear(this.gl.COLOR_BUFFER_BIT); }
  render(src, sw, sh, o) {
    if (!this.ok || this.lost) return;
    const gl = this.gl, c = this.canvas, U = this.U, p = o.preset;
    gl.viewport(0, 0, c.width, c.height);
    gl.bindTexture(gl.TEXTURE_2D, this.tex);
    if (o.upload !== false || !this.uploaded) {
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, src);
      this.uploaded = true;
    }
    gl.uniform1i(U.tex, 0);
    gl.uniform2f(U.res, c.width, c.height);
    gl.uniform2f(U.vres, sw, sh);
    gl.uniform1f(U.time, o.time || 0);
    gl.uniform1f(U.mirror, o.mirror ? 1 : 0);
    gl.uniform1f(U.zoom, o.zoom || 1);
    PARAMS.forEach(k => gl.uniform1f(U[k], p[k]));
    gl.uniform3fv(U.tone, p.tone);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  }
}
