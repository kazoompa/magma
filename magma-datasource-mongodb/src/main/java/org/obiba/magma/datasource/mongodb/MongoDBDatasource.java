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

import java.net.UnknownHostException;
import java.util.Set;

import javax.annotation.Nonnull;

import org.obiba.magma.MagmaRuntimeException;
import org.obiba.magma.NoSuchValueTableException;
import org.obiba.magma.ValueTable;
import org.obiba.magma.ValueTableWriter;
import org.obiba.magma.support.AbstractDatasource;

import com.google.common.collect.ImmutableSet;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.MongoClient;

public class MongoDBDatasource extends AbstractDatasource {

  public static final String TYPE = "mongodb";

  static final String DEFAULT_HOST = "localhost";

  static final int DEFAULT_PORT = 27017;

  static final String VALUE_TABLE_COLLECTION = "value_table";

  private final String host;

  private final int port;

  private MongoClient client;

  public MongoDBDatasource(@Nonnull String name) {
    this(name, DEFAULT_HOST, DEFAULT_PORT);
  }

  public MongoDBDatasource(@Nonnull String name, String host) {
    this(name, host, DEFAULT_PORT);
  }

  public MongoDBDatasource(@Nonnull String name, String host, int port) {
    super(name, TYPE);
    this.host = host;
    this.port = port;
  }

  MongoClient getClient() {
    return client;
  }

  DB getDB() {
    return client.getDB(getName());
  }

  DBCollection getValueTableCollection() {
    return getDB().getCollection(VALUE_TABLE_COLLECTION);
  }

  @Override
  protected void onInitialise() {
    try {
      client = new MongoClient(host, port);
    } catch(UnknownHostException e) {
      throw new MagmaRuntimeException(e);
    }
  }

  @Nonnull
  @Override
  public ValueTableWriter createWriter(@Nonnull String tableName, @Nonnull String entityType) {
    MongoDBValueTable valueTable = null;
    if(hasValueTable(tableName)) {
      valueTable = (MongoDBValueTable) getValueTable(tableName);
    } else {
      addValueTable(valueTable = new MongoDBValueTable(this, tableName, entityType));
    }
    return new MongoDBValueTableWriter(valueTable);
  }

  @Override
  protected Set<String> getValueTableNames() {
    DBCursor cursor = getValueTableCollection()
        .find(new BasicDBObject(), BasicDBObjectBuilder.start().add("name", 1).get());
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    try {
      while(cursor.hasNext()) {
        builder.add(cursor.next().get("name").toString());
      }
    } finally {
      cursor.close();
    }
    return builder.build();
  }

  @Override
  public Set<ValueTable> getValueTables() {
    return super.getValueTables();    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  protected ValueTable initialiseValueTable(String tableName) {
    return new MongoDBValueTable(this, tableName);
  }
}