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

import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.api.translators.PatientReferenceTranslator;
import org.openmrs.module.fhir2.api.translators.PractitionerReferenceTranslator;
import org.openmrs.module.tasks.Priority;
import org.openmrs.module.tasks.SystemTask;
import org.openmrs.module.tasks.Task;
import org.openmrs.module.tasks.api.TasksService;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for CarePlanMapper's instantiatesCanonical handling. Verifies that tasks created from
 * system task templates correctly include the PlanDefinition reference in the CarePlan.
 */
public class CarePlanMapperInstantiatesCanonicalTest extends BaseModuleContextSensitiveTest {
	
	@Override
	public Properties getRuntimeProperties() {
		Properties props = super.getRuntimeProperties();
		props.setProperty("module.allow_web_admin", "false");
		return props;
	}
	
	private CarePlanMapper carePlanMapper;
	
	@Mock
	private PatientReferenceTranslator patientReferenceTranslator;
	
	@Mock
	private PractitionerReferenceTranslator<Provider> practitionerReferenceTranslator;
	
	@Autowired
	private TasksService tasksService;
	
	private Patient testPatient;
	
	private SystemTask testSystemTask;
	
	@Before
	public void setUp() throws Exception {
		MockitoAnnotations.initMocks(this);
		
		PatientService patientService = Context.getPatientService();
		testPatient = patientService.getPatient(2);
		
		// Mock patient reference translator
		Reference patientRef = new Reference();
		patientRef.setReference("Patient/" + testPatient.getUuid());
		when(patientReferenceTranslator.toFhirResource(any(Patient.class))).thenReturn(patientRef);
		
		// Create mapper instance with mocked dependencies
		carePlanMapper = new CarePlanMapper(patientReferenceTranslator, practitionerReferenceTranslator);
		
		// Create and save a test system task
		testSystemTask = new SystemTask();
		testSystemTask.setName("test-system-task");
		testSystemTask.setTitle("Test System Task");
		testSystemTask.setDescription("A test system task for unit tests");
		testSystemTask.setPriority(Priority.HIGH);
		testSystemTask.setRationale("Testing instantiatesCanonical");
		tasksService.saveSystemTask(testSystemTask);
		
		Context.flushSession();
		Context.clearSession();
		
		// Refresh from database
		testSystemTask = tasksService.getSystemTaskByUuid(testSystemTask.getUuid());
	}
	
	@Test
	public void toCarePlan_withSystemTask_shouldIncludeInstantiatesCanonical() {
		// Given: A Task created from a SystemTask template
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Task from template");
		task.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		task.setSystemTask(testSystemTask);
		
		// When: Converting Task to CarePlan
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		// Then: CarePlan should include instantiatesCanonical referencing the PlanDefinition
		assertThat(carePlan, is(notNullValue()));
		assertThat(carePlan.hasInstantiatesCanonical(), is(true));
		assertThat(carePlan.getInstantiatesCanonical().size(), is(1));
		
		String canonical = carePlan.getInstantiatesCanonical().get(0).getValue();
		assertThat(canonical, is("PlanDefinition/" + testSystemTask.getUuid()));
	}
	
	@Test
	public void toCarePlan_withoutSystemTask_shouldNotIncludeInstantiatesCanonical() {
		// Given: A Task without a SystemTask template
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Standalone task");
		task.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		task.setSystemTask(null);
		
		// When: Converting Task to CarePlan
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		// Then: CarePlan should not include instantiatesCanonical
		assertThat(carePlan, is(notNullValue()));
		assertThat(carePlan.hasInstantiatesCanonical(), is(false));
	}
	
	@Test
	public void applyCarePlanToTask_withInstantiatesCanonical_shouldSetSystemTask() {
		// Given: A CarePlan with instantiatesCanonical referencing a SystemTask
		CarePlan carePlan = new CarePlan();
		carePlan.setStatus(CarePlan.CarePlanStatus.ACTIVE);
		carePlan.setIntent(CarePlan.CarePlanIntent.PLAN);
		carePlan.addInstantiatesCanonical("PlanDefinition/" + testSystemTask.getUuid());
		
		Reference patientRef = new Reference();
		patientRef.setReference("Patient/" + testPatient.getUuid());
		carePlan.setSubject(patientRef);
		
		CarePlan.CarePlanActivityComponent activity = new CarePlan.CarePlanActivityComponent();
		CarePlan.CarePlanActivityDetailComponent detail = new CarePlan.CarePlanActivityDetailComponent();
		detail.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		detail.setDescription("Task from FHIR");
		activity.setDetail(detail);
		carePlan.addActivity(activity);
		
		Task task = new Task();
		
		// When: Converting CarePlan to Task
		Task result = carePlanMapper.applyCarePlanToTask(task, carePlan, testPatient, null, null);
		
		// Then: Task should have systemTask set
		assertThat(result, is(notNullValue()));
		assertThat(result.getSystemTask(), is(notNullValue()));
		assertThat(result.getSystemTask().getUuid(), is(testSystemTask.getUuid()));
		assertThat(result.getSystemTask().getName(), is("test-system-task"));
		assertThat(result.getSystemTask().getTitle(), is("Test System Task"));
	}
	
