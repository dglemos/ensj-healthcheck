/*
  Copyright (C) 2003 EBI, GRL

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation; either
  version 2.1 of the License, or (at your option) any later version.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public
  License along with this library; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

/**
 * <p>Title: EnsTestCase.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Organisation: EMBL</p>
 * <p>Created on March 11, 2003, 1:12 PM</p>
 * @author Glenn Proctor <glenn@ebi.ac.uk>
 * @version $Revision$
 */

package org.ensembl.healthcheck;

import java.util.*;
import java.sql.*;

import org.ensembl.healthcheck.util.DBUtils;

public abstract class EnsTestCase {
    
    protected TestRunner testRunner;
    protected String databaseRegexp = "";
    protected ArrayList groups;
    
    // -------------------------------------------------------------------------
    /** 
     * Creates a new instance of EnsTestCase
     */
    public EnsTestCase() {
    
      groups = new ArrayList();
      addToGroup("all");             // everything is in all, by default
      
    } // EnsTestCase
    
    // -------------------------------------------------------------------------
    abstract TestResult run();   
    
    // -------------------------------------------------------------------------
    
    /** 
     * Get the TestRunner that is controlling this EnsTestCase.
     * @return The parent TestRunner.
     */    
    public TestRunner getTestRunner() {
      
	return testRunner;
	
    } // getTestRunner
    
    // -------------------------------------------------------------------------
    
    /** 
     * Sets up this test. <B>Must</B> be called before the object is used.
     * @param tr The TestRunner to associate with this test. Usually just <CODE>this</CODE>
     * if being called from the TestRunner.
     */    
    public void init(TestRunner tr) {

      this.testRunner = tr;
    
    } // init

    // -------------------------------------------------------------------------
    
    /** 
     * Gets the full name of this test.
     * @return The full name of the test, e.g. org.ensembl.healthcheck.EnsTestCase
     */    
    public String getTestName() {
      
      return this.getClass().getName();
      
    }
    
     // -------------------------------------------------------------------------
    
    /** 
     * Get the short form of the test name, i.e. the name of the test class without the
     * package qualifier.
     *
     * @return The short test name, e.g. EnsTestCase
     */    
    public String getShortTestName() {
      
      String longName = getTestName();

      return longName.substring(longName.lastIndexOf('.')+1);
      
    }
    
    // -------------------------------------------------------------------------
    
    /** 
     * Get a list of the names of the groups which this test case is a member of.
     * @return The list of names as Strings.
     */    
    public ArrayList getGroups() {

      return groups;
      
    }   

    /** 
     * Get a list of the groups that this test case is a member of, formatted for easy
     * printing.
     * @return The comma-separated list of group names.
     */    
    public String getCommaSeparatedGroups() {

      StringBuffer gString = new StringBuffer();
      
      java.util.Iterator it = groups.iterator();
      while (it.hasNext()) {
        gString.append((String)it.next());
	if (it.hasNext()) {
	  gString.append(",");
	}
      }
      return gString.toString();
    }   
    
    /** 
     * Convenience method for assigning this test case to several groups at once.
     * @param s A list of Strings containing the group names.
     */    
    public void setGroups(ArrayList s) {
      
	groups = s;
    
    }
    
    /** 
     * Convenience method for assigning this test case to several groups at once.
     * @param s Array of group names.
     */    
    public void setGroups(String[] s) {
      for (int i = 0; i < s.length; i++) {
	groups.add(s[i]);
      }
    }
    
    /** 
     * Add this test case to a new group.
     * If the test case is already a member of the group, a warning is printed and 
     * it is not added again.
     * @param newGroupName The name of the new group.
     */    
    public void addToGroup(String newGroupName) {

      if (!groups.contains(newGroupName)) {
	groups.add(newGroupName);
      } else {
	System.err.println("Warning: " + getTestName() + " is already a member of " + newGroupName + " not added again.");
      }
      
    }
    
    /** 
     * Remove this test case from the specified group.
     * If the test case is not a member of the specified group, a warning is printed.
     * @param groupName The name of the group from which this test case is to be removed.
     */    
    public void removeFromGroup(String groupName) {
     
      if (groups.contains(groupName)) {
	groups.remove(groupName);
      } else {
	System.err.println("Warning: " + getTestName() + " was not a memeber of " + groupName);
      }
      
    }
    
    /** 
     * Test if this test case is a member of a particular group.
     * @param group The name of the group to check.
     * @return True if this test case is a member of the named group, false otherwise.
     */    
    public boolean inGroup(String group) {
     
      return groups.contains(group);
      
    }
    
