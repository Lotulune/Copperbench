package dev.copperbench.assets;

import com.google.gson.JsonObject;
import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.util.*;

/** Parses standard Blockbench Java exports into their default pose. No compilation, class loading or execution. */
final class JavaModelPreview {
    static final class UnsupportedGeometry extends IOException { UnsupportedGeometry(String message){super(message);} }
    private static final class Part {
        final Map<String,Part> children=new LinkedHashMap<>(); final List<Box> boxes=new ArrayList<>();
        Part parent; double[] offset={0,0,0},rotation={0,0,0};double u,v;boolean mirror;
    }
    private record Box(double x,double y,double z,double width,double height,double depth,double[] inflate,double u,double v,boolean mirror) {}
    private static final class Cubes {final List<Box> boxes=new ArrayList<>();double u,v;boolean mirror;}
    private final Map<String,ExpressionTree> expressions=new HashMap<>();
    private final Map<String,Part> parts=new LinkedHashMap<>();
    private final Part root=new Part();
    private final Set<String> localMethods=new HashSet<>();
    private String className;
    private double textureWidth=64,textureHeight=32;
    private boolean modern;

    static JsonObject parse(String code,String texture)throws IOException {
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null)throw new UnsupportedGeometry("The Java model parser is not available in this runtime");
        DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        JavaFileObject file=new SimpleJavaFileObject(URI.create("string:///Preview.java"),JavaFileObject.Kind.SOURCE){@Override public CharSequence getCharContent(boolean ignored){return code;}};
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,java.nio.charset.StandardCharsets.UTF_8)) {
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("-proc:none"),null,List.of(file));
            CompilationUnitTree unit=task.parse().iterator().next();
            if(diagnostics.getDiagnostics().stream().anyMatch(d->d.getKind()==Diagnostic.Kind.ERROR))throw new UnsupportedGeometry("Java model source has syntax errors");
            ClassTree model=unit.getTypeDecls().stream().filter(ClassTree.class::isInstance).map(ClassTree.class::cast).findFirst().orElseThrow(()->new UnsupportedGeometry("Java model class was not found"));
            return new JavaModelPreview().read(model,texture);
        }
    }
    private JsonObject read(ClassTree model,String texture)throws IOException {
        className=model.getSimpleName().toString();
        List<MethodTree> methods=new ArrayList<>();
        for(Tree member:model.getMembers()) {
            if(member instanceof VariableTree field && field.getInitializer()!=null)expressions.put(field.getName().toString(),field.getInitializer());
            if(member instanceof MethodTree method){methods.add(method);localMethods.add(method.getName().toString());}
        }
        for(String name:List.of("textureWidth","texWidth"))if(expressions.containsKey(name))textureWidth=number(expressions.get(name));
        for(String name:List.of("textureHeight","texHeight"))if(expressions.containsKey(name))textureHeight=number(expressions.get(name));
        MethodTree geometry=methods.stream().filter(m->Set.of("createBodyLayer","getTexturedModelData","createLayer").contains(m.getName().toString())&&m.getBody()!=null).findFirst().orElse(null);
        modern=geometry!=null;
        if(modern)statements(geometry.getBody().getStatements());
        else {
            MethodTree constructor=methods.stream().filter(m->m.getReturnType()==null&&m.getBody()!=null).findFirst().orElseThrow(()->new UnsupportedGeometry("No supported Java geometry factory or constructor"));
            for(Tree member:model.getMembers())if(member instanceof VariableTree field&&isNewPart(field.getInitializer()))parts.put(field.getName().toString(),newPart((NewClassTree)field.getInitializer()));
            statements(constructor.getBody().getStatements());
        }
        if(textureWidth<=0||textureHeight<=0||textureWidth>8192||textureHeight>8192)throw new IOException("Invalid Java model texture dimensions");
        PreviewMeshBuilder builder=new PreviewMeshBuilder();Set<Part> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        double[] base=PreviewMeshBuilder.translation(new double[]{0,24,0});
        if(modern)emit(root,base,builder,texture,visited,0);
        else for(Part part:new LinkedHashSet<>(parts.values()))if(part.parent==null)emit(part,base,builder,texture,visited,0);
        JsonObject result=builder.scene("java");result.addProperty("defaultPose",true);return result;
    }
    private void statements(List<? extends StatementTree> statements)throws IOException {
        for(StatementTree statement:statements) {
            if(statement instanceof VariableTree variable) {
                ExpressionTree value=variable.getInitializer();if(value==null)continue;
                String name=variable.getName().toString();expressions.put(name,value);
                if(isNewPart(value))parts.put(name,newPart((NewClassTree)value));
                else if(value instanceof MethodInvocationTree call && Set.of("getRoot","addOrReplaceChild","addChild").contains(method(call)))parts.put(name,createPart(call));
            } else if(statement instanceof ExpressionStatementTree expression)expression(expression.getExpression());
            else if(statement instanceof ReturnTree returned && returned.getExpression() instanceof MethodInvocationTree call) {
                if(Set.of("create","of").contains(method(call))&&call.getArguments().size()==3){textureWidth=number(call.getArguments().get(1));textureHeight=number(call.getArguments().get(2));}
                else throw new UnsupportedGeometry("Java geometry factory uses an unsupported return expression");
            } else if(statement instanceof BlockTree block)statements(block.getStatements());
            else if(statement.getKind()!=Tree.Kind.EMPTY_STATEMENT)throw new UnsupportedGeometry("Java geometry uses "+statement.getKind()+"; only static exported geometry is supported");
        }
    }
    private void expression(ExpressionTree expression)throws IOException {
        if(expression instanceof AssignmentTree assignment) {
            String key=key(assignment.getVariable());ExpressionTree value=assignment.getExpression();
            if(key.equals("textureWidth")||key.equals("texWidth")){textureWidth=number(value);return;}
            if(key.equals("textureHeight")||key.equals("texHeight")){textureHeight=number(value);return;}
            if(isNewPart(value)){parts.put(key,newPart((NewClassTree)value));return;}
            if(assignment.getVariable() instanceof MemberSelectTree member) {
                Part part=parts.get(key(member.getExpression()));String field=member.getIdentifier().toString();
                if(part!=null) {
                    if(field.equals("mirror")){part.mirror=bool(value);return;}
                    int axis=switch(field){case "rotateAngleX","xRot"->0;case "rotateAngleY","yRot"->1;case "rotateAngleZ","zRot"->2;default->-1;};
                    if(axis>=0){part.rotation[axis]=number(value);return;}
                    throw new UnsupportedGeometry("Unsupported Java model part property: "+field);
                }
            }
            expressions.put(key,value);return;
        }
        if(!(expression instanceof MethodInvocationTree call))throw new UnsupportedGeometry("Unsupported Java geometry expression");
        String name=method(call);List<? extends ExpressionTree> args=call.getArguments();
        if(Set.of("setRotationAngle","setRotation","setRotateAngle").contains(name)&&args.size()==4){Part part=part(args.get(0));part.rotation=triple(args,1);return;}
        if((name.equals("addOrReplaceChild")||name.equals("addChild"))&&args.size()==3){createPart(call);return;}
        ExpressionTree receiver=receiver(call);Part target=tryPart(receiver);
        if(name.equals("add") && receiver instanceof MemberSelectTree member && Set.of("cubeList","cubes").contains(member.getIdentifier().toString()) && args.size()==1 && args.getFirst() instanceof NewClassTree box) {
            Part owner=part(member.getExpression());List<? extends ExpressionTree> fields=box.getArguments();
            if(!box.getIdentifier().toString().endsWith("ModelBox")||fields.size()<10)throw new UnsupportedGeometry("Unsupported Java model cube constructor");
            Cubes cubes=new Cubes();cubes.u=number(fields.get(1));cubes.v=number(fields.get(2));cubes.mirror=fields.size()>10?bool(fields.get(10)):owner.mirror;
            owner.boxes.add(box(fields.subList(3,fields.size()),cubes));return;
        }
        if(target==null) {
            if((receiver==null||receiver.toString().equals("this")||receiver.toString().equals(className))&&localMethods.contains(name))
                throw new UnsupportedGeometry("Java model uses an unsupported current-class helper: "+name);
            for(ExpressionTree argument:args)if(tryPart(argument)!=null)throw new UnsupportedGeometry("Java model uses a custom geometry helper: "+name);
            return; // Non-geometry calls (including super) are never executed.
        }
        switch(name) {
            case "setRotationPoint","setPos","setPivot" -> target.offset=triple(args,0);
            case "setTextureSize" -> {textureWidth=number(args.get(0));textureHeight=number(args.get(1));}
            case "addChild" -> attach(target,key(args.getFirst()),part(args.getFirst()));
            case "setTextureOffset","texOffs" -> {target.u=number(args.get(0));target.v=number(args.get(1));}
            case "addBox","cuboid" -> {Cubes cubes=builder(call);target.boxes.addAll(cubes.boxes);target.u=cubes.u;target.v=cubes.v;target.mirror=cubes.mirror;}
            default -> throw new UnsupportedGeometry("Unsupported Java model part operation: "+name);
        }
    }
    private Part createPart(MethodInvocationTree call)throws IOException {
        String name=method(call);if(name.equals("getRoot"))return root;
        List<? extends ExpressionTree> args=call.getArguments();
        if(args.size()!=3)throw new UnsupportedGeometry("Unsupported Java model child declaration");
        Part parent=part(receiver(call)),created=new Part();created.boxes.addAll(builder(args.get(1)).boxes);
        ExpressionTree pose=unwrap(args.get(2));
        if(pose instanceof MethodInvocationTree transform) {
            String operation=method(transform);List<? extends ExpressionTree> coordinates=transform.getArguments();
            if(Set.of("offset","pivot","origin").contains(operation)&&coordinates.size()==3)created.offset=triple(coordinates,0);
            else if(Set.of("offsetAndRotation","of").contains(operation)&&coordinates.size()==6){created.offset=triple(coordinates,0);created.rotation=triple(coordinates,3);}
            else if(operation.equals("rotation")&&coordinates.size()==3)created.rotation=triple(coordinates,0);
            else throw new UnsupportedGeometry("Unsupported Java model pose: "+operation);
        } else if(!pose.toString().endsWith(".ZERO")&&!pose.toString().endsWith(".NONE"))throw new UnsupportedGeometry("Java model pose is not static");
        attach(parent,string(args.get(0)),created);return created;
    }
    private static void attach(Part parent,String name,Part child)throws IOException {for(Part p=parent;p!=null;p=p.parent)if(p==child)throw new IOException("Java model hierarchy contains a cycle");if(child.parent!=null)child.parent.children.values().remove(child);child.parent=parent;parent.children.put(name,child);}
    private Cubes builder(ExpressionTree raw)throws IOException {
        return builder(raw,new HashSet<>(),0);
    }
    private Cubes builder(ExpressionTree raw,Set<String> resolving,int depth)throws IOException {
        if(depth>128)throw new UnsupportedGeometry("Java cube builder exceeds the static parsing depth limit");
        ExpressionTree expression=unwrap(raw);
        if(expression instanceof IdentifierTree identifier && expressions.containsKey(identifier.getName().toString())) {
            String name=identifier.getName().toString();
            if(!resolving.add(name))throw new UnsupportedGeometry("Java cube builder contains a cyclic assignment: "+name);
            try {return builder(expressions.get(name),resolving,depth+1);}
            finally {resolving.remove(name);}
        }
        if(expression instanceof MethodInvocationTree call) {
            String method=method(call);List<? extends ExpressionTree> args=call.getArguments();
            if(method.equals("create")&&call.getArguments().isEmpty()&&receiver(call)!=null
                    &&(receiver(call).toString().endsWith("CubeListBuilder")||receiver(call).toString().endsWith("ModelPartBuilder")))return new Cubes();
            Cubes result=builder(receiver(call),resolving,depth+1);
            switch(method) {
                case "texOffs","uv","setTextureOffset" -> {result.u=number(args.get(0));result.v=number(args.get(1));}
                case "mirror","mirrored" -> result.mirror=args.isEmpty()||bool(args.getFirst());
                case "addBox","cuboid" -> result.boxes.add(box(args,result));
                default -> throw new UnsupportedGeometry("Unsupported Java cube builder: "+method);
            }
            return result;
        }
        Part part=tryPart(expression);if(part!=null){Cubes result=new Cubes();result.u=part.u;result.v=part.v;result.mirror=part.mirror;return result;}
        throw new UnsupportedGeometry("Java cube builder is not a static export");
    }
    private Box box(List<? extends ExpressionTree> args,Cubes builder)throws IOException {
        if(args.size()<6||args.size()>8)throw new UnsupportedGeometry("Unsupported Java cube arguments");
        double[] values=new double[6];for(int i=0;i<6;i++)values[i]=number(args.get(i));
        for(int i=3;i<6;i++)if(values[i]<0)throw new IOException("Invalid Java cube dimensions");
        double[] inflate={0,0,0};boolean mirror=builder.mirror;
        if(args.size()>6) {
            ExpressionTree padding=unwrap(args.get(6));
            if(padding instanceof NewClassTree object) {
                if(!object.getIdentifier().toString().endsWith("CubeDeformation")&&!object.getIdentifier().toString().endsWith("Dilation"))throw new UnsupportedGeometry("Unsupported Java cube deformation");
                if(object.getArguments().size()==1)Arrays.fill(inflate,number(object.getArguments().getFirst()));
                else if(object.getArguments().size()==3)inflate=triple(object.getArguments(),0);
                else throw new UnsupportedGeometry("Invalid Java cube deformation");
            } else Arrays.fill(inflate,number(padding));
        }
        if(args.size()==8)mirror=bool(args.get(7));
        return new Box(values[0],values[1],values[2],values[3],values[4],values[5],inflate,builder.u,builder.v,mirror);
    }
    private void emit(Part part,double[] parent,PreviewMeshBuilder builder,String texture,Set<Part> visited,int depth)throws IOException {
        if(depth>64||!visited.add(part))throw new IOException("Invalid Java model hierarchy");
        double[] offset={-part.offset[0],-part.offset[1],part.offset[2]},angles={-part.rotation[0],-part.rotation[1],part.rotation[2]};
        double[] matrix=PreviewMeshBuilder.multiply(parent,PreviewMeshBuilder.multiply(PreviewMeshBuilder.translation(offset),PreviewMeshBuilder.rotation(angles)));
        for(Box box:part.boxes) {
            double[] from={-box.x-box.width-box.inflate[0],-box.y-box.height-box.inflate[1],box.z-box.inflate[2]};
            double[] to={-box.x+box.inflate[0],-box.y+box.inflate[1],box.z+box.depth+box.inflate[2]};
            Map<String,double[]> uv=PreviewMeshBuilder.boxUv(new double[]{box.width,box.height,box.depth},box.u,box.v,box.mirror);
            for(var face:PreviewMeshBuilder.boxFaces(from,to).entrySet())builder.polygon(Arrays.stream(face.getValue()).map(p->PreviewMeshBuilder.transform(p,matrix,1d/16)).toList(),
                    Arrays.asList(PreviewMeshBuilder.faceUv(uv.get(face.getKey()),textureWidth,textureHeight,0)),null,texture==null?PreviewMeshBuilder.Material.plain():new PreviewMeshBuilder.Material(texture));
        }
        for(Part child:part.children.values())emit(child,matrix,builder,texture,visited,depth+1);
    }
    private Part newPart(NewClassTree tree)throws IOException {Part part=new Part();if(tree.getArguments().size()>=3){part.u=number(tree.getArguments().get(1));part.v=number(tree.getArguments().get(2));}return part;}
    private static boolean isNewPart(ExpressionTree expression){return expression instanceof NewClassTree object&&(object.getIdentifier().toString().endsWith("ModelRenderer")||object.getIdentifier().toString().endsWith("ModelPart"));}
    private Part tryPart(ExpressionTree expression)throws IOException {
        return tryPart(expression,0);
    }
    private Part tryPart(ExpressionTree expression,int depth)throws IOException {
        if(depth>128)throw new UnsupportedGeometry("Java model receiver exceeds the static parsing depth limit");
        if(expression==null)return null;
        if(expression instanceof MethodInvocationTree call) {
            if(method(call).equals("getRoot"))return root;
            if(method(call).equals("getChild")){Part owner=tryPart(receiver(call),depth+1);if(owner==null)throw new UnsupportedGeometry("Unknown Java model parent");return owner.children.get(string(call.getArguments().getFirst()));}
            return tryPart(receiver(call),depth+1);
        }
        return parts.get(key(expression));
    }
    private Part part(ExpressionTree expression)throws IOException {Part part=tryPart(expression);if(part==null)throw new UnsupportedGeometry("Unknown Java model parent: "+expression);return part;}
    private double[] triple(List<? extends ExpressionTree> args,int start)throws IOException {if(args.size()<start+3)throw new UnsupportedGeometry("Incomplete Java model vector");return new double[]{number(args.get(start)),number(args.get(start+1)),number(args.get(start+2))};}
    private double number(ExpressionTree expression)throws IOException {double value=number(expression,0);if(!Double.isFinite(value)||Math.abs(value)>1_000_000)throw new IOException("Invalid Java model coordinate");return value;}
    private double number(ExpressionTree raw,int depth)throws IOException {
        if(depth>32)throw new UnsupportedGeometry("Java constant expression is too deep");ExpressionTree expression=unwrap(raw);
        if(expression instanceof LiteralTree literal&&literal.getValue() instanceof Number number)return number.doubleValue();
        if(expression instanceof UnaryTree unary)return switch(unary.getKind()){case UNARY_MINUS->-number(unary.getExpression(),depth+1);case UNARY_PLUS->number(unary.getExpression(),depth+1);default->throw new UnsupportedGeometry("Unsupported Java numeric expression");};
        if(expression instanceof BinaryTree binary){double a=number(binary.getLeftOperand(),depth+1),b=number(binary.getRightOperand(),depth+1);return switch(binary.getKind()){case PLUS->a+b;case MINUS->a-b;case MULTIPLY->a*b;case DIVIDE->a/b;default->throw new UnsupportedGeometry("Unsupported Java numeric operator");};}
        if(Set.of("Math.PI","Mth.PI","MathHelper.PI").contains(expression.toString()))return Math.PI;
        ExpressionTree value=expressions.get(key(expression));if(value!=null)return number(value,depth+1);
        throw new UnsupportedGeometry("Java geometry depends on a nonconstant expression: "+expression);
    }
    private static ExpressionTree unwrap(ExpressionTree expression){while(true){if(expression instanceof ParenthesizedTree p)expression=p.getExpression();else if(expression instanceof TypeCastTree c)expression=c.getExpression();else return expression;}}
    private boolean bool(ExpressionTree raw)throws IOException {ExpressionTree value=unwrap(raw);if(value instanceof LiteralTree literal&&literal.getValue() instanceof Boolean flag)return flag;throw new UnsupportedGeometry("Java model mirror flag is not constant");}
    private static String string(ExpressionTree raw)throws IOException {ExpressionTree value=unwrap(raw);if(value instanceof LiteralTree literal&&literal.getValue() instanceof String text)return text;throw new UnsupportedGeometry("Java model part name is not constant");}
    private static String key(ExpressionTree expression){return expression.toString().replaceFirst("^this\\.","");}
    private static String method(MethodInvocationTree call){return call.getMethodSelect() instanceof MemberSelectTree m?m.getIdentifier().toString():call.getMethodSelect().toString();}
    private static ExpressionTree receiver(MethodInvocationTree call){return call.getMethodSelect() instanceof MemberSelectTree member?member.getExpression():null;}
}
