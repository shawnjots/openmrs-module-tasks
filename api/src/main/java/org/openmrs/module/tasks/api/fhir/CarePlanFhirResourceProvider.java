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
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
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
	
	private ProviderService providerService;
	
	private CarePlanMapper carePlanMapper;
	
	public CarePlanFhirResourceProvider() {
	}
	
	public CarePlanFhirResourceProvider(TasksService tasksService, PatientService patientService,
	    ProviderService providerService) {
		this.tasksService = tasksService;
		this.patientService = patientService;
		this.providerService = providerService;
	}
	
	public void setCarePlanMapper(CarePlanMapper carePlanMapper) {
		this.carePlanMapper = carePlanMapper;
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
		CarePlanContext context = resolveCarePlanContext(carePlan);
		
		if (context.getPatient() == null) {
			throw new IllegalArgumentException("Patient reference is required");
		}
		
		// Convert CarePlan to Task
		Task task = carePlanMapper.toTask(carePlan, context.getPatient(), context.getAssignee(),
		    context.getAssigneeRoleUuid());
		
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
	 * Updates an existing CarePlan resource.
	 * 
	 * @param id the CarePlan ID
	 * @param carePlan the updated CarePlan resource
	 * @return the updated CarePlan
	 */
	@Update
	public MethodOutcome update(@IdParam IdType id, @ResourceParam CarePlan carePlan) {
		if (id == null || StringUtils.isBlank(id.getIdPart())) {
			throw new IllegalArgumentException("CarePlan ID is required");
		}
		
		Task existingTask = tasksService.getTaskByUuid(id.getIdPart());
		if (existingTask == null) {
			throw new IllegalArgumentException("Task not found for CarePlan ID " + id.getIdPart());
		}
		
		CarePlanContext context = resolveCarePlanContext(carePlan);
		
		Patient patient = context.getPatient() != null ? context.getPatient() : existingTask.getPatient();
		if (patient == null) {
			throw new IllegalArgumentException("Patient reference is required");
		}
		
		// Ensure CarePlan ID matches the resource being updated
		carePlan.setId(id.getIdPart());
		
		carePlanMapper.applyCarePlanToTask(existingTask, carePlan, patient, context.getAssignee(),
		    context.getAssigneeRoleUuid());
		
		Task savedTask = tasksService.saveTask(existingTask);
		CarePlan savedCarePlan = carePlanMapper.toCarePlan(savedTask);
		
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
	
	private CarePlanContext resolveCarePlanContext(CarePlan carePlan) {
		Patient patient = null;
		Provider assignee = null;
		String assigneeRoleUuid = null;
		
		if (carePlan != null && carePlan.hasSubject() && carePlan.getSubject().hasReference()) {
			IIdType subjectRef = carePlan.getSubject().getReferenceElement();
			if (subjectRef != null && StringUtils.equalsIgnoreCase(subjectRef.getResourceType(), "Patient")) {
				String patientUuid = subjectRef.getIdPart();
				if (StringUtils.isNotBlank(patientUuid)) {
					patient = patientService.getPatientByUuid(patientUuid);
				}
			}
		}
		
		if (carePlan != null && carePlan.hasActivity() && !carePlan.getActivity().isEmpty()) {
			CarePlan.CarePlanActivityComponent activity = carePlan.getActivityFirstRep();
			if (activity.hasDetail() && activity.getDetail().hasPerformer()) {
				for (Reference performerRef : activity.getDetail().getPerformer()) {
					IIdType performerId = performerRef.getReferenceElement();
					if (performerId == null) {
						continue;
					}
					
					if (assignee == null && StringUtils.equalsIgnoreCase(performerId.getResourceType(), "Practitioner")) {
						String practitionerUuid = performerId.getIdPart();
						if (StringUtils.isNotBlank(practitionerUuid)) {
							assignee = providerService.getProviderByUuid(practitionerUuid);
						}
					} else if (StringUtils.isBlank(assigneeRoleUuid)
					        && StringUtils.equalsIgnoreCase(performerId.getResourceType(), "PractitionerRole")) {
						assigneeRoleUuid = performerId.getIdPart();
					}
					
					if (assignee != null && StringUtils.isNotBlank(assigneeRoleUuid)) {
						break;
					}
				}
			}
		}
		
		return new CarePlanContext(patient, assignee, assigneeRoleUuid);
	}
	
	private static class CarePlanContext {
		
		private final Patient patient;
		
		private final Provider assignee;
		
		private final String assigneeRoleUuid;
		
		private CarePlanContext(Patient patient, Provider assignee, String assigneeRoleUuid) {
			this.patient = patient;
			this.assignee = assignee;
			this.assigneeRoleUuid = assigneeRoleUuid;
		}
		
		public Patient getPatient() {
			return patient;
		}
		
		public Provider getAssignee() {
			return assignee;
		}
		
		public String getAssigneeRoleUuid() {
			return assigneeRoleUuid;
		}
	}
	
	public void setTasksService(TasksService tasksService) {
		this.tasksService = tasksService;
	}
	
	public void setPatientService(PatientService patientService) {
		this.patientService = patientService;
	}
	
	public void setProviderService(ProviderService providerService) {
		this.providerService = providerService;
	}
}
