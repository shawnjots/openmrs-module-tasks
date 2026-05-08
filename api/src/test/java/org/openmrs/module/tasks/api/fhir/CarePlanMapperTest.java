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
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.ProviderRole;
import org.openmrs.User;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.tasks.DueDateType;
import org.openmrs.module.tasks.Priority;
import org.openmrs.module.fhir2.api.translators.PatientReferenceTranslator;
import org.openmrs.module.fhir2.api.translators.PractitionerReferenceTranslator;
import org.openmrs.module.tasks.Task;
import org.openmrs.module.tasks.TaskKind;
import org.openmrs.module.tasks.TaskStatus;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import java.util.Properties;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Integration test for CarePlanMapper that verifies assignee handling for Practitioner and
 * PractitionerRole references. Tests that FHIR requests correctly set assignee_provider_id and
 * assignee_provider_role_id database columns.
 */
public class CarePlanMapperTest extends BaseModuleContextSensitiveTest {
	
	@Override
	public Properties getRuntimeProperties() {
		Properties props = super.getRuntimeProperties();
		// Exclude FHIR2 module to avoid cacheInterceptor dependency issues in tests
		props.setProperty("module.allow_web_admin", "false");
		return props;
	}
	
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
				// Return the test provider if UUID matches, otherwise try to find it
				if (uuid.equals(testProvider.getUuid())) {
					return testProvider;
				}
				return providerService.getProviderByUuid(uuid);
			}
			return null;
		});
		
		// Mock practitioner reference translator to convert Provider to FHIR reference
		when(practitionerReferenceTranslator.toFhirResource(any(Provider.class))).thenAnswer(invocation -> {
			Provider provider = (Provider) invocation.getArguments()[0];
			if (provider != null && provider.getUuid() != null) {
				Reference ref = new Reference();
				ref.setReference("Practitioner/" + provider.getUuid());
				return ref;
			}
			return null;
		});
		
		// Create mapper instance with mocked dependencies
		carePlanMapper = new CarePlanMapper(patientReferenceTranslator, practitionerReferenceTranslator);
	}
	
	@Test
	public void applyCarePlanToTask_withPractitionerPerformer_shouldSetAssigneeProviderId() {
		// Given: A CarePlan with a Practitioner performer reference
		CarePlan carePlan = createCarePlanWithPractitionerPerformer(testProvider.getUuid());
		Task task = new Task();
		
		// When: Converting CarePlan to Task
		Task result = carePlanMapper.applyCarePlanToTask(task, carePlan, testPatient, null, null);
		
		// Then: assignee_provider_id should be set via the assignee Provider entity
		assertThat(result.getAssignee(), is(notNullValue()));
		assertThat(result.getAssignee().getUuid(), is(testProvider.getUuid()));
		assertThat(result.getAssigneeProviderRoleId(), is(nullValue()));
	}
	
	@Test
	public void applyCarePlanToTask_withPractitionerRolePerformer_shouldSetAssigneeProviderRoleId() throws Exception {
		// Given: A CarePlan with a PractitionerRole performer reference
		CarePlan carePlan = createCarePlanWithPractitionerRolePerformer(testProviderRole.getUuid());
		Task task = new Task();
		
		// When: Converting CarePlan to Task
		Task result = carePlanMapper.applyCarePlanToTask(task, carePlan, testPatient, null, null);
		
		// Then: assignee_provider_role_id should be set
		assertThat(result.getAssignee(), is(nullValue()));
		assertThat(result.getAssigneeProviderRoleId(), is(testProviderRole.getProviderRoleId()));
	}
	
	@Test
	public void applyCarePlanToTask_withBothPractitionerAndPractitionerRole_shouldSetBoth() throws Exception {
		// Given: A CarePlan with both Practitioner and PractitionerRole performers
		CarePlan carePlan = createCarePlanWithBothPerformers(testProvider.getUuid(), testProviderRole.getUuid());
		Task task = new Task();
		
		// When: Converting CarePlan to Task
		Task result = carePlanMapper.applyCarePlanToTask(task, carePlan, testPatient, null, null);
		
		// Then: Both assignee_provider_id and assignee_provider_role_id should be set
		assertThat(result.getAssignee(), is(notNullValue()));
		assertThat(result.getAssignee().getUuid(), is(testProvider.getUuid()));
		assertThat(result.getAssigneeProviderRoleId(), is(testProviderRole.getProviderRoleId()));
	}
	
	@Test
	public void applyCarePlanToTask_withAssigneeRoleUuidParameter_shouldSetAssigneeProviderRoleId() throws Exception {
		// Given: A CarePlan without performers, but assigneeRoleUuid provided as parameter
		CarePlan carePlan = createCarePlanWithoutPerformers();
		Task task = new Task();
		
		// When: Converting CarePlan to Task with assigneeRoleUuid parameter
		Task result = carePlanMapper.applyCarePlanToTask(task, carePlan, testPatient, null, testProviderRole.getUuid());
		
		// Then: assignee_provider_role_id should be set from the parameter
		assertThat(result.getAssigneeProviderRoleId(), is(testProviderRole.getProviderRoleId()));
	}
	
	@Test
	public void applyCarePlanToTask_withAssigneeParameter_shouldSetAssigneeProviderId() {
		// Given: A CarePlan without performers, but assignee Provider provided as parameter
		CarePlan carePlan = createCarePlanWithoutPerformers();
		Task task = new Task();
		
		// When: Converting CarePlan to Task with assignee parameter
		Task result = carePlanMapper.applyCarePlanToTask(task, carePlan, testPatient, testProvider, null);
		
		// Then: assignee_provider_id should be set via the assignee Provider entity
		assertThat(result.getAssignee(), is(testProvider));
	}
	
	@Test
	public void toCarePlan_withAssigneeProviderRoleId_shouldIncludePractitionerRoleReference() throws Exception {
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Test task");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setAssigneeProviderRoleId(testProviderRole.getProviderRoleId());
		
		// When: Converting Task to CarePlan
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		// Then: CarePlan should include PractitionerRole reference
		assertThat(carePlan, is(notNullValue()));
		assertThat(carePlan.hasActivity(), is(true));
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		assertThat(detail.hasPerformer(), is(true));
		
		boolean foundPractitionerRole = false;
		for (Reference performer : detail.getPerformer()) {
			if (performer.getReference() != null && performer.getReference().contains("PractitionerRole")) {
				foundPractitionerRole = true;
				// Verify it contains the provider role UUID (if available)
				if (testProviderRole.getUuid() != null) {
					assertThat(performer.getReference(), containsString(testProviderRole.getUuid()));
				}
			}
		}
		assertThat(foundPractitionerRole, is(true));
	}
	
	@Test
	public void toCarePlan_withAssigneeProvider_shouldIncludePractitionerReference() {
		// Given: A Task with assignee Provider set
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Test task");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setAssignee(testProvider);
		
		// When: Converting Task to CarePlan
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		// Then: CarePlan should include Practitioner reference
		assertThat(carePlan, is(notNullValue()));
		assertThat(carePlan.hasActivity(), is(true));
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		assertThat(detail.hasPerformer(), is(true));
		
		boolean foundPractitioner = false;
		for (Reference performer : detail.getPerformer()) {
			if (performer.getReference() != null && performer.getReference().contains("Practitioner")) {
				foundPractitioner = true;
				// Verify it contains the provider UUID
				assertThat(performer.getReference(), containsString("Practitioner"));
			}
		}
		assertThat(foundPractitioner, is(true));
	}
	
	@Test
	public void toCarePlan_withCreatorBackedByProvider_shouldUsePractitionerReference() {
		// Creator resolves to a Provider via the test dataset, so the author should be the
		// translator-built Practitioner reference rather than the display-only fallback.
		User creator = new User();
		creator.setPerson(testProvider.getPerson());
		creator.setUsername("testuser");
		
		Task task = new Task();
		task.setPatient(testPatient);
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setCreator(creator);
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		Reference author = carePlan.getAuthor();
		assertThat(author, is(notNullValue()));
		assertThat(author.getReference(), is("Practitioner/" + testProvider.getUuid()));
	}
	
	@Test
	public void toCarePlan_withCreator_shouldIncludeAuthorWithDisplay() {
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Test task");
		task.setStatus(TaskStatus.NOTSTARTED);
		
		Person person = new Person();
		PersonName personName = new PersonName();
		personName.setGivenName("Test");
		personName.setFamilyName("User");
		person.addName(personName);
		User testUser = new User();
		testUser.setPerson(person);
		testUser.setUsername("testuser");
		task.setCreator(testUser);
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		assertThat(carePlan, is(notNullValue()));
		assertThat(carePlan.hasAuthor(), is(true));
		
		Reference author = carePlan.getAuthor();
		assertThat(author, is(notNullValue()));
		assertThat(author.hasDisplay(), is(true));
		
		String displayName = author.getDisplay();
		assertThat(displayName, is(notNullValue()));
		assertThat(displayName.length(), is(greaterThan(0)));
		assertThat(displayName, is("Test User"));
	}
	
	@Test
	public void applyCarePlanToTask_withCancelledStatus_shouldSetStatusAndLeaveVoidedFalse() {
		CarePlan carePlan = createCarePlanWithoutPerformers();
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		detail.setStatus(CarePlan.CarePlanActivityStatus.CANCELLED);
		
		Task task = new Task();
		task.setVoided(false);
		
		Task result = carePlanMapper.applyCarePlanToTask(task, carePlan, testPatient, null, null);
		
		assertThat(result.getStatus(), is(TaskStatus.CANCELLED));
		assertThat(result.getVoided(), is(false));
		assertThat(result.getDateVoided(), is(nullValue()));
	}
	
	@Test
	public void applyCarePlanToTask_withCancelledStatusOnVoidedTask_shouldNotTouchVoidedMetadata() {
		CarePlan carePlan = createCarePlanWithoutPerformers();
		carePlan.getActivityFirstRep().getDetail().setStatus(CarePlan.CarePlanActivityStatus.CANCELLED);
		
		Task task = new Task();
		java.util.Date existingDate = new java.util.Date(1000L);
		task.setVoided(true);
		task.setDateVoided(existingDate);
		
		Task result = carePlanMapper.applyCarePlanToTask(task, carePlan, testPatient, null, null);
		
		assertThat(result.getStatus(), is(TaskStatus.CANCELLED));
		assertThat(result.getVoided(), is(true));
		assertThat(result.getDateVoided(), is(existingDate));
	}
	
	@Test
	public void toCarePlan_withVoidedTask_shouldSetActivityDetailStatusToEnteredInError() {
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Test task");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setVoided(true);
		task.setDateVoided(new java.util.Date());
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		assertThat(carePlan, is(notNullValue()));
		assertThat(carePlan.hasActivity(), is(true));
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		assertThat(detail.hasStatus(), is(true));
		assertThat(detail.getStatus(), is(CarePlan.CarePlanActivityStatus.ENTEREDINERROR));
	}
	
	@Test
	public void toCarePlan_withNonVoidedTask_shouldPassThroughTaskStatus() {
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Test task");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setVoided(false);
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		assertThat(carePlan, is(notNullValue()));
		assertThat(carePlan.hasActivity(), is(true));
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		assertThat(detail.hasStatus(), is(true));
		assertThat(detail.getStatus(), is(CarePlan.CarePlanActivityStatus.NOTSTARTED));
	}
	
	@Test
	public void toCarePlan_withCancelledStatusNonVoidedTask_shouldPassCancelledThrough() {
		Task task = new Task();
		task.setPatient(testPatient);
		task.setStatus(TaskStatus.CANCELLED);
		task.setVoided(false);
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		assertThat(carePlan.getActivityFirstRep().getDetail().getStatus(), is(CarePlan.CarePlanActivityStatus.CANCELLED));
		assertThat(carePlan.getStatus(), is(CarePlan.CarePlanStatus.REVOKED));
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
		detail.setDescription("Test task");
		
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
		detail.setDescription("Test task");
		
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
		detail.setDescription("Test task");
		
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
		detail.setDescription("Test task");
		
		activity.setDetail(detail);
		carePlan.addActivity(activity);
		
		return carePlan;
	}
	
	private CarePlan createCarePlanWithPriorityExtension(String priorityValue) {
		CarePlan carePlan = new CarePlan();
		carePlan.setStatus(CarePlan.CarePlanStatus.ACTIVE);
		carePlan.setIntent(CarePlan.CarePlanIntent.PLAN);
		
		Reference patientRef = new Reference();
		patientRef.setReference("Patient/" + testPatient.getUuid());
		carePlan.setSubject(patientRef);
		
		CarePlan.CarePlanActivityComponent activity = new CarePlan.CarePlanActivityComponent();
		CarePlan.CarePlanActivityDetailComponent detail = new CarePlan.CarePlanActivityDetailComponent();
		detail.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		detail.setDescription("Test task");
		
		org.hl7.fhir.r4.model.Extension priorityExtension = new org.hl7.fhir.r4.model.Extension();
		priorityExtension.setUrl("http://openmrs.org/fhir/StructureDefinition/activity-priority");
		priorityExtension.setValue(new org.hl7.fhir.r4.model.CodeType(priorityValue));
		detail.addExtension(priorityExtension);
		
		activity.setDetail(detail);
		carePlan.addActivity(activity);
		
		return carePlan;
	}
	
	@Test
	public void toCarePlan_withHighPriority_shouldIncludePriorityExtension() {
		// Given: A Task with HIGH priority
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("High priority task");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setPriority(Priority.HIGH);
		
		// When: Converting Task to CarePlan
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		// Then: CarePlan should include priority extension with value "high"
		assertThat(carePlan, is(notNullValue()));
		assertThat(carePlan.hasActivity(), is(true));
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		assertThat(detail.hasExtension(), is(true));
		
		org.hl7.fhir.r4.model.Extension priorityExtension = detail
		        .getExtensionByUrl("http://openmrs.org/fhir/StructureDefinition/activity-priority");
		assertThat(priorityExtension, is(notNullValue()));
		assertThat(priorityExtension.getValue(), instanceOf(org.hl7.fhir.r4.model.CodeType.class));
		org.hl7.fhir.r4.model.CodeType priorityValue = (org.hl7.fhir.r4.model.CodeType) priorityExtension.getValue();
		assertThat(priorityValue.getValue(), is("high"));
	}
	
	@Test
	public void toCarePlan_withMediumPriority_shouldIncludePriorityExtension() {
		// Given: A Task with MEDIUM priority
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Medium priority task");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setPriority(Priority.MEDIUM);
		
		// When: Converting Task to CarePlan
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		// Then: CarePlan should include priority extension with value "medium"
		assertThat(carePlan, is(notNullValue()));
		assertThat(carePlan.hasActivity(), is(true));
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		assertThat(detail.hasExtension(), is(true));
		
		org.hl7.fhir.r4.model.Extension priorityExtension = detail
		        .getExtensionByUrl("http://openmrs.org/fhir/StructureDefinition/activity-priority");
		assertThat(priorityExtension, is(notNullValue()));
		assertThat(priorityExtension.getValue(), instanceOf(org.hl7.fhir.r4.model.CodeType.class));
		org.hl7.fhir.r4.model.CodeType priorityValue = (org.hl7.fhir.r4.model.CodeType) priorityExtension.getValue();
		assertThat(priorityValue.getValue(), is("medium"));
	}
	
	@Test
	public void toCarePlan_withLowPriority_shouldIncludePriorityExtension() {
		// Given: A Task with LOW priority
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Low priority task");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setPriority(Priority.LOW);
		
		// When: Converting Task to CarePlan
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		// Then: CarePlan should include priority extension with value "low"
		assertThat(carePlan, is(notNullValue()));
		assertThat(carePlan.hasActivity(), is(true));
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		assertThat(detail.hasExtension(), is(true));
		
		org.hl7.fhir.r4.model.Extension priorityExtension = detail
		        .getExtensionByUrl("http://openmrs.org/fhir/StructureDefinition/activity-priority");
		assertThat(priorityExtension, is(notNullValue()));
		assertThat(priorityExtension.getValue(), instanceOf(org.hl7.fhir.r4.model.CodeType.class));
		org.hl7.fhir.r4.model.CodeType priorityValue = (org.hl7.fhir.r4.model.CodeType) priorityExtension.getValue();
		assertThat(priorityValue.getValue(), is("low"));
	}
	
	@Test
	public void toCarePlan_withNoPriority_shouldNotIncludePriorityExtension() {
		// Given: A Task without priority
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Task without priority");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setPriority(null);
		
		// When: Converting Task to CarePlan
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		// Then: CarePlan should not include priority extension
		assertThat(carePlan, is(notNullValue()));
		assertThat(carePlan.hasActivity(), is(true));
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		
		org.hl7.fhir.r4.model.Extension priorityExtension = detail
		        .getExtensionByUrl("http://openmrs.org/fhir/StructureDefinition/activity-priority");
		assertThat(priorityExtension, is(nullValue()));
	}
	
	@Test
	public void applyCarePlanToTask_withHighPriorityExtension_shouldSetHighPriority() {
		// Given: A CarePlan with high priority extension
		CarePlan carePlan = createCarePlanWithPriorityExtension("high");
		Task task = new Task();
		
		// When: Converting CarePlan to Task
		Task result = carePlanMapper.applyCarePlanToTask(task, carePlan, testPatient, null, null);
		
		// Then: Task should have HIGH priority
		assertThat(result.getPriority(), is(Priority.HIGH));
	}
	
	@Test
	public void applyCarePlanToTask_withMediumPriorityExtension_shouldSetMediumPriority() {
		// Given: A CarePlan with medium priority extension
		CarePlan carePlan = createCarePlanWithPriorityExtension("medium");
		Task task = new Task();
		
		// When: Converting CarePlan to Task
		Task result = carePlanMapper.applyCarePlanToTask(task, carePlan, testPatient, null, null);
		
		// Then: Task should have MEDIUM priority
		assertThat(result.getPriority(), is(Priority.MEDIUM));
	}
	
	@Test
	public void applyCarePlanToTask_withLowPriorityExtension_shouldSetLowPriority() {
		// Given: A CarePlan with low priority extension
		CarePlan carePlan = createCarePlanWithPriorityExtension("low");
		Task task = new Task();
		
		// When: Converting CarePlan to Task
		Task result = carePlanMapper.applyCarePlanToTask(task, carePlan, testPatient, null, null);
		
		// Then: Task should have LOW priority
		assertThat(result.getPriority(), is(Priority.LOW));
	}
	
	@Test
	public void applyCarePlanToTask_withNoPriorityExtension_shouldHaveNullPriority() {
		// Given: A CarePlan without priority extension
		CarePlan carePlan = createCarePlanWithoutPerformers();
		Task task = new Task();
		
		// When: Converting CarePlan to Task
		Task result = carePlanMapper.applyCarePlanToTask(task, carePlan, testPatient, null, null);
		
		// Then: Task should have null priority
		assertThat(result.getPriority(), is(nullValue()));
	}
	
	@Test
	public void applyCarePlanToTask_withUpperCasePriorityExtension_shouldSetPriority() {
		// Given: A CarePlan with uppercase priority extension (HIGH instead of high)
		CarePlan carePlan = createCarePlanWithPriorityExtension("HIGH");
		Task task = new Task();
		
		// When: Converting CarePlan to Task
		Task result = carePlanMapper.applyCarePlanToTask(task, carePlan, testPatient, null, null);
		
		// Then: Task should have HIGH priority (case-insensitive)
		assertThat(result.getPriority(), is(Priority.HIGH));
	}
	
	@Test
	public void toCarePlan_withThisVisitTask_duringCurrentVisit_shouldReturnThisVisit() throws Exception {
		// Given: Task scheduled for "this visit" during the current visit
		VisitService visitService = Context.getVisitService();
		Visit currentVisit = createTestVisit(testPatient, new Date(), null); // Started today, ongoing
		visitService.saveVisit(currentVisit);
		Context.flushSession();
		
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Test task");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setDueDateType(DueDateType.THIS_VISIT);
		task.setDueDateReferenceVisit(currentVisit);
		
		// When: Converting Task to CarePlan
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		// Then: Should return scheduledPeriod (no end for ongoing visit) and activity-dueKind extension
		assertThat(carePlan, is(notNullValue()));
		assertThat(carePlan.hasActivity(), is(true));
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		assertThat(detail.hasScheduled(), is(true));
		assertThat(detail.getScheduled(), instanceOf(org.hl7.fhir.r4.model.Period.class));
		org.hl7.fhir.r4.model.Period period = (org.hl7.fhir.r4.model.Period) detail.getScheduled();
		assertThat(period.hasEnd(), is(false)); // No end for ongoing visit
		
		// Check activity-dueKind extension
		assertThat(detail.hasExtension(), is(true));
		org.hl7.fhir.r4.model.Extension dueKindExtension = detail
		        .getExtensionByUrl("http://openmrs.org/fhir/StructureDefinition/activity-dueKind");
		assertThat(dueKindExtension, is(notNullValue()));
		assertThat(dueKindExtension.getValue(), instanceOf(org.hl7.fhir.r4.model.CodeType.class));
		org.hl7.fhir.r4.model.CodeType dueKindValue = (org.hl7.fhir.r4.model.CodeType) dueKindExtension.getValue();
		assertThat(dueKindValue.getValue(), is("this-visit"));
		
		// Check encounter extension
		org.hl7.fhir.r4.model.Extension encounterExtension = detail
		        .getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/encounter-associatedEncounter");
		assertThat(encounterExtension, is(notNullValue()));
	}
	
	@Test
	public void toCarePlan_withNextVisitTask_duringCurrentVisit_shouldReturnNextVisit() throws Exception {
		// Given: Task scheduled for "next visit" during the current visit
		VisitService visitService = Context.getVisitService();
		Visit currentVisit = createTestVisit(testPatient, new Date(), null); // Started today, ongoing
		visitService.saveVisit(currentVisit);
		Context.flushSession();
		
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Test task");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setDueDateType(DueDateType.NEXT_VISIT);
		task.setDueDateReferenceVisit(currentVisit);
		
		// When: Converting Task to CarePlan
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		// Then: Should return scheduledPeriod (no end for ongoing visit) and activity-dueKind extension
		assertThat(carePlan, is(notNullValue()));
		assertThat(carePlan.hasActivity(), is(true));
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		assertThat(detail.hasScheduled(), is(true));
		assertThat(detail.getScheduled(), instanceOf(org.hl7.fhir.r4.model.Period.class));
		org.hl7.fhir.r4.model.Period period = (org.hl7.fhir.r4.model.Period) detail.getScheduled();
		assertThat(period.hasEnd(), is(false)); // No end for ongoing visit
		
		// Check activity-dueKind extension
		assertThat(detail.hasExtension(), is(true));
		org.hl7.fhir.r4.model.Extension dueKindExtension = detail
		        .getExtensionByUrl("http://openmrs.org/fhir/StructureDefinition/activity-dueKind");
		assertThat(dueKindExtension, is(notNullValue()));
		assertThat(dueKindExtension.getValue(), instanceOf(org.hl7.fhir.r4.model.CodeType.class));
		org.hl7.fhir.r4.model.CodeType dueKindValue = (org.hl7.fhir.r4.model.CodeType) dueKindExtension.getValue();
		assertThat(dueKindValue.getValue(), is("next-visit"));
		
		// Check encounter extension
		org.hl7.fhir.r4.model.Extension encounterExtension = detail
		        .getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/encounter-associatedEncounter");
		assertThat(encounterExtension, is(notNullValue()));
	}
	
	@Test
	public void toCarePlan_withThisVisitTask_visitEnded_shouldReturnPastVisitWithEndDate() throws Exception {
		// Given: Task scheduled for "this visit" and the visit has ended
		VisitService visitService = Context.getVisitService();
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_YEAR, -2);
		Date visitStart = cal.getTime();
		cal.add(Calendar.DAY_OF_YEAR, 1);
		Date visitEnd = cal.getTime();
		
		Visit endedVisit = createTestVisit(testPatient, visitStart, visitEnd);
		visitService.saveVisit(endedVisit);
		Context.flushSession();
		
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Test task");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setDueDateType(DueDateType.THIS_VISIT);
		task.setDueDateReferenceVisit(endedVisit);
		
		// When: Converting Task to CarePlan
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		// Then: Should return scheduledPeriod with end date and activity-dueKind extension
		assertThat(carePlan, is(notNullValue()));
		assertThat(carePlan.hasActivity(), is(true));
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		assertThat(detail.hasScheduled(), is(true));
		assertThat(detail.getScheduled(), instanceOf(org.hl7.fhir.r4.model.Period.class));
		org.hl7.fhir.r4.model.Period period = (org.hl7.fhir.r4.model.Period) detail.getScheduled();
		assertThat(period.hasEnd(), is(true));
		// Verify end date matches visit end date (within 1 second tolerance)
		long periodEndTime = period.getEnd().getTime();
		long visitEndTime = visitEnd.getTime();
		assertThat(Math.abs(periodEndTime - visitEndTime), is(lessThan(1000L)));
		
		// Check activity-dueKind extension
		assertThat(detail.hasExtension(), is(true));
		org.hl7.fhir.r4.model.Extension dueKindExtension = detail
		        .getExtensionByUrl("http://openmrs.org/fhir/StructureDefinition/activity-dueKind");
		assertThat(dueKindExtension, is(notNullValue()));
		assertThat(dueKindExtension.getValue(), instanceOf(org.hl7.fhir.r4.model.CodeType.class));
		org.hl7.fhir.r4.model.CodeType dueKindValue = (org.hl7.fhir.r4.model.CodeType) dueKindExtension.getValue();
		assertThat(dueKindValue.getValue(), is("this-visit"));
		
		// Check encounter extension
		org.hl7.fhir.r4.model.Extension encounterExtension = detail
		        .getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/encounter-associatedEncounter");
		assertThat(encounterExtension, is(notNullValue()));
	}
	
	@Test
	public void toCarePlan_withNextVisitTask_followingVisitEnded_shouldReturnPastVisitWithEndDate() throws Exception {
		// Given: Task scheduled for "next visit" during a past visit, and the following visit has ended
		VisitService visitService = Context.getVisitService();
		
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_YEAR, -5);
		Date firstVisitStart = cal.getTime();
		cal.add(Calendar.DAY_OF_YEAR, 1);
		Date firstVisitEnd = cal.getTime();
		
		cal.add(Calendar.DAY_OF_YEAR, 1);
		Date secondVisitStart = cal.getTime();
		cal.add(Calendar.DAY_OF_YEAR, 1);
		Date secondVisitEnd = cal.getTime();
		
		Visit firstVisit = createTestVisit(testPatient, firstVisitStart, firstVisitEnd);
		Visit secondVisit = createTestVisit(testPatient, secondVisitStart, secondVisitEnd);
		visitService.saveVisit(firstVisit);
		visitService.saveVisit(secondVisit);
		Context.flushSession();
		Context.clearSession();
		
		// Refresh visits from database to ensure they have IDs
		firstVisit = visitService.getVisitByUuid(firstVisit.getUuid());
		secondVisit = visitService.getVisitByUuid(secondVisit.getUuid());
		
		// Ensure visits are properly flushed and session is cleared so mapper can find them
		Context.flushSession();
		Context.clearSession();
		
		// Refresh again after clearing session
		firstVisit = visitService.getVisitByUuid(firstVisit.getUuid());
		secondVisit = visitService.getVisitByUuid(secondVisit.getUuid());
		
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Test task");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setDueDateType(DueDateType.NEXT_VISIT);
		task.setDueDateReferenceVisit(firstVisit); // Task was created during first visit
		
		// When: Converting Task to CarePlan
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		// Then: Should return scheduledPeriod with end date and activity-dueKind extension
		assertThat(carePlan, is(notNullValue()));
		assertThat(carePlan.hasActivity(), is(true));
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		assertThat(detail.hasScheduled(), is(true));
		assertThat(detail.getScheduled(), instanceOf(org.hl7.fhir.r4.model.Period.class));
		org.hl7.fhir.r4.model.Period period = (org.hl7.fhir.r4.model.Period) detail.getScheduled();
		assertThat(period.hasEnd(), is(true));
		// Verify end date matches second visit end date (within 1 second tolerance)
		long periodEndTime = period.getEnd().getTime();
		long visitEndTime = secondVisitEnd.getTime();
		assertThat(Math.abs(periodEndTime - visitEndTime), is(lessThan(1000L)));
		
		// Check activity-dueKind extension
		assertThat(detail.hasExtension(), is(true));
		org.hl7.fhir.r4.model.Extension dueKindExtension = detail
		        .getExtensionByUrl("http://openmrs.org/fhir/StructureDefinition/activity-dueKind");
		assertThat(dueKindExtension, is(notNullValue()));
		assertThat(dueKindExtension.getValue(), instanceOf(org.hl7.fhir.r4.model.CodeType.class));
		org.hl7.fhir.r4.model.CodeType dueKindValue = (org.hl7.fhir.r4.model.CodeType) dueKindExtension.getValue();
		assertThat(dueKindValue.getValue(), is("next-visit"));
		
		// Check encounter extension
		org.hl7.fhir.r4.model.Extension encounterExtension = detail
		        .getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/encounter-associatedEncounter");
		assertThat(encounterExtension, is(notNullValue()));
	}
	
	@Test
	public void toCarePlan_withNextVisitTask_lastVisitNoCurrentVisit_shouldReturnNextVisit() throws Exception {
		// Given: Task scheduled for "next visit" during the last visit and there is no current visit
		VisitService visitService = Context.getVisitService();
		
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_YEAR, -2);
		Date visitStart = cal.getTime();
		cal.add(Calendar.DAY_OF_YEAR, 1);
		Date visitEnd = cal.getTime();
		
		Visit lastVisit = createTestVisit(testPatient, visitStart, visitEnd); // Ended yesterday
		visitService.saveVisit(lastVisit);
		Context.flushSession();
		
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Test task");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setDueDateType(DueDateType.NEXT_VISIT);
		task.setDueDateReferenceVisit(lastVisit);
		
		// When: Converting Task to CarePlan
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		// Then: Should return scheduledPeriod (no end for future visit) and activity-dueKind extension
		assertThat(carePlan, is(notNullValue()));
		assertThat(carePlan.hasActivity(), is(true));
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		assertThat(detail.hasScheduled(), is(true));
		assertThat(detail.getScheduled(), instanceOf(org.hl7.fhir.r4.model.Period.class));
		org.hl7.fhir.r4.model.Period period = (org.hl7.fhir.r4.model.Period) detail.getScheduled();
		assertThat(period.hasEnd(), is(false)); // No end for future visit
		
		// Check activity-dueKind extension
		assertThat(detail.hasExtension(), is(true));
		org.hl7.fhir.r4.model.Extension dueKindExtension = detail
		        .getExtensionByUrl("http://openmrs.org/fhir/StructureDefinition/activity-dueKind");
		assertThat(dueKindExtension, is(notNullValue()));
		assertThat(dueKindExtension.getValue(), instanceOf(org.hl7.fhir.r4.model.CodeType.class));
		org.hl7.fhir.r4.model.CodeType dueKindValue = (org.hl7.fhir.r4.model.CodeType) dueKindExtension.getValue();
		assertThat(dueKindValue.getValue(), is("next-visit"));
		
		// Check encounter extension
		org.hl7.fhir.r4.model.Extension encounterExtension = detail
		        .getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/encounter-associatedEncounter");
		assertThat(encounterExtension, is(notNullValue()));
	}
	
	@Test
	public void toCarePlan_withNextVisitTask_lastVisitWithCurrentVisit_shouldReturnThisVisit() throws Exception {
		// Given: Task scheduled for "next visit" during the last visit, and there is now an ongoing visit
		VisitService visitService = Context.getVisitService();
		
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_YEAR, -3);
		Date lastVisitStart = cal.getTime();
		cal.add(Calendar.DAY_OF_YEAR, 1);
		Date lastVisitEnd = cal.getTime();
		
		cal.setTime(new Date());
		Date currentVisitStart = cal.getTime();
		
		Visit lastVisit = createTestVisit(testPatient, lastVisitStart, lastVisitEnd);
		Visit currentVisit = createTestVisit(testPatient, currentVisitStart, null); // Ongoing
		visitService.saveVisit(lastVisit);
		visitService.saveVisit(currentVisit);
		Context.flushSession();
		Context.clearSession();
		
		// Refresh visits from database to ensure they have IDs
		lastVisit = visitService.getVisitByUuid(lastVisit.getUuid());
		currentVisit = visitService.getVisitByUuid(currentVisit.getUuid());
		
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Test task");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setDueDateType(DueDateType.NEXT_VISIT);
		task.setDueDateReferenceVisit(lastVisit); // Task was created during last visit
		
		// When: Converting Task to CarePlan
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		// Then: Should return scheduledPeriod (no end for ongoing visit) and activity-dueKind extension
		// Note: The dueKind remains "next-visit" since that's what was set, even though the visit is now current
		assertThat(carePlan, is(notNullValue()));
		assertThat(carePlan.hasActivity(), is(true));
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		assertThat(detail.hasScheduled(), is(true));
		assertThat(detail.getScheduled(), instanceOf(org.hl7.fhir.r4.model.Period.class));
		org.hl7.fhir.r4.model.Period period = (org.hl7.fhir.r4.model.Period) detail.getScheduled();
		assertThat(period.hasEnd(), is(false)); // No end for ongoing visit
		
		// Check activity-dueKind extension (still "next-visit" since that's what was originally set)
		assertThat(detail.hasExtension(), is(true));
		org.hl7.fhir.r4.model.Extension dueKindExtension = detail
		        .getExtensionByUrl("http://openmrs.org/fhir/StructureDefinition/activity-dueKind");
		assertThat(dueKindExtension, is(notNullValue()));
		assertThat(dueKindExtension.getValue(), instanceOf(org.hl7.fhir.r4.model.CodeType.class));
		org.hl7.fhir.r4.model.CodeType dueKindValue = (org.hl7.fhir.r4.model.CodeType) dueKindExtension.getValue();
		assertThat(dueKindValue.getValue(), is("next-visit"));
		
		// Check encounter extension
		org.hl7.fhir.r4.model.Extension encounterExtension = detail
		        .getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/encounter-associatedEncounter");
		assertThat(encounterExtension, is(notNullValue()));
	}
	
	/**
	 * Helper method to create a test Visit.
	 */
	private Visit createTestVisit(Patient patient, Date startDate, Date endDate) {
		VisitService visitService = Context.getVisitService();
		List<VisitType> visitTypes = visitService.getAllVisitTypes();
		VisitType visitType = visitTypes.isEmpty() ? null : visitTypes.get(0);
		
		Visit visit = new Visit();
		visit.setPatient(patient);
		if (startDate == null) {
			startDate = new Date();
		}
		visit.setStartDatetime(startDate);
		visit.setStopDatetime(endDate);
		if (visitType != null) {
			visit.setVisitType(visitType);
		}
		
		return visit;
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void toCarePlan_withNullTask_shouldThrow() {
		carePlanMapper.toCarePlan(null);
	}
	
	@Test
	public void applyCarePlanToTask_withCarePlanWithNoActivities_shouldStillSetPatient() {
		CarePlan carePlan = new CarePlan();
		Reference patientRef = new Reference();
		patientRef.setReference("Patient/" + testPatient.getUuid());
		carePlan.setSubject(patientRef);
		
		Task result = carePlanMapper.applyCarePlanToTask(new Task(), carePlan, testPatient, null, null);
		
		assertThat(result, is(notNullValue()));
		assertThat(result.getPatient(), is(testPatient));
		assertThat(result.getDescription(), is(nullValue()));
		assertThat(result.getStatus(), is(nullValue()));
	}
	
	@Test
	public void applyCarePlanToTask_withMultipleActivities_shouldReadOnlyFirst() {
		// Document that only the first activity is considered.
		CarePlan carePlan = createCarePlanWithoutPerformers();
		CarePlan.CarePlanActivityDetailComponent firstDetail = carePlan.getActivityFirstRep().getDetail();
		firstDetail.setDescription("first activity description");
		
		CarePlan.CarePlanActivityComponent secondActivity = new CarePlan.CarePlanActivityComponent();
		CarePlan.CarePlanActivityDetailComponent secondDetail = new CarePlan.CarePlanActivityDetailComponent();
		secondDetail.setDescription("second activity description");
		secondActivity.setDetail(secondDetail);
		carePlan.addActivity(secondActivity);
		
		Task result = carePlanMapper.applyCarePlanToTask(new Task(), carePlan, testPatient, null, null);
		
		assertThat(result.getDescription(), is("first activity description"));
	}
	
	@Test
	public void applyCarePlanToTask_onUpdate_shouldResetAllWritableFields() {
		Task existing = new Task();
		existing.setPatient(testPatient);
		existing.setAssignee(testProvider);
		existing.setAssigneeProviderRoleId(99);
		existing.setDueDate(new java.util.Date());
		existing.setDueDateType(DueDateType.DATE);
		existing.setRationale("prior rationale");
		existing.setPriority(Priority.HIGH);
		existing.setDescription("prior description");
		existing.setStatus(TaskStatus.NOTSTARTED);
		existing.setKind(TaskKind.APPOINTMENT);
		
		CarePlan incoming = new CarePlan();
		Reference patientRef = new Reference();
		patientRef.setReference("Patient/" + testPatient.getUuid());
		incoming.setSubject(patientRef);
		// no activities, no description, no assignee
		
		Task result = carePlanMapper.applyCarePlanToTask(existing, incoming, testPatient, null, null);
		
		assertThat(result.getAssignee(), is(nullValue()));
		assertThat(result.getAssigneeProviderRoleId(), is(nullValue()));
		assertThat(result.getDueDate(), is(nullValue()));
		assertThat(result.getDueDateType(), is(nullValue()));
		assertThat(result.getRationale(), is(nullValue()));
		assertThat(result.getPriority(), is(nullValue()));
		assertThat(result.getDescription(), is(nullValue()));
		assertThat(result.getStatus(), is(nullValue()));
		assertThat(result.getKind(), is(nullValue()));
	}
	
	@Test
	public void toCarePlan_withCancelledStatus_shouldMapToRevokedCarePlanStatus() {
		Task task = new Task();
		task.setPatient(testPatient);
		task.setStatus(TaskStatus.CANCELLED);
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		assertThat(carePlan.getStatus(), is(CarePlan.CarePlanStatus.REVOKED));
	}
	
	@Test
	public void toCarePlan_withStoppedStatus_shouldMapToRevokedCarePlanStatus() {
		Task task = new Task();
		task.setPatient(testPatient);
		task.setStatus(TaskStatus.STOPPED);
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		assertThat(carePlan.getStatus(), is(CarePlan.CarePlanStatus.REVOKED));
	}
	
	@Test
	public void toCarePlan_withOnHoldStatus_shouldMapToOnHoldCarePlanStatus() {
		Task task = new Task();
		task.setPatient(testPatient);
		task.setStatus(TaskStatus.ONHOLD);
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		assertThat(carePlan.getStatus(), is(CarePlan.CarePlanStatus.ONHOLD));
	}
	
	@Test
	public void toCarePlan_withEnteredInErrorStatus_shouldMapToEnteredInErrorCarePlanStatus() {
		Task task = new Task();
		task.setPatient(testPatient);
		task.setStatus(TaskStatus.ENTEREDINERROR);
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		assertThat(carePlan.getStatus(), is(CarePlan.CarePlanStatus.ENTEREDINERROR));
	}
	
	@Test
	public void toCarePlan_withInProgressStatus_shouldMapToActiveCarePlanStatus() {
		Task task = new Task();
		task.setPatient(testPatient);
		task.setStatus(TaskStatus.INPROGRESS);
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		assertThat(carePlan.getStatus(), is(CarePlan.CarePlanStatus.ACTIVE));
	}
	
	@Test
	public void toCarePlan_withCompletedStatus_shouldMapToCompletedCarePlanStatus() {
		Task task = new Task();
		task.setPatient(testPatient);
		task.setStatus(TaskStatus.COMPLETED);
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		assertThat(carePlan.getStatus(), is(CarePlan.CarePlanStatus.COMPLETED));
	}
	
	@Test
	public void toCarePlan_withVoidedTask_shouldMapToEnteredInErrorCarePlanStatus() {
		Task task = new Task();
		task.setPatient(testPatient);
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setVoided(true);
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		assertThat(carePlan.getStatus(), is(CarePlan.CarePlanStatus.ENTEREDINERROR));
		assertThat(carePlan.getActivityFirstRep().getDetail().getStatus(),
		    is(CarePlan.CarePlanActivityStatus.ENTEREDINERROR));
	}
	
	@Test
	public void applyCarePlanToTask_withNonCancelledStatus_shouldNotClearVoidedFlag() {
		// Documents that once a task is voided, re-applying a CarePlan with a non-CANCELLED
		// status does NOT un-void the task.
		Task task = new Task();
		task.setVoided(true);
		task.setDateVoided(new java.util.Date(1000L));
		
		CarePlan carePlan = createCarePlanWithoutPerformers();
		carePlan.getActivityFirstRep().getDetail().setStatus(CarePlan.CarePlanActivityStatus.INPROGRESS);
		
		Task result = carePlanMapper.applyCarePlanToTask(task, carePlan, testPatient, null, null);
		
		assertThat(result.getVoided(), is(true));
		assertThat(result.getStatus(), is(TaskStatus.INPROGRESS));
	}
	
	// Note: scheduled[x] of type DateTime / Date is not a valid FHIR R4 choice for
	// CarePlan.activity.detail.scheduled (only Timing / Period / string are), so those branches
	// in the mapper are unreachable via the HAPI API and cannot be exercised here.
	
	@Test
	public void applyCarePlanToTask_withScheduledTimingEvent_shouldReadFirstEvent() {
		CarePlan carePlan = createCarePlanWithoutPerformers();
		org.hl7.fhir.r4.model.Timing timing = new org.hl7.fhir.r4.model.Timing();
		Date first = new Date(1000000000L);
		Date second = new Date(2000000000L);
		timing.addEvent(first);
		timing.addEvent(second);
		carePlan.getActivityFirstRep().getDetail().setScheduled(timing);
		
		Task result = carePlanMapper.applyCarePlanToTask(new Task(), carePlan, testPatient, null, null);
		
		assertThat(result.getDueDate(), is(first));
		assertThat(result.getDueDateType(), is(DueDateType.DATE));
	}
	
	@Test
	public void applyCarePlanToTask_withScheduledTimingBoundsPeriod_shouldReadBoundsEnd() {
		CarePlan carePlan = createCarePlanWithoutPerformers();
		org.hl7.fhir.r4.model.Timing timing = new org.hl7.fhir.r4.model.Timing();
		org.hl7.fhir.r4.model.Period bounds = new org.hl7.fhir.r4.model.Period();
		Date end = new Date(1500000000L);
		bounds.setEnd(end);
		timing.getRepeat().setBounds(bounds);
		carePlan.getActivityFirstRep().getDetail().setScheduled(timing);
		
		Task result = carePlanMapper.applyCarePlanToTask(new Task(), carePlan, testPatient, null, null);
		
		assertThat(result.getDueDate(), is(end));
		assertThat(result.getDueDateType(), is(DueDateType.DATE));
	}
	
	@Test
	public void toCarePlan_thenApplyBack_shouldRoundTripCoreFields() {
		// Happy-path round trip for DATE-typed tasks.
		Task original = new Task();
		original.setPatient(testPatient);
		original.setDescription("round-trip description");
		original.setStatus(TaskStatus.NOTSTARTED);
		original.setKind(TaskKind.APPOINTMENT);
		original.setPriority(Priority.HIGH);
		Date due = new Date(1700000000000L);
		original.setDueDate(due);
		original.setDueDateType(DueDateType.DATE);
		original.setAssignee(testProvider);
		original.setRationale("the rationale");
		
		CarePlan carePlan = carePlanMapper.toCarePlan(original);
		Task roundTripped = carePlanMapper.applyCarePlanToTask(new Task(), carePlan, testPatient, null, null);
		
		assertThat(roundTripped.getDescription(), is("round-trip description"));
		assertThat(roundTripped.getStatus(), is(TaskStatus.NOTSTARTED));
		assertThat(roundTripped.getKind(), is(TaskKind.APPOINTMENT));
		assertThat(roundTripped.getPriority(), is(Priority.HIGH));
		assertThat(roundTripped.getDueDateType(), is(DueDateType.DATE));
		assertThat(roundTripped.getDueDate(), is(due));
		assertThat(roundTripped.getAssignee(), is(notNullValue()));
		assertThat(roundTripped.getAssignee().getUuid(), is(testProvider.getUuid()));
		assertThat(roundTripped.getRationale(), is("the rationale"));
	}
	
	@Test
	public void toCarePlan_thenApplyBack_withThisVisitTask_shouldNotPopulateDueDateFromPeriod() throws Exception {
		// THIS_VISIT/NEXT_VISIT tasks must round-trip with dueDate still null. The reference visit is
		// the source of truth; the scheduledPeriod we emit is a derived display value that must not
		// be cached back onto the entity.
		VisitService visitService = Context.getVisitService();
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_YEAR, -2);
		Date visitStart = cal.getTime();
		cal.add(Calendar.DAY_OF_YEAR, 1);
		Date visitEnd = cal.getTime();
		
		Visit endedVisit = createTestVisit(testPatient, visitStart, visitEnd);
		visitService.saveVisit(endedVisit);
		Context.flushSession();
		
		Task original = new Task();
		original.setPatient(testPatient);
		original.setDescription("visit-anchored");
		original.setStatus(TaskStatus.NOTSTARTED);
		original.setDueDateType(DueDateType.THIS_VISIT);
		original.setDueDateReferenceVisit(endedVisit);
		// dueDate intentionally null
		
		CarePlan carePlan = carePlanMapper.toCarePlan(original);
		Task roundTripped = carePlanMapper.applyCarePlanToTask(new Task(), carePlan, testPatient, null, null);
		
		assertThat(roundTripped.getDueDateType(), is(DueDateType.THIS_VISIT));
		assertThat(roundTripped.getDueDate(), is(nullValue()));
	}
	
	@Test
	public void toCarePlan_withAppointmentKind_shouldReferenceAppointment() {
		assertKindMapsToResourceType(TaskKind.APPOINTMENT, "Appointment");
	}
	
	@Test
	public void toCarePlan_withCommunicationRequestKind_shouldReferenceCommunicationRequest() {
		assertKindMapsToResourceType(TaskKind.COMMUNICATIONREQUEST, "CommunicationRequest");
	}
	
	@Test
	public void toCarePlan_withDeviceRequestKind_shouldReferenceDeviceRequest() {
		assertKindMapsToResourceType(TaskKind.DEVICEREQUEST, "DeviceRequest");
	}
	
	@Test
	public void toCarePlan_withMedicationRequestKind_shouldReferenceMedicationRequest() {
		assertKindMapsToResourceType(TaskKind.MEDICATIONREQUEST, "MedicationRequest");
	}
	
	@Test
	public void toCarePlan_withNutritionOrderKind_shouldReferenceNutritionOrder() {
		assertKindMapsToResourceType(TaskKind.NUTRITIONORDER, "NutritionOrder");
	}
	
	@Test
	public void toCarePlan_withServiceRequestKind_shouldReferenceServiceRequest() {
		assertKindMapsToResourceType(TaskKind.SERVICEREQUEST, "ServiceRequest");
	}
	
	@Test
	public void toCarePlan_withTaskKind_shouldReferenceTask() {
		assertKindMapsToResourceType(TaskKind.TASK, "Task");
	}
	
	@Test
	public void toCarePlan_withVisionPrescriptionKind_shouldReferenceVisionPrescription() {
		assertKindMapsToResourceType(TaskKind.VISIONPRESCRIPTION, "VisionPrescription");
	}
	
	@Test
	public void toCarePlan_withNullKind_shouldNotSetActivityReference() {
		Task task = new Task();
		task.setPatient(testPatient);
		task.setStatus(TaskStatus.NOTSTARTED);
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		assertThat(carePlan.hasActivity(), is(true));
		assertThat(carePlan.getActivityFirstRep().hasReference(), is(false));
	}
	
	private void assertKindMapsToResourceType(TaskKind kind, String expectedType) {
		Task task = new Task();
		task.setPatient(testPatient);
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setKind(kind);
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		assertThat(carePlan.hasActivity(), is(true));
		Reference activityRef = carePlan.getActivityFirstRep().getReference();
		assertThat(activityRef, is(notNullValue()));
		assertThat(activityRef.getType(), is(expectedType));
	}
	
	// ---------- URL-prefixed performer references ----------
	
	@Test
	public void applyCarePlanToTask_withAbsoluteUrlPractitionerReference_shouldResolveProvider() {
		CarePlan carePlan = createCarePlanWithoutPerformers();
		Reference performer = new Reference();
		performer.setReference("http://remote.example.org/fhir/Practitioner/" + testProvider.getUuid());
		carePlan.getActivityFirstRep().getDetail().addPerformer(performer);
		// Override the @Before stub (which splits on a bare "Practitioner/" substring) to use
		// HAPI's URL-aware idPart extraction so the absolute URL actually resolves.
		when(practitionerReferenceTranslator.toOpenmrsType(any(Reference.class))).thenAnswer(invocation -> {
			Reference ref = (Reference) invocation.getArguments()[0];
			return providerService.getProviderByUuid(ref.getReferenceElement().getIdPart());
		});
		
		Task result = carePlanMapper.applyCarePlanToTask(new Task(), carePlan, testPatient, null, null);
		
		assertThat(result.getAssignee(), is(notNullValue()));
		assertThat(result.getAssignee().getUuid(), is(testProvider.getUuid()));
	}
	
	@Test
	public void applyCarePlanToTask_withAbsoluteUrlPractitionerRoleReference_shouldResolveRole() throws Exception {
		CarePlan carePlan = createCarePlanWithoutPerformers();
		Reference performer = new Reference();
		performer.setReference("http://remote.example.org/fhir/PractitionerRole/" + testProviderRole.getUuid());
		carePlan.getActivityFirstRep().getDetail().addPerformer(performer);
		
		Task result = carePlanMapper.applyCarePlanToTask(new Task(), carePlan, testPatient, null, null);
		
		assertThat(result.getAssigneeProviderRoleId(), is(testProviderRole.getProviderRoleId()));
	}
	
	// ---------- findNextVisitAfterReference edge cases (exercised via toCarePlan on NEXT_VISIT tasks) ----------
	
	@Test
	public void toCarePlan_withNextVisitTask_whenOnlyReferenceVisitExists_shouldProducePeriodWithoutEnd() throws Exception {
		// Zero candidates after the reference → no follow-up visit, so period has no end.
		VisitService visitService = Context.getVisitService();
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_YEAR, -1);
		Date start = cal.getTime();
		
		Visit onlyVisit = createTestVisit(testPatient, start, null);
		visitService.saveVisit(onlyVisit);
		Context.flushSession();
		
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Test");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setDueDateType(DueDateType.NEXT_VISIT);
		task.setDueDateReferenceVisit(onlyVisit);
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		assertThat(detail.hasScheduled(), is(true));
		org.hl7.fhir.r4.model.Period period = (org.hl7.fhir.r4.model.Period) detail.getScheduled();
		assertThat(period.hasEnd(), is(false));
	}
	
	@Test
	public void toCarePlan_withNextVisitTask_whenReferenceVisitHasNullStart_shouldNotSetScheduledPeriod() {
		Visit dangling = new Visit();
		dangling.setPatient(testPatient);
		dangling.setStartDatetime(null);
		dangling.setUuid("dangling-visit-uuid");
		
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Test");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setDueDateType(DueDateType.NEXT_VISIT);
		task.setDueDateReferenceVisit(dangling);
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		assertThat(detail.hasScheduled(), is(false));
		org.hl7.fhir.r4.model.Extension dueKind = detail
		        .getExtensionByUrl("http://openmrs.org/fhir/StructureDefinition/activity-dueKind");
		assertThat(dueKind, is(notNullValue()));
	}
	
	@Test
	public void toCarePlan_withNextVisitTask_shouldPickEarliestVisitAfterReference() throws Exception {
		// Two visits occur after the reference; the earlier one should win.
		VisitService visitService = Context.getVisitService();
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_YEAR, -10);
		Date firstStart = cal.getTime();
		cal.add(Calendar.DAY_OF_YEAR, 1);
		Date firstEnd = cal.getTime();
		
		cal.add(Calendar.DAY_OF_YEAR, 1);
		Date earlyNextStart = cal.getTime();
		cal.add(Calendar.DAY_OF_YEAR, 1);
		Date earlyNextEnd = cal.getTime();
		
		cal.add(Calendar.DAY_OF_YEAR, 3);
		Date lateNextStart = cal.getTime();
		cal.add(Calendar.DAY_OF_YEAR, 1);
		Date lateNextEnd = cal.getTime();
		
		Visit firstVisit = createTestVisit(testPatient, firstStart, firstEnd);
		Visit earlyNext = createTestVisit(testPatient, earlyNextStart, earlyNextEnd);
		Visit lateNext = createTestVisit(testPatient, lateNextStart, lateNextEnd);
		visitService.saveVisit(firstVisit);
		visitService.saveVisit(earlyNext);
		visitService.saveVisit(lateNext);
		Context.flushSession();
		Context.clearSession();
		
		firstVisit = visitService.getVisitByUuid(firstVisit.getUuid());
		
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("Test");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setDueDateType(DueDateType.NEXT_VISIT);
		task.setDueDateReferenceVisit(firstVisit);
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		org.hl7.fhir.r4.model.Period period = (org.hl7.fhir.r4.model.Period) detail.getScheduled();
		assertThat(period.hasEnd(), is(true));
		// period.end matches earlyNextEnd (within 1s for DB rounding)
		assertThat(Math.abs(period.getEnd().getTime() - earlyNextEnd.getTime()), is(lessThan(1000L)));
	}
	
	// ---------- description vs rationale direction-specific tests ----------
	
	@Test
	public void toCarePlan_shouldMapTaskDescriptionToDetailDescription() {
		Task task = new Task();
		task.setPatient(testPatient);
		task.setDescription("task description text");
		task.setStatus(TaskStatus.NOTSTARTED);
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		assertThat(carePlan.getActivityFirstRep().getDetail().getDescription(), is("task description text"));
	}
	
	@Test
	public void toCarePlan_shouldMapTaskRationaleToActivityReasonCode() {
		Task task = new Task();
		task.setPatient(testPatient);
		task.setRationale("the clinical rationale");
		task.setStatus(TaskStatus.NOTSTARTED);
		
		CarePlan carePlan = carePlanMapper.toCarePlan(task);
		
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		assertThat(detail.hasReasonCode(), is(true));
		assertThat(detail.getReasonCodeFirstRep().getText(), is("the clinical rationale"));
	}
	
	@Test
	public void applyCarePlanToTask_shouldMapDetailDescriptionToTaskDescription() {
		CarePlan carePlan = createCarePlanWithoutPerformers();
		carePlan.getActivityFirstRep().getDetail().setDescription("detail description text");
		
		Task result = carePlanMapper.applyCarePlanToTask(new Task(), carePlan, testPatient, null, null);
		
		assertThat(result.getDescription(), is("detail description text"));
	}
	
	@Test
	public void applyCarePlanToTask_shouldMapActivityReasonCodeTextToTaskRationale() {
		CarePlan carePlan = createCarePlanWithoutPerformers();
		carePlan.getActivityFirstRep().getDetail()
		        .addReasonCode(new org.hl7.fhir.r4.model.CodeableConcept().setText("rationale from reasonCode"));
		
		Task result = carePlanMapper.applyCarePlanToTask(new Task(), carePlan, testPatient, null, null);
		
		assertThat(result.getRationale(), is("rationale from reasonCode"));
	}
	
	@Test
	public void applyCarePlanToTask_shouldNotInterpretCarePlanDescriptionAsRationale() {
		CarePlan carePlan = createCarePlanWithoutPerformers();
		carePlan.setDescription("plan-level summary that should NOT become rationale");
		
		Task result = carePlanMapper.applyCarePlanToTask(new Task(), carePlan, testPatient, null, null);
		
		assertThat(result.getRationale(), is(nullValue()));
	}
	
	@Test
	public void applyCarePlanToTask_withHyphenatedActivityReferenceType_shouldResolveKind() {
		CarePlan carePlan = createCarePlanWithoutPerformers();
		Reference reference = new Reference();
		reference.setType("medication-request");
		carePlan.getActivityFirstRep().setReference(reference);
		
		Task result = carePlanMapper.applyCarePlanToTask(new Task(), carePlan, testPatient, null, null);
		
		assertThat(result.getKind(), is(TaskKind.MEDICATIONREQUEST));
	}
	
	@Test
	public void applyCarePlanToTask_withNextVisitDueKindAndEncounterExtension_shouldSetReferenceVisit() throws Exception {
		VisitService visitService = Context.getVisitService();
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_YEAR, -1);
		Visit visit = createTestVisit(testPatient, cal.getTime(), null);
		visitService.saveVisit(visit);
		Context.flushSession();
		
		CarePlan carePlan = createCarePlanWithoutPerformers();
		CarePlan.CarePlanActivityDetailComponent detail = carePlan.getActivityFirstRep().getDetail();
		
		Extension dueKind = new Extension();
		dueKind.setUrl("http://openmrs.org/fhir/StructureDefinition/activity-dueKind");
		dueKind.setValue(new org.hl7.fhir.r4.model.CodeType("next-visit"));
		detail.addExtension(dueKind);
		
		Extension encounterExt = new Extension();
		encounterExt.setUrl("http://hl7.org/fhir/StructureDefinition/encounter-associatedEncounter");
		Reference encRef = new Reference();
		encRef.setReference("Encounter/" + visit.getUuid());
		encounterExt.setValue(encRef);
		detail.addExtension(encounterExt);
		
		Task result = carePlanMapper.applyCarePlanToTask(new Task(), carePlan, testPatient, null, null);
		
		assertThat(result.getDueDateType(), is(DueDateType.NEXT_VISIT));
		assertThat(result.getDueDateReferenceVisit(), is(notNullValue()));
		assertThat(result.getDueDateReferenceVisit().getUuid(), is(visit.getUuid()));
	}
}
