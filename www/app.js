'use strict';
const $ = s => document.querySelector(s);
const canvas = $('#gl');
const gl = canvas.getContext('webgl', { preserveDrawingBuffer: true, antialias: false });
const video = document.createElement('video');
video.setAttribute('playsinline', ''); video.muted = true;

let stream = null, facing = 'environment', cur = PRESETS[0], stampOn = true, torchOn = false;
let t0 = performance.now();

const VS = `attribute vec2 p;varying vec2 uv;void main(){uv=p*0.5+0.5;gl_Position=vec4(p,0.,1.);}`;
const FS = `precision highp float;
varying vec2 uv;
uniform sampler2D tex; uniform vec2 res; uniform vec2 vres; uniform float time; uniform float mirror;
uniform float exposure,contrast,sat,temp,tint,fade,grain,vignette,halation,aberration,bloom,leak,scan,toneAmt;
uniform vec3 tone;
float h(vec2 s){return fract(sin(dot(s,vec2(12.9898,78.233)))*43758.5453);}
vec2 cover(vec2 u){ // center-crop video to canvas aspect
  float ca=res.x/res.y, va=vres.x/vres.y; vec2 s=vec2(1.);
  if(va>ca) s.x=ca/va; else s.y=va/ca;
  u=(u-.5)*s+.5; if(mirror>.5) u.x=1.-u.x; return u;}
vec3 samp(vec2 u){return texture2D(tex,cover(u)).rgb;}
void main(){
  vec2 c=uv-.5; float r2=dot(c,c);
  vec2 d=c*r2*aberration*8.;
  vec3 col=vec3(samp(uv-d).r,samp(uv).g,samp(uv+d).b);
  // halation + bloom from bright areas
  vec3 acc=vec3(0.); float px=1./res.x;
  for(int i=-3;i<=3;i++)for(int j=-3;j<=3;j++){
    vec3 s=samp(uv+vec2(float(i),float(j))*px*6.);
    float l=dot(s,vec3(.299,.587,.114)); acc+=s*smoothstep(.6,1.,l);}
  acc/=49.;
  col+=acc*vec3(1.,.35,.2)*halation*4.+acc*bloom*3.;
  col*=pow(2.,exposure*2.);
  col+= vec3(temp,tint*.5,-temp);
  float l=dot(col,vec3(.299,.587,.114));
  col=mix(vec3(l),col,sat);
  col=(col-.5)*contrast+.5;
  col=mix(col,col*tone,toneAmt*(1.-l)*3.);
  col=col*(1.-fade)+fade; // lifted blacks
  // light leak from right edge
  float lk=smoothstep(.55,1.,uv.x)*smoothstep(.2,.9,uv.y)*leak;
  col+=vec3(1.,.4,.15)*lk*(.6+.4*sin(time*.5));
  // scanlines
  col*=1.-scan*.5*(.5+.5*sin(uv.y*res.y*1.5));
  // grain
  float g=h(uv*res+time*61.)-.5; col+=g*grain*(1.-l*.5);
  // vignette
  col*=1.-vignette*smoothstep(.15,.75,r2*2.);
  gl_FragColor=vec4(clamp(col,0.,1.),1.);
}`;

function sh(type, src){const s=gl.createShader(type);gl.shaderSource(s,src);gl.compileShader(s);
  if(!gl.getShaderParameter(s,gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));return s;}
const prog=gl.createProgram();
gl.attachShader(prog,sh(gl.VERTEX_SHADER,VS));gl.attachShader(prog,sh(gl.FRAGMENT_SHADER,FS));
gl.linkProgram(prog);gl.useProgram(prog);
const buf=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buf);
gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,1,1]),gl.STATIC_DRAW);
const loc=gl.getAttribLocation(prog,'p');gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,2,gl.FLOAT,false,0,0);
const tex=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,tex);
[[gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE],[gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE],[gl.TEXTURE_MIN_FILTER,gl.LINEAR],[gl.TEXTURE_MAG_FILTER,gl.LINEAR]]
  .forEach(([k,v])=>gl.texParameteri(gl.TEXTURE_2D,k,v));
gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true);
const U=n=>gl.getUniformLocation(prog,n);

