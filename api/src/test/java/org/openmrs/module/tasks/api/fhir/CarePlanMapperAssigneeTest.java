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

import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.ProviderRole;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.api.translators.PatientReferenceTranslator;
import org.openmrs.module.fhir2.api.translators.PractitionerReferenceTranslator;
import org.openmrs.module.tasks.Task;
import org.openmrs.module.tasks.api.dao.TasksDao;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Properties;

/**
 * Integration test that verifies FHIR CarePlan requests correctly set assignee_provider_id and
 * assignee_provider_role_id database columns when creating tasks.
 */
public class CarePlanMapperAssigneeTest extends BaseModuleContextSensitiveTest {
	
	@Override
	public Properties getRuntimeProperties() {
		Properties props = super.getRuntimeProperties();
		// Exclude FHIR2 module to avoid cacheInterceptor dependency issues in tests
		props.setProperty("module.allow_web_admin", "false");
		return props;
	}
	
	@Autowired
	private TasksDao tasksDao;
	
	private CarePlanMapper carePlanMapper;
	
	@Mock
	private PatientReferenceTranslator patientReferenceTranslator;
	
	@Mock
	private PractitionerReferenceTranslator<Provider> practitionerReferenceTranslator;
	
	private Patient testPatient;
	
	private Provider testProvider;
	
	private ProviderRole testProviderRole;
	
	private PatientService patientService;
	
	private ProviderService providerService;
	
	@Before
	public void setUp() throws Exception {
		MockitoAnnotations.initMocks(this);
		
		patientService = Context.getPatientService();
		providerService = Context.getProviderService();
		
		// Get test patient from test dataset
		testPatient = patientService.getPatient(2);
		
		// Get test provider from test dataset
		testProvider = providerService.getProvider(1);
		
		executeDataSet("datasets/ProviderRoleTestDataset.xml");
		testProviderRole = providerService.getProviderRoleByUuid("test-provider-role-uuid");
		assertThat("ProviderRole test fixture must load via DBUnit", testProviderRole, is(notNullValue()));
		
		// Mock patient reference translator
		Reference patientRef = new Reference();
		patientRef.setReference("Patient/" + testPatient.getUuid());
		when(patientReferenceTranslator.toFhirResource(any(Patient.class))).thenReturn(patientRef);
		
		// Mock practitioner reference translator to resolve Practitioner references
		when(practitionerReferenceTranslator.toOpenmrsType(any(Reference.class))).thenAnswer(invocation -> {
			Reference ref = (Reference) invocation.getArguments()[0];
			if (ref.getReference() != null && ref.getReference().contains("Practitioner/")) {
				String uuid = ref.getReference().substring("Practitioner/".length());
				if (uuid.equals(testProvider.getUuid())) {
					return testProvider;
				}
				return providerService.getProviderByUuid(uuid);
			}
			return null;
		});
		
		// Create mapper instance with mocked dependencies
		carePlanMapper = new CarePlanMapper(patientReferenceTranslator, practitionerReferenceTranslator);
	}
	
	@Test
	public void createTaskFromCarePlan_withPractitionerPerformer_shouldSetAssigneeProviderIdInDatabase() {
		// Given: A FHIR CarePlan request with Practitioner performer
		CarePlan carePlan = createCarePlanWithPractitionerPerformer(testProvider.getUuid());
		
		// When: Converting CarePlan to Task and saving to database
		Task task = carePlanMapper.applyCarePlanToTask(new Task(), carePlan, testPatient, null, null);
		Task savedTask = tasksDao.saveTask(task);
		
		// Flush and clear session to ensure we read from database
		Context.flushSession();
		Context.clearSession();
		
		// Then: The saved task should have assignee_provider_id set (via assignee Provider)
		Task retrievedTask = tasksDao.getTaskByUuid(savedTask.getUuid());
		assertThat(retrievedTask.getAssignee(), is(notNullValue()));
		assertThat(retrievedTask.getAssignee().getUuid(), is(testProvider.getUuid()));
		assertThat(retrievedTask.getAssignee().getProviderId(), is(testProvider.getProviderId()));
		assertThat(retrievedTask.getAssigneeProviderRoleId(), is(nullValue()));
	}
	
