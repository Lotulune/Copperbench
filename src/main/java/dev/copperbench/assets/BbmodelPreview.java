package dev.copperbench.assets;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;

/** Static Blockbench cubes, polygon meshes and nested outliner transforms (including the v5 group table). */
final class BbmodelPreview {
    private record Texture(String reference,double width,double height) {}
    private final JsonObject document;
    private final PreviewMeshBuilder builder=new PreviewMeshBuilder();
    private final Map<String,JsonObject> elements=new LinkedHashMap<>(),groups=new HashMap<>();
    private final Map<String,Texture> textures=new HashMap<>();
    private final Set<String> visited=new HashSet<>(),stack=new HashSet<>();
    private final double width,height;

    static JsonObject parse(JsonObject document,String source,ModelPreviewService resources)throws IOException {
        return new BbmodelPreview(document,source,resources).read();
    }
    private BbmodelPreview(JsonObject document,String source,ModelPreviewService resources)throws IOException {
        this.document=document;
        JsonObject resolution=document.has("resolution")?document.getAsJsonObject("resolution"):new JsonObject();
        width=number(resolution,"width",number(document,"texture_width",16));height=number(resolution,"height",number(document,"texture_height",16));
        if(width<=0||height<=0||width>8192||height>8192)throw new IOException("Invalid Blockbench texture resolution");
        List<Map.Entry<String,JsonElement>> table=new ArrayList<>();
        if(document.has("textures")) {
            if(document.get("textures").isJsonObject())table.addAll(document.getAsJsonObject("textures").entrySet());
            else {JsonArray array=document.getAsJsonArray("textures");for(int i=0;i<array.size();i++)table.add(Map.entry(Integer.toString(i),array.get(i)));}
        }
        if(table.size()>32)throw new IOException("Too many Blockbench textures");
        for(int i=0;i<table.size();i++) {
            JsonElement entry=table.get(i).getValue();JsonObject data=entry.isJsonObject()?entry.getAsJsonObject():new JsonObject();
            String reference=null;
            for(String key:List.of("relative_path","path","source")) if(data.has(key)&&data.get(key).isJsonPrimitive()) {
                reference=resources.textureReference(source,data.get(key).getAsString());if(reference!=null)break;
            }
            if(reference==null&&entry.isJsonPrimitive())reference=resources.textureReference(source,entry.getAsString());
            Texture texture=new Texture(reference==null?"#missing-texture:"+i:reference,number(data,"uv_width",width),number(data,"uv_height",height));
            if(texture.width()<=0||texture.height()<=0)throw new IOException("Invalid texture UV dimensions");
            textures.put(table.get(i).getKey(),texture);
            for(String key:List.of("uuid","id"))if(data.has(key)) {
                String alias=data.get(key).getAsString();Texture prior=textures.putIfAbsent(alias,texture);
                if(prior!=null&&prior!=texture)throw new IOException("Ambiguous Blockbench texture identity");
            }
        }
        JsonArray geometry=document.has("elements")?document.getAsJsonArray("elements"):new JsonArray();
        if(geometry.size()>4096)throw new IOException("Too many Blockbench elements");
        for(int i=0;i<geometry.size();i++) {
            JsonObject element=geometry.get(i).getAsJsonObject();String id=text(element,"uuid","index:"+i);
            if(elements.put(id,element)!=null)throw new IOException("Duplicate Blockbench element identity");
        }
        if(document.has("groups"))for(JsonElement raw:document.getAsJsonArray("groups")) {
            JsonObject group=raw.getAsJsonObject();String id=text(group,"uuid","");
            if(id.isBlank()||groups.put(id,group)!=null)throw new IOException("Invalid Blockbench group identity");
        }
    }
    private JsonObject read()throws IOException {
        if(document.has("outliner"))for(JsonElement node:document.getAsJsonArray("outliner"))node(node,PreviewMeshBuilder.identity(),true,0);
        for(var entry:elements.entrySet())if(!visited.contains(entry.getKey()))element(entry.getKey(),entry.getValue(),PreviewMeshBuilder.identity(),true);
        return builder.scene("bbmodel");
    }
    private void node(JsonElement raw,double[] parent,boolean visible,int depth)throws IOException {
        if(depth>64)throw new IOException("Blockbench outliner is too deep");
        String id=raw.isJsonObject()?text(raw.getAsJsonObject(),"uuid",""):raw.getAsString();
        if(elements.containsKey(id)){element(id,elements.get(id),parent,visible);return;}
        JsonObject group=groups.containsKey(id)?groups.get(id).deepCopy():new JsonObject();
        if(raw.isJsonObject())raw.getAsJsonObject().entrySet().forEach(e->group.add(e.getKey(),e.getValue()));
        if(group.isEmpty())throw new IOException("Blockbench outliner refers to a missing element");
        String cycleId=id.isBlank()?"group:"+System.identityHashCode(raw):id;
        if(!stack.add(cycleId))throw new IOException("Blockbench outliner contains a cycle");
        visible=visible&&flag(group,"visibility",true)&&flag(group,"export",true);
        double[] matrix=PreviewMeshBuilder.multiply(parent,PreviewMeshBuilder.pivot(vec(group,"origin"),vec(group,"rotation"),PreviewMeshBuilder.vector(group.get("scale"),1,1,1)));
        if(group.has("children"))for(JsonElement child:group.getAsJsonArray("children"))node(child,matrix,visible,depth+1);
        stack.remove(cycleId);
    }
    private void element(String id,JsonObject element,double[] parent,boolean visible)throws IOException {
        if(!visited.add(id))throw new IOException("Blockbench element appears more than once in the outliner");
        if(!visible||!flag(element,"visibility",true)||!flag(element,"export",true))return;
        String type=text(element,"type","cube");
        if(Set.of("locator","null_object","camera").contains(type))return;
        double[] origin=vec(element,"origin"),rotation=vec(element,"rotation");
        if(type.equals("mesh")) {
            JsonObject points=element.getAsJsonObject("vertices"),faces=element.getAsJsonObject("faces");
            if(points==null||faces==null||points.size()>40_000||faces.size()>40_000)throw new IOException("Invalid Blockbench mesh");
            double[] matrix=PreviewMeshBuilder.multiply(parent,PreviewMeshBuilder.multiply(PreviewMeshBuilder.translation(origin),PreviewMeshBuilder.rotation(Arrays.stream(rotation).map(Math::toRadians).toArray())));
            for(var entry:faces.entrySet()) {
                JsonObject face=entry.getValue().getAsJsonObject();if(disabled(face))continue;
                JsonArray indices=face.getAsJsonArray("vertices");if(indices==null||indices.size()<3)continue;
                JsonObject mapping=face.has("uv")?face.getAsJsonObject("uv"):new JsonObject();Texture texture=texture(face);
                List<double[]> positions=new ArrayList<>(),uv=new ArrayList<>();
                for(JsonElement index:indices) {
                    String vertex=index.getAsString();if(!points.has(vertex))throw new IOException("Blockbench face references a missing vertex");
                    positions.add(PreviewMeshBuilder.transform(PreviewMeshBuilder.vector(points.get(vertex),0,0,0),matrix,1d/16));
                    double[] coordinates=PreviewMeshBuilder.vector(mapping.get(vertex),0,0);uv.add(new double[]{coordinates[0]/texture.width(),1-coordinates[1]/texture.height()});
                }
                builder.polygon(positions,uv,null,material(texture));
            }
        } else if(type.equals("cube")) {
            if(!element.has("from")||!element.has("to"))throw new IOException("Blockbench cube has no coordinates");
            double[] from=vec(element,"from"),to=vec(element,"to"),size=PreviewMeshBuilder.sub(to,from);
            for(double v:size)if(v<0)throw new IOException("Invalid Blockbench cube size");
            double inflate=number(element,"inflate",0);for(int i=0;i<3;i++){from[i]-=inflate;to[i]+=inflate;}
            for(int i=0;i<3;i++)if(from[i]>to[i])throw new IOException("Invalid inflated Blockbench cube size");
            double[] scale={1,1,1};
            if(flag(element,"rescale",false))for(int axis=0;axis<3;axis++) {
                double cosine=Math.cos(Math.toRadians(rotation[axis]));if(Math.abs(cosine)<.01)throw new IOException("Invalid Blockbench cube rescale");
                for(int other=0;other<3;other++)if(other!=axis)scale[other]/=Math.abs(cosine);
            }
            double[] matrix=PreviewMeshBuilder.multiply(parent,PreviewMeshBuilder.pivot(origin,rotation,scale));
            double[] offset=PreviewMeshBuilder.vector(element.get("uv_offset"),0,0);
            Map<String,double[]> boxUv=PreviewMeshBuilder.boxUv(size,offset[0],offset[1],flag(element,"mirror_uv",false));
            JsonObject faces=element.has("faces")?element.getAsJsonObject("faces"):new JsonObject();
            boolean box=flag(element,"box_uv",flag(document,"box_uv",document.has("meta")&&flag(document.getAsJsonObject("meta"),"box_uv",false)));
            for(var entry:PreviewMeshBuilder.boxFaces(from,to).entrySet()) {
                JsonObject face=faces.has(entry.getKey())?faces.getAsJsonObject(entry.getKey()):new JsonObject();if(disabled(face))continue;
                Texture texture=texture(face);double[] rect=box?boxUv.get(entry.getKey()):PreviewMeshBuilder.vector(face.get("uv"),0,0,width,height);
                double angle=number(face,"rotation",0);if(!Set.of(0d,90d,180d,270d).contains(angle))throw new IOException("Invalid Blockbench UV rotation");int turns=(int)angle;
                List<double[]> positions=Arrays.stream(entry.getValue()).map(p->PreviewMeshBuilder.transform(p,matrix,1d/16)).toList();
                builder.polygon(positions,Arrays.asList(PreviewMeshBuilder.faceUv(rect,texture.width(),texture.height(),turns)),null,material(texture));
            }
        } else throw new IOException("Unsupported Blockbench geometry type: "+type);
    }
    private static boolean disabled(JsonObject face){return face.has("texture")&&face.get("texture").isJsonNull();}
    private Texture texture(JsonObject face) {
        JsonElement value=face.get("texture");
        if(value==null||value.isJsonNull()||value.isJsonPrimitive()&&value.getAsJsonPrimitive().isBoolean())return new Texture(null,width,height);
        String key=value.getAsString().replaceFirst("^#","");return textures.getOrDefault(key,new Texture("#missing-texture:"+key,width,height));
    }
    private static PreviewMeshBuilder.Material material(Texture texture){return texture.reference()==null?PreviewMeshBuilder.Material.plain():new PreviewMeshBuilder.Material(texture.reference());}
    private static double[] vec(JsonObject object,String key)throws IOException{return PreviewMeshBuilder.vector(object.get(key),0,0,0);}
    private static double number(JsonObject object,String key,double fallback){return object.has(key)?object.get(key).getAsDouble():fallback;}
    private static String text(JsonObject object,String key,String fallback){return object.has(key)?object.get(key).getAsString():fallback;}
    private static boolean flag(JsonObject object,String key,boolean fallback){return object.has(key)?object.get(key).getAsBoolean():fallback;}
}
