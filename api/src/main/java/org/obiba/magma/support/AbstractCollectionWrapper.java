package org.obiba.magma.support;

import java.util.Set;

import org.obiba.magma.Collection;
import org.obiba.magma.Datasource;
import org.obiba.magma.NoSuchVariableException;
import org.obiba.magma.ValueSet;
import org.obiba.magma.Variable;
import org.obiba.magma.VariableEntity;
import org.obiba.magma.VariableValueSource;

public abstract class AbstractCollectionWrapper implements CollectionWrapper {

  Collection collection;

  @Override
  public Datasource getDatasource() {
    return collection.getDatasource();
  }

  @Override
  public Set<VariableEntity> getEntities(String entityType) {
    return collection.getEntities(entityType);
  }

  @Override
  public Set<String> getEntityTypes() {
    return collection.getEntityTypes();
  }

  @Override
  public String getName() {
    return collection.getName();
  }

  @Override
  public VariableValueSource getVariableValueSource(String entityType, String variableName) throws NoSuchVariableException {
    return collection.getVariableValueSource(entityType, variableName);
  }

  @Override
  public Set<VariableValueSource> getVariableValueSources(String entityType) {
    return collection.getVariableValueSources(entityType);
  }

  @Override
  public Set<Variable> getVariables() {
    return collection.getVariables();
  }

  @Override
  public ValueSet loadValueSet(VariableEntity entity) {
    return collection.loadValueSet(entity);
  }

  @Override
  public Collection getWrappedCollection() {
    return collection;
  }
}
