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

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.api.PatientService;
import org.openmrs.api.UserService;
import org.openmrs.module.fhir2.api.annotations.R4Provider;
import org.openmrs.module.tasks.Task;
import org.openmrs.module.tasks.api.TasksService;

import java.util.ArrayList;
import java.util.List;

/**
 * FHIR Resource Provider for CarePlan resources. Integrates with fhir2 module to expose CarePlan
 * resources at /ws/fhir2/R4/CarePlan. Each CarePlan corresponds to a Task entity.
 */
@R4Provider
public class CarePlanFhirResourceProvider implements IResourceProvider {
	
	private TasksService tasksService;
	
	private PatientService patientService;
	
	private UserService userService;
	
	private CarePlanMapper carePlanMapper = new CarePlanMapper();
	
	public CarePlanFhirResourceProvider() {
	}
	
	public CarePlanFhirResourceProvider(TasksService tasksService, PatientService patientService, UserService userService) {
		this.tasksService = tasksService;
		this.patientService = patientService;
		this.userService = userService;
	}
	
	@Override
	public Class<CarePlan> getResourceType() {
		return CarePlan.class;
	}
	
	/**
	 * Creates a new CarePlan resource.
	 * 
	 * @param carePlan the CarePlan resource to create
	 * @return the created CarePlan with its ID
	 */
	@Create
	public MethodOutcome create(@ResourceParam CarePlan carePlan) {
		// Resolve patient reference
		Patient patient = null;
		if (carePlan.hasSubject() && carePlan.getSubject().hasReference()) {
			String patientRef = carePlan.getSubject().getReference();
			if (patientRef.startsWith("Patient/")) {
				String patientId = patientRef.substring("Patient/".length());
				patient = patientService.getPatientByUuid(patientId);
			}
		}
		
		if (patient == null) {
			throw new IllegalArgumentException("Patient reference is required");
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
					if (userRef.startsWith("Provider/")) {
						String userId = userRef.substring("Provider/".length());
						assignee = userService.getUser(Integer.parseInt(userId));
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
		
		// Create MethodOutcome
		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType("CarePlan", savedCarePlan.getId()));
		outcome.setResource(savedCarePlan);
		return outcome;
	}
	
	/**
	 * Reads a CarePlan resource by ID.
	 * 
	 * @param id the CarePlan ID
	 * @return the CarePlan resource
	 */
	@Read
	public CarePlan read(@IdParam IdType id) {
		Task task = tasksService.getTaskByUuid(id.getIdPart());
		if (task == null) {
			return null;
		}
		return carePlanMapper.toCarePlan(task);
	}
	
	/**
	 * Searches for CarePlans by patient.
	 * 
	 * @param subject the patient reference in format "Patient/{patientId}" or just "{patientId}"
	 * @return list of CarePlan resources
	 */
	@Search
	public List<CarePlan> search(@ca.uhn.fhir.rest.annotation.OptionalParam(name = "subject") String subject) {
		List<CarePlan> carePlans = new ArrayList<>();
		
		if (subject != null && !subject.isEmpty()) {
			String patientIdStr = subject;
			// Handle both "Patient/{id}" and just "{id}" formats
			if (patientIdStr.startsWith("Patient/")) {
				patientIdStr = patientIdStr.substring("Patient/".length());
			}
			
			Patient patient = patientService.getPatientByUuid(patientIdStr);
			
			if (patient != null) {
				// Get tasks for patient using patient ID
				List<Task> tasks = tasksService.getTasksByPatientId(patient.getPatientId());
				
				// Convert to CarePlans
				for (Task task : tasks) {
					carePlans.add(carePlanMapper.toCarePlan(task));
				}
			}
		}
		
		return carePlans;
	}
	
	public void setTasksService(TasksService tasksService) {
		this.tasksService = tasksService;
	}
	
	public void setPatientService(PatientService patientService) {
		this.patientService = patientService;
	}
	
	public void setUserService(UserService userService) {
		this.userService = userService;
	}
}
