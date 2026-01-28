/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.tasks.api.fhir;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.openmrs.module.fhir2.api.annotations.R4Provider;
import org.openmrs.module.tasks.SystemTask;
import org.openmrs.module.tasks.api.TasksService;

import java.util.ArrayList;
import java.util.List;

/**
 * FHIR Resource Provider for PlanDefinition resources (read-only). Integrates with fhir2 module to
 * expose PlanDefinition resources at /ws/fhir2/R4/PlanDefinition. Each PlanDefinition corresponds
 * to a SystemTask entity (task template). This provider is read-only; system tasks are loaded from
 * CSV configuration files.
 */
@R4Provider
public class PlanDefinitionFhirResourceProvider implements IResourceProvider {
	
	private TasksService tasksService;
	
	private PlanDefinitionMapper planDefinitionMapper;
	
	public PlanDefinitionFhirResourceProvider() {
	}
	
	public PlanDefinitionFhirResourceProvider(TasksService tasksService, PlanDefinitionMapper planDefinitionMapper) {
		this.tasksService = tasksService;
		this.planDefinitionMapper = planDefinitionMapper;
	}
	
	@Override
	public Class<PlanDefinition> getResourceType() {
		return PlanDefinition.class;
	}
	
	/**
	 * Reads a PlanDefinition resource by ID.
	 * 
	 * @param id the PlanDefinition ID (SystemTask UUID)
	 * @return the PlanDefinition resource, or null if not found
	 */
	@Read
	public PlanDefinition read(@IdParam IdType id) {
		if (id == null || StringUtils.isBlank(id.getIdPart())) {
			return null;
		}
		
		SystemTask systemTask = tasksService.getSystemTaskByUuid(id.getIdPart());
		if (systemTask == null) {
			return null;
		}
		
		return planDefinitionMapper.toPlanDefinition(systemTask);
	}
	
	/**
	 * Searches for PlanDefinition resources. By default, returns only active (non-retired) system
	 * tasks.
	 * 
	 * @param status optional status filter ("active" or "retired")
	 * @return list of PlanDefinition resources
	 */
	@Search
	public List<PlanDefinition> search(@OptionalParam(name = "status") String status) {
		List<PlanDefinition> planDefinitions = new ArrayList<>();

		// Determine whether to include retired based on status parameter
		boolean includeRetired = false;
		if (StringUtils.isNotBlank(status)) {
			if ("retired".equalsIgnoreCase(status)) {
				// Only return retired ones - we'll filter below
				includeRetired = true;
			} else if (!"active".equalsIgnoreCase(status)) {
				// Unknown status, return empty
				return planDefinitions;
			}
		}

		List<SystemTask> systemTasks = tasksService.getAllSystemTasks(includeRetired);

		for (SystemTask systemTask : systemTasks) {
			// If status filter is specified, apply it
			if (StringUtils.isNotBlank(status)) {
				boolean isRetired = Boolean.TRUE.equals(systemTask.getRetired());
				if ("active".equalsIgnoreCase(status) && isRetired) {
					continue;
				}
				if ("retired".equalsIgnoreCase(status) && !isRetired) {
					continue;
				}
			}

			PlanDefinition planDefinition = planDefinitionMapper.toPlanDefinition(systemTask);
			if (planDefinition != null) {
				planDefinitions.add(planDefinition);
			}
		}

		return planDefinitions;
	}
	
	public void setTasksService(TasksService tasksService) {
		this.tasksService = tasksService;
	}
	
	public void setPlanDefinitionMapper(PlanDefinitionMapper planDefinitionMapper) {
		this.planDefinitionMapper = planDefinitionMapper;
	}
}
