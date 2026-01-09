/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.webservices.rest.web.controller.tasks;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.MethodOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.IdType;
import org.openmrs.module.tasks.api.fhir.CarePlanFhirResourceProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * REST controller surfaced through the Webservices REST module dispatcher. Handles CarePlan search
 * and create requests under /ws/rest/v1/tasks/careplan.
 */
@RestController
@RequestMapping("/rest/v1/tasks/careplan")
public class TasksCarePlanController {
	
	private static final FhirContext FHIR_CONTEXT = FhirContext.forR4Cached();
	
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	
	private final CarePlanFhirResourceProvider carePlanFhirResourceProvider;
	
	public TasksCarePlanController(CarePlanFhirResourceProvider carePlanFhirResourceProvider) {
		this.carePlanFhirResourceProvider = carePlanFhirResourceProvider;
	}
	
	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> search(@RequestParam(name = "subject") String subject) {
		List<CarePlan> carePlans = carePlanFhirResourceProvider.search(subject);
		
		Bundle bundle = new Bundle();
		bundle.setId(UUID.randomUUID().toString());
		bundle.setType(Bundle.BundleType.SEARCHSET);
		bundle.setTimestamp(java.util.Date.from(Instant.now()));
		bundle.setTotal(carePlans.size());
		carePlans.forEach(carePlan -> bundle.addEntry().setResource(carePlan));
		
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(encodeResource(bundle));
	}
	
	@GetMapping(path = "/{carePlanId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> read(@PathVariable("carePlanId") String carePlanId) {
		CarePlan carePlan = carePlanFhirResourceProvider.read(new IdType("CarePlan", carePlanId));
		
		if (carePlan == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
			        .body(errorJson("CarePlan not found for ID: " + carePlanId));
		}
		
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(encodeResource(carePlan));
	}
	
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> create(@RequestBody String carePlanPayload) {
		CarePlan carePlan = parseCarePlan(carePlanPayload);
		
		MethodOutcome outcome = carePlanFhirResourceProvider.create(carePlan);
		CarePlan savedCarePlan = (CarePlan) outcome.getResource();
		
		HttpHeaders headers = new HttpHeaders();
		if (outcome.getId() != null) {
			headers.setLocation(URI.create(outcome.getId().getValue()));
		}
		
		return ResponseEntity.status(HttpStatus.CREATED).headers(headers).contentType(MediaType.APPLICATION_JSON)
		        .body(encodeResource(savedCarePlan));
	}
	
	@PutMapping(path = "/{carePlanId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> update(@PathVariable("carePlanId") String carePlanId, @RequestBody String carePlanPayload) {
		CarePlan carePlan = parseCarePlan(carePlanPayload);
		carePlan.setId(carePlanId);
		
		MethodOutcome outcome = carePlanFhirResourceProvider.update(new IdType("CarePlan", carePlanId), carePlan);
		CarePlan updatedCarePlan = (CarePlan) outcome.getResource();
		
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(encodeResource(updatedCarePlan));
	}
	
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
		return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(errorJson(ex.getMessage()));
	}
	
	private CarePlan parseCarePlan(String payload) {
		try {
			IParser parser = FHIR_CONTEXT.newJsonParser();
			return parser.parseResource(CarePlan.class, payload);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Invalid CarePlan payload", ex);
		}
	}
	
	private String encodeResource(org.hl7.fhir.instance.model.api.IBaseResource resource) {
		IParser parser = FHIR_CONTEXT.newJsonParser();
		return parser.encodeResourceToString(resource);
	}
	
	private String errorJson(String message) {
		ObjectNode node = OBJECT_MAPPER.createObjectNode();
		node.put("error", message);
		try {
			return OBJECT_MAPPER.writeValueAsString(node);
		}
		catch (JsonProcessingException ex) {
			return "{\"error\":\"" + message + "\"}";
		}
	}
}
