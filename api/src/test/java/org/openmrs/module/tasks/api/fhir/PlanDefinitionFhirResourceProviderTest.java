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
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.api.context.Context;
import org.openmrs.module.tasks.Priority;
import org.openmrs.module.tasks.SystemTask;
import org.openmrs.module.tasks.api.TasksService;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import java.util.List;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Integration tests for PlanDefinitionFhirResourceProvider. Tests that system tasks are correctly
 * exposed via the FHIR PlanDefinition API.
 */
public class PlanDefinitionFhirResourceProviderTest extends BaseModuleContextSensitiveTest {
	
	@Override
	public Properties getRuntimeProperties() {
		Properties props = super.getRuntimeProperties();
		props.setProperty("module.allow_web_admin", "false");
		return props;
	}
	
	private PlanDefinitionFhirResourceProvider provider;
	
	private TasksService tasksService;
	
	private PlanDefinitionMapper mapper;
	
	@Before
	public void setUp() throws Exception {
		tasksService = Context.getService(TasksService.class);
		mapper = new PlanDefinitionMapper();
		
		provider = new PlanDefinitionFhirResourceProvider();
		provider.setTasksService(tasksService);
		provider.setPlanDefinitionMapper(mapper);
	}
	
	@Test
	public void read_shouldReturnPlanDefinitionForExistingSystemTask() {
		// Given: A system task exists
		SystemTask systemTask = new SystemTask();
		systemTask.setName("test-task-template");
		systemTask.setTitle("Test Task Template");
		systemTask.setDescription("A test template");
		systemTask.setPriority(Priority.HIGH);
		systemTask.setRationale("Testing purposes");
		tasksService.saveSystemTask(systemTask);
		
		Context.flushSession();
		Context.clearSession();
		
		// When: Reading by ID
		PlanDefinition result = provider.read(new IdType(systemTask.getUuid()));
		
		// Then: Should return the PlanDefinition
		assertThat(result, is(notNullValue()));
		assertThat(result.getId(), is(systemTask.getUuid()));
		assertThat(result.getName(), is("test-task-template"));
		assertThat(result.getTitle(), is("Test Task Template"));
		assertThat(result.getDescription(), is("A test template"));
		assertThat(result.getStatus(), is(Enumerations.PublicationStatus.ACTIVE));
		// Rationale is on action.reason, not purpose
		assertThat(result.hasAction(), is(true));
		assertThat(result.getActionFirstRep().getReasonFirstRep().getText(), is("Testing purposes"));
	}
	
	@Test
	public void read_shouldReturnNullForNonExistentSystemTask() {
		// When: Reading a non-existent ID
		PlanDefinition result = provider.read(new IdType("non-existent-uuid"));
		
		// Then: Should return null
		assertThat(result, is(nullValue()));
	}
	
	@Test
	public void read_shouldReturnRetiredPlanDefinition() {
		// Given: A retired system task exists
		SystemTask systemTask = new SystemTask();
		systemTask.setName("retired-template");
		systemTask.setTitle("Retired Template");
		systemTask.setRetired(true);
		systemTask.setRetireReason("No longer used");
		tasksService.saveSystemTask(systemTask);
		
		Context.flushSession();
		Context.clearSession();
		
		// When: Reading by ID
		PlanDefinition result = provider.read(new IdType(systemTask.getUuid()));
		
		// Then: Should return the PlanDefinition with retired status
		assertThat(result, is(notNullValue()));
		assertThat(result.getStatus(), is(Enumerations.PublicationStatus.RETIRED));
	}
	
	@Test
	public void search_shouldReturnActiveSystemTasks() {
		// Given: Multiple system tasks exist
		SystemTask active1 = new SystemTask();
		active1.setName("active-template-1");
		active1.setTitle("Active Template 1");
		active1.setPriority(Priority.HIGH);
		tasksService.saveSystemTask(active1);

		SystemTask active2 = new SystemTask();
		active2.setName("active-template-2");
		active2.setTitle("Active Template 2");
		active2.setPriority(Priority.MEDIUM);
		tasksService.saveSystemTask(active2);

		SystemTask retired = new SystemTask();
		retired.setName("retired-template");
		retired.setTitle("Retired Template");
		retired.setRetired(true);
		retired.setRetireReason("No longer used");
		tasksService.saveSystemTask(retired);

		Context.flushSession();
		Context.clearSession();

		// When: Searching without status filter
		List<PlanDefinition> results = provider.search(null);

		// Then: Should return only active system tasks
		assertThat(results.size(), is(greaterThanOrEqualTo(2)));

		boolean foundActive1 = results.stream().anyMatch(pd -> "active-template-1".equals(pd.getName()));
		boolean foundActive2 = results.stream().anyMatch(pd -> "active-template-2".equals(pd.getName()));
		boolean foundRetired = results.stream().anyMatch(pd -> "retired-template".equals(pd.getName()));

		assertThat(foundActive1, is(true));
		assertThat(foundActive2, is(true));
		assertThat(foundRetired, is(false));
	}
	
