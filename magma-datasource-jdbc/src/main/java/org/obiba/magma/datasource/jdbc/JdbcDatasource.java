package org.obiba.magma.datasource.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.sql.DataSource;
import javax.validation.constraints.NotNull;

import org.obiba.magma.MagmaRuntimeException;
import org.obiba.magma.ValueTable;
import org.obiba.magma.ValueTableWriter;
import org.obiba.magma.datasource.jdbc.support.CreateTableChangeBuilder;
import org.obiba.magma.datasource.jdbc.support.InsertDataChangeBuilder;
import org.obiba.magma.datasource.jdbc.support.MySqlEngineVisitor;
import org.obiba.magma.datasource.jdbc.support.TableUtils;
import org.obiba.magma.datasource.jdbc.support.UpdateDataChangeBuilder;
import org.obiba.magma.support.AbstractDatasource;
import org.obiba.magma.support.Initialisables;
import org.obiba.magma.type.TextType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import liquibase.change.Change;
import liquibase.change.core.RenameTableChange;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.DatabaseList;
import liquibase.database.ObjectQuotingStrategy;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.SnapshotControl;
import liquibase.snapshot.SnapshotGeneratorFactory;
import liquibase.sql.visitor.SqlVisitor;
import liquibase.statement.SqlStatement;
import liquibase.structure.core.Column;
import liquibase.structure.core.Table;

import static org.obiba.magma.datasource.jdbc.JdbcValueTableWriter.*;
import static org.obiba.magma.datasource.jdbc.support.TableUtils.newTable;

public class JdbcDatasource extends AbstractDatasource {

  private static final Logger log = LoggerFactory.getLogger(JdbcDatasource.class);

  private static final Set<String> RESERVED_NAMES = ImmutableSet
      .of(VALUE_TABLES_TABLE, VARIABLES_TABLE, VARIABLE_ATTRIBUTES_TABLE, CATEGORIES_TABLE, CATEGORY_ATTRIBUTES_TABLE);

  private static final String TYPE = "jdbc";

  private final JdbcTemplate jdbcTemplate;

  private final JdbcDatasourceSettings settings;

  private DatabaseSnapshot snapshot;

  private Map<String, String> valueTableMap;

  private PlatformTransactionManager txManager;

  private final String ESC_ENTITY_TYPE_COLUMN, ESC_VALUE_TABLES_TABLE, ESC_DATASOURCE_COLUMN, ESC_NAME_COLUMN, ESC_VALUE_TABLE_COLUMN, ESC_SQL_NAME_COLUMN;

  @SuppressWarnings("ConstantConditions")
  public JdbcDatasource(String name, @NotNull DataSource datasource, @NotNull JdbcDatasourceSettings settings,
      PlatformTransactionManager txManager) {
    super(name, TYPE);
    if(settings == null) throw new IllegalArgumentException("null settings");
    if(datasource == null) throw new IllegalArgumentException("null datasource");
    this.settings = settings;
    jdbcTemplate = new JdbcTemplate(datasource);
    this.txManager = txManager;
    ESC_ENTITY_TYPE_COLUMN = escapeColumnName(ENTITY_TYPE_COLUMN);
    ESC_VALUE_TABLES_TABLE = escapeTableName(VALUE_TABLES_TABLE);
    ESC_DATASOURCE_COLUMN = escapeColumnName(DATASOURCE_COLUMN);
    ESC_NAME_COLUMN = escapeColumnName(NAME_COLUMN);
    ESC_SQL_NAME_COLUMN = escapeColumnName(SQL_NAME_COLUMN);
    ESC_VALUE_TABLE_COLUMN = escapeColumnName(VALUE_TABLE_COLUMN);
  }

  public JdbcDatasource(String name, @NotNull DataSource datasource, @NotNull JdbcDatasourceSettings settings) {
    this(name, datasource, settings, new DataSourceTransactionManager(datasource));
  }

  public JdbcDatasource(String name, DataSource datasource, String defaultEntityType, boolean useMetadataTables,
      PlatformTransactionManager txManager) {
    this(name, datasource, new JdbcDatasourceSettings(defaultEntityType, null, null, useMetadataTables), txManager);
  }

