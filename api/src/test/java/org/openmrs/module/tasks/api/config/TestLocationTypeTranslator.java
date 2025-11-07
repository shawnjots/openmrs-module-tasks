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
