package com.nephest.battlenet.sc2.extension;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class MockedStaticExtension
implements BeforeAllCallback, AfterAllCallback,
BeforeEachCallback, AfterEachCallback,
ParameterResolver, TestInstancePostProcessor
{

    private static final Namespace NAMESPACE
        = Namespace.create(MockedStaticExtension.class);

    @Override
    public void beforeAll(ExtensionContext context)
     {
        if(getPhase(context) == ExecutionPhase.CLASS) before(context);
    }

    @Override
    public void afterAll(ExtensionContext context)
    {
        if(getPhase(context) == ExecutionPhase.CLASS) after(context);
    }

    @Override
    public void beforeEach(ExtensionContext context)
     {
        if(getPhase(context) == ExecutionPhase.METHOD) before(context);
    }

    @Override
    public void afterEach(ExtensionContext context)
    {
        if(getPhase(context) == ExecutionPhase.METHOD) after(context);
    }

    @Override
    public boolean supportsParameter
    (
        ParameterContext parameterContext,
        ExtensionContext extensionContext
    )
    {
        return getMockedStaticTypeParameter(parameterContext) != null;
    }

    @Override
    public Object resolveParameter
    (
        ParameterContext parameterContext,
        ExtensionContext extensionContext
    )
    {
        Class<?> targetClass = getMockedStaticTypeParameter(parameterContext);
        return getOrComputeMockedStatic(extensionContext, targetClass);
    }

    @Override
    public void postProcessTestInstance
    (
        Object testInstance,
        ExtensionContext context
    )
    throws Exception
    {
        Set<Class<?>> targetClasses = getTargetClasses(context);
        Class<?> clazz = testInstance.getClass();
        while(clazz != null && clazz != Object.class)
        {
            for(Field field : clazz.getDeclaredFields())
            {
                Class<?> tp
                    = getMockedStaticTypeParameter(field.getGenericType());
                if(!targetClasses.contains(tp)) continue;

                MockedStatic<?> mockedStatic
                    = getOrComputeMockedStatic(context, tp);
                field.setAccessible(true);
                if(Modifier.isStatic(field.getModifiers()))
                {
                    field.set(null, mockedStatic);
                }
                else
                {
                    field.set(testInstance, mockedStatic);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static Set<Class<?>> getTargetClasses(ExtensionContext context)
    {
        return context.getTestClass()
            .map(c->c.getAnnotation(WithMockedStatic.class))
            .map(WithMockedStatic::value)
            .map(vals->Arrays.stream(vals).collect(Collectors.toSet()))
            .orElseThrow();
    }

    private static Class<?> getMockedStaticTypeParameter
    (
        Type type
    )
    {
        if(!(type instanceof ParameterizedType)) return null;

        ParameterizedType pt = (ParameterizedType) type;
        if(!pt.getRawType().equals(MockedStatic.class)) return null;

        Type[] args = pt.getActualTypeArguments();
        if(args.length == 0 && !(args[0] instanceof Class)) return null;

        return (Class<?>) args[0];
    }

    private static Class<?> getMockedStaticTypeParameter
    (
        ParameterContext parameterContext
    )
    {
        return getMockedStaticTypeParameter(
            parameterContext.getParameter().getParameterizedType()
        );
    }

    private void before(ExtensionContext context)
    {
        for(Class<?> targetClass : getTargetClasses(context))
            getOrComputeMockedStatic(context, targetClass);
    }

    private void after(ExtensionContext context)
    {
        ExtensionContext.Store store = getStore(context);
        for(Class<?> targetClass : getTargetClasses(context))
        {
            MockedStatic<?> mockedStatic = store
                .get(targetClass, MockedStatic.class);
            if(mockedStatic != null)
            {
                mockedStatic.close();
                store.remove(targetClass);
            }
        }
    }

    private Store getStore(ExtensionContext context)
    {
        return context.getStore(NAMESPACE);
    }

    private ExecutionPhase getPhase(ExtensionContext context)
    {
        return context.getTestClass()
            .map(c->c.getAnnotation(WithMockedStatic.class))
            .map(WithMockedStatic::phase)
            .orElseThrow();
    }

    private MockedStatic<?> getOrComputeMockedStatic
    (
        ExtensionContext context,
        Class<?> targetClass
    )
    {
        return getStore(context).getOrComputeIfAbsent
        (
            targetClass,
            key->Mockito.mockStatic(targetClass),
            MockedStatic.class
        );
    }

}
