const distance=(a,b)=>Math.hypot(a.x-b.x,a.y-b.y);
const interpolate=(a,b,t)=>({x:a.x+(b.x-a.x)*t,y:a.y+(b.y-a.y)*t,p:a.p+(b.p-a.p)*t,t:a.t+(b.t-a.t)*t});
function brushPoints(path,radius){const result=[];for(let i=0;i<path.length;i++){if(i){const a=path[i-1],b=path[i],steps=Math.ceil(distance(a,b)/Math.max(1,radius/2));for(let j=1;j<steps;j++)result.push({x:a.x+(b.x-a.x)*j/steps,y:a.y+(b.y-a.y)*j/steps});}result.push(path[i]);}return result;}
// null means untouched; [] means fully erased. Survivors retain pressure/time.
export function cutStroke(points,eraserPath,radius){
 if(!points.length||!eraserPath.length||!Number.isFinite(radius)||radius<=0)return null;
 const brush=brushPoints(eraserPath,radius),r2=radius*radius;
 if(points.length===1)return brush.some(c=>distance(c,points[0])<radius)?[]:null;
 let touched=false,current=null;const parts=[];
 for(let i=1;i<points.length;i++){
  const a=points[i-1],b=points[i],dx=b.x-a.x,dy=b.y-a.y,len=dx*dx+dy*dy,cuts=[];
  for(const c of brush){
   if(c.x+radius<Math.min(a.x,b.x)||c.x-radius>Math.max(a.x,b.x)||c.y+radius<Math.min(a.y,b.y)||c.y-radius>Math.max(a.y,b.y))continue;
   const ox=a.x-c.x,oy=a.y-c.y;
   if(len<1e-12){if(ox*ox+oy*oy<r2)cuts.push([0,1]);continue;}
   const linear=2*(ox*dx+oy*dy),constant=ox*ox+oy*oy-r2,disc=linear*linear-4*len*constant;if(disc<=0)continue;
   const start=Math.max(0,(-linear-Math.sqrt(disc))/(2*len)),end=Math.min(1,(-linear+Math.sqrt(disc))/(2*len));if(end>start)cuts.push([start,end]);
  }
  cuts.sort((x,y)=>x[0]-y[0]);const union=[];for(const cut of cuts){const last=union.at(-1);if(last&&cut[0]<=last[1])last[1]=Math.max(last[1],cut[1]);else union.push([...cut]);}
  if(union.length)touched=true;const kept=[];let from=0;for(const [start,end]of union){if(start>from)kept.push([from,start]);from=Math.max(from,end);}if(from<1)kept.push([from,1]);
  if(!kept.length){current=null;continue;}
  for(const [start,end]of kept){const first=interpolate(a,b,start),last=interpolate(a,b,end);if(!current||start>0||distance(current.at(-1),first)>1e-7){current=[first];parts.push(current);}current.push(last);if(end<1)current=null;}
 }
 return touched?parts:null;
}
const cross=(a,b,c)=>(b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);
function crossing(a,b,c,d){if(Math.max(a.x,b.x)<Math.min(c.x,d.x)||Math.max(c.x,d.x)<Math.min(a.x,b.x)||Math.max(a.y,b.y)<Math.min(c.y,d.y)||Math.max(c.y,d.y)<Math.min(a.y,b.y))return false;return cross(a,b,c)*cross(a,b,d)<=0&&cross(c,d,a)*cross(c,d,b)<=0;}
export function intersectsLasso(points,polygon){
 if(polygon.length<3)return false;let area=0;for(let i=0;i<polygon.length;i++){const a=polygon[i],b=polygon[(i+1)%polygon.length];area+=a.x*b.y-b.x*a.y;}if(Math.abs(area)<1)return false;
 for(const p of points){let inside=false;for(let i=0,j=polygon.length-1;i<polygon.length;j=i++){const a=polygon[i],b=polygon[j];if((a.y>p.y)!==(b.y>p.y)&&p.x<(b.x-a.x)*(p.y-a.y)/(b.y-a.y)+a.x)inside=!inside;}if(inside)return true;}
 for(let i=1;i<points.length;i++)for(let j=0;j<polygon.length;j++)if(crossing(points[i-1],points[i],polygon[j],polygon[(j+1)%polygon.length]))return true;
 return false;
}
