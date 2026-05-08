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

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.module.tasks.Task;
import org.openmrs.module.tasks.TaskStatus;
import org.openmrs.module.tasks.api.TasksService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CarePlanFhirResourceProvider}. Verifies the provider's orchestration logic:
 * resolving patient/performer references from a CarePlan payload, delegating to the mapper, and
 * translating results to {@link MethodOutcome}. Integration with the persistence layer is out of
 * scope here and covered in {@code CarePlanMapperAssigneeTest}.
 */
public class CarePlanFhirResourceProviderTest {
	
	private static final String PATIENT_UUID = "patient-uuid-1";
	
	private static final String PROVIDER_UUID = "provider-uuid-1";
	
	private static final String PROVIDER_ROLE_UUID = "provider-role-uuid-1";
	
	private static final String CARE_PLAN_UUID = "care-plan-uuid-1";
	
	@Mock
	private TasksService tasksService;
	
	@Mock
	private PatientService patientService;
	
	@Mock
	private ProviderService providerService;
	
	@Mock
	private CarePlanMapper carePlanMapper;
	
	private CarePlanFhirResourceProvider provider;
	
	private Patient testPatient;
	
	private Provider testProvider;
	
	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		
		testPatient = new Patient();
		testPatient.setUuid(PATIENT_UUID);
		testPatient.setPatientId(42);
		
		testProvider = new Provider();
		testProvider.setUuid(PROVIDER_UUID);
		
