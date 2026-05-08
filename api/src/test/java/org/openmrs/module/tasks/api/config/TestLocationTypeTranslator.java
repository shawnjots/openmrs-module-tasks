/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.tasks.api.config;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.openmrs.Location;
import org.openmrs.module.fhir2.api.translators.LocationTypeTranslator;

import java.util.Collections;
import java.util.List;

public class TestLocationTypeTranslator implements LocationTypeTranslator {
	
	@Override
	public List<CodeableConcept> toFhirResource(Location location) {
		return Collections.emptyList();
	}
	
	@Override
	public Location toOpenmrsType(Location location, List<CodeableConcept> codeableConcepts) {
		return location;
	}
}