    /** 
     * Convenience method for checking if this test case belongs to any of several groups.
     * @param checkGroups The list of group names to check.
     * @return True if this test case is in any of the groups, false if it is in none.
     */    
    public boolean inGroups(ArrayList checkGroups) {
     
      boolean result = false;
      
      java.util.Iterator it = checkGroups.iterator();
      while (it.hasNext()) {
	if (inGroup((String)it.next())) {
	  result = true;
	}
      }
      return result;
      
    }
    // -------------------------------------------------------------------------
    
    /** 
     * Get a list of the databases matching a particular pattern.
     * @param databaseRegexp The Regular Expression to match.
     * @return The list of database names matched.
     */    
    public String[] getAffectedDatabases(String databaseRegexp) {
    
      return testRunner.getListOfDatabaseNames(databaseRegexp);
      
    } // getAffectedDatabases
    
    // -------------------------------------------------------------------------
    
    /** 
     * Prints (to stdout) all the databases that match the current class' database regular expression.
     * @param databaseRegexp The pattern of database names to match.
     */    
    public void printAffectedDatabases(String databaseRegexp) {
    
      System.out.println("Databases matching " + databaseRegexp + ":");
      String[] databaseList = getAffectedDatabases(databaseRegexp);
      for (int i = 0; i < databaseList.length; i++) {
	System.out.println("\t\t" + databaseList[i]);
      }
      
    } // printAffectedDatabases
    
    // -------------------------------------------------------------------------
    /**
     * Count the number of rows in a table.
     * @param con The database connection to use. Should have been opened already.
     * @param table The name of the table to analyse.
     * @return The number of rows in the table.
     */    
    public int countRowsInTable(Connection con, String table) {
            
      return getRowCount(con, "SELECT COUNT(*) FROM " + table);
            
    } // countRowsInTable
    
    // -------------------------------------------------------------------------
   
    public int getRowCount(Connection con, String sql) {
      
      int result = -1;
      
      try {
	java.sql.Statement stmt = con.createStatement();
	java.sql.ResultSet rs = stmt.executeQuery(sql);
	if (rs != null) {
	  rs.next();
	  result = rs.getInt(1);	
	}
	rs.close();
	stmt.close();
      } catch (Exception e) {
	e.printStackTrace();
      }
      
      return result;
      
    }
    
    // -------------------------------------------------------------------------
    
    public int countOrphans(Connection con, String table1, String col1, String table2, String col2, boolean oneWayOnly) {
      
      int resultLeft, resultRight;
      
      String sql = "SELECT COUNT(*) FROM " + table1 + 
                   " LEFT JOIN " + table2 + " ON " + table1 + "." + col1 + " = " + table2 + "." + col2 + 
		   " WHERE " + table2 + "." + col2 + " iS NULL";

      resultLeft = getRowCount(con, sql);
      
      if (!oneWayOnly) {
	// and the other way ... (a right join?)
	sql = "SELECT COUNT(*) FROM " + table2 + 
	      " LEFT JOIN " + table1 + " ON " + table2 + "." + col2 + " = " + table1 + "." + col1 + 
	      " WHERE " + table1 + "." + col1 + " IS NULL";
     
	resultRight = getRowCount(con, sql);
      } else {
	 resultRight = 0;
      }
      
      System.out.println("Left: " + resultLeft + " Right: " + resultRight);
      
      return resultLeft + resultRight;
      
      
    } // countOrphans
    
    // -------------------------------------------------------------------------
    
    public boolean checkSameSQLResult(String sql, String dbRegexp) {
      
      ArrayList resultSetGroup = new ArrayList();
      ArrayList statements = new ArrayList();
      
      org.ensembl.healthcheck.util.DatabaseConnectionIterator dcit = testRunner.getDatabaseConnectionIterator(getAffectedDatabases(dbRegexp));
      
      while (dcit.hasNext()) {
	
	Connection con = (Connection)dcit.next();
	
	try {
	  java.sql.Statement stmt = con.createStatement();
	  java.sql.ResultSet rs = stmt.executeQuery(sql);
	  if (rs != null) {
	    resultSetGroup.add(rs);
	  }
	  System.out.println("Added ResultSet for " + sql);
	  // note that the Statement can't be closed here as we use the ResultSet elsewhere
	  // so store a reference to it for closing later
	  statements.add(stmt);
	  con.close();
	} catch (Exception e) {
	  e.printStackTrace();
	}
      }
      
      boolean same = DBUtils.compareResultSetGroup(resultSetGroup);
     
      Iterator it = statements.iterator();
      while (it.hasNext()) {
	  try {
	    ((Statement)it.next()).close();
	  } catch (Exception e) {
	  e.printStackTrace();
	}
      }
      
      return same;
    
    } // checkSameSQLResult
    
    // -------------------------------------------------------------------------
    
} // EnsTestCase
