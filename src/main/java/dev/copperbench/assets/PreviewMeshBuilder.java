package dev.copperbench.assets;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;

/** Bounded triangle geometry shared by the static model readers. Coordinates are in preview units. */
final class PreviewMeshBuilder {
    record Material(String texture, double[] color, double opacity, boolean doubleSided, boolean repeatTexture) {
        Material(String texture, double[] color, double opacity, boolean doubleSided) { this(texture,color,opacity,doubleSided,false); }
        Material(String texture) { this(texture, new double[]{1,1,1}, 1, true, false); }
        static Material plain() { return new Material(null, new double[]{.72,.72,.72},1,true); }
    }
    private final Map<String, JsonObject> meshes = new LinkedHashMap<>();
    private int vertexCount;
    void polygon(List<double[]> vertices, List<double[]> uv, List<double[]> normals, Material material) throws IOException {
        if(vertices.size()<3) return;
        if(vertices.size()>256 || uv.size()!=vertices.size()) throw new IOException("Invalid model face");
        for(double[] v:vertices) check(v,3); for(double[] t:uv) check(t,2);
        if(normals!=null) { if(normals.size()!=vertices.size())throw new IOException("Invalid model normals"); for(double[] n:normals)check(n,3); }
        check(material.color(),3); if(!Double.isFinite(material.opacity())||material.opacity()<0||material.opacity()>1)throw new IOException("Invalid material opacity");
        int[] triangles=triangulate(vertices);
        if((vertexCount+=triangles.length)>120_000)throw new IOException("Model exceeds the triangle limit");
        String key=Objects.toString(material.texture(),"")+Arrays.toString(material.color())+material.opacity()+material.doubleSided()+material.repeatTexture();
        JsonObject mesh=meshes.get(key);
        if(mesh==null) {
            if(meshes.size()>=64)throw new IOException("Model exceeds the material limit");
            mesh=new JsonObject(); mesh.add("positions",new JsonArray());mesh.add("uv",new JsonArray());mesh.add("normals",new JsonArray());
            if(material.texture()!=null)mesh.addProperty("texture",material.texture());
            mesh.add("color",array(material.color()));mesh.addProperty("opacity",material.opacity());mesh.addProperty("doubleSided",material.doubleSided());
            mesh.addProperty("repeatTexture",material.repeatTexture());
            meshes.put(key,mesh);
        }
        for(int i=0;i<triangles.length;i+=3) {
            double[] normal=normalize(cross(sub(vertices.get(triangles[i+1]),vertices.get(triangles[i])),sub(vertices.get(triangles[i+2]),vertices.get(triangles[i]))));
            for(int j=0;j<3;j++) {int index=triangles[i+j]; append(mesh.getAsJsonArray("positions"),vertices.get(index));append(mesh.getAsJsonArray("uv"),uv.get(index));append(mesh.getAsJsonArray("normals"),normals==null?normal:normalize(normals.get(index)));}
        }
    }
    JsonObject scene(String format) throws IOException {
        if(vertexCount==0)throw new IOException("Model has no visible geometry");
        JsonObject result=new JsonObject();result.addProperty("kind","mesh");result.addProperty("format",format);
        JsonArray list=new JsonArray();meshes.values().forEach(list::add);result.add("meshes",list);return result;
    }

