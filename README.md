# revisitor-annotations-processor

![](https://jitpack.io/v/manuelleduc/revisitorannotationsprocessor.svg)

This is an attempt to port the revisitor implementation pattern (cf https://hal.inria.fr/hal-01568169) as a vanilla Java library.

## Maven

So far the only version available is the bleeding edge HEAD of the main branch of this repository.

```xml
<repositories>
  <repository>
      <id>jitpack.io</id>
      <url>https://jitpack.io</url>
  </repository>
</repositories>
```

```xml
<dependency>
    <groupId>com.github.manuelleduc</groupId>
    <artifactId>revisitorannotationsprocessor</artifactId>
    <version>-SNAPSHOT</version>
</dependency>

```

## Usage

Adding the `@Revisitor` annotation on a Java object will allow the generation of a *Revisitor* interface following the class hierarchy.

The `@Revisitor` annotation is active and is triggered during code generation, before the compilation phase.


### Example

For instance the following java definition.
```java

package demo;

@Revisitor
class A {}
class B extends A {}
class C extends A {}
class D extends C {}
```

Lead to the generation of the following interface:

```java
package demo.revisitor;


interface ARevisitor<AT, BT extends AT, CT extends AT, DT extends CT> {
  AT a(A it);
  BT b(B it);
  CT c(C it);
  DT d(D it);

  default AT $(A it) {
    if(Objects.equals(it.getClass(), A.class)) return a(it);
    if(Objects.equals(it.getClass(), B.class)) return b((B)it);
    if(Objects.equals(it.getClass(), C.class)) return c((C)it);
    if(Objects.equals(it.getClass(), D.class)) return d((D)it);
    return null;
  }

  default BT $(B it) {
    if(Objects.equals(it.getClass(), B.class)) return b(it);
    return null;
  }

  default CT $(C it) {
    if(Objects.equals(it.getClass(), C.class)) return c(it);
    if(Objects.equals(it.getClass(), D.class)) return d((D)it);
    return null;
  }

  default DT $(D it) {
    if(Objects.equals(it.getClass(), D.class)) return d(it);
    return null;
  }

}
```


## Notes

So far the generation is only working by inheritance.
A comprehensive implementation should also support classes references.
