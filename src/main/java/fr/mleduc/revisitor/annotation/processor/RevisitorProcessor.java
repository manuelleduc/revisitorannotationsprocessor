package fr.mleduc.revisitor.annotation.processor;

import com.google.auto.service.AutoService;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import com.squareup.javapoet.*;
import org.apache.commons.lang.StringUtils;

import javax.annotation.processing.*;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static java.lang.System.lineSeparator;
import static java.util.Arrays.asList;
import static javax.lang.model.SourceVersion.RELEASE_8;
import static javax.lang.model.element.Modifier.*;

@SupportedAnnotationTypes("fr.mleduc.revisitor.annotation.processor.Revisitor")
@SupportedSourceVersion(RELEASE_8)
@AutoService(Processor.class)
public class RevisitorProcessor extends AbstractProcessor {


    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {

//        getClasses(roundEnv);
        for (final TypeElement te : annotations) {

            // filter GeneratedBy annotations.
            final Element descriptor = roundEnv.getElementsAnnotatedWith(te).iterator().next();

            final Revisitor annotation = descriptor.getAnnotation(Revisitor.class);

            final List<String> packages = Arrays.asList(annotation.packages());


            final Set<? extends Element> classes = roundEnv.getRootElements().stream().filter(e ->
                    packages.contains(processingEnv.getElementUtils().getPackageOf(e).getQualifiedName().toString())).collect(Collectors.toSet());

            final String[] path = splitFullyQualifiedName(descriptor);
            JavaFile javaFile = buildJavaFile(initRevisitorClassName(path), initRevisitorPackageName(path), initBaseClassName(path), initBasePackageName(path), classes);
            writeToDisk(javaFile);


        }
        return true;
    }


    /**
     * Generate a revisitor from a set of classes.
     *
     * @param revClassName The generated revisitor class name
     * @param revPackages  The generated revisitor package name
     * @param className    The AST root class name
     * @param packages     The AST root package name
     * @param classes      The set of classes of the AST.
     * @return The generated Revisitor class.
     */
    private JavaFile buildJavaFile(String revClassName, String revPackages, String className, String packages, Set<? extends Element> classes) {

        // build the interface
        final TypeSpec.Builder builder = TypeSpec.interfaceBuilder(ClassName.get(revPackages, revClassName))
                .addModifiers(PUBLIC)
                .addTypeVariables(classes.stream().sorted((Comparator<Element>) (o1, o2) -> o1.getSimpleName().toString().compareTo(o2.getSimpleName().toString())).map(
                        c -> {
                            // build the bounded generics
                            final TypeVariableName baseTypeVariableName = TypeVariableName.get(c.getSimpleName() + "T");
                            return classes.stream().filter(x -> Objects.equals(((TypeElement) c).getSuperclass(), x.asType())).findAny()
                                    .map(parent -> baseTypeVariableName.withBounds(TypeVariableName.get(parent.getSimpleName() + "T")))
                                    .orElse(baseTypeVariableName);

                        }
                ).collect(Collectors.toList()));


        final Map<Element, Set<Element>> mapping = prepareMapping(classes);


        // complete the revisitor with the factory methods
        final TypeSpec res = builder.addMethods(classes.stream()
                .filter(it -> !it.getModifiers().contains(Modifier.ABSTRACT))
                .map(c -> methodBuilder(StringUtils.uncapitalize('_' + StringUtils.uncapitalize(String.valueOf(c.getSimpleName()))))
                        .addModifiers(ABSTRACT)
                        .addModifiers(PUBLIC)
                        .addParameter(ParameterSpec.builder(TypeName.get(c.asType()), "it").build())
                        .returns(TypeVariableName.get(c.getSimpleName() + "T"))
                        .build())
                .collect(Collectors.toList()))
                .addMethods(mapping.entrySet().stream().map(c -> {
                            MethodSpec.Builder builder1 = methodBuilder("$")
                                    .returns(TypeVariableName.get(c.getKey().getSimpleName() + "T"))
                                    .addModifiers(DEFAULT)
                                    .addModifiers(PUBLIC)
                                    .addParameter(ParameterSpec.builder(TypeName.get(c.getKey().asType()), "it").build());
                            return builder1.addStatement(c.getValue()
                                    .stream()
                                    .filter(it -> !it.getModifiers().contains(Modifier.ABSTRACT))
                                    .map(x -> "if(java.util.Objects.equals(" + x.getSimpleName() + ".class, it.getClass())) return _" + StringUtils.uncapitalize(x.getSimpleName().toString()) + "((" + x.getSimpleName() + ") it);")
                                    .collect(Collectors.joining(lineSeparator(), "", lineSeparator())) + "return null").build();
                        }
                ).collect(Collectors.toList()))
                .build();
        return JavaFile.builder(revPackages, res).build();
    }

    private Map<Element, Set<Element>> prepareMapping(Set<? extends Element> classes) {

        final Map<Element, Set<Element>> mapping = new HashMap<>();

        if (classes.size() > 1) {

            /*
             * A grap dependency analysis is only needed if we have many element in the revisitor
             */

            final MutableGraph<Element> g = GraphBuilder.directed().build();
            classes.forEach(e0 -> classes.stream()
                    .filter(x -> {
                        TypeMirror superclass = ((TypeElement) e0).getSuperclass();
                        TypeMirror b = x.asType();
                        return Objects.equals(superclass, b);
                    })
                    .forEach(x -> g.putEdge(x, e0)));

            System.out.println(g);
            for (Element e0 : classes) {
                final List<Element> m = new ArrayList<>();
                m.add(e0);
                if (g.nodes().contains(e0))
                    m.addAll(Graphs.reachableNodes(g, e0));
                mapping.put(e0, new HashSet<>(m));
            }
        } else if (!classes.isEmpty()) {
            mapping.put(classes.iterator().next(), new HashSet<>());
        }
        return mapping;
    }

    private void writeToDisk(final JavaFile javaFile) {
        try {
            javaFile.writeTo(processingEnv.getFiler());
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    private String initRevisitorPackageName(final String[] path) {
        final String s = initBasePackageName(path);
        if (Objects.equals("", s))
            return "revisitor";
        else
            return s + ".revisitor";
    }

    private String initBasePackageName(String[] path) {
        return asList(path).subList(0, path.length - 1).stream().collect(Collectors.joining("."));
    }

    private String initRevisitorClassName(String[] s1) {
        return initBaseClassName(s1);
    }

    private String initBaseClassName(String[] s) {
        return s[s.length - 1];
    }

    private String[] splitFullyQualifiedName(Element e) {
        final String qualifiedName = e.toString();
        return qualifiedName.split("\\.");
    }
}
