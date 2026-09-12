package com.waad.tba.common.search;

import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.type.BasicType;
import org.hibernate.type.StandardBasicTypes;

/**
 * Registers the PostgreSQL waad_search_normalize(text) function with Hibernate
 * as a string-returning function. Without this metadata Hibernate treats
 * waad_search_normalize(...) as Object during JPQL validation and
 * rejects LIKE predicates at application startup.
 */
public class WaadSearchNormalizeFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        BasicType<String> stringType = functionContributions
                .getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.STRING);

        functionContributions
                .getFunctionRegistry()
                .registerPattern("waad_search_normalize", "waad_search_normalize(?1)", stringType);
    }
}
