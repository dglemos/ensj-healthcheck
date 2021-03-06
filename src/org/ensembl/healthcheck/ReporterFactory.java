/*
 * Copyright [1999-2015] Wellcome Trust Sanger Institute and the EMBL-European Bioinformatics Institute
 * Copyright [2016-2018] EMBL-European Bioinformatics Institute
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

import org.ensembl.healthcheck.reporter.DatabaseReporter;
import org.ensembl.healthcheck.reporter.TextReporter;

public class ReporterFactory {

	/**
	 * An enumeration of the kinds of reporter objects that the 
	 * reporter object can produce.
	 *
	 */
	public static enum ReporterType {
		TEXT, DATABASE
	}

	/**
	 * 
	 * @param reporterType One of "Text",  "Database"
	 * @return An implementation of a Reporter
	 * 
	 */
	public Reporter getTestReporter(ReporterType reporterType) {

		Reporter r = null;
		
		if (reporterType == ReporterType.TEXT) {
			r = new TextReporter();
		}
		if (reporterType == ReporterType.DATABASE) {
			r = new DatabaseReporter();
		}		
		return r;
	}
}
