// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.extension;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.lang.reflect.Field;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

public class ValidatorExtension
implements BeforeAllCallback, AfterAllCallback, ParameterResolver, TestInstancePostProcessor
{

    private static final Namespace NAMESPACE = Namespace.create(ValidatorExtension.class);
    private static final String VALIDATOR_STORE_NAME = "validator";
    private static final String VALIDATOR_FACTORY_STORE_NAME = "validatorFactory";

    @Override
    public void beforeAll(ExtensionContext context)
    {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        getStore(context).put(VALIDATOR_FACTORY_STORE_NAME, factory);
        getStore(context).put(VALIDATOR_STORE_NAME, factory.getValidator());
    }

    @Override
    public void afterAll(ExtensionContext context)
    {
        ValidatorFactory factory = getStore(context)
            .remove(VALIDATOR_FACTORY_STORE_NAME, ValidatorFactory.class);
        if(factory != null) factory.close();
    }

    @Override
    public boolean supportsParameter
    (
        ParameterContext parameterContext,
        ExtensionContext extensionContext
    )
    {
        return parameterContext.getParameter().getType() == Validator.class;
    }

    @Override
    public Object resolveParameter
    (
        ParameterContext parameterContext,
        ExtensionContext extensionContext
    )
    {
        return getStore(extensionContext).get(VALIDATOR_STORE_NAME, Validator.class);
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context)
    throws Exception
    {
        Validator validator = getStore(context).get(VALIDATOR_STORE_NAME, Validator.class);
        for (Field field : testInstance.getClass().getDeclaredFields())
        {
            if (field.getType() == Validator.class)
            {
                field.setAccessible(true);
                field.set(testInstance, validator);
            }
        }
    }

    private Store getStore(ExtensionContext context)
    {
        return context.getStore(NAMESPACE);
    }

}