  public JdbcDatasource(String name, DataSource datasource, String defaultEntityType, boolean useMetadataTables) {
    this(name, datasource, defaultEntityType, useMetadataTables, new DataSourceTransactionManager(datasource));
  }

  //
  // AbstractDatasource Methods
  //
  @Override
  public boolean canDropTable(String tableName) {
    return hasValueTable(tableName);
  }

  @Override
  public void dropTable(@NotNull String tableName) {
    JdbcValueTable table = (JdbcValueTable) getValueTable(tableName);
    table.drop();
    removeValueTable(table);
    valueTableMap.remove(tableName);
  }

  @Override
  public boolean canRenameTable(String tableName) {
    return hasValueTable(tableName);
  }

  @Override
  public void renameTable(String tableName, String newName) {
    if(hasValueTable(newName)) throw new MagmaRuntimeException("A table already exists with the name: " + newName);

    JdbcValueTable table = (JdbcValueTable) getValueTable(tableName);
    removeValueTable(table);
    String newSqlName = getSettings().isUseMetadataTables() ? generateSqlTableName(newName) : newName;
    getValueTableMap().remove(tableName);
    getValueTableMap().put(newName, newSqlName);

    doWithDatabase(
        new ChangeDatabaseCallback(getTableRenameChanges(tableName, table.getSqlName(), newName, newSqlName)));
    databaseChanged();

    ValueTable vt = initialiseValueTable(newName);
    Initialisables.initialise(vt);
    addValueTable(vt);
  }

  @Override
  public boolean canDrop() {
    return true;
  }

  @Override
  public void drop() {
    for(ValueTable valueTable : ImmutableList.copyOf(getValueTables())) {
      dropTable(valueTable.getName());
    }
  }

  /**
   * Returns a {@link ValueTableWriter} for writing to a new or existing {@link JdbcValueTable}.
   * <p/>
   * Note: Newly created tables have a single entity identifier column, "entity_id".
   */
  @SuppressWarnings({ "AssignmentToMethodParameter", "PMD.AvoidReassigningParameters" })
  @NotNull
  @Override
  public ValueTableWriter createWriter(@NotNull String tableName, @NotNull String entityType) {
    //noinspection ConstantConditions
    if(entityType == null) {
      entityType = settings.getDefaultEntityType();
    }

    JdbcValueTable table;

    if(hasValueTable(tableName)) {
      table = (JdbcValueTable) getValueTable(tableName);
    } else {
      JdbcValueTableSettings tableSettings = settings.getTableSettingsForMagmaTable(tableName);

      if(tableSettings == null) {
        tableSettings = new JdbcValueTableSettings(generateSqlTableName(tableName), tableName, entityType,
            settings.getDefaultEntityIdColumnName());
        settings.getTableSettings().add(tableSettings);
      }

      table = new JdbcValueTable(this, tableSettings);
      Initialisables.initialise(table);
      addValueTable(table);
      addTableMetaData(tableName, tableSettings);
    }

    return new JdbcValueTableWriter(table);
  }

  private void addTableMetaData(@NotNull String tableName, @NotNull JdbcValueTableSettings tableSettings) {
    if(!getSettings().isUseMetadataTables()) return;

    InsertDataChangeBuilder idc = InsertDataChangeBuilder.newBuilder() //
        .tableName(VALUE_TABLES_TABLE);

    if(getSettings().isMultipleDatasources()) idc.withColumn(DATASOURCE_COLUMN, getName());

    idc.withColumn(NAME_COLUMN, tableName) //
        .withColumn(ENTITY_TYPE_COLUMN, tableSettings.getEntityType()) //
        .withColumn(CREATED_COLUMN, new Date()) //
        .withColumn(UPDATED_COLUMN, new Date()) //
        .withColumn(SQL_NAME_COLUMN, tableSettings.getSqlTableName());

    doWithDatabase(new ChangeDatabaseCallback(idc.build()));
  }

  @Override
  protected void onInitialise() {
    if(getSettings().isUseMetadataTables()) {
      createMetadataTablesIfNotPresent();
    }
  }

  @Override
  protected void onDispose() {
  }

  @Override
  protected Set<String> getValueTableNames() {
    if(!getValueTableMap().isEmpty()) return getValueTableMap().keySet();

    Set<String> names = getSettings().isUseMetadataTables()
        ? getRegisteredValueTableNames()
        : getObservedValueTableNames();

    for(String name : names) {
      getValueTableMap().put(name, name);
    }

    return names;
  }

