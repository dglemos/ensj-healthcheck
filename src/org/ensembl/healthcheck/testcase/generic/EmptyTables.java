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

package org.ensembl.healthcheck.testcase.generic;

import java.sql.Connection;

import org.ensembl.healthcheck.DatabaseRegistryEntry;
import org.ensembl.healthcheck.DatabaseType;
import org.ensembl.healthcheck.ReportManager;
import org.ensembl.healthcheck.Species;
import org.ensembl.healthcheck.Team;
import org.ensembl.healthcheck.testcase.SingleDatabaseTestCase;
import org.ensembl.healthcheck.util.DBUtils;
import org.ensembl.healthcheck.util.Utils;
import org.ensembl.healthcheck.util.SqlTemplate;


/**
 * Check that all tables have data.
 */
public class EmptyTables extends SingleDatabaseTestCase {

	/**
	 * Creates a new instance of EmptyTablesTestCase
	 */
	public EmptyTables() {

		addToGroup("post_genebuild");
		addToGroup("release");
		addToGroup("compara-ancestral");
		addToGroup("pre-compara-handover");
		addToGroup("post-compara-handover");

		setDescription("Checks that all tables have data");

		setTeamResponsible(Team.GENEBUILD);

	}

	// ---------------------------------------------------------------------

	/**
	 * Define what tables are to be checked.
	 */
	private String[] getTablesToCheck(final DatabaseRegistryEntry dbre) {

		String[] tables = getTableNames(dbre.getConnection());
		Species species = dbre.getSpecies();
		DatabaseType type = dbre.getType();
                boolean karyotype = karyotypeExists(dbre);

		// ----------------------------------------------------
		if (species == Species.ANCESTRAL_SEQUENCES) {

			// Only a few tables need to be filled in ancestral databases
			String[] ancestral = { "meta", "coord_system", "dna", "seq_region", "assembly" };
			tables = ancestral;

		} else if (type == DatabaseType.CORE || type == DatabaseType.VEGA) {			
			//The following are views & therefore we do not care about if they are empty
			String[] views = DBUtils.getViews(dbre.getConnection()).toArray(new String[]{});
			tables = remove(tables, views);
			
			// the following tables are allowed to be empty
			String[] allowedEmpty = { "alt_allele", "assembly_exception", "data_file", "dnac", "seq_region_mapping", "unconventional_transcript_association", "operon", "operon_transcript", "operon_transcript_gene", "intron_supporting_evidence", "transcript_intron_supporting_evidence", "associated_xref", "associated_group" };
			tables = remove(tables, allowedEmpty);
			
			// ID mapping related tables are checked in a separate test case
			String[] idMapping = { "gene_archive", "peptide_archive", "mapping_session", "stable_id_event" };
			tables = remove(tables, idMapping);

                        // density_feature only populated for species with a karyotype
                        if (!karyotype) {
                                String [] density = {"density_feature", "density_type"};
                                tables = remove(tables, density);
                        }		

                	// only rat has entries in QTL tables
			if (species != Species.RATTUS_NORVEGICUS) {
				String[] qtlTables = { "qtl", "qtl_feature", "qtl_synonym" };
				tables = remove(tables, qtlTables);
			}
			
			// map, marker etc
			if (species != Species.HOMO_SAPIENS && species != Species.MUS_MUSCULUS && species != Species.RATTUS_NORVEGICUS && species != Species.DANIO_RERIO && species != Species.BOS_TAURUS && species != Species.CANIS_FAMILIARIS && species != Species.GALLUS_GALLUS && species != Species.MACACA_MULATTA && species != Species.SUS_SCROFA) {
				String[] markerTables = { "map", "marker", "marker_map_location", "marker_synonym", "marker_feature" };
				tables = remove(tables, markerTables);
			}

			// misc_feature etc
			if (species != Species.HOMO_SAPIENS && species != Species.DANIO_RERIO) {
				String[] miscTables = { "misc_feature", "misc_feature_misc_set", "misc_set", "misc_attrib" };
				tables = remove(tables, miscTables);
			}

			// only certain species have a karyotype
			if (species != Species.DROSOPHILA_MELANOGASTER && species != Species.HOMO_SAPIENS && species != Species.MUS_MUSCULUS
					&& species != Species.RATTUS_NORVEGICUS) {
				tables = Utils.removeStringFromArray(tables, "karyotype");
			}

			// only human, mouse and medaka currently have ditag data
			if (species != Species.HOMO_SAPIENS && species != Species.MUS_MUSCULUS && species != Species.ORYZIAS_LATIPES) {
				tables = Utils.removeStringFromArray(tables, "ditag");
				tables = Utils.removeStringFromArray(tables, "ditag_feature");
			}

			// only have splicing events in human, mouse, danio, rat and drosophila
			if (species != Species.HOMO_SAPIENS && species != Species.MUS_MUSCULUS && species != Species.DANIO_RERIO && species != Species.RATTUS_NORVEGICUS && species != Species.DROSOPHILA_MELANOGASTER && species != Species.CAENORHABDITIS_ELEGANS) {
				tables = Utils.removeStringFromArray(tables, "splicing_event");
				tables = Utils.removeStringFromArray(tables, "splicing_event_feature");
				tables = Utils.removeStringFromArray(tables, "splicing_transcript_pair");
			}

			// ----------------------------------------------------

		} else if (type == DatabaseType.OTHERFEATURES || type == DatabaseType.CDNA) {

			// Only a few tables need to be filled in EST
			String[] est = { "analysis", "analysis_description", "assembly", "attrib_type", "coord_system", "dna_align_feature", "external_db", "meta_coord", "meta", "misc_set", "seq_region", "seq_region_attrib", "unmapped_reason" };
			tables = est;
			
		} else if (type == DatabaseType.RNASEQ ) {

			String[] est = { "analysis", "analysis_description", "assembly", "attrib_type", "coord_system", "data_file", "dna_align_feature", "external_db", "meta_coord", "meta", "misc_set", "seq_region", "seq_region_attrib", "unmapped_reason" };
			tables = est;
		}
		// -----------------------------------------------------
		// many tables are allowed to be empty in vega and sangervega databases
		if (type == DatabaseType.VEGA) {

			String[] allowedEmpty = { "affy_array", "affy_feature", "affy_probe", "ditag", "ditag_feature", "dna", "external_synonym", "identity_xref", "map", "mapping_session", "marker", "marker_feature",
					"marker_map_location", "marker_synonym", "misc_attrib", "misc_feature", "misc_feature_misc_set", "misc_set", "prediction_exon", "prediction_transcript", "repeat_consensus",
					"repeat_feature", "simple_feature", "supporting_feature", "transcript_attrib", "unconventional_transcript_association", "splicing_transcript_pair", "splicing_event_feature",
          "splicing_event", "dependent_xref", "seq_region_synonym", "density_feature", "mapping_set", "density_type" };
			tables = remove(tables, allowedEmpty);
                        if (species == Species.DANIO_RERIO) {// for zebrafish, the following tables are also allowed to be empty
                                tables = remove(tables, new String[] { "ontology_xref" });
                        }
			}

			// remove backup tables, starting with backup_ they are allowed to be empty
			for (String table : tables) {
				if (table.startsWith("backup_")) {
					tables = remove(tables, new String[] { table });
				}
			}

		return tables;

	}

