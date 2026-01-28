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

import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.tasks.Priority;
import org.openmrs.module.tasks.SystemTask;

import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Unit tests for PlanDefinitionMapper.
 */
public class PlanDefinitionMapperTest {
	
	private PlanDefinitionMapper mapper;
	
	@Before
	public void setUp() {
		mapper = new PlanDefinitionMapper();
	}
	
	@Test
	public void toPlanDefinition_shouldMapBasicFields() {
		SystemTask systemTask = new SystemTask();
		systemTask.setUuid("test-uuid-1234");
		systemTask.setName("daily-vital-check");
		systemTask.setTitle("Daily Vital Check");
		systemTask.setDescription("Check patient vitals daily");
		systemTask.setRationale("Routine monitoring");
		systemTask.setDateCreated(new Date());
		
		PlanDefinition result = mapper.toPlanDefinition(systemTask);
		
		assertThat(result, is(notNullValue()));
		assertThat(result.getId(), is("test-uuid-1234"));
		assertThat(result.getName(), is("daily-vital-check"));
		assertThat(result.getTitle(), is("Daily Vital Check"));
		assertThat(result.getDescription(), is("Check patient vitals daily"));
		// Rationale is on action.reason, not purpose
		assertThat(result.hasPurpose(), is(false));
		assertThat(result.getStatus(), is(Enumerations.PublicationStatus.ACTIVE));
		// Verify rationale is on action.reason
		assertThat(result.hasAction(), is(true));
		assertThat(result.getActionFirstRep().hasReason(), is(true));
		assertThat(result.getActionFirstRep().getReasonFirstRep().getText(), is("Routine monitoring"));
	}
	
	@Test
	public void toPlanDefinition_shouldSetRetiredStatus() {
		SystemTask systemTask = new SystemTask();
		systemTask.setUuid("retired-uuid");
		systemTask.setName("old-task");
		systemTask.setTitle("Old Task");
		systemTask.setRetired(true);
		
		PlanDefinition result = mapper.toPlanDefinition(systemTask);
		
		assertThat(result.getStatus(), is(Enumerations.PublicationStatus.RETIRED));
	}
	
	@Test
	public void toPlanDefinition_shouldMapPriorityAsExtension() {
		SystemTask systemTask = new SystemTask();
		systemTask.setUuid("priority-uuid");
		systemTask.setName("high-priority-task");
		systemTask.setTitle("High Priority Task");
		systemTask.setPriority(Priority.HIGH);

		PlanDefinition result = mapper.toPlanDefinition(systemTask);

		assertThat(result.hasAction(), is(true));
		assertThat(result.getActionFirstRep().hasExtension(), is(true));

		boolean foundPriorityExtension = result.getActionFirstRep().getExtension().stream()
		        .anyMatch(ext -> ext.getUrl().contains("activity-priority")
		                && ext.getValue().toString().contains("high"));
		assertThat(foundPriorityExtension, is(true));
	}
	
	@Test
	public void toPlanDefinition_shouldCreateActionWithTitleAndDescription() {
		SystemTask systemTask = new SystemTask();
		systemTask.setUuid("action-uuid");
		systemTask.setName("task-with-description");
		systemTask.setTitle("Task with Description");
		systemTask.setDescription("Detailed description here");
		
		PlanDefinition result = mapper.toPlanDefinition(systemTask);
		
		assertThat(result.hasAction(), is(true));
		// Action title should use the human-readable title
		assertThat(result.getActionFirstRep().getTitle(), is("Task with Description"));
		assertThat(result.getActionFirstRep().getDescription(), is("Detailed description here"));
	}
	
	@Test
	public void toPlanDefinition_shouldReturnNullForNullInput() {
		PlanDefinition result = mapper.toPlanDefinition(null);
		assertThat(result, is(nullValue()));
	}
	
	@Test
	public void toSystemTask_shouldMapBasicFields() {
		PlanDefinition planDefinition = new PlanDefinition();
		planDefinition.setId("pd-uuid");
		planDefinition.setName("plan-definition-name");
		planDefinition.setTitle("Plan Definition Title");
		planDefinition.setDescription("Plan Description");
		planDefinition.setStatus(Enumerations.PublicationStatus.ACTIVE);
		
		// Add action with reason for rationale
		PlanDefinition.PlanDefinitionActionComponent action = planDefinition.addAction();
		org.hl7.fhir.r4.model.CodeableConcept reason = new org.hl7.fhir.r4.model.CodeableConcept();
		reason.setText("Plan Rationale");
		action.addReason(reason);
		
		SystemTask result = mapper.toSystemTask(planDefinition);
		
		assertThat(result, is(notNullValue()));
		assertThat(result.getUuid(), is("pd-uuid"));
		assertThat(result.getName(), is("plan-definition-name"));
		assertThat(result.getTitle(), is("Plan Definition Title"));
		assertThat(result.getDescription(), is("Plan Description"));
		assertThat(result.getRationale(), is("Plan Rationale"));
		assertThat(result.getRetired(), is(false));
	}
	
	@Test
	public void toSystemTask_shouldMapRetiredStatus() {
		PlanDefinition planDefinition = new PlanDefinition();
		planDefinition.setId("retired-pd-uuid");
		planDefinition.setName("Retired Plan");
		planDefinition.setStatus(Enumerations.PublicationStatus.RETIRED);
		
		SystemTask result = mapper.toSystemTask(planDefinition);
		
		assertThat(result.getRetired(), is(true));
	}
	
	@Test
	public void toSystemTask_shouldReturnNullForNullInput() {
		SystemTask result = mapper.toSystemTask(null);
		assertThat(result, is(nullValue()));
	}
	
	@Test
	public void toSystemTask_shouldMapTitleSeparately() {
		PlanDefinition planDefinition = new PlanDefinition();
		planDefinition.setId("title-uuid");
		planDefinition.setName("machine-name");
		planDefinition.setTitle("Human Readable Title");
		
		SystemTask result = mapper.toSystemTask(planDefinition);
		
		assertThat(result.getName(), is("machine-name"));
		assertThat(result.getTitle(), is("Human Readable Title"));
	}
}
