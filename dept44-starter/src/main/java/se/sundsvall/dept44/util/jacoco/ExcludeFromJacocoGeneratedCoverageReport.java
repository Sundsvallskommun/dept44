package se.sundsvall.dept44.util.jacoco;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE, ElementType.CONSTRUCTOR })
public @interface ExcludeFromJacocoGeneratedCoverageReport {
}
