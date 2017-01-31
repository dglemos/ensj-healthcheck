/*
 * Copyright [1999-2016] EMBL-European Bioinformatics Institute
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ensembl.healthcheck;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.ensembl.healthcheck.configuration.ConfigureTestGroups;
import org.ensembl.healthcheck.configurationmanager.ConfigurationException;
import org.ensembl.healthcheck.testcase.EnsTestCase;
import org.ensembl.healthcheck.testcase.SingleDatabaseTestCase;
import org.ensembl.healthcheck.util.DBUtils;

import com.mysql.jdbc.Driver;

import uk.co.flamingpenguin.jewel.cli.ArgumentValidationException;
import uk.co.flamingpenguin.jewel.cli.CliFactory;
import uk.co.flamingpenguin.jewel.cli.Option;

/**
 * Simple class to run one or more tests or groups on a single database
 * 
 * @author dstaines
 *
 */
public class StandaloneTestRunner {

    /**
     * 
     */
    private static final String WRITE_STDOUT = "-";

    /**
     * Options that specify how the tests run
     * 
     * @author dstaines
     *
     */
    public interface StandaloneTestOptions extends ConfigureTestGroups {

        @Option(helpRequest = true, description = "display help")
        boolean getHelp();

        @Option(shortName = "o", longName = "output_file", defaultValue = "failures.txt", description = "File to write any failures to (use '-' for standard out)")
        String getOutputFile();

        @Option(shortName = "v", longName="verbose", description = "Show detailed debugging output")
        boolean isVerbose();

        @Option(shortName = "d", longName="dbname", description = "Database to test")
        String getDbname();

        @Option(shortName = "u", longName="user", description = "Username for test database")
        String getUser();

        @Option(shortName = "h", longName="host", description = "Host for test database")
        String getHost();

        @Option(shortName = "p", longName="pass", description = "Password for test database")
        String getPassword();

        boolean isPassword();

        @Option(shortName = "P", longName="port", description = "Port for test database")
        int getPort();

        @Option(longName = "compara_dbname", defaultValue = "ensembl_compara_master", description = "Name of compara master database")
        String getComparaMasterDbname();

        boolean isComparaMasterDbname();
        
        @Option(longName = "compara_host", description = "Compara master database host")
        String getComparaHost();

        boolean isComparaHost();

        @Option(longName = "compara_port", description = "Compara master database port")
        int getComparaPort();

        boolean isComparaPort();

        @Option(longName = "compara_user", description = "Compara master database user")
        String getComparaUser();

        boolean isComparaUser();

        @Option(longName = "compara_pass", description = "Compara master database password")
        String getComparaPassword();

        boolean isComparaPassword();


        @Option(longName = "prod_dbname", defaultValue = "ensembl_production", description = "Name of production database")
        String getProductionDbname();

        boolean isProductionDbname();

        @Option(longName = "prod_host", description = "Production database host")
        String getProductionHost();

        boolean isProductionHost();

        @Option(longName = "prod_port", description = "Production database port")
        int getProductionPort();

        boolean isProductionPort();

        @Option(longName = "prod_user", description = "Production database user")
        String getProductionUser();

        boolean isProductionUser();

        @Option(longName = "prod_pass", description = "Production database password")
        String getProductionPassword();

        boolean isProductionPassword();

        @Option(longName = "secondary_host", description = "Secondary database host")
        String getSecondaryHost();

        boolean isSecondaryHost();

        @Option(longName = "secondary_port", description = "Secondary database port")
        int getSecondaryPort();

        boolean isSecondaryPort();

        @Option(longName = "secondary_user", description = "Secondary database user")
        String getSecondaryUser();

        boolean isSecondaryUser();

        @Option(longName = "secondary_pass", description = "Secondary database password")
        String getSecondaryPassword();

        boolean isSecondaryPassword();

        @Option(longName = "release", shortName = "r", description = "Current release")
        String getRelease();

        boolean isRelease();

    }

    /**
     * @param args
     */
    public static void main(String[] args) {

        StandaloneTestOptions options = null;
        try {
            options = CliFactory.parseArguments(StandaloneTestOptions.class, args);
        } catch (ArgumentValidationException e) {
            System.err.println(e.getMessage());
            System.exit(2);
        }

        StandaloneTestRunner runner = new StandaloneTestRunner(options);

        if (!StringUtils.isEmpty(options.getOutputFile()) && !options.getOutputFile().equals(WRITE_STDOUT)) {
            File outfile = new File(options.getOutputFile());
            if (outfile.exists()) {
                runner.getLogger().fine("Deleting existing output file " + options.getOutputFile());
                if (!outfile.delete()) {
                    runner.getLogger().fine("Could not delete existing output file " + options.getOutputFile());
                    System.exit(3);
                }
            }
        }

        StandaloneReporter reporter = new StandaloneReporter(runner.getLogger());
        ReportManager.setReporter(reporter);

        boolean result = runner.runAll();
        if (!result) {
            if (options.getOutputFile().equals(WRITE_STDOUT)) {
                runner.getLogger().severe("Failures detected - writing details to screen");
                try {
                    PrintWriter writer = new PrintWriter(System.out);
                    reporter.writeFailures(writer);
                    writer.close();
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                    System.exit(4);
                }
            } else {
                runner.getLogger().severe("Failures detected - writing details to " + options.getOutputFile());
                reporter.writeFailureFile(options.getOutputFile());
            }
        } else {
            runner.getLogger().info("Completed healthchecks with no failures");
        }
        System.exit(result ? 0 : 1);

    }

