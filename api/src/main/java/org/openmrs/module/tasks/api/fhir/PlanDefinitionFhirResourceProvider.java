/*
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
import org.springframework.beans.factory.annotation.Qualifier;

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
	
	public PlanDefinitionFhirResourceProvider(@Qualifier("tasks.TasksService") TasksService tasksService,
	    PlanDefinitionMapper planDefinitionMapper) {
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
		
		// Translate the optional status filter into the includeRetired flag the service exposes.
		// status=retired → fetch only retired (the per-row filter below drops actives).
		// status=active or unset → fetch only actives, no per-row filter needed.
		// Anything else → empty result.
		boolean wantRetired = false;
		if (StringUtils.isNotBlank(status)) {
			if ("retired".equalsIgnoreCase(status)) {
				wantRetired = true;
			} else if (!"active".equalsIgnoreCase(status)) {
				return planDefinitions;
			}
		}
		
		List<SystemTask> systemTasks = tasksService.getAllSystemTasks(wantRetired);
		
		for (SystemTask systemTask : systemTasks) {
			if (wantRetired && !Boolean.TRUE.equals(systemTask.getRetired())) {
				// getAllSystemTasks(true) returns retired AND active rows; keep only retired here.
				continue;
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
