package fr.mleduc.revisitor.annotation.processor;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

@Inherited
@Target(TYPE)
@Retention(SOURCE)
public @interface Revisitor {
}