	@Test
	public void applyCarePlanToTask_withInvalidInstantiatesCanonical_shouldNotSetSystemTask() {
		// Given: A CarePlan with instantiatesCanonical referencing a non-existent SystemTask
		CarePlan carePlan = new CarePlan();
		carePlan.setStatus(CarePlan.CarePlanStatus.ACTIVE);
		carePlan.setIntent(CarePlan.CarePlanIntent.PLAN);
		carePlan.addInstantiatesCanonical("PlanDefinition/non-existent-uuid");
		
		Reference patientRef = new Reference();
		patientRef.setReference("Patient/" + testPatient.getUuid());
		carePlan.setSubject(patientRef);
		
		CarePlan.CarePlanActivityComponent activity = new CarePlan.CarePlanActivityComponent();
		CarePlan.CarePlanActivityDetailComponent detail = new CarePlan.CarePlanActivityDetailComponent();
		detail.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		detail.setDescription("Task from FHIR");
		activity.setDetail(detail);
		carePlan.addActivity(activity);
		
		Task task = new Task();
		
		// When: Converting CarePlan to Task
		Task result = carePlanMapper.applyCarePlanToTask(task, carePlan, testPatient, null, null);
		
		// Then: Task should not have systemTask set (graceful handling of invalid reference)
		assertThat(result, is(notNullValue()));
		assertThat(result.getSystemTask(), is(nullValue()));
	}
	
	@Test
	public void applyCarePlanToTask_withoutInstantiatesCanonical_shouldNotSetSystemTask() {
		// Given: A CarePlan without instantiatesCanonical
		CarePlan carePlan = new CarePlan();
		carePlan.setStatus(CarePlan.CarePlanStatus.ACTIVE);
		carePlan.setIntent(CarePlan.CarePlanIntent.PLAN);
		
		Reference patientRef = new Reference();
		patientRef.setReference("Patient/" + testPatient.getUuid());
		carePlan.setSubject(patientRef);
		
		CarePlan.CarePlanActivityComponent activity = new CarePlan.CarePlanActivityComponent();
		CarePlan.CarePlanActivityDetailComponent detail = new CarePlan.CarePlanActivityDetailComponent();
		detail.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		detail.setDescription("Standalone task");
		activity.setDetail(detail);
		carePlan.addActivity(activity);
		
		Task task = new Task();
		
		// When: Converting CarePlan to Task
		Task result = carePlanMapper.applyCarePlanToTask(task, carePlan, testPatient, null, null);
		
		// Then: Task should not have systemTask set
		assertThat(result, is(notNullValue()));
		assertThat(result.getSystemTask(), is(nullValue()));
	}
	
	@Test
	public void applyCarePlanToTask_withNonPlanDefinitionCanonical_shouldNotSetSystemTask() {
		// Given: A CarePlan with instantiatesCanonical referencing a non-PlanDefinition resource
		CarePlan carePlan = new CarePlan();
		carePlan.setStatus(CarePlan.CarePlanStatus.ACTIVE);
		carePlan.setIntent(CarePlan.CarePlanIntent.PLAN);
		carePlan.addInstantiatesCanonical("ActivityDefinition/some-uuid"); // Not a PlanDefinition
		
		Reference patientRef = new Reference();
		patientRef.setReference("Patient/" + testPatient.getUuid());
		carePlan.setSubject(patientRef);
		
		CarePlan.CarePlanActivityComponent activity = new CarePlan.CarePlanActivityComponent();
		CarePlan.CarePlanActivityDetailComponent detail = new CarePlan.CarePlanActivityDetailComponent();
		detail.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		detail.setDescription("Task from FHIR");
		activity.setDetail(detail);
		carePlan.addActivity(activity);
		
		Task task = new Task();
		
		// When: Converting CarePlan to Task
		Task result = carePlanMapper.applyCarePlanToTask(task, carePlan, testPatient, null, null);
		
		// Then: Task should not have systemTask set (only PlanDefinition is supported)
		assertThat(result, is(notNullValue()));
		assertThat(result.getSystemTask(), is(nullValue()));
	}
}
