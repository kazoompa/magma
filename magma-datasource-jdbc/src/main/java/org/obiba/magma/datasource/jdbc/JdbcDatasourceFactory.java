package org.obiba.magma.datasource.jdbc;

import javax.sql.DataSource;
import javax.validation.constraints.NotNull;

import org.obiba.magma.AbstractDatasourceFactory;
import org.obiba.magma.Datasource;
import org.springframework.transaction.PlatformTransactionManager;

public class JdbcDatasourceFactory extends AbstractDatasourceFactory {

  private JdbcDatasourceSettings datasourceSettings;

  private DataSource dataSource;

  private PlatformTransactionManager txManager;

  @NotNull
  @Override
  public Datasource internalCreate() {
    return new JdbcDatasource(getName(), dataSource, datasourceSettings, txManager);
  }

  public void setDataSource(@NotNull DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public void setDatasourceSettings(@NotNull JdbcDatasourceSettings datasourceSettings) {
    this.datasourceSettings = datasourceSettings;
  }

  public void setDataSourceTransactionManager(@NotNull PlatformTransactionManager transactionManager) {
    this.txManager = transactionManager;
  }
}
