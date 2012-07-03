/*
 * Copyright (C) 2004 EBI, GRL
 * 
 * This library is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307 USA
 */

package org.ensembl.healthcheck.testcase.generic;

import java.sql.Connection;

import org.ensembl.healthcheck.DatabaseRegistryEntry;
import org.ensembl.healthcheck.DatabaseType;
import org.ensembl.healthcheck.ReportManager;
import org.ensembl.healthcheck.Species;
import org.ensembl.healthcheck.Team;
import org.ensembl.healthcheck.testcase.Priority;
import org.ensembl.healthcheck.testcase.SingleDatabaseTestCase;
import org.ensembl.healthcheck.util.DBUtils;

/**
 * Check that certain regions have a specific gene count.
 */
public class GeneCount extends SingleDatabaseTestCase {

	/**
	 * Creates a new instance of GeneCount
	 */
	public GeneCount() {

		addToGroup("release");
		addToGroup("pre-compara-handover");
		addToGroup("post-compara-handover");

		setDescription("Check that certain regions have a specific gene count.");
		setPriority(Priority.AMBER);
		setEffect("Causes incorrect display of gene counts and confusing contigview displays.");
		setFix("Add/remove genes.");
		setTeamResponsible(Team.GENEBUILD);

	}

	/**
	 * Run the test.
	 * 
	 * @param dbr
	 *            The database registry containing all the specified databases.
	 */
	public boolean run(DatabaseRegistryEntry dbre) {

		boolean result = true;

		// MT chromosome should have 13 protein coding genes, only applies to
		// core database
		if ( dbre.getType() == DatabaseType.CORE) {
			result &= countMTGenes(dbre.getConnection());
		}

		return result;

	} // run

	// -----------------------------------------------------------------------

	private boolean countMTGenes(Connection con) {

		boolean result = true;
                
                int MT = DBUtils.getRowCount( con, "SELECT COUNT(*) FROM seq_region WHERE name='MT'");   

                if (MT == 0) {
                        return result;
                }


		int genes = DBUtils
				.getRowCount(
						con,
						"SELECT COUNT(*) FROM coord_system cs, seq_region sr, seq_region_attrib sa, gene g WHERE cs.coord_system_id=sr.coord_system_id AND cs.attrib like 'default_version%' AND sr.name='MT' AND sr.seq_region_id=sa.seq_region_id AND sa.attrib_type_id=6 AND g.seq_region_id=sr.seq_region_id AND g.biotype='protein_coding'");

		if (genes != 13) {

			ReportManager.problem(this, con,
					"MT chromosome should have 13 protein coding genes, actually has "
							+ genes);
			result = false;

		} else {

			ReportManager.correct(this, con,
					"MT chromosome has 13 protein coding genes.");

		}

		return result;

	}

	// -----------------------------------------------------------------------

} // GeneCount

