package com.nephest.battlenet.sc2.extension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith({MockedStaticExtension.class})
public @interface WithMockedStatic
{

    Class<?>[] value();

    ExecutionPhase phase() default ExecutionPhase.METHOD;

}
