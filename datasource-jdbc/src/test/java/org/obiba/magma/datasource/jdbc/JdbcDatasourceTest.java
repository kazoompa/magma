package org.obiba.magma.datasource.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;

import javax.sql.DataSource;

import org.junit.Test;
import org.obiba.core.test.spring.Dataset;
import org.obiba.core.test.spring.DbUnitAwareTestExecutionListener;
import org.obiba.magma.Category;
import org.obiba.magma.ValueSet;
import org.obiba.magma.ValueTable;
import org.obiba.magma.ValueTableWriter;
import org.obiba.magma.Variable;
import org.obiba.magma.VariableEntity;
import org.obiba.magma.ValueTableWriter.ValueSetWriter;
import org.obiba.magma.ValueTableWriter.VariableWriter;
import org.obiba.magma.support.VariableEntityBean;
import org.obiba.magma.test.AbstractMagmaTest;
import org.obiba.magma.type.DecimalType;
import org.obiba.magma.type.IntegerType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.test.jdbc.SimpleJdbcTestUtils;

@org.junit.runner.RunWith(value = SpringJUnit4ClassRunner.class)
@org.springframework.test.context.ContextConfiguration(locations = { "/test-spring-context.xml" })
@org.springframework.test.context.transaction.TransactionConfiguration(transactionManager = "transactionManager")
@org.springframework.test.context.TestExecutionListeners(value = { DependencyInjectionTestExecutionListener.class, DirtiesContextTestExecutionListener.class, TransactionalTestExecutionListener.class, JdbcDatasourceTest.SchemaCreationTestExecutionListener.class, DbUnitAwareTestExecutionListener.class })
public class JdbcDatasourceTest extends AbstractMagmaTest {
  //
  // Instance Variables
  //

  @Autowired
  private DataSource dataSource;

  //
  // Test Methods
  //

  @Dataset
  @Test
  public void testCreateDatasourceFromExistingDatabase() {
    JdbcDatasource jdbcDatasource = new JdbcDatasource("my-datasource", dataSource, "Participant", false);
    jdbcDatasource.initialise();

    testCreateDatasourceFromExistingDatabase(jdbcDatasource);

    jdbcDatasource.dispose();
  }

  @Dataset
  @Test
  public void testCreateDatasourceFromExistingDatabaseUsingMetadataTables() {
    JdbcDatasource jdbcDatasource = new JdbcDatasource("my-datasource", dataSource, "Participant", true);
    jdbcDatasource.initialise();

    testCreateDatasourceFromExistingDatabase(jdbcDatasource);

    ValueTable valueTable = jdbcDatasource.getValueTable("BONE_DENSITY");
    Variable bdVar = valueTable.getVariable("BD");
    Variable bdVar2 = valueTable.getVariable("BD_2");

    // Verify variable attributes.
    assertTrue(bdVar.hasAttribute("description"));
    assertEquals("BD description", bdVar.getAttributeValue("description").toString());
    assertTrue(bdVar2.hasAttribute("description"));
    assertEquals("BD_2 description", bdVar2.getAttributeValue("description").toString());

    // Verify categories.
    assertTrue(hasCategory(bdVar, "PNA", "88"));
    assertTrue(hasCategory(bdVar, "DNK", "99"));

    jdbcDatasource.dispose();
  }