    /** Plane-projected ear clipping also handles concave OBJ polygons, preserving each corner's UV and normal. */
    private static int[] triangulate(List<double[]> points) throws IOException {
        int size=points.size(); double[] n={0,0,0};
        for(int i=0;i<size;i++){double[] a=points.get(i),b=points.get((i+1)%size);n[0]+=(a[1]-b[1])*(a[2]+b[2]);n[1]+=(a[2]-b[2])*(a[0]+b[0]);n[2]+=(a[0]-b[0])*(a[1]+b[1]);}
        int drop=Math.abs(n[0])>Math.abs(n[1])?0:1;if(Math.abs(n[2])>Math.abs(n[drop]))drop=2;
        if(Math.abs(n[drop])<1e-12)return new int[0];
        int a=(drop+1)%3,b=(drop+2)%3; double orientation=Math.signum(n[drop]);
        List<Integer> remaining=new ArrayList<>(),output=new ArrayList<>();for(int i=0;i<size;i++)remaining.add(i);
        while(remaining.size()>3){boolean clipped=false;
            for(int i=0;i<remaining.size();i++) {
                int p=remaining.get((i+remaining.size()-1)%remaining.size()),q=remaining.get(i),r=remaining.get((i+1)%remaining.size());
                if(turn(points.get(p),points.get(q),points.get(r),a,b)*orientation<=1e-12)continue;
                boolean occupied=false;
                for(int k:remaining)if(k!=p&&k!=q&&k!=r && turn(points.get(p),points.get(q),points.get(k),a,b)*orientation>=-1e-12
                        && turn(points.get(q),points.get(r),points.get(k),a,b)*orientation>=-1e-12 && turn(points.get(r),points.get(p),points.get(k),a,b)*orientation>=-1e-12){occupied=true;break;}
                if(!occupied){output.add(p);output.add(q);output.add(r);remaining.remove(i);clipped=true;break;}
            }
            if(!clipped)throw new IOException("Model contains a self-intersecting or degenerate face");
        }
        output.addAll(remaining);return output.stream().mapToInt(Integer::intValue).toArray();
    }
    private static double turn(double[] p,double[] q,double[] r,int a,int b){return(q[a]-p[a])*(r[b]-p[b])-(q[b]-p[b])*(r[a]-p[a]);}
    static void check(double[] values,int size)throws IOException {if(values.length!=size)throw new IOException("Invalid model vector");for(double v:values)if(!Double.isFinite(v)||Math.abs(v)>1_000_000)throw new IOException("Invalid model coordinate");}
    static double[] vector(JsonElement raw,double... fallback)throws IOException {if(raw==null||raw.isJsonNull())return fallback.clone();JsonArray a=raw.getAsJsonArray();double[] result=new double[a.size()];for(int i=0;i<result.length;i++)result[i]=a.get(i).getAsDouble();check(result,fallback.length);return result;}
    static JsonArray array(double... values){JsonArray result=new JsonArray();append(result,values);return result;}
    private static void append(JsonArray target,double[] values){for(double n:values)target.add(n);}
    static double[] sub(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    static double[] normalize(double[] n){double len=Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]);return len<1e-12?new double[]{0,1,0}:new double[]{n[0]/len,n[1]/len,n[2]/len};}

    static double[] identity(){return new double[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};}
    static double[] multiply(double[] a,double[] b){double[] r=new double[16];for(int row=0;row<4;row++)for(int col=0;col<4;col++)for(int k=0;k<4;k++)r[row*4+col]+=a[row*4+k]*b[k*4+col];return r;}
    static double[] translation(double[] p){double[] r=identity();r[3]=p[0];r[7]=p[1];r[11]=p[2];return r;}
    static double[] scaling(double[] scale){double[] r=identity();for(int i=0;i<3;i++)r[i*5]=scale[i];return r;}
    static double[] rotation(double[] radians){double[] result=identity();for(int axis=0;axis<3;axis++) {int a=(axis+1)%3,b=(axis+2)%3;double c=Math.cos(radians[axis]),s=Math.sin(radians[axis]);double[] r=identity();r[a*4+a]=r[b*4+b]=c;r[a*4+b]=-s;r[b*4+a]=s;result=multiply(r,result);}return result;}
    static double[] pivot(double[] origin,double[] degrees){double[] angles=Arrays.stream(degrees).map(Math::toRadians).toArray();return multiply(multiply(translation(origin),rotation(angles)),translation(new double[]{-origin[0],-origin[1],-origin[2]}));}
    static double[] pivot(double[] origin,double[] degrees,double[] scale){double[] angles=Arrays.stream(degrees).map(Math::toRadians).toArray();return multiply(multiply(multiply(translation(origin),rotation(angles)),scaling(scale)),translation(new double[]{-origin[0],-origin[1],-origin[2]}));}
    static double[] transform(double[] p,double[] matrix,double scale){return new double[]{(matrix[0]*p[0]+matrix[1]*p[1]+matrix[2]*p[2]+matrix[3])*scale,(matrix[4]*p[0]+matrix[5]*p[1]+matrix[6]*p[2]+matrix[7])*scale,(matrix[8]*p[0]+matrix[9]*p[1]+matrix[10]*p[2]+matrix[11])*scale};}
    static Map<String,double[][]> boxFaces(double[] from,double[] to){double x=from[0],y=from[1],z=from[2],X=to[0],Y=to[1],Z=to[2];return Map.of(
            "down",new double[][]{{x,y,Z},{x,y,z},{X,y,z},{X,y,Z}},"up",new double[][]{{x,Y,z},{x,Y,Z},{X,Y,Z},{X,Y,z}},
            "north",new double[][]{{X,Y,z},{X,y,z},{x,y,z},{x,Y,z}},"south",new double[][]{{x,Y,Z},{x,y,Z},{X,y,Z},{X,Y,Z}},
            "west",new double[][]{{x,Y,z},{x,y,z},{x,y,Z},{x,Y,Z}},"east",new double[][]{{X,Y,Z},{X,y,Z},{X,y,z},{X,Y,z}});}
    static double[][] faceUv(double[] rect,double width,double height,int rotation){double[][] uv={{rect[0]/width,1-rect[1]/height},{rect[0]/width,1-rect[3]/height},{rect[2]/width,1-rect[3]/height},{rect[2]/width,1-rect[1]/height}};double[][] r=new double[4][];for(int i=0;i<4;i++)r[i]=uv[(i+rotation/90)%4];return r;}
    static Map<String,double[]> boxUv(double[] size,double u,double v,boolean mirror){double x=size[0],y=size[1],z=size[2];Map<String,double[]> result=new HashMap<>();
        result.put("east",new double[]{u,v+z,u+z,v+z+y});result.put("west",new double[]{u+z+x,v+z,u+2*z+x,v+z+y});
        result.put("up",new double[]{u+z+x,v+z,u+z,v});result.put("down",new double[]{u+z+2*x,v,u+z+x,v+z});
        result.put("south",new double[]{u+2*z+x,v+z,u+2*z+2*x,v+z+y});result.put("north",new double[]{u+z,v+z,u+z+x,v+z+y});
        if(mirror){result.values().forEach(r->{double t=r[0];r[0]=r[2];r[2]=t;});double[] t=result.get("east");result.put("east",result.get("west"));result.put("west",t);}return result;}
}
