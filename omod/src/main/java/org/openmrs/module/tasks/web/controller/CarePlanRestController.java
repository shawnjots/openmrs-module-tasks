/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.tasks.web.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Reference;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.api.PatientService;
import org.openmrs.api.UserService;
import org.openmrs.module.tasks.Task;
import org.openmrs.module.tasks.api.TasksService;
import org.openmrs.module.tasks.api.fhir.CarePlanMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * REST controller for FHIR v4 CarePlan endpoints. Each task corresponds to a CarePlan with one
 * activity. Note: This controller is explicitly defined as a bean in
 * webModuleApplicationContext.xml. The @Controller annotation is not needed (and would cause
 * ambiguous mapping conflicts).
 */
@RequestMapping("/fhir/CarePlan")
public class CarePlanRestController {
	
	private static final FhirContext fhirContext = FhirContext.forR4();
	
	@Autowired
	private TasksService tasksService;
	
	private CarePlanMapper carePlanMapper = new CarePlanMapper();
	
	@Autowired
	private PatientService patientService;
	
	@Autowired
	private UserService userService;
	
	/**
	 * Creates a CarePlan from the provided FHIR CarePlan JSON.
	 * 
	 * @param carePlanJson the FHIR CarePlan JSON string
	 * @return the created CarePlan as JSON
	 */
	@RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<String> createCarePlan(@RequestBody String carePlanJson) {
		try {
			IParser parser = fhirContext.newJsonParser();
			CarePlan carePlan = parser.parseResource(CarePlan.class, carePlanJson);
			
			// Resolve patient reference
			Patient patient = null;
			if (carePlan.hasSubject() && carePlan.getSubject().hasReference()) {
				String patientRef = carePlan.getSubject().getReference();
				if (patientRef.startsWith("Patient/")) {
					String patientId = patientRef.substring("Patient/".length());
					patient = patientService.getPatient(Integer.parseInt(patientId));
					if (patient == null) {
						return createErrorResponse("Patient not found: " + patientId, HttpStatus.NOT_FOUND);
					}
				}
			}
			
			if (patient == null) {
				return createErrorResponse("Patient reference is required", HttpStatus.BAD_REQUEST);
			}
			
			// Resolve assignee (performer) reference if present
			User assignee = null;
			if (carePlan.hasActivity() && !carePlan.getActivity().isEmpty()) {
				CarePlan.CarePlanActivityComponent activity = carePlan.getActivity().get(0);
				if (activity.hasDetail() && activity.getDetail().hasPerformer()
				        && !activity.getDetail().getPerformer().isEmpty()) {
					Reference performerRef = activity.getDetail().getPerformer().get(0);
					if (performerRef.hasReference()) {
						String userRef = performerRef.getReference();
						if (userRef.startsWith("Practitioner/")) {
							String userId = userRef.substring("Practitioner/".length());
							assignee = userService.getUser(Integer.parseInt(userId));
							// Note: assignee can be null if not found
						}
					}
				}
			}
			
			// Convert CarePlan to Task
			Task task = carePlanMapper.toTask(carePlan, patient, assignee);
			
			// Save task
			Task savedTask = tasksService.saveTask(task);
			
			// Convert back to CarePlan
			CarePlan savedCarePlan = carePlanMapper.toCarePlan(savedTask);
			
			// Serialize to JSON
			String responseJson = parser.encodeResourceToString(savedCarePlan);
			return new ResponseEntity<String>(responseJson, HttpStatus.CREATED);
			
		}
		catch (Exception e) {
			return createErrorResponse("Error creating CarePlan: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * Gets all CarePlans for a patient.
	 * 
	 * @param subject the patient reference in format "Patient/{patientId}"
	 * @return a FHIR Bundle containing CarePlans
	 */
	@RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<String> getCarePlansByPatient(@RequestParam(value = "subject", required = false) String subject) {
		try {
			if (subject == null || !subject.startsWith("Patient/")) {
				return createErrorResponse("subject parameter is required in format 'Patient/{patientId}'",
				    HttpStatus.BAD_REQUEST);
			}
			
			String patientIdStr = subject.substring("Patient/".length());
			Integer patientId = Integer.parseInt(patientIdStr);
			
			// Verify patient exists
			Patient patient = patientService.getPatient(patientId);
			if (patient == null) {
				return createErrorResponse("Patient not found: " + patientId, HttpStatus.NOT_FOUND);
			}
			
			// Get tasks for patient
			List<Task> tasks = tasksService.getTasksByPatientId(patientId);
			
			// Convert to CarePlans and create Bundle
			Bundle bundle = new Bundle();
			bundle.setType(Bundle.BundleType.SEARCHSET);
			bundle.setTotal(tasks.size());
			
			CarePlanMapper mapper = new CarePlanMapper();
			for (Task task : tasks) {
				CarePlan carePlan = mapper.toCarePlan(task);
				Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
				entry.setResource(carePlan);
				bundle.addEntry(entry);
			}
			
			// Serialize to JSON
			IParser parser = fhirContext.newJsonParser();
			String responseJson = parser.encodeResourceToString(bundle);
			return new ResponseEntity<String>(responseJson, HttpStatus.OK);
			
		}
		catch (NumberFormatException e) {
			return createErrorResponse("Invalid patient ID format", HttpStatus.BAD_REQUEST);
		}
		catch (Exception e) {
			return createErrorResponse("Error retrieving CarePlans: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * Creates an error response with FHIR OperationOutcome.
	 */
	private ResponseEntity<String> createErrorResponse(String message, HttpStatus status) {
		OperationOutcome outcome = new OperationOutcome();
		OperationOutcome.OperationOutcomeIssueComponent issue = outcome.addIssue();
		issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
		issue.setCode(OperationOutcome.IssueType.PROCESSING);
		issue.setDiagnostics(message);
		
		IParser parser = fhirContext.newJsonParser();
		String json = parser.encodeResourceToString(outcome);
		return new ResponseEntity<String>(json, status);
	}
}