  @Override
  protected ValueTable initialiseValueTable(String tableName) {
    JdbcValueTableSettings tableSettings = settings.getTableSettingsForMagmaTable(tableName);
    String sqlTableName = getValueTableMap().containsKey(tableName) ? getValueTableMap().get(tableName) : tableName;
    String entityType = null;

    if(getSettings().isUseMetadataTables()) {
      String sql = getSettings().isMultipleDatasources()
          ? String.format("SELECT %s FROM %s WHERE %s = ? AND %s = ?", ESC_ENTITY_TYPE_COLUMN, ESC_VALUE_TABLES_TABLE,
          ESC_DATASOURCE_COLUMN, ESC_NAME_COLUMN)
          : String.format("SELECT %s FROM %s WHERE %s = ?", ESC_ENTITY_TYPE_COLUMN, ESC_VALUE_TABLES_TABLE, ESC_NAME_COLUMN);
      Object[] params = getSettings().isMultipleDatasources()
          ? new Object[] { getName(), tableName }
          : new Object[] { tableName };
      entityType = getJdbcTemplate().queryForObject(sql, params, String.class);
    }

    entityType = Strings.isNullOrEmpty(entityType) ? settings.getDefaultEntityType() : entityType;

    if(tableSettings != null) return new JdbcValueTable(this, tableSettings);

    Table table = getDatabaseSnapshot().get(newTable(sqlTableName));

    return table == null ? new JdbcValueTable(this,
        new JdbcValueTableSettings(generateSqlTableName(tableName), tableName, entityType,
            settings.getDefaultEntityIdColumnName())) : new JdbcValueTable(this, tableName, table, entityType);
  }

  //
  // Methods
  //

  @NotNull
  private Set<String> getRegisteredValueTableNames() {
    Set<String> names = new LinkedHashSet<>();
    String select = getSettings().isMultipleDatasources()
        ? String
        .format("SELECT %s FROM %s WHERE %s = '%s'", ESC_NAME_COLUMN, ESC_VALUE_TABLES_TABLE, ESC_DATASOURCE_COLUMN, getName())
        : String.format("SELECT %s FROM %s", ESC_NAME_COLUMN, ESC_VALUE_TABLES_TABLE);

    names.addAll(getJdbcTemplate().query(select, new RowMapper<String>() {
      @Override
      public String mapRow(ResultSet rs, int rowNum) throws SQLException {
        return rs.getString(NAME_COLUMN);
      }
    }));

    return names;
  }

  @NotNull
  private Set<String> getObservedValueTableNames() {
    Set<String> names = new LinkedHashSet<>();
    for(Table table : getDatabaseSnapshot().get(Table.class)) {
      String tableName = table.getName();

      if(RESERVED_NAMES.contains(tableName.toLowerCase()) ||
          !settings.getMappedTables().isEmpty() && !settings.getMappedTables().contains(tableName)) continue;

      JdbcValueTableSettings tableSettings = settings.getTableSettingsForSqlTable(tableName);

      if(tableSettings != null) {
        names.add(tableSettings.getMagmaTableName());
      } else if(!JdbcValueTable.getEntityIdentifierColumns(table).isEmpty()) {
        names.add(tableName);
      }
    }
    return names;
  }

