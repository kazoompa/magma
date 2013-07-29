/*
 * Copyright (c) 2013 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.obiba.magma.datasource.mongodb;

import java.io.IOException;
import java.util.Date;

import javax.annotation.Nonnull;

import org.bson.BSONObject;
import org.obiba.magma.ValueTableWriter;
import org.obiba.magma.VariableEntity;

import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;

class MongoDBValueTableWriter implements ValueTableWriter {

  private final MongoDBValueTable table;

  MongoDBValueTableWriter(@Nonnull MongoDBValueTable table) {
    this.table = table;
  }

  @Override
  public VariableWriter writeVariables() {
    return table.createVariableWriter();
  }

  @Nonnull
  @Override
  public ValueSetWriter writeValueSet(@Nonnull VariableEntity entity) {
    return table.createValueSetWriter(entity);
  }

  @Override
  public void close() throws IOException {
    DBObject tableObject = table.asDBObject();
    BSONObject timestamps = (BSONObject) tableObject.get(MongoDBValueTable.TIMESTAMPS_FIELD);
    timestamps.put("updated", new Date());
    // insert or update
    table.getValueTableCollection().save(tableObject);
  }

}