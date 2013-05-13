/*
 * Copyright (c) 2013 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.obiba.magma.datasource.neo4j.domain;

import java.util.List;

import org.neo4j.graphdb.Direction;
import org.springframework.data.neo4j.annotation.Indexed;
import org.springframework.data.neo4j.annotation.NodeEntity;
import org.springframework.data.neo4j.annotation.RelatedTo;
import org.springframework.data.neo4j.support.index.IndexType;

@NodeEntity
public class DatasourceNode extends AbstractAttributeAwareNode implements Timestamped {

  @Indexed(indexType = IndexType.FULLTEXT, indexName = "datasource", unique = true)
  private String name;

  @RelatedTo(type = "HAS_TABLES", direction = Direction.OUTGOING)
  private List<ValueTableNode> valueTables;

  public DatasourceNode() {
  }

  public DatasourceNode(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<ValueTableNode> getValueTables() {
    return valueTables;
  }

  public void setValueTables(List<ValueTableNode> valueTables) {
    this.valueTables = valueTables;
  }

}