  /**
   * Changes when a table is renamed.
   *
   * @param tableName
   * @param sqlName
   * @param newName
   * @param newSqlName
   */
  private List<Change> getTableRenameChanges(String tableName, String sqlName, String newName, String newSqlName) {
    List<Change> changes = Lists.newArrayList();
    if(!getSettings().isUseMetadataTables()) return changes;

    String whereClause = getSettings().isMultipleDatasources()
        ? String.format("%s = '%s' AND %s = '%s'", ESC_DATASOURCE_COLUMN, getName(), ESC_NAME_COLUMN, tableName)
        : String.format("%s = '%s'", ESC_NAME_COLUMN, tableName);
    changes.add(UpdateDataChangeBuilder.newBuilder().tableName(VALUE_TABLES_TABLE) //
        .withColumn(NAME_COLUMN, newName) //
        .withColumn(SQL_NAME_COLUMN, newSqlName) //
        .withColumn(UPDATED_COLUMN, new Date()) //
        .where(whereClause).build());

    whereClause = getSettings().isMultipleDatasources()
        ? String.format("%s = '%s' AND %s = '%s'", ESC_DATASOURCE_COLUMN, getName(), ESC_VALUE_TABLE_COLUMN, tableName)
        : String.format("%s = '%s'", ESC_VALUE_TABLE_COLUMN, tableName);

    changes.add(UpdateDataChangeBuilder.newBuilder().tableName(VARIABLES_TABLE) //
        .withColumn(VALUE_TABLE_COLUMN, newName) //
        .where(whereClause).build());

    changes.add(UpdateDataChangeBuilder.newBuilder().tableName(VARIABLE_ATTRIBUTES_TABLE) //
        .withColumn(VALUE_TABLE_COLUMN, newName) //
        .where(whereClause).build());

    changes.add(UpdateDataChangeBuilder.newBuilder().tableName(CATEGORIES_TABLE) //
        .withColumn(VALUE_TABLE_COLUMN, newName) //
        .where(whereClause).build());

    changes.add(UpdateDataChangeBuilder.newBuilder().tableName(CATEGORY_ATTRIBUTES_TABLE) //
        .withColumn(VALUE_TABLE_COLUMN, newName) //
        .where(whereClause).build());

    RenameTableChange rtc = new RenameTableChange();
    rtc.setOldTableName(sqlName);
    rtc.setNewTableName(newSqlName);
    changes.add(rtc);

    return changes;
  }

  private String generateSqlTableName(String tableName) {
    return getSettings().isMultipleDatasources()
        ? String.format("%s_%s", TableUtils.normalize(getName()), TableUtils.normalize(tableName))
        : TableUtils.normalize(tableName);
  }

  private Map<String, String> getValueTableMap() {
    if(valueTableMap == null) {
      valueTableMap = new HashMap<>();

      if(getSettings().isUseMetadataTables()) {
        String select = getSettings().isMultipleDatasources()
            ? String.format("SELECT %s, %s FROM %s WHERE %s = '%s'", ESC_NAME_COLUMN, ESC_SQL_NAME_COLUMN, ESC_VALUE_TABLES_TABLE,
            ESC_DATASOURCE_COLUMN, getName())
            : String.format("SELECT %s, %s FROM %s", ESC_NAME_COLUMN, ESC_SQL_NAME_COLUMN, ESC_VALUE_TABLES_TABLE);

        List<Map.Entry<String, String>> entries = getJdbcTemplate()
            .query(select, new RowMapper<Map.Entry<String, String>>() {
              @Override
              public Map.Entry<String, String> mapRow(ResultSet rs, int rowNum) throws SQLException {
                return Maps.immutableEntry(rs.getString(NAME_COLUMN), rs.getString(SQL_NAME_COLUMN));
              }
            });

        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

        for(Map.Entry<String, String> entry : entries) {
          builder.put(entry);
        }

        valueTableMap.putAll(builder.build());
      }
    }

    return valueTableMap;
  }

  public JdbcDatasourceSettings getSettings() {
    return settings;
  }

  JdbcTemplate getJdbcTemplate() {
    return jdbcTemplate;
  }

  TransactionTemplate getTransactionTemplate() {
    TransactionTemplate txTemplate = new TransactionTemplate(txManager);
    txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    return txTemplate;
  }

  public String escapeTableName(final String identifier) {
    return doWithDatabase(new DatabaseCallback<String>() {
      @Nullable
      @Override
      public String doInDatabase(Database database) throws LiquibaseException {
        return database.escapeObjectName(identifier, Table.class);
      }
    });
  }

  public String escapeColumnName(final String identifier) {
    return doWithDatabase(new DatabaseCallback<String>() {
      @Nullable
      @Override
      public String doInDatabase(Database database) throws LiquibaseException {
        return database.escapeObjectName(identifier, Column.class);
      }
    });
  }

  DatabaseSnapshot getDatabaseSnapshot() {
    if(snapshot == null) {
      snapshot = doWithDatabase(new DatabaseCallback<DatabaseSnapshot>() {
        @Override
        public DatabaseSnapshot doInDatabase(Database database) throws LiquibaseException {
          return SnapshotGeneratorFactory.getInstance()
              .createSnapshot(database.getDefaultSchema(), database, new SnapshotControl(database));
        }
      });
    }

    return snapshot;
  }

