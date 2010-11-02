package org.ensembl.healthcheck.configuration;

import java.util.List;

import uk.co.flamingpenguin.jewel.cli.Option;

/**
 * 
 * Interface for the parameters with which the database is specified on which
 * the tests will be run.
 * 
 * @author michael
 * 
 */
public interface ConfigureDatabases {

	// The databases on which healthchecks will be run
	//
	// Update 11/10/2010: Changed from output.databases to test.databases in
	// order to prevent confusion as requested by Dan
	//
	@Option(shortName = "d", longName = "test_databases", description = "Name of databases that should be tested (e.g.: "
			+ "ensembl_compara_bacteria_5_58). If there is more than one "
			+ "database, separate with spaces. Any configured tests will "
			+ "be run on these databases. Does not support same format as output.databases!")
	List<String> getTestDatabases();

	boolean isTestDatabases();

}