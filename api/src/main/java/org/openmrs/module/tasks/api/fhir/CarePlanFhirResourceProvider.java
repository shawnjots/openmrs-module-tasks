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

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.Delete;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
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
		Patient patient = resolvePatientOrThrow(carePlan);
		CarePlanContext context = resolveCarePlanContext(carePlan);
		
		Task task = carePlanMapper.toTask(carePlan, patient, context.getAssignee(), context.getAssigneeRoleUuid());
		
		if (task.getCreator() == null) {
			User authenticatedUser = Context.getAuthenticatedUser();
			if (authenticatedUser != null) {
				task.setCreator(authenticatedUser);
			}
		}
		
		Task savedTask = tasksService.saveTask(task);
		CarePlan savedCarePlan = carePlanMapper.toCarePlan(savedTask);
		
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
			throw new InvalidRequestException("CarePlan ID is required");
		}
		
		Task existingTask = tasksService.getTaskByUuid(id.getIdPart());
		if (existingTask == null) {
			throw new ResourceNotFoundException(id);
		}
		
		Patient patient = carePlanHasSubjectReference(carePlan) ? resolvePatientOrThrow(carePlan)
		        : existingTask.getPatient();
		if (patient == null) {
			throw new InvalidRequestException("Patient reference is required");
		}
		
		CarePlanContext context = resolveCarePlanContext(carePlan);
		
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
			throw new ResourceNotFoundException(id);
		}
		return carePlanMapper.toCarePlan(task);
	}
	
	/**
	 * Deletes (voids) a CarePlan resource by ID. Maps to the task's underlying void operation — the
	 * stored {@code task.status} is unchanged; voiding is tracked via {@code voided}/{@code voidedBy}
	 * /{@code dateVoided}/{@code voidReason} and surfaced on read as
	 * {@code CarePlan.status=ENTEREDINERROR} / {@code activity.detail.status=ENTEREDINERROR}.
	 *
	 * @param id the CarePlan ID
	 * @return a MethodOutcome for the deletion
	 */
	@Delete
	public MethodOutcome delete(@IdParam IdType id) {
		if (id == null || StringUtils.isBlank(id.getIdPart())) {
			throw new InvalidRequestException("CarePlan ID is required");
		}
		
		Task task = tasksService.getTaskByUuid(id.getIdPart());
		if (task == null) {
			throw new ResourceNotFoundException(id);
		}
		
		tasksService.voidTask(task, "Voided via FHIR DELETE");
		
		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType("CarePlan", id.getIdPart()));
		return outcome;
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
				List<Task> tasks = tasksService.getActiveTasksByPatientId(patient.getPatientId());
				for (Task task : tasks) {
					carePlans.add(carePlanMapper.toCarePlan(task));
				}
			}
		}
		
		return carePlans;
	}
	
	private boolean carePlanHasSubjectReference(CarePlan carePlan) {
		return carePlan != null && carePlan.hasSubject() && carePlan.getSubject().hasReference();
	}
	
	private Patient resolvePatientOrThrow(CarePlan carePlan) {
		if (!carePlanHasSubjectReference(carePlan)) {
			throw new InvalidRequestException("Subject reference is required");
		}
		IIdType subjectRef = carePlan.getSubject().getReferenceElement();
		if (subjectRef == null || !StringUtils.equalsIgnoreCase(subjectRef.getResourceType(), "Patient")) {
			throw new InvalidRequestException("Subject reference must point to a Patient");
		}
		String patientUuid = subjectRef.getIdPart();
		if (StringUtils.isBlank(patientUuid)) {
			throw new InvalidRequestException("Subject reference is missing a Patient id");
		}
		Patient patient = patientService.getPatientByUuid(patientUuid);
		if (patient == null) {
			throw new ResourceNotFoundException(new IdType("Patient", patientUuid));
		}
		return patient;
	}
	
	private CarePlanContext resolveCarePlanContext(CarePlan carePlan) {
		Provider assignee = null;
		String assigneeRoleUuid = null;
		
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
		
		return new CarePlanContext(assignee, assigneeRoleUuid);
	}
	
	private static class CarePlanContext {
		
		private final Provider assignee;
		
		private final String assigneeRoleUuid;
		
		private CarePlanContext(Provider assignee, String assigneeRoleUuid) {
			this.assignee = assignee;
			this.assigneeRoleUuid = assigneeRoleUuid;
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
