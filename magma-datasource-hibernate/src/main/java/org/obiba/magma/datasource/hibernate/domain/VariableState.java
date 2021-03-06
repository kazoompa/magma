/*
 * Copyright (c) 2011 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.obiba.magma.datasource.hibernate.domain;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.annotation.Nullable;
import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.obiba.magma.ValueType;
import org.obiba.magma.Variable;
import org.obiba.magma.datasource.hibernate.type.ValueTypeHibernateType;

@Entity
@Table(name = "variable", uniqueConstraints = @UniqueConstraint(columnNames = { "value_table_id", "name" }))
@TypeDef(name = "value_type", typeClass = ValueTypeHibernateType.class)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@NamedQuery(name = "allValues",
    query = "select vs.variableEntity.identifier, vsv.value, vs.id from ValueSetState as vs " + //
        "left outer join vs.values as vsv with vsv.id.variable.id = :variableId " + //
        "where vs.valueTable.id = :valueTableId " + //
        "order by vs.variableEntity.identifier")
@SuppressWarnings("UnusedDeclaration")
public class VariableState extends AbstractAttributeAwareEntity implements Timestamped {

  private static final long serialVersionUID = 1L;

  @Column(nullable = false)
  private String name;

  @ManyToOne(optional = false)
  @JoinColumn(name = "value_table_id", nullable = false, updatable = false)
  private ValueTableState valueTable;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "variable")
  @OrderColumn(name = "category_index")
  private List<CategoryState> categories;

  @Column(nullable = false)
  private String entityType;

  private String mimeType;

  private String occurrenceGroup;

  private String referencedEntityType;

  private String unit;

  @Column(name = "weight")
  private Integer index;

  @Type(type = "value_type")
  @Column(nullable = false)
  private ValueType valueType;

  @Column(nullable = false)
  private boolean repeatable;

  @ElementCollection // always cascaded
  @CollectionTable(name = "variable_attributes", joinColumns = @JoinColumn(name = "variable_id"))
  private List<AttributeState> attributes;

  public VariableState() { }

  public VariableState(ValueTableState valueTable, Variable variable) {
    this.valueTable = valueTable;
    name = variable.getName();
    copyVariableFields(variable);
  }

  /**
   * Copies all fields of the specified {@link Variable} (but not its name).
   *
   * @param variable variable
   */
  public void copyVariableFields(Variable variable) {
    entityType = variable.getEntityType();
    valueType = variable.getValueType();
    mimeType = variable.getMimeType();
    occurrenceGroup = variable.getOccurrenceGroup();
    referencedEntityType = variable.getReferencedEntityType();
    unit = variable.getUnit();
    repeatable = variable.isRepeatable();
    index = variable.getIndex();
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public ValueTableState getValueTable() {
    return valueTable;
  }

  public void setValueTable(ValueTableState valueTable) {
    this.valueTable = valueTable;
  }

  public String getEntityType() {
    return entityType;
  }

  public void setEntityType(String entityType) {
    this.entityType = entityType;
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public String getOccurrenceGroup() {
    return occurrenceGroup;
  }

  public void setOccurrenceGroup(String occurrenceGroup) {
    this.occurrenceGroup = occurrenceGroup;
  }

  public String getReferencedEntityType() {
    return referencedEntityType;
  }

  public void setReferencedEntityType(String referencedEntityType) {
    this.referencedEntityType = referencedEntityType;
  }

  public Integer getIndex() {
    return index;
  }

  public void setIndex(Integer index) {
    this.index = index;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = unit;
  }

  public ValueType getValueType() {
    return valueType;
  }

  public void setValueType(ValueType valueType) {
    this.valueType = valueType;
  }

  public void setRepeatable(boolean repeatable) {
    this.repeatable = repeatable;
  }

  public boolean isRepeatable() {
    return repeatable;
  }

  public List<CategoryState> getCategories() {
    return categories == null ? (categories = new ArrayList<>()) : categories;
  }

  public void addCategory(CategoryState state) {
    if(getCategories().add(state)) state.setVariable(this);
  }

  public void addCategory(int index, CategoryState state) {
    getCategories().add(index, state);
    state.setVariable(this);
  }

  public void removeCategory(CategoryState categoryState) {
    if(getCategories().remove(categoryState)) {
      categoryState.setUpdated(new Date());
      categoryState.setVariable(null);
    }
  }

  @Nullable
  public CategoryState getCategory(String categoryName) {
    for(CategoryState state : getCategories()) {
      if(categoryName.equals(state.getName())) {
        return state;
      }
    }
    return null;
  }

  public int getCategoryIndex(String categoryName) {
    int index = 0;
    for(CategoryState state : getCategories()) {
      if(categoryName.equals(state.getName())) {
        return index;
      }
      index++;
    }
    return -1;
  }

  @Override
  public List<AttributeState> getAttributes() {
    return attributes == null ? (attributes = new ArrayList<>()) : attributes;
  }

  @Override
  public void setAttributes(List<AttributeState> attributes) {
    this.attributes = attributes;
  }
}
