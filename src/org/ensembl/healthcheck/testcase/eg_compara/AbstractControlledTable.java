package org.ensembl.healthcheck.testcase.eg_compara;

import java.sql.Connection;

import org.ensembl.healthcheck.util.ChecksumDatabase;
import org.ensembl.healthcheck.util.SqlTemplate;
import org.ensembl.healthcheck.util.SqlTemplate.ResultSetCallback;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.ensembl.healthcheck.DatabaseRegistryEntry;
import org.ensembl.healthcheck.DatabaseType;
import org.ensembl.healthcheck.ReportManager;
import org.ensembl.healthcheck.Team;
import org.ensembl.healthcheck.testcase.AbstractTemplatedTestCase;
import org.ensembl.healthcheck.testcase.EnsTestCase;

public abstract class AbstractControlledTable extends AbstractTemplatedTestCase {
	
	/**
	 * The name of the table that will be compared to the master. 
	 */
	protected abstract String getControlledTableName();

	/**
	 * The maximum amount of mismatches that this test is allowed to report.
	 */
	protected int getMaxReportedMismatches() {
		return 50;
	}
	
	/**
	 * How the tables should be compared.
	 */
	protected ComparisonStrategy getComparisonStrategy() {
		return ComparisonStrategy.RowByRow;
	}

	protected enum ComparisonStrategy { RowByRow, Checksum };
	
	public AbstractControlledTable() {
		appliesToType(DatabaseType.COMPARA);
		setTeamResponsible(Team.ENSEMBL_GENOMES);
	}
	
	@Override
	protected boolean runTest(DatabaseRegistryEntry dbre) {

		String controlledTableToTest = getControlledTableName();
		
		DatabaseRegistryEntry masterDbRe = getComparaMasterDatabase();
		Connection testDbConn = dbre.getConnection();
		
		if (masterDbRe==null) {
			ReportManager.problem(
				this, 
				testDbConn, 
				"Can't get connection to master database! Perhaps it is not "
				+"configured?"
			);
			return false;
		}
		
		boolean passed;
		
		if (getComparisonStrategy() == ComparisonStrategy.RowByRow) {
			
			passed = checkAllRowsInTable(
					controlledTableToTest, 
					dbre,
					masterDbRe				
			);
			
		} else {
			
			passed = checkByChecksum(
					controlledTableToTest,
					dbre,
					masterDbRe				
			);
			
			if (!passed) {
				ReportManager.problem(
					this, 
					dbre.getConnection(), 
					"The table " + controlledTableToTest + " differs from the one in the master database. This was established by using checksums so the rows in question are not shown."
				);
			}			
		}		
		return passed;
	}
	
	/**
	 * 
	 * Checks whether a table that exists in two databases has the same 
	 * content. This is done using checksums. 
	 * 
	 * @param controlledTableToTest
	 * @param testDbRe
	 * @param masterDbRe
	 * @return
	 */
	protected boolean checkByChecksum(
			final String controlledTableToTest,
			DatabaseRegistryEntry testDbRe,
			DatabaseRegistryEntry masterDbRe
		) {
		
		List<String> tablesToChecksum = new ArrayList<String>();
		tablesToChecksum.add(controlledTableToTest);
		
		String checksumValueMaster = calculateChecksumForTable(
				masterDbRe, tablesToChecksum);
		
		String checksumValueTest = calculateChecksumForTable(
				testDbRe, tablesToChecksum);
		
		return checksumValueMaster.equals(checksumValueTest);
	}

	/**
	 * 
	 * Calculates the checksum of a list of tables in a given database.
	 * 
	 * @param dbre
	 * @param tablesToChecksum
	 * @return
	 */
	protected String calculateChecksumForTable(
			DatabaseRegistryEntry dbre, 
			List<String> tablesToChecksum
		) {
		ChecksumDatabase cd = new ChecksumDatabase(dbre, tablesToChecksum);
		
		// Should be something like this:
		// {ensembl_compara_master.dnafrag=635082403}
		//
		Properties checksumMaster = cd.getChecksumFromDatabase();
		
		Set<String> entrySet = checksumMaster.stringPropertyNames();
		if (entrySet.size()!=1) {
			throw new RuntimeException("Unexpected result from checksumming (expected only one element): ");
		}
		// Will be prefixed with the database name like: 
		// "ensembl_compara_master.dnafrag"
		//
		String tableName = entrySet.iterator().next();
		
		String checksumValueMaster = (String) checksumMaster.get(tableName);
		return checksumValueMaster;
	}