	@Test
	public void search_withActiveStatus_shouldReturnOnlyActiveSystemTasks() {
		// Given: Both active and retired system tasks exist
		SystemTask active = new SystemTask();
		active.setName("active-for-status-filter");
		active.setTitle("Active for Status Filter");
		tasksService.saveSystemTask(active);

		SystemTask retired = new SystemTask();
		retired.setName("retired-for-status-filter");
		retired.setTitle("Retired for Status Filter");
		retired.setRetired(true);
		retired.setRetireReason("Testing");
		tasksService.saveSystemTask(retired);

		Context.flushSession();
		Context.clearSession();

		// When: Searching with status=active
		List<PlanDefinition> results = provider.search("active");

		// Then: Should return only active system tasks
		boolean foundActive = results.stream().anyMatch(pd -> "active-for-status-filter".equals(pd.getName()));
		boolean foundRetired = results.stream().anyMatch(pd -> "retired-for-status-filter".equals(pd.getName()));

		assertThat(foundActive, is(true));
		assertThat(foundRetired, is(false));

		// All results should have ACTIVE status
		for (PlanDefinition pd : results) {
			assertThat(pd.getStatus(), is(Enumerations.PublicationStatus.ACTIVE));
		}
	}
	
	@Test
	public void search_withRetiredStatus_shouldReturnOnlyRetiredSystemTasks() {
		// Given: Both active and retired system tasks exist
		SystemTask active = new SystemTask();
		active.setName("active-for-retired-filter");
		active.setTitle("Active for Retired Filter");
		tasksService.saveSystemTask(active);

		SystemTask retired = new SystemTask();
		retired.setName("retired-for-retired-filter");
		retired.setTitle("Retired for Retired Filter");
		retired.setRetired(true);
		retired.setRetireReason("Testing");
		tasksService.saveSystemTask(retired);

		Context.flushSession();
		Context.clearSession();

		// When: Searching with status=retired
		List<PlanDefinition> results = provider.search("retired");

		// Then: Should return only retired system tasks
		boolean foundActive = results.stream().anyMatch(pd -> "active-for-retired-filter".equals(pd.getName()));
		boolean foundRetired = results.stream().anyMatch(pd -> "retired-for-retired-filter".equals(pd.getName()));

		assertThat(foundActive, is(false));
		assertThat(foundRetired, is(true));

		// All results should have RETIRED status
		for (PlanDefinition pd : results) {
			assertThat(pd.getStatus(), is(Enumerations.PublicationStatus.RETIRED));
		}
	}
	
	@Test
	public void search_withInvalidStatus_shouldReturnEmptyList() {
		// Given: System tasks exist
		SystemTask task = new SystemTask();
		task.setName("task-for-invalid-status");
		task.setTitle("Task for Invalid Status");
		tasksService.saveSystemTask(task);
		
		Context.flushSession();
		Context.clearSession();
		
		// When: Searching with invalid status
		List<PlanDefinition> results = provider.search("invalid-status");
		
		// Then: Should return empty list
		assertThat(results, is(empty()));
	}
	
	@Test
	public void getResourceType_shouldReturnPlanDefinitionClass() {
		// When/Then
		assertThat(provider.getResourceType(), is(equalTo(PlanDefinition.class)));
	}
	
	@Test
	public void search_shouldIncludePriorityExtension() {
		// Given: A system task with priority
		SystemTask systemTask = new SystemTask();
		systemTask.setName("high-priority-template");
		systemTask.setTitle("High Priority Template");
		systemTask.setPriority(Priority.HIGH);
		tasksService.saveSystemTask(systemTask);

		Context.flushSession();
		Context.clearSession();

		// When: Searching
		List<PlanDefinition> results = provider.search(null);

		// Then: The result should include priority extension
		PlanDefinition found = results.stream()
		        .filter(pd -> "high-priority-template".equals(pd.getName()))
		        .findFirst()
		        .orElse(null);

		assertThat(found, is(notNullValue()));
		assertThat(found.hasAction(), is(true));
		assertThat(found.getActionFirstRep().hasExtension(), is(true));

		boolean hasPriorityExtension = found.getActionFirstRep().getExtension().stream()
		        .anyMatch(ext -> ext.getUrl().contains("activity-priority"));
		assertThat(hasPriorityExtension, is(true));
	}
	
	@Test
	public void search_shouldIncludeActionWithRationale() {
		// Given: A system task with rationale
		SystemTask systemTask = new SystemTask();
		systemTask.setName("template-with-rationale");
		systemTask.setTitle("Template with Rationale");
		systemTask.setRationale("Important clinical reason");
		tasksService.saveSystemTask(systemTask);

		Context.flushSession();
		Context.clearSession();

		// When: Searching
		List<PlanDefinition> results = provider.search(null);

		// Then: The result should include rationale on action.reason (not purpose)
		PlanDefinition found = results.stream()
		        .filter(pd -> "template-with-rationale".equals(pd.getName()))
		        .findFirst()
		        .orElse(null);

		assertThat(found, is(notNullValue()));
		// Rationale is on action.reason, not purpose
		assertThat(found.hasPurpose(), is(false));
		assertThat(found.hasAction(), is(true));
		assertThat(found.getActionFirstRep().hasReason(), is(true));
		assertThat(found.getActionFirstRep().getReasonFirstRep().getText(), is("Important clinical reason"));
	}
}