	@Test
	public void createTaskFromCarePlan_withPractitionerRolePerformer_shouldSetAssigneeProviderRoleIdInDatabase()
	        throws Exception {
		// Given: A FHIR CarePlan request with PractitionerRole performer
		CarePlan carePlan = createCarePlanWithPractitionerRolePerformer(testProviderRole.getUuid());
		
		// When: Converting CarePlan to Task and saving to database
		Task task = carePlanMapper.applyCarePlanToTask(new Task(), carePlan, testPatient, null, null);
		Task savedTask = tasksDao.saveTask(task);
		
		// Flush and clear session to ensure we read from database
		Context.flushSession();
		Context.clearSession();
		
		// Then: The saved task should have assignee_provider_role_id set
		Task retrievedTask = tasksDao.getTaskByUuid(savedTask.getUuid());
		assertThat(retrievedTask.getAssignee(), is(nullValue()));
		assertThat(retrievedTask.getAssigneeProviderRoleId(), is(testProviderRole.getProviderRoleId()));
	}
	
	@Test
	public void createTaskFromCarePlan_withBothPractitionerAndPractitionerRole_shouldSetBothInDatabase() throws Exception {
		// Given: A FHIR CarePlan request with both Practitioner and PractitionerRole performers
		CarePlan carePlan = createCarePlanWithBothPerformers(testProvider.getUuid(), testProviderRole.getUuid());
		
		// When: Converting CarePlan to Task and saving to database
		Task task = carePlanMapper.applyCarePlanToTask(new Task(), carePlan, testPatient, null, null);
		Task savedTask = tasksDao.saveTask(task);
		
		// Flush and clear session to ensure we read from database
		Context.flushSession();
		Context.clearSession();
		
		// Then: Both assignee_provider_id and assignee_provider_role_id should be set
		Task retrievedTask = tasksDao.getTaskByUuid(savedTask.getUuid());
		assertThat(retrievedTask.getAssignee(), is(notNullValue()));
		assertThat(retrievedTask.getAssignee().getUuid(), is(testProvider.getUuid()));
		assertThat(retrievedTask.getAssignee().getProviderId(), is(testProvider.getProviderId()));
		assertThat(retrievedTask.getAssigneeProviderRoleId(), is(testProviderRole.getProviderRoleId()));
	}
	
	@Test
	public void createTaskFromCarePlan_withAssigneeProviderParameter_shouldSetAssigneeProviderIdInDatabase() {
		// Given: A CarePlan without performers, but assignee Provider provided as parameter
		CarePlan carePlan = createCarePlanWithoutPerformers();
		
		// When: Converting CarePlan to Task with assignee parameter and saving
		Task task = carePlanMapper.applyCarePlanToTask(new Task(), carePlan, testPatient, testProvider, null);
		Task savedTask = tasksDao.saveTask(task);
		
		// Flush and clear session to ensure we read from database
		Context.flushSession();
		Context.clearSession();
		
		// Then: assignee_provider_id should be set via the assignee Provider entity
		Task retrievedTask = tasksDao.getTaskByUuid(savedTask.getUuid());
		assertThat(retrievedTask.getAssignee(), is(notNullValue()));
		assertThat(retrievedTask.getAssignee().getUuid(), is(testProvider.getUuid()));
		assertThat(retrievedTask.getAssignee().getProviderId(), is(testProvider.getProviderId()));
	}
	