	/**
	 * For every row of the table controlledTableToTest in the database 
	 * testDbre this checks, if this row also exists in the table 
	 * controlledTableToTest of masterDbRe.
	 * 
	 * @param controlledTableToTest
	 * @param testDbre
	 * @param masterDbRe
	 * @return
	 */
	protected boolean checkAllRowsInTable(
			final String controlledTableToTest,
			DatabaseRegistryEntry testDbre,
			DatabaseRegistryEntry masterDbRe
		) {
		
		final Connection testDbConn        = testDbre.getConnection();
		final Connection comparaMasterconn = masterDbRe.getConnection();
		
		final SqlTemplate sqlTemplateTestDb        = getSqlTemplate(testDbConn);  
		final SqlTemplate sqlTemplateComparaMaster = getSqlTemplate(comparaMasterconn);
		
		String fetchAllRowsFromTableSql = generateFetchAllRowsFromTableSql(testDbConn, controlledTableToTest);

		final List<String> columns = getColumnsOfTable(testDbConn, controlledTableToTest);
		
		final EnsTestCase thisTest = this;
		
		final int maxReportedMismatches = getMaxReportedMismatches();		
		
		boolean result = sqlTemplateTestDb.execute(
			fetchAllRowsFromTableSql,
			new ResultSetCallback<Boolean>() {

				@Override public Boolean process(ResultSet rs) throws SQLException {
					
					rs.setFetchSize(1);
					
					boolean allRowsPresentInMasterDb = true;
					
					int numReportedRows = 0;
					boolean numReportedRowsExceedsMaximum = false;
					
					while (rs.next() && !numReportedRowsExceedsMaximum) {

						boolean currentRowPresentInMasterDb = isCurrentRowInMaster(
							rs,
							sqlTemplateComparaMaster, 
							controlledTableToTest,
							columns 
						);
						
						allRowsPresentInMasterDb &= currentRowPresentInMasterDb;
						
						if (!currentRowPresentInMasterDb) {
							
							numReportedRows++;
							numReportedRowsExceedsMaximum = numReportedRows>maxReportedMismatches;
							
							if (numReportedRowsExceedsMaximum) {
								ReportManager.problem(
										thisTest, 
										testDbConn, 
										"The maximum of " + maxReportedMismatches + " reported rows has been reached, no further rows will be tested."
								);
							} else {							
								ReportManager.problem(
									thisTest, 
									testDbConn, 
									"Row not found in master: " + resultSetRowAsString(rs)
								);
							}
						}
					}					
					return allRowsPresentInMasterDb;
				}
			},
			// No bound parameters
			//
			new Object[0]
		);
		return result;
	}
	
	/**
	 * 
	 * Will check, if the current for of the ResultSet is present in the master database.
	 * 
	 * The columns are passed in each time so this doesn't have to be generated for each
	 * call.
	 * 
	 * @param controlledTableToTest
	 * @param sqlTemplateComparaMaster
	 * @param columns
	 * @param rsFromTestDb
	 * @throws SQLException
	 */
	protected boolean isCurrentRowInMaster(
			final ResultSet rsFromTestDb,
			final SqlTemplate sqlTemplateComparaMaster,
			final String controlledTableToTest,
			final List<String> columns 
	) throws SQLException {
		
		int numColumns = rsFromTestDb.getMetaData().getColumnCount();
		List<Object> columnValuesObjects = new ArrayList<Object>(numColumns);						

		for(int currentColIndex=0; currentColIndex<numColumns; currentColIndex++) {
			
			Object value = rsFromTestDb.getObject(currentColIndex+1);
			columnValuesObjects.add(currentColIndex, value);						
		}
		
		String countMatchingRowsSql = "select count(*) from " + controlledTableToTest + " where " + asParameterisedWhereClause(columns, columnValuesObjects);
		
		final EnsTestCase thisTest = this;
		
		boolean isInMasterDb = sqlTemplateComparaMaster.execute(
			countMatchingRowsSql, 
			new ResultSetCallback<Boolean>() {

				@Override public Boolean process(ResultSet rsFromMaster) throws SQLException {
					
					int numColumns = rsFromMaster.getMetaData().getColumnCount();
					
					if (numColumns!=1) {
						throw new RuntimeException(
							"Expected one column, but got " + numColumns + 
							" instead!"	+ resultSetRowAsString(rsFromMaster)
						);
					}
					
					rsFromMaster.next();
					
					int numberOfMatchingRowsInMaster = rsFromMaster.getInt(1);
					
					if (numberOfMatchingRowsInMaster==1) {
						return true;
					}
					if (numberOfMatchingRowsInMaster==0) {
						return false;
					}
					
					ReportManager.problem(thisTest, rsFromMaster.getStatement().getConnection(), 
						"Found " + numberOfMatchingRowsInMaster + " "
						+ "matching rows in the master database!\n"
						+ "The row searched for was:\n"
						+ resultSetRowAsString(rsFromTestDb)
					);
					
					// We return true, because there is a row in the master 
					// database. The tested database has passed for this row,
					// it is the master database that has the problem.
					//
					return true;
				}
			},
			columnValuesObjects.toArray()
		);
		return isInMasterDb;
	}
	
