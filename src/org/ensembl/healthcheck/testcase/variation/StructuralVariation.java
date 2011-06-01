/*
 * Copyright (C) 2004 EBI, GRL
 * 
 * This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package org.ensembl.healthcheck.testcase.variation;

import java.sql.Connection;

import org.ensembl.healthcheck.DatabaseRegistryEntry;
import org.ensembl.healthcheck.DatabaseType;
import org.ensembl.healthcheck.ReportManager;
import org.ensembl.healthcheck.Team;
import org.ensembl.healthcheck.testcase.SingleDatabaseTestCase;

/**
 * Check that the structural variations do not contain anomalities
 */
public class StructuralVariation extends SingleDatabaseTestCase {

	/**
	 * Creates a new instance of StructuralVariation
	 */
	public StructuralVariation() {

		addToGroup("variation");
		addToGroup("variation-release");
		
		setDescription("Checks that the structural variation tables make sense");
		setTeamResponsible(Team.VARIATION);

	}

	// ---------------------------------------------------------------------

	/**
	 * Check that the structural variation tables make sense.
	 * 
	 * @param dbre
	 *          The database to check.
	 * @return true if the test passed.
	 */
	public boolean run(DatabaseRegistryEntry dbre) {

		Connection con = dbre.getConnection();
		boolean result = true;
		
		try {
	
			result &= checkCountIsZero(con, "structural_variation", "(inner_start < seq_region_start OR inner_end > seq_region_end)");

			// At the moment, this is ok since that means an insertion relative to the reference. In the future, we should probably represent these Ensembl-style (start = end+1)
			// result &= checkCountIsZero(con, "structural_variation", "inner_start = inner_end");
	
		} catch (Exception e) {
			ReportManager.problem(this, con, "HealthCheck caused an exception: " + e.getMessage());
			result = false;
		}
		return result;

	} // run

	// -----------------------------------------------------------------

	/**
	 * This only applies to variation databases.
	 */
	public void types() {

		removeAppliesToType(DatabaseType.OTHERFEATURES);
		removeAppliesToType(DatabaseType.CDNA);
		removeAppliesToType(DatabaseType.CORE);
		removeAppliesToType(DatabaseType.VEGA);

	}

} // StructuralVariation
