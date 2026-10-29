package dev.copperbench.assets;

import com.google.gson.JsonObject;
import de.javagl.obj.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** OBJ/MTL static geometry using the project's existing OBJ reader. */
final class ObjModelPreview {
    private record MaterialSource(Mtl material, String source) {}
    static JsonObject parse(String document, String source, ModelPreviewService resources, Map<String,String> overrides) throws IOException {
        Obj obj = ObjReader.read(new StringReader(document));
        if(obj.getNumVertices()>120_000 || obj.getNumFaces()>40_000 || obj.getMtlFileNames().size()>32)throw new IOException("OBJ exceeds the preview limit");
        Map<String,MaterialSource> materials=new HashMap<>();
        for(String file:obj.getMtlFileNames()) {
            AssetDescriptor asset=resources.referenced(source,file);
            if(asset==null)continue;
            for(Mtl material:MtlReader.read(new ByteArrayInputStream(resources.readReferenced(asset,256*1024))))materials.put(material.getName(),new MaterialSource(material,asset.relativePath()));
        }
        PreviewMeshBuilder builder=new PreviewMeshBuilder(); String current="";
        for(int i=0;i<obj.getNumFaces();i++) {
            ObjFace face=obj.getFace(i);String activated=obj.getActivatedMaterialGroupName(face);if(activated!=null)current=activated;
            MaterialSource selected=materials.get(current);Mtl mtl=selected==null?null:selected.material();
            String texture=overrides.get(current);
            if(texture==null && mtl!=null && mtl.getMapKd()!=null) {
                texture=resources.textureReference(selected.source(),mtl.getMapKd()); if(texture==null)texture="#missing:"+mtl.getMapKd();
            }
            if(texture==null && mtl==null && !current.isBlank())texture="#missing-material:"+current;
            double[] color=mtl!=null&&mtl.getKd()!=null?tuple(mtl.getKd(),3):texture!=null?new double[]{1,1,1}:new double[]{.72,.72,.72};
            double opacity=mtl!=null&&mtl.getD()!=null?mtl.getD():1;
            TextureOptions options=mtl==null?null:mtl.getMapKdOptions();
            var material=new PreviewMeshBuilder.Material(texture,color,opacity,true,options==null||!Boolean.TRUE.equals(options.isClamp()));
            List<double[]> vertices=new ArrayList<>(),uv=new ArrayList<>(),normals=face.containsNormalIndices()?new ArrayList<>():null;
            for(int j=0;j<face.getNumVertices();j++) {
                vertices.add(tuple(obj.getVertex(face.getVertexIndex(j)),3));
                double[] tex=face.containsTexCoordIndices()?tuple(obj.getTexCoord(face.getTexCoordIndex(j)),2):new double[]{0,0};
                if(options!=null)for(int axis=0;axis<2;axis++) { if(options.getS()!=null)tex[axis]*=options.getS().get(axis);if(options.getO()!=null)tex[axis]+=options.getO().get(axis);if(Boolean.TRUE.equals(options.isClamp()))tex[axis]=Math.clamp(tex[axis],0,1); }
                uv.add(tex);if(normals!=null)normals.add(tuple(obj.getNormal(face.getNormalIndex(j)),3));
            }
            builder.polygon(vertices,uv,normals,material);
        }
        return builder.scene("obj");
    }
    private static double[] tuple(FloatTuple tuple,int count)throws IOException {if(tuple.getDimensions()<count)throw new IOException("Incomplete OBJ coordinate");double[] result=new double[count];for(int i=0;i<count;i++)result[i]=tuple.get(i);return result;}
}
