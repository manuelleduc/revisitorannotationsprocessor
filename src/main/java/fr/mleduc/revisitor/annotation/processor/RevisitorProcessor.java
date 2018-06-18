package fr.mleduc.revisitor.annotation.processor;

import com.google.auto.service.AutoService;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.squareup.javapoet.*;
import org.apache.commons.lang.StringUtils;

import javax.annotation.processing.*;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static javax.lang.model.SourceVersion.RELEASE_8;
import static javax.lang.model.element.Modifier.*;

@SupportedAnnotationTypes("fr.mleduc.revisitor.annotation.processor.Revisitor")
@SupportedSourceVersion(RELEASE_8)
@AutoService(Processor.class)
public class RevisitorProcessor extends AbstractProcessor {

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        for (final TypeElement te : annotations) {
            final Set<? extends Element> classes = roundEnv.getElementsAnnotatedWith(te).stream().filter(c -> c.getAnnotationMirrors().stream().noneMatch(am -> {
                // filter derived Truffle classes
                return Objects.equals(am.getAnnotationType().asElement().getSimpleName().toString(), "GeneratedBy");
            })).collect(Collectors.toSet());

            // look for AST root element
            final TypeElement e = findRoot(classes);
            if (e != null) {
                final String[] path = splitFullyQualifiedName(e);
                writeToDisk(buildJavaFile(initRevisitorClassName(path), initRevisitorPackageName(path), initBaseClassName(path), initBasePackageName(path), classes));
            }
        }
        return true;
    }

    /**
     * Find the root element of the AST by analyzing a set of classes
     *
     * @param classes A set of classes
     * @return the root element
     */
    private TypeElement findRoot(Set<? extends Element> classes) {
        for (final Element e0 : classes) {
            final TypeElement e = ((TypeElement) e0);
            boolean res = classes.stream().anyMatch(x -> Objects.equals(e.getSuperclass(), x.asType()));

            if (!res) {
                return e;
            }
        }
        return null;
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
        final TypeSpec.Builder builder = TypeSpec.interfaceBuilder(ClassName.get(revPackages, revClassName)).addTypeVariables(classes.stream().map(
                c -> {

                    // build the bounded generics
                    final TypeVariableName baseTypeVariableName = TypeVariableName.get(c.getSimpleName() + "T");
                    return classes.stream().filter(x -> Objects.equals(((TypeElement) c).getSuperclass(), x.asType())).findAny()
                            .map(parent -> baseTypeVariableName.withBounds(TypeVariableName.get(parent.getSimpleName() + "T")))
                            .orElse(baseTypeVariableName);

                }
        ).collect(Collectors.toList()));


        final MutableGraph<Element> g = GraphBuilder.directed().build();
        for (Element e0 : classes) {
            final TypeElement e = ((TypeElement) e0);
            classes.stream().filter(x -> e.getSuperclass().equals(x.asType())).forEach(x -> {
                g.putEdge(e0, x);
            });
        }


        final Map<Element, Set<Element>> mapping = new HashMap<>();
        for (Element e0 : classes) {
            List<Element> m = new ArrayList<>();
            m.add(e0);
            m.addAll(g.predecessors(e0));
            mapping.put(e0, new HashSet<>(m));
        }


        // complete the revisitor with the factory methods
        final TypeSpec res = builder.addMethods(classes.stream().map(c ->
                MethodSpec.methodBuilder(StringUtils.uncapitalize(String.valueOf(c.getSimpleName())))
                        .addModifiers(ABSTRACT)
                        .addModifiers(PUBLIC)
                        .addParameter(ParameterSpec.builder(TypeName.get(c.asType()), "it").build())
                        .returns(TypeVariableName.get(c.getSimpleName() + "T"))
                        .build()
        ).collect(Collectors.toList()))
                .addMethods(mapping.entrySet().stream().map(c -> {
                            MethodSpec.Builder builder1 = MethodSpec.methodBuilder("$")
                                    .returns(TypeVariableName.get(c.getKey().getSimpleName() + "T"))
                                    .addModifiers(DEFAULT)
                                    .addModifiers(PUBLIC)
                                    .addParameter(ParameterSpec.builder(TypeName.get(c.getKey().asType()), "it").build());
                            return builder1.addStatement(c.getValue().stream().map(x -> "if(java.util.Objects.equals(" + x.getSimpleName() + ".class, it.getClass())) return " + StringUtils.uncapitalize(x.getSimpleName().toString()) + "((" + x.getSimpleName() + ") it);")
                                    .collect(Collectors.joining(System.lineSeparator())) + "return null").build();
                        }
                ).collect(Collectors.toList()))

                .build();
        return JavaFile.builder(revPackages, res).build();
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
        return Arrays.asList(path).subList(0, path.length - 1).stream().collect(Collectors.joining("."));
    }

    private String initRevisitorClassName(String[] s1) {
        return initBaseClassName(s1) + "Revisitor";
    }

    private String initBaseClassName(String[] s) {
        return s[s.length - 1];
    }

    private String[] splitFullyQualifiedName(Element e) {
        final String qualifiedName = e.toString();
        return qualifiedName.split("\\.");
    }
}