    private Logger logger;
    private final StandaloneTestOptions options;
    private DatabaseRegistryEntry productionDb;
    private DatabaseRegistryEntry comparaMasterDb;
    private DatabaseRegistryEntry testDb;
    private DatabaseServer primaryServer;
    private DatabaseServer secondaryServer;

    public StandaloneTestRunner(StandaloneTestOptions options) {
        this.options = options;
        getLogger().fine("Connecting to primary server " + options.getHost());
        DBUtils.overrideMainDatabaseServer(getPrimaryServer());
        if (options.isSecondaryHost()) {
            getLogger().fine("Connecting to secondary server " + options.getSecondaryHost());
            DBUtils.overrideSecondaryDatabaseServer(getSecondaryServer());
        }
        if (options.isRelease()) {
            DBUtils.setRelease(options.getRelease());
        }
        System.setProperty("compara_master.database", options.getComparaMasterDbname());
    }

    public Logger getLogger() {
        if (logger == null) {
            logger = Logger.getLogger(StandaloneTestRunner.class.getCanonicalName());
            ConsoleHandler localConsoleHandler = new ConsoleHandler();
            localConsoleHandler.setFormatter(new Formatter() {
                DateFormat format = new SimpleDateFormat("dd-M-yyyy hh:mm:ss");

                @Override
                public String format(LogRecord record) {
                    return String.format("%s %s %s : %s%n", format.format(new Date(record.getMillis())),
                            record.getSourceClassName(), record.getLevel().toString(), record.getMessage());
                }
            });
            if (options.isVerbose()) {
                localConsoleHandler.setLevel(Level.ALL);
                logger.setLevel(Level.ALL);
            } else {
                localConsoleHandler.setLevel(Level.INFO);
                logger.setLevel(Level.INFO);
            }
            logger.setUseParentHandlers(false);
            logger.addHandler(localConsoleHandler);
        }
        return logger;
    }

    public DatabaseRegistryEntry getProductionDb() {
        if (productionDb == null && options.isProductionHost()) {
            getLogger().info("Connecting to production database " + options.getProductionDbname());
            productionDb = new DatabaseRegistryEntry(new DatabaseServer(options.getProductionHost(),
                    String.valueOf(options.getProductionPort()), options.getProductionUser(),
                    options.isProductionPassword() ? options.getProductionPassword() : null, Driver.class.getName()),
                    options.getProductionDbname(), null, null);
        }
        return productionDb;
    }

    public DatabaseRegistryEntry getComparaMasterDb() {
        if (comparaMasterDb == null && options.isProductionHost()) {
            getLogger().info("Connecting to compara master database " + options.getComparaMasterDbname());
            comparaMasterDb = new DatabaseRegistryEntry(new DatabaseServer(options.getProductionHost(),
                    String.valueOf(options.getProductionPort()), options.getProductionUser(),
                    options.isProductionPassword() ? options.getProductionPassword() : null, Driver.class.getName()),
                    options.getComparaMasterDbname(), null, null);
        }
        return comparaMasterDb;
    }

    public DatabaseRegistryEntry getTestDb() {
        if (testDb == null) {
            getLogger().info("Connecting to test database " + options.getDbname());
            testDb = new DatabaseRegistryEntry(getPrimaryServer(), options.getDbname(), null, null);
            if(testDb.getConnection()==null) {
                throw new ConfigurationException("Test database "+options.getDbname()+" not found");
            }
        }
        return testDb;
    }

    public DatabaseServer getPrimaryServer() {
        if (primaryServer == null)
            primaryServer = new DatabaseServer(options.getHost(), String.valueOf(options.getPort()), options.getUser(),
                    options.isPassword() ? options.getPassword() : null, Driver.class.getName());
        return primaryServer;
    }

    public DatabaseServer getSecondaryServer() {
        if (secondaryServer == null) {
            secondaryServer = new DatabaseServer(options.getSecondaryHost(), String.valueOf(options.getSecondaryPort()),
                    options.getSecondaryUser(), options.isSecondaryPassword() ? options.getSecondaryPassword() : null,
                    Driver.class.getName());
        }
        return secondaryServer;
    }

    private TestRegistry testRegistry;

    private TestRegistry getTestRegistry() {
        if (testRegistry == null) {
            try {
                this.testRegistry = new ConfigurationBasedTestRegistry(options);
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (UnknownTestTypeException e) {
                throw new RuntimeException(e);
            }
        }
        return testRegistry;
    }

    public boolean runAll() {
        boolean success = true;
        for (EnsTestCase testCase : getTestRegistry().getAll()) {
            success &= runTestCase(testCase);
        }
        return success;
    }

    public boolean runTestCase(EnsTestCase test) {
        boolean success = true;
        if (SingleDatabaseTestCase.class.isAssignableFrom(test.getClass())) {
            getLogger().info("Executing testcase " + test.getName());
            test.setProductionDatabase(getProductionDb());
            test.setComparaMasterDatabase(getComparaMasterDb());
            ReportManager.startTestCase(test, getTestDb());
            boolean result = ((SingleDatabaseTestCase) test).run(getTestDb());
            ReportManager.finishTestCase(test, result, getTestDb());
            getLogger().info(test.getName() + " " + (result ? "succeeded" : "failed"));
            success &= result;
        } else {
            getLogger().fine("Skipping non-single testcase " + test.getName());
        }
        return success;
    }

}