	/**
	 * 
	 * For the given ResultSet object this will return a stringified version 
	 * of the current row. Useful to print in error or debug messages.
	 * 
	 * @param rs
	 * @return
	 * @throws SQLException
	 */
	protected String resultSetRowAsString(ResultSet rs)
			throws SQLException {
		int numColumns = rs.getMetaData().getColumnCount();
		List<String> columnValuesStringy = new ArrayList<String>(numColumns);
		for(int currentColIndex=0; currentColIndex<numColumns; currentColIndex++) {
			
			Object value = rs.getObject(currentColIndex+1);
			String convertedValue;
			if (value==null) {
				convertedValue = "<null>";
			} else {
				convertedValue = value.toString();
			}
			columnValuesStringy.add(currentColIndex, convertedValue);							
		}
		return asCommaSeparatedString(columnValuesStringy);
	}

	/**
	 * 
	 * Generates a sql statement that will fetch the given columns of all rows
	 * of the table.
	 * 
	 * @param conn
	 * @param tableName
	 * @param columns
	 * @return
	 */
	protected String fetchAllRowsFromTableSql(
			Connection conn, 
			String tableName, 
			List<String> columns
		) {
		return "select " + asCommaSeparatedString(columns) + " from " + tableName;			
	}
	
	/**
	 * 
	 * Generates a sql statement that will fetch all columns of all rows from
	 * the given table.
	 * 
	 * @param conn
	 * @param tableName
	 * @return
	 */
	protected String generateFetchAllRowsFromTableSql(Connection conn, String tableName) {

		List<String> columns = getColumnsOfTable(conn, tableName);			
		String sql = fetchAllRowsFromTableSql(conn, tableName, columns);
			
		return sql;
	}
	
	/**
	 * 
	 * Creates a where clause for a sql statement of the form column_1=? and 
	 * column_2=? ... column_n=?. The listOfValues parameter is used to 
	 * determine whether a value will be compared with "=" or with "is". By
	 * default "=" is used, but "is" will be used for null values like 
	 * "... and column_i is null".  
	 * 
	 * @param listOfColumns
	 * @param listOfValues
	 * @return
	 */
	protected String asParameterisedWhereClause(List<String> listOfColumns, List<Object> listOfValues) {
		
		int numColumns = listOfColumns.size();
		int numValues  = listOfValues.size();
		
		if (numColumns != numValues) {
			throw new IllegalArgumentException(
				"listOfColumns ("+listOfColumns.size()+") does not have the "
				+"same size as listOfValues ("+listOfValues.size()+")!"
			);
		}		
		
		StringBuffer whereClause = new StringBuffer();
		for(int i=0; i<numColumns; i++) {
			
			// Join the individual conditions with "and", but don't start the
			// where clause with an "and".
			//
			String joiner;
			if (i==0) {
				joiner="";
			} else {
				joiner=" and ";
			}
			
			// Tests for null values have to be done with "is" and not with
			// "=?". The latter would always evaluate to false.
			//
			if (listOfValues.get(i) == null) {
				whereClause.append(joiner + listOfColumns.get(i) + " is ?");
			} else {
				whereClause.append(joiner + listOfColumns.get(i) + "=?");
			}			
		}
		return whereClause.toString();
	}
	
	/**
	 * Joins the list of strings into one comma (and space) separated string.
	 * 
	 * @param listOfStrings
	 * @return
	 */
	protected String asCommaSeparatedString(List<String> listOfStrings) {		
		return joinListOfStrings(listOfStrings, ", ");
	}
	
	/**
	 * 
	 * Joins a list of strings with a separator.
	 * 
	 * @param listOfStrings
	 * @param separator
	 * @return
	 */
	protected String joinListOfStrings(List<String> listOfStrings, String separator) {
		
		int numStrings = listOfStrings.size();
		
		StringBuffer commaSeparated = new StringBuffer();			
		
		commaSeparated.append(listOfStrings.get(0));
		for(int i=1; i<numStrings; i++) {
			commaSeparated.append(separator + listOfStrings.get(i));
		}
		return commaSeparated.toString();

	}
	
	/**
	 * 
	 * Returns the names of all tables in the database.
	 * 
	 * @param conn
	 * @return
	 * @throws SQLException
	 */
	protected List<String> getTablesOfDb(Connection conn) throws SQLException {
		
		DatabaseMetaData md = conn.getMetaData();
		
		List<String> tablesOfDb = new ArrayList<String>(); 
		
		ResultSet rs = md.getTables(null, null, "%", null);
		while (rs.next()) {
			tablesOfDb.add(rs.getString(3));
		}
		
		return tablesOfDb;
	}
	
	/**
	 * 
	 * Returns the names of all columns for a given table.
	 * 
	 * @param conn
	 * @param table
	 * @return
	 */
	protected List<String> getColumnsOfTable(Connection conn, String table) {
		
		List<String> columnsOfTable;

		try {
			DatabaseMetaData md = conn.getMetaData();
			columnsOfTable = new ArrayList<String>();
			ResultSet rs = md.getColumns(null, null, table, null);
			
			while (rs.next()) {
				columnsOfTable.add(rs.getString(4));
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		if (columnsOfTable.size()==0) {
			throw new RuntimeException("Got no columns for table " + table);
		}
		return columnsOfTable;
	}
}