	private CarePlan createCarePlanWithPractitionerPerformer(String providerUuid) {
		CarePlan carePlan = new CarePlan();
		carePlan.setStatus(CarePlan.CarePlanStatus.ACTIVE);
		carePlan.setIntent(CarePlan.CarePlanIntent.PLAN);
		
		Reference patientRef = new Reference();
		patientRef.setReference("Patient/" + testPatient.getUuid());
		carePlan.setSubject(patientRef);
		
		CarePlan.CarePlanActivityComponent activity = new CarePlan.CarePlanActivityComponent();
		CarePlan.CarePlanActivityDetailComponent detail = new CarePlan.CarePlanActivityDetailComponent();
		detail.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		detail.setDescription("Test task with Practitioner assignee");
		
		Reference practitionerRef = new Reference();
		practitionerRef.setReference("Practitioner/" + providerUuid);
		detail.addPerformer(practitionerRef);
		
		activity.setDetail(detail);
		carePlan.addActivity(activity);
		
		return carePlan;
	}
	
	private CarePlan createCarePlanWithPractitionerRolePerformer(String providerRoleUuid) {
		CarePlan carePlan = new CarePlan();
		carePlan.setStatus(CarePlan.CarePlanStatus.ACTIVE);
		carePlan.setIntent(CarePlan.CarePlanIntent.PLAN);
		
		Reference patientRef = new Reference();
		patientRef.setReference("Patient/" + testPatient.getUuid());
		carePlan.setSubject(patientRef);
		
		CarePlan.CarePlanActivityComponent activity = new CarePlan.CarePlanActivityComponent();
		CarePlan.CarePlanActivityDetailComponent detail = new CarePlan.CarePlanActivityDetailComponent();
		detail.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		detail.setDescription("Test task with PractitionerRole assignee");
		
		Reference practitionerRoleRef = new Reference();
		practitionerRoleRef.setReference("PractitionerRole/" + providerRoleUuid);
		detail.addPerformer(practitionerRoleRef);
		
		activity.setDetail(detail);
		carePlan.addActivity(activity);
		
		return carePlan;
	}
	
	private CarePlan createCarePlanWithBothPerformers(String providerUuid, String providerRoleUuid) {
		CarePlan carePlan = new CarePlan();
		carePlan.setStatus(CarePlan.CarePlanStatus.ACTIVE);
		carePlan.setIntent(CarePlan.CarePlanIntent.PLAN);
		
		Reference patientRef = new Reference();
		patientRef.setReference("Patient/" + testPatient.getUuid());
		carePlan.setSubject(patientRef);
		
		CarePlan.CarePlanActivityComponent activity = new CarePlan.CarePlanActivityComponent();
		CarePlan.CarePlanActivityDetailComponent detail = new CarePlan.CarePlanActivityDetailComponent();
		detail.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		detail.setDescription("Test task with both Practitioner and PractitionerRole assignees");
		
		Reference practitionerRef = new Reference();
		practitionerRef.setReference("Practitioner/" + providerUuid);
		detail.addPerformer(practitionerRef);
		
		Reference practitionerRoleRef = new Reference();
		practitionerRoleRef.setReference("PractitionerRole/" + providerRoleUuid);
		detail.addPerformer(practitionerRoleRef);
		
		activity.setDetail(detail);
		carePlan.addActivity(activity);
		
		return carePlan;
	}
	
	private CarePlan createCarePlanWithoutPerformers() {
		CarePlan carePlan = new CarePlan();
		carePlan.setStatus(CarePlan.CarePlanStatus.ACTIVE);
		carePlan.setIntent(CarePlan.CarePlanIntent.PLAN);
		
		Reference patientRef = new Reference();
		patientRef.setReference("Patient/" + testPatient.getUuid());
		carePlan.setSubject(patientRef);
		
		CarePlan.CarePlanActivityComponent activity = new CarePlan.CarePlanActivityComponent();
		CarePlan.CarePlanActivityDetailComponent detail = new CarePlan.CarePlanActivityDetailComponent();
		detail.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		detail.setDescription("Test task without performers");
		
		activity.setDetail(detail);
		carePlan.addActivity(activity);
		
		return carePlan;
	}
}