	// ---------------------------------------------------------------------

	/**
	 * Check that every table has more than 0 rows.
	 * 
	 * @param dbre
	 *          The database to check.
	 * @return true if the test passed.
	 */
	public boolean run(DatabaseRegistryEntry dbre) {

		boolean result = true;

		String[] tables = getTablesToCheck(dbre);
		Connection con = dbre.getConnection();

		for (int i = 0; i < tables.length; i++) {

			String table = tables[i];
			// logger.finest("Checking that " + table + " has rows");

			if (!tableHasRows(con, table)) {

				ReportManager.problem(this, con, table + " has zero rows");
				result = false;

			}
		}

		if (result) {
			ReportManager.correct(this, con, "All required tables have data");
		}

		return result;

	} // run

	// -----------------------------------------------------------------

	private String[] remove(final String[] src, final String[] tablesToRemove) {

		String[] result = src;

		for (int i = 0; i < tablesToRemove.length; i++) {
			result = Utils.removeStringFromArray(result, tablesToRemove[i]);
		}

		return result;

	}

	// -----------------------------------------------------------------

        protected boolean karyotypeExists(DatabaseRegistryEntry dbre) {
                Connection con = dbre.getConnection();
                SqlTemplate t = DBUtils.getSqlTemplate(dbre);
                boolean result = true;
                String sqlKaryotype = "SELECT count(*) FROM seq_region_attrib sa, attrib_type at WHERE at.attrib_type_id = sa.attrib_type_id AND code = 'karyotype_rank'";
                int karyotype = t.queryForDefaultObject(sqlKaryotype, Integer.class);
                if (karyotype == 0) {
                        result = false;
                }
                return result;
        }

        // -----------------------------------------------------------------


} // EmptyTablesTestCase