  @Test
  public void testCreateDatasourceFromScratch() { // i.e., no existing database
    JdbcDatasource jdbcDatasource = new JdbcDatasource("my-datasource-nodb", dataSource, "Participant", false);
    jdbcDatasource.initialise();

    // Create a new ValueTable.
    ValueTableWriter tableWriter = jdbcDatasource.createWriter("MY_TABLE", null);
    try {
      assertNotNull(tableWriter);
      assertEquals("my-datasource-nodb", jdbcDatasource.getName());
      assertTrue(jdbcDatasource.hasValueTable("MY_TABLE"));

      // Write some variables.
      VariableWriter variableWriter = tableWriter.writeVariables();
      try {
        variableWriter.writeVariable(Variable.Builder.newVariable("my_var1", IntegerType.get(), "Participant").build());
        variableWriter.writeVariable(Variable.Builder.newVariable("my_var2", DecimalType.get(), "Participant").build());
      } finally {
        try {
          variableWriter.close();
        } catch(IOException ex) {
          fail("Failed to close variableWriter");
        }
      }

      // Write a value set.
      VariableEntity myEntity1 = new VariableEntityBean("Participant", "1");
      ValueSetWriter valueSetWriter = tableWriter.writeValueSet(myEntity1);
      try {
        Variable myVar1 = jdbcDatasource.getValueTable("MY_TABLE").getVariable("my_var1");
        Variable myVar2 = jdbcDatasource.getValueTable("MY_TABLE").getVariable("my_var2");
        valueSetWriter.writeValue(myVar1, IntegerType.get().valueOf(77));
        valueSetWriter.writeValue(myVar2, IntegerType.get().valueOf(78));
      } finally {
        try {
          valueSetWriter.close();
        } catch(IOException ex) {
          fail("Failed to close valueSetWriter");
        }
      }
    } finally {
      try {
        tableWriter.close();
      } catch(IOException ex) {
        fail("Failed to close tableWriter");
      }
    }

    jdbcDatasource.dispose();
  }

  //
  // Methods
  //

  private void testCreateDatasourceFromExistingDatabase(JdbcDatasource jdbcDatasource) {
    assertNotNull(jdbcDatasource);
    assertEquals("my-datasource", jdbcDatasource.getName());

    assertEquals(1, jdbcDatasource.getValueTables().size());
    assertTrue(jdbcDatasource.hasValueTable("BONE_DENSITY"));
    ValueTable bdTable = jdbcDatasource.getValueTable("BONE_DENSITY");
    assertEquals("Participant", bdTable.getEntityType());

    // Check variables.
    int variableCount = 0;
    for(Variable variable : bdTable.getVariables()) {
      variableCount++;
    }
    assertEquals(2, variableCount);

    // Check entities and value sets.
    int valueSetCount = 0;
    for(ValueSet valueSet : bdTable.getValueSets()) {
      valueSetCount++;
    }
    assertEquals(2, valueSetCount);
    VariableEntity entity1234_2 = new VariableEntityBean(bdTable.getEntityType(), "1234-2");
    VariableEntity entity1234_3 = new VariableEntityBean(bdTable.getEntityType(), "1234-3");
    assertTrue(bdTable.hasValueSet(entity1234_2));
    assertTrue(bdTable.hasValueSet(entity1234_3));

    // Check variable values.
    ValueSet vs1234_2 = bdTable.getValueSet(entity1234_2);
    assertEquals(IntegerType.get().valueOf(64), bdTable.getValue(bdTable.getVariable("BD"), vs1234_2));
    assertEquals(IntegerType.get().valueOf(65), bdTable.getValue(bdTable.getVariable("BD_2"), vs1234_2));
    ValueSet vs1234_3 = bdTable.getValueSet(entity1234_3);
    assertEquals(IntegerType.get().valueOf(65), bdTable.getValue(bdTable.getVariable("BD"), vs1234_3));
    assertEquals(IntegerType.get().valueOf(65), bdTable.getValue(bdTable.getVariable("BD_2"), vs1234_3));

    jdbcDatasource.dispose();
  }

  private boolean hasCategory(Variable variable, String categoryName, String categoryCode) {
    if(variable.hasCategories()) {
      for(Category category : variable.getCategories()) {
        if(category.getName().equals(categoryName) && category.getCode().equals(categoryCode)) {
          return true;
        }
      }
    }
    return false;
  }

  //
  // Inner Classes
  //

  public static class SchemaCreationTestExecutionListener extends AbstractTestExecutionListener {
    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
      DataSource dataSource = (DataSource) testContext.getApplicationContext().getBean("dataSource");
      SimpleJdbcTestUtils.executeSqlScript(new SimpleJdbcTemplate(dataSource), new ClassPathResource("org/obiba/magma/datasource/jdbc/create.sql"), true);
    }

    @Override
    public void afterTestMethod(TestContext testContext) throws Exception {
      DataSource dataSource = (DataSource) testContext.getApplicationContext().getBean("dataSource");
      SimpleJdbcTestUtils.executeSqlScript(new SimpleJdbcTemplate(dataSource), new ClassPathResource("org/obiba/magma/datasource/jdbc/drop.sql"), true);
    }
  }

}