package org.obiba.magma.datasource.hibernate.support;

import java.util.Set;

import org.hibernate.cfg.AnnotationConfiguration;
import org.obiba.magma.datasource.hibernate.domain.CategoryState;
import org.obiba.magma.datasource.hibernate.domain.DatasourceState;
import org.obiba.magma.datasource.hibernate.domain.HibernateAttribute;
import org.obiba.magma.datasource.hibernate.domain.ValueSetState;
import org.obiba.magma.datasource.hibernate.domain.ValueSetValue;
import org.obiba.magma.datasource.hibernate.domain.ValueTableState;
import org.obiba.magma.datasource.hibernate.domain.VariableEntityState;
import org.obiba.magma.datasource.hibernate.domain.VariableState;

import com.google.common.collect.ImmutableSet;

public class AnnotationConfigurationHelper {

  private final Set<Class<?>> annotatedTypes = new ImmutableSet.Builder<Class<?>>().add(DatasourceState.class, VariableEntityState.class, ValueTableState.class, ValueSetState.class, ValueSetValue.class, VariableState.class, CategoryState.class, HibernateAttribute.class).build();

  public AnnotationConfiguration configure(AnnotationConfiguration configuration) {
    for(Class<?> type : getAnnotatedTypes()) {
      configuration.addAnnotatedClass(type);
    }
    return configuration;
  }

  public Set<Class<?>> getAnnotatedTypes() {
    return annotatedTypes;
  }
}