function resize(){
  const vf=$('#viewfinder'); let w=vf.clientWidth, h=vf.clientHeight;
  // fit preset aspect ratio (portrait orientation: height = width / ratio... use inverse)
  const ar=1/cur.ratio; // portrait
  if(w/h>ar) w=Math.floor(h*ar); else h=Math.floor(w/ar);
  const dpr=Math.min(window.devicePixelRatio||1,2);
  canvas.width=Math.floor(w*dpr);canvas.height=Math.floor(h*dpr);
  canvas.style.width=w+'px';canvas.style.height=h+'px';
  canvas.style.margin='0 auto';
}
function draw(outW,outH){
  gl.viewport(0,0,gl.canvas.width,gl.canvas.height);
  if(video.readyState>=2){
    gl.bindTexture(gl.TEXTURE_2D,tex);
    gl.texImage2D(gl.TEXTURE_2D,0,gl.RGB,gl.RGB,gl.UNSIGNED_BYTE,video);
    gl.uniform2f(U('vres'),video.videoWidth,video.videoHeight);
  }
  gl.uniform2f(U('res'),gl.canvas.width,gl.canvas.height);
  gl.uniform1f(U('time'),((performance.now()-t0)/1000)%100);
  gl.uniform1f(U('mirror'),facing==='user'?1:0);
  ['exposure','contrast','sat','temp','tint','fade','grain','vignette','halation','aberration','bloom','leak','scan','toneAmt']
    .forEach(k=>gl.uniform1f(U(k),cur[k]));
  gl.uniform3fv(U('tone'),cur.tone);
  gl.drawArrays(gl.TRIANGLE_STRIP,0,4);
}
function loop(){draw();requestAnimationFrame(loop);}

async function startCamera(){
  if(stream) stream.getTracks().forEach(t=>t.stop());
  try{
    stream=await navigator.mediaDevices.getUserMedia({audio:false,video:{facingMode:{ideal:facing},width:{ideal:4032},height:{ideal:3024}}});
    video.srcObject=stream;await video.play();$('#perm').hidden=true;
  }catch(e){
    $('#permMsg').textContent='Camera unavailable: '+e.message;$('#perm').hidden=false;
  }
}
function toast(m){const t=$('#toast');t.textContent=m;t.classList.add('show');setTimeout(()=>t.classList.remove('show'),1800);}

function pad(n){return String(n).padStart(2,'0');}
function stampText(){const d=new Date();return `'${pad(d.getFullYear()%100)}  ${pad(d.getMonth()+1)}  ${pad(d.getDate())}`;}

async function capture(){
  // Render at higher resolution offscreen-equivalent: temporarily enlarge canvas
  const old={w:canvas.width,h:canvas.height};
  const ar=canvas.width/canvas.height;
  const outH=Math.min(video.videoWidth||2000,video.videoHeight||2000,3000);
  const outW=Math.round(outH*ar);
  canvas.width=outW;canvas.height=outH;draw();
  const out=document.createElement('canvas');out.width=outW;out.height=outH;
  const ctx=out.getContext('2d');ctx.drawImage(canvas,0,0);
  if(stampOn&&cur.stamp){
    ctx.font=`bold ${Math.round(outH*0.032)}px monospace`;ctx.fillStyle=cur.stampColor;
    ctx.shadowColor=cur.stampColor;ctx.shadowBlur=outH*0.006;ctx.textAlign='right';
    ctx.fillText(stampText(),outW-outH*0.04,outH-outH*0.04);
  }
  canvas.width=old.w;canvas.height=old.h;
  const dataUrl=out.toDataURL('image/jpeg',0.95);
  $('#thumb').style.backgroundImage=`url(${dataUrl})`;
  try{
    const Media=window.Capacitor?.Plugins?.Media;
    if(Media){
      let albumId;const {albums}=await Media.getAlbums();
      const a=albums.find(x=>x.name==='RetroCam');
      if(a) albumId=a.identifier; else {await Media.createAlbum({name:'RetroCam'});albumId=(await Media.getAlbums()).albums.find(x=>x.name==='RetroCam').identifier;}
      await Media.savePhoto({path:dataUrl,albumIdentifier:albumId});toast('Saved to gallery');
    } else { const a=document.createElement('a');a.href=dataUrl;a.download=`retrocam_${Date.now()}.jpg`;a.click();toast('Downloaded'); }
  }catch(e){toast('Save failed: '+(e.message||e));}
}

function selectPreset(p){
  cur=p;$('#presetName').textContent=p.name;
  document.querySelectorAll('.preset').forEach(el=>el.classList.toggle('sel',el.dataset.id===p.id));
  $('#btnDate').classList.toggle('on',stampOn&&p.stamp);resize();
}
PRESETS.forEach(p=>{const b=document.createElement('button');b.className='preset';b.dataset.id=p.id;b.textContent=p.name;
  b.onclick=()=>selectPreset(p);$('#presets').appendChild(b);});
$('#btnDate').onclick=()=>{stampOn=!stampOn;$('#btnDate').classList.toggle('on',stampOn&&cur.stamp);};
$('#btnFlip').onclick=()=>{facing=facing==='user'?'environment':'user';startCamera();};
$('#btnTorch').onclick=async()=>{
  const tr=stream&&stream.getVideoTracks()[0];if(!tr)return;
  try{torchOn=!torchOn;await tr.applyConstraints({advanced:[{torch:torchOn}]});$('#btnTorch').classList.toggle('on',torchOn);}
  catch(e){torchOn=false;toast('Flash not supported on this camera');}
};
$('#shutter').onclick=capture;$('#permBtn').onclick=startCamera;
window.addEventListener('resize',resize);
selectPreset(PRESETS[0]);startCamera();loop();