  void databaseChanged() {
    snapshot = null;
  }

  <T> T doWithDatabase(final DatabaseCallback<T> databaseCallback) {
    return jdbcTemplate.execute(new ConnectionCallback<T>() {
      @Nullable
      @Override
      public T doInConnection(Connection con) throws SQLException, DataAccessException {
        try {
          Database database = DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(con));
          database.setObjectQuotingStrategy(ObjectQuotingStrategy.QUOTE_ALL_OBJECTS);

          return databaseCallback.doInDatabase(database);
        } catch(LiquibaseException e) {
          throw new SQLException(e);
        }
      }
    });
  }

  private void createMetadataTablesIfNotPresent() {
    List<Change> changes = new ArrayList<>();
    createDatasourceMetadataTablesIfNotPresent(changes);
    createVariableMetadataTablesIfNotPresent(changes);
    createCategoryMetadataTablesIfNotPresent(changes);
    doWithDatabase(new ChangeDatabaseCallback(changes));
  }

  private void createDatasourceMetadataTablesIfNotPresent(List<Change> changes) {
    if(getDatabaseSnapshot().get(newTable(VALUE_TABLES_TABLE)) == null) {
      CreateTableChangeBuilder builder = new CreateTableChangeBuilder()//
          .tableName(VALUE_TABLES_TABLE);

      if(getSettings().isMultipleDatasources()) builder.withColumn(DATASOURCE_COLUMN, "VARCHAR(255)").primaryKey();

      builder.withColumn(NAME_COLUMN, "VARCHAR(255)").primaryKey() //
          .withColumn(ENTITY_TYPE_COLUMN, "VARCHAR(255)").notNull() //
          .withColumn(CREATED_COLUMN, "TIMESTAMP").notNull() //
          .withColumn(UPDATED_COLUMN, "TIMESTAMP").notNull() //
          .withColumn(SQL_NAME_COLUMN, "VARCHAR(255)").notNull();
      changes.add(builder.build());
    }
  }

  private void createVariableMetadataTablesIfNotPresent(List<Change> changes) {
    if(getDatabaseSnapshot().get(newTable(VARIABLES_TABLE)) == null) {
      CreateTableChangeBuilder builder = new CreateTableChangeBuilder().tableName(VARIABLES_TABLE);

      if(getSettings().isMultipleDatasources()) builder.withColumn(DATASOURCE_COLUMN, "VARCHAR(255)").primaryKey();

      builder.withColumn(VALUE_TABLE_COLUMN, "VARCHAR(255)").primaryKey() //
          .withColumn(NAME_COLUMN, "VARCHAR(255)").primaryKey() //
          .withColumn(VALUE_TYPE_COLUMN, "VARCHAR(255)").notNull() //
          .withColumn("mime_type", "VARCHAR(255)") //
          .withColumn("units", "VARCHAR(255)") //
          .withColumn("is_repeatable", "BOOLEAN") //
          .withColumn("occurrence_group", "VARCHAR(255)") //
          .withColumn("index", "INT") //
          .withColumn(SQL_NAME_COLUMN, "VARCHAR(255)").notNull();
      changes.add(builder.build());
    }

    if(getDatabaseSnapshot().get(newTable(VARIABLE_ATTRIBUTES_TABLE)) == null) {
      CreateTableChangeBuilder builder = new CreateTableChangeBuilder() //
          .tableName(VARIABLE_ATTRIBUTES_TABLE);

      if(getSettings().isMultipleDatasources()) builder.withColumn(DATASOURCE_COLUMN, "VARCHAR(255)");

      builder.withColumn(VALUE_TABLE_COLUMN, "VARCHAR(255)") //
          .withColumn(VARIABLE_COLUMN, "VARCHAR(255)") //
          .withColumn(NAMESPACE_COLUMN, "VARCHAR(20)") //
          .withColumn(NAME_COLUMN, "VARCHAR(255)") //
          .withColumn(LOCALE_COLUMN, "VARCHAR(20)") //
          .withColumn(VALUE_COLUMN, SqlTypes.sqlTypeFor(TextType.get(), SqlTypes.TEXT_TYPE_HINT_MEDIUM));
      changes.add(builder.build());
    }
  }

  private void createCategoryMetadataTablesIfNotPresent(List<Change> changes) {
    if(getDatabaseSnapshot().get(newTable(CATEGORIES_TABLE)) == null) {
      CreateTableChangeBuilder builder = new CreateTableChangeBuilder() //
          .tableName(CATEGORIES_TABLE);

      if(getSettings().isMultipleDatasources()) builder.withColumn(DATASOURCE_COLUMN, "VARCHAR(255)").primaryKey();

      builder.withColumn(VALUE_TABLE_COLUMN, "VARCHAR(255)").primaryKey() //
          .withColumn(VARIABLE_COLUMN, "VARCHAR(255)").primaryKey() //
          .withColumn(NAME_COLUMN, "VARCHAR(255)").primaryKey() //
          .withColumn(MISSING_COLUMN, "BOOLEAN").notNull();
      changes.add(builder.build());
    }

    if(getDatabaseSnapshot().get(newTable(CATEGORY_ATTRIBUTES_TABLE)) == null) {
      CreateTableChangeBuilder builder = new CreateTableChangeBuilder() //
          .tableName(CATEGORY_ATTRIBUTES_TABLE);

      if(getSettings().isMultipleDatasources()) builder.withColumn(DATASOURCE_COLUMN, "VARCHAR(255)");

      builder.withColumn(VALUE_TABLE_COLUMN, "VARCHAR(255)") //
          .withColumn(VARIABLE_COLUMN, "VARCHAR(255)") //
          .withColumn(CATEGORY_COLUMN, "VARCHAR(255)") //
          .withColumn(NAMESPACE_COLUMN, "VARCHAR(20)") //
          .withColumn(NAME_COLUMN, "VARCHAR(255)") //
          .withColumn(LOCALE_COLUMN, "VARCHAR(20)") //
          .withColumn(VALUE_COLUMN, SqlTypes.sqlTypeFor(TextType.get(), SqlTypes.TEXT_TYPE_HINT_MEDIUM));
      changes.add(builder.build());
    }
  }

  /**
   * Callback used for accessing the {@code Database} instance in a safe and consistent way.
   *
   * @param <T> the type of object returned by the callback if any
   */
  interface DatabaseCallback<T> {
    @Nullable
    T doInDatabase(Database database) throws LiquibaseException;
  }

  /**
   * An implementation of {@code DatabaseCallback} for issuing {@code Change} instances to the {@code Database}
   */
  static class ChangeDatabaseCallback implements DatabaseCallback<Object> {

    private final List<SqlVisitor> sqlVisitors;

    private final Iterable<Change> changes;

    ChangeDatabaseCallback(Change... changes) {
      this(Arrays.asList(changes));
    }

    ChangeDatabaseCallback(Iterable<Change> changes) {
      this(changes, Lists.newArrayList(new MySqlEngineVisitor()));
    }

    ChangeDatabaseCallback(Iterable<Change> changes, Iterable<? extends SqlVisitor> visitors) {
      if(changes == null) throw new IllegalArgumentException("changes cannot be null");
      if(visitors == null) throw new IllegalArgumentException("visitors cannot be null");
      this.changes = changes;
      sqlVisitors = ImmutableList.copyOf(visitors);
    }

    @Nullable
    @Override
    public Object doInDatabase(final Database database) throws LiquibaseException {
      for(Change change : changes) {
        if(log.isDebugEnabled()) {
          for(SqlStatement st : change.generateStatements(database)) {
            log.debug("Issuing statement: {}", st);
          }
        }

        database.execute(change.generateStatements(database), getFilteredVisitors(database));

        try {
          database.commit(); //explicit commit needed for postgres.
        } catch(Exception e) {
          if(!e.getMessage().contains("Commit can not be set while enrolled in a transaction")) throw e;
        }
      }

      return null;
    }

    private List<SqlVisitor> getFilteredVisitors(final Database database) {
      return Lists.newArrayList(Iterables.filter(sqlVisitors, new Predicate<SqlVisitor>() {
        @Override
        public boolean apply(@Nullable SqlVisitor input) {
          return DatabaseList.definitionMatches(input.getApplicableDbms(), database.getShortName(), true);
        }
      }));
    }
  }
}
