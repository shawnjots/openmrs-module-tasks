package org.openmrs.module.tasks.api.config;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.openmrs.module.fhir2.api.search.SearchQueryInclude;
import org.openmrs.module.fhir2.api.search.param.SearchParameterMap;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class TestSearchQueryInclude implements SearchQueryInclude {
	
	@Override
	public Set<IBaseResource> getIncludedResources(List resources, SearchParameterMap searchParameterMap) {
		return Collections.emptySet();
	}
}