		provider = new CarePlanFhirResourceProvider();
		provider.setTasksService(tasksService);
		provider.setPatientService(patientService);
		provider.setProviderService(providerService);
		provider.setCarePlanMapper(carePlanMapper);
	}
	
	@Test
	public void getResourceType_shouldReturnCarePlan() {
		assertThat(provider.getResourceType(), is(equalTo(CarePlan.class)));
	}
	
	// ---------- create ----------
	
	@Test(expected = InvalidRequestException.class)
	public void create_withoutPatientReference_shouldThrow() {
		CarePlan carePlan = new CarePlan();
		
		provider.create(carePlan);
	}
	
	@Test(expected = ResourceNotFoundException.class)
	public void create_withUnknownPatient_shouldThrowResourceNotFound() {
		CarePlan carePlan = carePlanWithSubject(PATIENT_UUID);
		when(patientService.getPatientByUuid(PATIENT_UUID)).thenReturn(null);
		
		provider.create(carePlan);
	}
	
	@Test(expected = ResourceNotFoundException.class)
	public void update_withUnknownPatientInPayload_shouldThrowResourceNotFound() {
		Task existing = new Task();
		existing.setUuid(CARE_PLAN_UUID);
		existing.setPatient(testPatient);
		CarePlan incoming = carePlanWithSubject("unknown-patient-uuid");
		
		when(tasksService.getTaskByUuid(CARE_PLAN_UUID)).thenReturn(existing);
		when(patientService.getPatientByUuid("unknown-patient-uuid")).thenReturn(null);
		
		provider.update(new IdType("CarePlan", CARE_PLAN_UUID), incoming);
	}
	
	@Test(expected = InvalidRequestException.class)
	public void create_withNonPatientSubject_shouldThrowInvalidRequest() {
		CarePlan carePlan = new CarePlan();
		Reference subject = new Reference();
		subject.setReference("Group/some-group-uuid");
		carePlan.setSubject(subject);
		
		provider.create(carePlan);
	}
	
	@Test(expected = InvalidRequestException.class)
	public void create_withSubjectReferenceMissingIdPart_shouldThrowInvalidRequest() {
		CarePlan carePlan = new CarePlan();
		Reference subject = new Reference();
		subject.setReference("Patient/");
		carePlan.setSubject(subject);
		
		provider.create(carePlan);
	}
	
	@Test
	public void create_withResolvedPatient_shouldDelegateToMapperAndReturnOutcome() {
		CarePlan incoming = carePlanWithSubject(PATIENT_UUID);
		Task mappedTask = new Task();
		// preset creator so the Context.getAuthenticatedUser() branch is not exercised
		mappedTask.setCreator(new User());
		Task savedTask = new Task();
		CarePlan saved = new CarePlan();
		saved.setId(CARE_PLAN_UUID);
		
		when(patientService.getPatientByUuid(PATIENT_UUID)).thenReturn(testPatient);
		when(carePlanMapper.toTask(eq(incoming), eq(testPatient), any(), any())).thenReturn(mappedTask);
		when(tasksService.saveTask(mappedTask)).thenReturn(savedTask);
		when(carePlanMapper.toCarePlan(savedTask)).thenReturn(saved);
		
		MethodOutcome outcome = provider.create(incoming);
		
		assertThat(outcome, is(notNullValue()));
		assertThat(outcome.getResource(), is(sameInstance(saved)));
		assertThat(outcome.getId().getResourceType(), is("CarePlan"));
		assertThat(outcome.getId().getIdPart(), is(CARE_PLAN_UUID));
	}
	
	@Test
	public void create_withPractitionerPerformer_shouldResolveProviderFromContext() {
		CarePlan incoming = carePlanWithSubject(PATIENT_UUID);
		addPerformer(incoming, "Practitioner/" + PROVIDER_UUID);
		Task mappedTask = new Task();
		mappedTask.setCreator(new User());
		
		when(patientService.getPatientByUuid(PATIENT_UUID)).thenReturn(testPatient);
		when(providerService.getProviderByUuid(PROVIDER_UUID)).thenReturn(testProvider);
		when(carePlanMapper.toTask(any(CarePlan.class), any(Patient.class), any(Provider.class), any()))
		        .thenReturn(mappedTask);
		when(tasksService.saveTask(any(Task.class))).thenReturn(mappedTask);
		when(carePlanMapper.toCarePlan(any(Task.class))).thenReturn(new CarePlan());
		
		provider.create(incoming);
		
		ArgumentCaptor<Provider> providerCaptor = ArgumentCaptor.forClass(Provider.class);
		verify(carePlanMapper).toTask(eq(incoming), eq(testPatient), providerCaptor.capture(), any());
		assertThat(providerCaptor.getValue(), is(sameInstance(testProvider)));
	}
	
	@Test
	public void create_withPractitionerRolePerformer_shouldPassRoleUuidToMapper() {
		CarePlan incoming = carePlanWithSubject(PATIENT_UUID);
		addPerformer(incoming, "PractitionerRole/" + PROVIDER_ROLE_UUID);
		Task mappedTask = new Task();
		mappedTask.setCreator(new User());
		
		when(patientService.getPatientByUuid(PATIENT_UUID)).thenReturn(testPatient);
		when(carePlanMapper.toTask(any(CarePlan.class), any(Patient.class), any(), any())).thenReturn(mappedTask);
		when(tasksService.saveTask(any(Task.class))).thenReturn(mappedTask);
		when(carePlanMapper.toCarePlan(any(Task.class))).thenReturn(new CarePlan());
		
		provider.create(incoming);
		
		ArgumentCaptor<String> roleCaptor = ArgumentCaptor.forClass(String.class);
		verify(carePlanMapper).toTask(eq(incoming), eq(testPatient), eq((Provider) null), roleCaptor.capture());
		assertThat(roleCaptor.getValue(), is(PROVIDER_ROLE_UUID));
	}
	
	@Test
	public void create_withBothPerformers_shouldResolveFirstOfEach() {
		CarePlan incoming = carePlanWithSubject(PATIENT_UUID);
		addPerformer(incoming, "Practitioner/" + PROVIDER_UUID);
		addPerformer(incoming, "Practitioner/other-provider");
		addPerformer(incoming, "PractitionerRole/" + PROVIDER_ROLE_UUID);
		addPerformer(incoming, "PractitionerRole/other-role");
		Task mappedTask = new Task();
		mappedTask.setCreator(new User());
		
		when(patientService.getPatientByUuid(PATIENT_UUID)).thenReturn(testPatient);
		when(providerService.getProviderByUuid(PROVIDER_UUID)).thenReturn(testProvider);
		when(carePlanMapper.toTask(any(CarePlan.class), any(Patient.class), any(), any())).thenReturn(mappedTask);
		when(tasksService.saveTask(any(Task.class))).thenReturn(mappedTask);
		when(carePlanMapper.toCarePlan(any(Task.class))).thenReturn(new CarePlan());
		
		provider.create(incoming);
		
		ArgumentCaptor<Provider> providerCaptor = ArgumentCaptor.forClass(Provider.class);
		ArgumentCaptor<String> roleCaptor = ArgumentCaptor.forClass(String.class);
		verify(carePlanMapper).toTask(eq(incoming), eq(testPatient), providerCaptor.capture(), roleCaptor.capture());
		assertThat(providerCaptor.getValue(), is(sameInstance(testProvider)));
		assertThat(roleCaptor.getValue(), is(PROVIDER_ROLE_UUID));
		verify(providerService, never()).getProviderByUuid("other-provider");
	}
	
	@Test
	public void create_withPreSetCreator_shouldNotOverwriteCreator() {
		CarePlan incoming = carePlanWithSubject(PATIENT_UUID);
		User preSetCreator = new User();
		preSetCreator.setUsername("preset");
		Task mappedTask = new Task();
		mappedTask.setCreator(preSetCreator);
		
		when(patientService.getPatientByUuid(PATIENT_UUID)).thenReturn(testPatient);
		when(carePlanMapper.toTask(any(CarePlan.class), any(Patient.class), any(), any())).thenReturn(mappedTask);
		when(tasksService.saveTask(any(Task.class))).thenReturn(mappedTask);
		when(carePlanMapper.toCarePlan(any(Task.class))).thenReturn(new CarePlan());
		
		provider.create(incoming);
		
		ArgumentCaptor<Task> savedTaskCaptor = ArgumentCaptor.forClass(Task.class);
		verify(tasksService).saveTask(savedTaskCaptor.capture());
		assertThat(savedTaskCaptor.getValue().getCreator(), is(sameInstance(preSetCreator)));
	}
	
	// ---------- update ----------
	
	@Test(expected = InvalidRequestException.class)
	public void update_withNullId_shouldThrow() {
		provider.update(null, new CarePlan());
	}
	
	@Test(expected = InvalidRequestException.class)
	public void update_withBlankId_shouldThrow() {
		provider.update(new IdType("CarePlan", ""), new CarePlan());
	}
	
	@Test(expected = ResourceNotFoundException.class)
	public void update_whenTaskNotFound_shouldThrowResourceNotFound() {
		when(tasksService.getTaskByUuid(CARE_PLAN_UUID)).thenReturn(null);
		
		provider.update(new IdType("CarePlan", CARE_PLAN_UUID), carePlanWithSubject(PATIENT_UUID));
	}
	
	@Test
	public void update_withoutPatientInPayload_shouldUseExistingTaskPatient() {
		Task existing = new Task();
		existing.setUuid(CARE_PLAN_UUID);
		existing.setPatient(testPatient);
		CarePlan incoming = new CarePlan(); // no subject
		
		when(tasksService.getTaskByUuid(CARE_PLAN_UUID)).thenReturn(existing);
		when(carePlanMapper.applyCarePlanToTask(eq(existing), eq(incoming), eq(testPatient), any(), any()))
		        .thenReturn(existing);
		when(tasksService.saveTask(existing)).thenReturn(existing);
		when(carePlanMapper.toCarePlan(existing)).thenReturn(new CarePlan());
		
		MethodOutcome outcome = provider.update(new IdType("CarePlan", CARE_PLAN_UUID), incoming);
		
		assertThat(outcome, is(notNullValue()));
		assertThat(incoming.getId(), is(CARE_PLAN_UUID));
		verify(carePlanMapper).applyCarePlanToTask(eq(existing), eq(incoming), eq(testPatient), any(), any());
	}
	
	@Test(expected = InvalidRequestException.class)
	public void update_withoutPatientInPayloadOrExisting_shouldThrow() {
		Task existing = new Task();
		existing.setUuid(CARE_PLAN_UUID);
		// existing has no patient
		CarePlan incoming = new CarePlan();
		
		when(tasksService.getTaskByUuid(CARE_PLAN_UUID)).thenReturn(existing);
		
		provider.update(new IdType("CarePlan", CARE_PLAN_UUID), incoming);
	}
	
	@Test
	public void update_shouldSetCarePlanIdFromPath() {
		Task existing = new Task();
		existing.setUuid(CARE_PLAN_UUID);
		existing.setPatient(testPatient);
		CarePlan incoming = carePlanWithSubject(PATIENT_UUID);
		incoming.setId("some-other-id");
		
		when(tasksService.getTaskByUuid(CARE_PLAN_UUID)).thenReturn(existing);
		when(patientService.getPatientByUuid(PATIENT_UUID)).thenReturn(testPatient);
		when(carePlanMapper.applyCarePlanToTask(eq(existing), eq(incoming), eq(testPatient), any(), any()))
		        .thenReturn(existing);
		when(tasksService.saveTask(existing)).thenReturn(existing);
		when(carePlanMapper.toCarePlan(existing)).thenReturn(new CarePlan());
		
		provider.update(new IdType("CarePlan", CARE_PLAN_UUID), incoming);
		
		assertThat(incoming.getId(), is(CARE_PLAN_UUID));
	}
	
	// ---------- read ----------
	
	@Test
	public void read_whenTaskExists_shouldReturnMappedCarePlan() {
		Task task = new Task();
		task.setUuid(CARE_PLAN_UUID);
		CarePlan expected = new CarePlan();
		expected.setId(CARE_PLAN_UUID);
		
		when(tasksService.getTaskByUuid(CARE_PLAN_UUID)).thenReturn(task);
		when(carePlanMapper.toCarePlan(task)).thenReturn(expected);
		
		CarePlan result = provider.read(new IdType("CarePlan", CARE_PLAN_UUID));
		
		assertThat(result, is(sameInstance(expected)));
	}
	
	@Test(expected = ResourceNotFoundException.class)
	public void read_whenTaskNotFound_shouldThrowResourceNotFound() {
		when(tasksService.getTaskByUuid(CARE_PLAN_UUID)).thenReturn(null);
		
		provider.read(new IdType("CarePlan", CARE_PLAN_UUID));
	}
	
	@Test
	public void read_whenTaskNotFound_shouldNotInvokeMapper() {
		when(tasksService.getTaskByUuid(CARE_PLAN_UUID)).thenReturn(null);
		
		try {
			provider.read(new IdType("CarePlan", CARE_PLAN_UUID));
		}
		catch (ResourceNotFoundException expected) {
			// expected
		}
		verify(carePlanMapper, never()).toCarePlan(any());
	}
	
	// ---------- delete ----------
	
	@Test(expected = InvalidRequestException.class)
	public void delete_withNullId_shouldThrow() {
		provider.delete(null);
	}
	
	@Test(expected = InvalidRequestException.class)
	public void delete_withBlankId_shouldThrow() {
		provider.delete(new IdType("CarePlan", ""));
	}
	
	@Test(expected = ResourceNotFoundException.class)
	public void delete_whenTaskNotFound_shouldThrowResourceNotFound() {
		when(tasksService.getTaskByUuid(CARE_PLAN_UUID)).thenReturn(null);
		
		provider.delete(new IdType("CarePlan", CARE_PLAN_UUID));
	}
	
	@Test
	public void delete_whenTaskExists_shouldVoidTaskAndReturnOutcome() {
		Task task = new Task();
		task.setUuid(CARE_PLAN_UUID);
		when(tasksService.getTaskByUuid(CARE_PLAN_UUID)).thenReturn(task);
		
		MethodOutcome outcome = provider.delete(new IdType("CarePlan", CARE_PLAN_UUID));
		
		verify(tasksService).voidTask(eq(task), eq("Voided via FHIR DELETE"));
		assertThat(outcome, is(notNullValue()));
		assertThat(outcome.getId().getResourceType(), is("CarePlan"));
		assertThat(outcome.getId().getIdPart(), is(CARE_PLAN_UUID));
	}
	
	@Test
	public void delete_shouldNotTouchTaskStatus() {
		// Voiding is orthogonal to task.status — the stored status stays whatever it was.
		Task task = new Task();
		task.setUuid(CARE_PLAN_UUID);
		task.setStatus(TaskStatus.INPROGRESS);
		when(tasksService.getTaskByUuid(CARE_PLAN_UUID)).thenReturn(task);
		
		provider.delete(new IdType("CarePlan", CARE_PLAN_UUID));
		
		assertThat(task.getStatus(), is(TaskStatus.INPROGRESS));
	}
	
	// ---------- search ----------
	
	@Test
	public void search_withNullSubject_shouldReturnEmptyList() {
		List<CarePlan> result = provider.search(null);
		
		assertThat(result, is(empty()));
		verifyNoInteractions(tasksService, patientService, carePlanMapper);
	}
	
	@Test
	public void search_withEmptySubject_shouldReturnEmptyList() {
		List<CarePlan> result = provider.search("");
		
		assertThat(result, is(empty()));
		verifyNoInteractions(tasksService, patientService, carePlanMapper);
	}
	
	@Test
	public void search_withUnknownPatient_shouldReturnEmptyList() {
		when(patientService.getPatientByUuid(PATIENT_UUID)).thenReturn(null);
		
		List<CarePlan> result = provider.search(PATIENT_UUID);
		
		assertThat(result, is(empty()));
		verify(tasksService, never()).getActiveTasksByPatientId(any(Integer.class));
	}
	
	@Test
	public void search_withPatientPrefix_shouldStripPrefixAndLookup() {
		Task task = new Task();
		when(patientService.getPatientByUuid(PATIENT_UUID)).thenReturn(testPatient);
		when(tasksService.getActiveTasksByPatientId(testPatient.getPatientId())).thenReturn(Collections.singletonList(task));
		when(carePlanMapper.toCarePlan(task)).thenReturn(new CarePlan());
		
		List<CarePlan> result = provider.search("Patient/" + PATIENT_UUID);
		
		assertThat(result, hasSize(1));
		verify(patientService).getPatientByUuid(PATIENT_UUID);
	}
	
	@Test
	public void search_withRawUuid_shouldLookupDirectly() {
		Task task = new Task();
		when(patientService.getPatientByUuid(PATIENT_UUID)).thenReturn(testPatient);
		when(tasksService.getActiveTasksByPatientId(testPatient.getPatientId())).thenReturn(Collections.singletonList(task));
		when(carePlanMapper.toCarePlan(task)).thenReturn(new CarePlan());
		
		List<CarePlan> result = provider.search(PATIENT_UUID);
		
		assertThat(result, hasSize(1));
		verify(patientService).getPatientByUuid(PATIENT_UUID);
	}
	
	@Test
	public void search_withMultipleTasks_shouldMapEach() {
		Task t1 = new Task();
		Task t2 = new Task();
		CarePlan cp1 = new CarePlan();
		CarePlan cp2 = new CarePlan();
		when(patientService.getPatientByUuid(PATIENT_UUID)).thenReturn(testPatient);
		when(tasksService.getActiveTasksByPatientId(testPatient.getPatientId())).thenReturn(Arrays.asList(t1, t2));
		when(carePlanMapper.toCarePlan(t1)).thenReturn(cp1);
		when(carePlanMapper.toCarePlan(t2)).thenReturn(cp2);
		
		List<CarePlan> result = provider.search(PATIENT_UUID);
		
		assertThat(result, hasSize(2));
		assertThat(result.get(0), is(sameInstance(cp1)));
		assertThat(result.get(1), is(sameInstance(cp2)));
	}
	
	// ---------- helpers ----------
	
	private static CarePlan carePlanWithSubject(String patientUuid) {
		CarePlan carePlan = new CarePlan();
		Reference subject = new Reference();
		subject.setReference("Patient/" + patientUuid);
		carePlan.setSubject(subject);
		return carePlan;
	}
	
	private static void addPerformer(CarePlan carePlan, String reference) {
		CarePlan.CarePlanActivityComponent activity;
		if (carePlan.getActivity().isEmpty()) {
			activity = carePlan.addActivity();
			activity.setDetail(new CarePlan.CarePlanActivityDetailComponent());
		} else {
			activity = carePlan.getActivityFirstRep();
			if (!activity.hasDetail()) {
				activity.setDetail(new CarePlan.CarePlanActivityDetailComponent());
			}
		}
		Reference ref = new Reference();
		ref.setReference(reference);
		activity.getDetail().addPerformer(ref);
	}
}
