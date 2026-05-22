/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.tasks.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.IdType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmrs.module.tasks.api.fhir.CarePlanFhirResourceProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * MockMvc tests for {@link CarePlanRestController}. Verifies HTTP status codes, content types, and
 * error response bodies for the REST shim that wraps {@link CarePlanFhirResourceProvider}.
 */
public class CarePlanRestControllerTest {
	
	private static final String CARE_PLAN_UUID = "care-plan-uuid-1";
	
	private static final String PATIENT_UUID = "patient-uuid-1";
	
	@Mock
	private CarePlanFhirResourceProvider resourceProvider;
	
	private MockMvc mockMvc;
	
	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		CarePlanRestController controller = new CarePlanRestController(resourceProvider);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}
	
	@Test
	public void search_shouldReturnBundleWithTotalAndEntries() throws Exception {
		CarePlan carePlan = new CarePlan();
		carePlan.setId(CARE_PLAN_UUID);
		when(resourceProvider.search("Patient/" + PATIENT_UUID)).thenReturn(Collections.singletonList(carePlan));
		
		mockMvc.perform(get("/rest/v1/tasks/careplan").param("subject", "Patient/" + PATIENT_UUID))
		        .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
		        .andExpect(jsonPath("$.resourceType").value("Bundle")).andExpect(jsonPath("$.total").value(1))
		        .andExpect(jsonPath("$.entry[0].resource.resourceType").value("CarePlan"))
		        .andExpect(jsonPath("$.entry[0].resource.id").value(CARE_PLAN_UUID));
	}
	
	@Test
	public void search_withEmptyResult_shouldReturnBundleWithZeroTotal() throws Exception {
		when(resourceProvider.search(PATIENT_UUID)).thenReturn(Collections.emptyList());
		
		mockMvc.perform(get("/rest/v1/tasks/careplan").param("subject", PATIENT_UUID)).andExpect(status().isOk())
		        .andExpect(jsonPath("$.resourceType").value("Bundle")).andExpect(jsonPath("$.total").value(0));
	}
	
	@Test
	public void read_whenCarePlanExists_shouldReturnResource() throws Exception {
		CarePlan carePlan = new CarePlan();
		carePlan.setId(CARE_PLAN_UUID);
		when(resourceProvider.read(any(IdType.class))).thenReturn(carePlan);
		
		mockMvc.perform(get("/rest/v1/tasks/careplan/{id}", CARE_PLAN_UUID)).andExpect(status().isOk())
		        .andExpect(jsonPath("$.resourceType").value("CarePlan")).andExpect(jsonPath("$.id").value(CARE_PLAN_UUID));
	}
	
	@Test
	public void read_whenCarePlanMissing_shouldReturn404() throws Exception {
		when(resourceProvider.read(any(IdType.class)))
		        .thenThrow(new ResourceNotFoundException(new IdType("CarePlan", CARE_PLAN_UUID)));
		
		mockMvc.perform(get("/rest/v1/tasks/careplan/{id}", CARE_PLAN_UUID)).andExpect(status().isNotFound())
		        .andExpect(jsonPath("$.error").exists());
	}
	
	@Test
	public void create_withValidPayload_shouldReturn201AndLocationHeader() throws Exception {
		CarePlan saved = new CarePlan();
		saved.setId(CARE_PLAN_UUID);
		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType("CarePlan", CARE_PLAN_UUID));
		outcome.setResource(saved);
		when(resourceProvider.create(any(CarePlan.class))).thenReturn(outcome);
		
		String payload = FhirContext.forR4Cached().newJsonParser().encodeResourceToString(newCarePlanWithSubject());
		
		mockMvc.perform(post("/rest/v1/tasks/careplan").contentType(MediaType.APPLICATION_JSON).content(payload))
		        .andExpect(status().isCreated()).andExpect(header().string("Location", "CarePlan/" + CARE_PLAN_UUID))
		        .andExpect(jsonPath("$.resourceType").value("CarePlan")).andExpect(jsonPath("$.id").value(CARE_PLAN_UUID));
	}
	
	@Test
	public void create_whenProviderRejectsPayload_shouldReturn400() throws Exception {
		when(resourceProvider.create(any(CarePlan.class)))
		        .thenThrow(new InvalidRequestException("Patient reference is required"));
		
		String payload = FhirContext.forR4Cached().newJsonParser().encodeResourceToString(newCarePlanWithSubject());
		
		mockMvc.perform(post("/rest/v1/tasks/careplan").contentType(MediaType.APPLICATION_JSON).content(payload))
		        .andExpect(status().isBadRequest())
		        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Patient reference")));
	}
	
	@Test
	public void create_withMalformedJson_shouldReturn400() throws Exception {
		mockMvc.perform(post("/rest/v1/tasks/careplan").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
		        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
	}
	
	@Test
	public void update_withValidPayload_shouldReturn200AndUpdatedResource() throws Exception {
		CarePlan saved = new CarePlan();
		saved.setId(CARE_PLAN_UUID);
		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType("CarePlan", CARE_PLAN_UUID));
		outcome.setResource(saved);
		when(resourceProvider.update(any(IdType.class), any(CarePlan.class))).thenReturn(outcome);
		
		String payload = FhirContext.forR4Cached().newJsonParser().encodeResourceToString(newCarePlanWithSubject());
		
		mockMvc.perform(
		    put("/rest/v1/tasks/careplan/{id}", CARE_PLAN_UUID).contentType(MediaType.APPLICATION_JSON).content(payload))
		        .andExpect(status().isOk()).andExpect(jsonPath("$.resourceType").value("CarePlan"))
		        .andExpect(jsonPath("$.id").value(CARE_PLAN_UUID));
	}
	
	@Test
	public void update_withMalformedJson_shouldReturn400() throws Exception {
		mockMvc.perform(put("/rest/v1/tasks/careplan/{id}", CARE_PLAN_UUID).contentType(MediaType.APPLICATION_JSON)
		        .content("{ not json")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
	}
	
	@Test
	public void update_whenCarePlanMissing_shouldReturn404() throws Exception {
		when(resourceProvider.update(any(IdType.class), any(CarePlan.class)))
		        .thenThrow(new ResourceNotFoundException(new IdType("CarePlan", CARE_PLAN_UUID)));
		
		String payload = FhirContext.forR4Cached().newJsonParser().encodeResourceToString(newCarePlanWithSubject());
		
		mockMvc.perform(
		    put("/rest/v1/tasks/careplan/{id}", CARE_PLAN_UUID).contentType(MediaType.APPLICATION_JSON).content(payload))
		        .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").exists());
	}
	
	private static CarePlan newCarePlanWithSubject() {
		CarePlan carePlan = new CarePlan();
		org.hl7.fhir.r4.model.Reference subject = new org.hl7.fhir.r4.model.Reference();
		subject.setReference("Patient/" + PATIENT_UUID);
		carePlan.setSubject(subject);
		return carePlan;
	}
}
