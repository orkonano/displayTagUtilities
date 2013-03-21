package orko.dev.decorator.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value= ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Totalization {

	boolean owner() default true;
	String propertyTotalLinked() default "";
}
