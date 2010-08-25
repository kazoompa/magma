package org.obiba.magma.math;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;
import org.obiba.magma.Datasource;
import org.obiba.magma.Initialisable;
import org.obiba.magma.NoSuchValueSetException;
import org.obiba.magma.Timestamps;
import org.obiba.magma.Value;
import org.obiba.magma.ValueSet;
import org.obiba.magma.ValueTable;
import org.obiba.magma.ValueType;
import org.obiba.magma.Variable;
import org.obiba.magma.VariableEntity;
import org.obiba.magma.VariableValueSource;
import org.obiba.magma.VectorSource;
import org.obiba.magma.support.AbstractValueTable;
import org.obiba.magma.support.NullTimestamps;
import org.obiba.magma.support.ValueSetBean;
import org.obiba.magma.support.VariableEntityBean;
import org.obiba.magma.support.VariableEntityProvider;
import org.obiba.magma.type.DecimalType;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * A {@code ValueTable} implementation that will compute a statistical summary for all numerical variables of another
 * table. Entities of this table are the {@code Variables} of the other. The variables of this table are the available
 * univariate statistics (mean, min, max, sum, etc.).
 */
public class SummaryStatisticsView extends AbstractValueTable implements Initialisable {

  private final ValueTable valueTable;

  private final SortedSet<VariableEntity> entities;

  private DescriptiveStatisticsProvider statsProvider = new DefaultDescriptiveStatisticsProvider();

  public SummaryStatisticsView(Datasource ds, String name, ValueTable valueTable) {
    super(ds, name);
    if(valueTable == null) throw new IllegalArgumentException("valueTable cannot be null");
    this.valueTable = valueTable;
    this.entities = new TreeSet<VariableEntity>(valueTable.getVariableEntities());
  }

  @Override
  public void initialise() {
    // Each variable in the wrapped table becomes a valueSet in this table
    super.setVariableEntityProvider(new AggregateVariableEntityProvider());
    super.addVariableValueSources(ImmutableSet.<VariableValueSource> of(//
    new StatVariableValueSource("Min"), new StatVariableValueSource("Max"), new StatVariableValueSource("Mean"),//
    new StatVariableValueSource("GeometricMean"), new StatVariableValueSource("n"), new StatVariableValueSource("Sum"),//
    new StatVariableValueSource("SumSq"), new StatVariableValueSource("StandardDeviation"), new StatVariableValueSource("Variance"),//
    new StatVariableValueSource("Skewness"), new StatVariableValueSource("Kurtosis")));
  }

  @Override
  public Timestamps getTimestamps(ValueSet valueSet) {
    return NullTimestamps.get();
  }

  @Override
  public ValueSet getValueSet(VariableEntity entity) throws NoSuchValueSetException {
    return new AggregateValueSet(entity);
  }

  private class AggregateValueSet extends ValueSetBean {

    private final DescriptiveStatistics ds;

    protected AggregateValueSet(VariableEntity entity) {
      super(SummaryStatisticsView.this, entity);
      String name = entity.getIdentifier();
      ds = statsProvider.compute(valueTable.getVariableValueSource(name), entities);
    }

    DescriptiveStatistics getStats() {
      return ds;
    }

  }

  private class StatVariableValueSource implements VariableValueSource {

    private final String statName;

    private final Method getter;

    public StatVariableValueSource(String name) {
      this.statName = name;
      this.getter = Iterables.find(Arrays.asList(DescriptiveStatistics.class.getMethods()), new Predicate<Method>() {

        @Override
        public boolean apply(Method input) {
          return input.getName().equalsIgnoreCase("get" + statName);
        }

      });
    }

    @Override
    public Variable getVariable() {
      return Variable.Builder.newVariable(statName, DecimalType.get(), getEntityType()).build();
    }

    @Override
    public Value getValue(ValueSet valueSet) {
      try {
        return DecimalType.get().valueOf((Number) getter.invoke(((AggregateValueSet) valueSet).getStats()));
      } catch(Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public ValueType getValueType() {
      return DecimalType.get();
    }

    @Override
    public VectorSource asVectorSource() {
      return null;
    }
  }

  private class AggregateVariableEntityProvider implements VariableEntityProvider {

    @Override
    public String getEntityType() {
      return "Variable";
    }

    @Override
    public Set<VariableEntity> getVariableEntities() {
      return ImmutableSet.copyOf(Iterables.transform(Iterables.filter(valueTable.getVariables(), new UnivariateFilter()), new VariableToEntity()));
    }

    @Override
    public boolean isForEntityType(String entityType) {
      return getEntityType().equals(entityType);
    }

  }

  /**
   * Transforms a Variable to a VariableEntity
   */
  private class VariableToEntity implements Function<Variable, VariableEntity> {

    @Override
    public VariableEntity apply(Variable from) {
      return new VariableEntityBean(getEntityType(), from.getName());
    }

  }

  /**
   * Returns true when a variable's type is numeric
   */
  private static class UnivariateFilter implements Predicate<Variable> {

    @Override
    public boolean apply(Variable input) {
      return input.getValueType().isNumeric();
    }

  }
}