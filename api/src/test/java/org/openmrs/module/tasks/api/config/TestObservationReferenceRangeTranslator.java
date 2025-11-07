package org.openmrs.module.tasks.api.config;

import org.hl7.fhir.r4.model.Observation;
import org.openmrs.Obs;
import org.openmrs.module.fhir2.api.translators.ObservationReferenceRangeTranslator;

import java.util.Collections;
import java.util.List;

public class TestObservationReferenceRangeTranslator implements ObservationReferenceRangeTranslator {
	
	@Override
	public List<Observation.ObservationReferenceRangeComponent> toFhirResource(Obs obs) {
		return Collections.emptyList();
	}
}
