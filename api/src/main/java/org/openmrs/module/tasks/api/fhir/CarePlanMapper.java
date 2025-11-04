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
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.module.tasks.Task;

/**
 * Mapper to convert between Task entity and FHIR CarePlan resource. Each Task corresponds to a
 * CarePlan with one activity, with task details stored in activity.detail.
 */
public class CarePlanMapper {
	
	/**
	 * Converts a Task entity to a FHIR CarePlan resource.
	 * 
	 * @param task the Task entity
	 * @return the FHIR CarePlan resource
	 */
	public CarePlan toCarePlan(Task task) {
		CarePlan carePlan = new CarePlan();
		
		carePlan.setId(task.getUuid());
		carePlan.setStatus(CarePlan.CarePlanStatus.ACTIVE);
		carePlan.setIntent(CarePlan.CarePlanIntent.PLAN);
		
		// Set subject (patient)
		if (task.getPatient() != null) {
			Reference subjectRef = new Reference();
			subjectRef.setReference("Patient/" + task.getPatient().getId());
			carePlan.setSubject(subjectRef);
		}
		
		// Create activity with detail
		CarePlan.CarePlanActivityComponent activity = new CarePlan.CarePlanActivityComponent();
		CarePlan.CarePlanActivityDetailComponent detail = new CarePlan.CarePlanActivityDetailComponent();
		
		// Set kind (required)
		// Note: HAPI FHIR uses enum for kind, but we store custom codes as strings
		// Try to map to enum if possible, otherwise use a default
		try {
			if (task.getKind() != null) {
				CarePlan.CarePlanActivityKind kindEnum = CarePlan.CarePlanActivityKind.fromCode(task.getKind());
				detail.setKind(kindEnum);
			} else {
				detail.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
			}
		}
		catch (Exception e) {
			// If the kind doesn't match an enum, use a default
			detail.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
		}
		
		if (task.getStatus() != null) {
			detail.setStatus(CarePlan.CarePlanActivityStatus.fromCode(task.getStatus()));
		}
		
		if (task.getDescription() != null) {
			detail.setDescription(task.getDescription());
		}
		
		if (task.getAssignee() != null) {
			Reference performerRef = new Reference();
			performerRef.setReference("Practitioner/" + task.getAssignee().getId());
			detail.addPerformer(performerRef);
		}
		
		activity.setDetail(detail);
		carePlan.addActivity(activity);
		
		return carePlan;
	}
	
	/**
	 * Converts a FHIR CarePlan resource to a Task entity.
	 * 
	 * @param carePlan the FHIR CarePlan resource
	 * @param patient the Patient entity (must be resolved separately)
	 * @param assignee the User entity for assignee (must be resolved separately, can be null)
	 * @return the Task entity
	 */
	public Task toTask(CarePlan carePlan, Patient patient, User assignee) {
		Task task = new Task();
		
		if (carePlan.hasId()) {
			task.setUuid(carePlan.getId());
		}
		
		task.setPatient(patient);
		
		if (carePlan.hasActivity() && !carePlan.getActivity().isEmpty()) {
			CarePlan.CarePlanActivityComponent activity = carePlan.getActivity().get(0);
			if (activity.hasDetail()) {
				CarePlan.CarePlanActivityDetailComponent detail = activity.getDetail();
				
				if (detail.hasKind()) {
					CarePlan.CarePlanActivityKind kind = detail.getKind();
					if (kind != null) {
						task.setKind(kind.toCode());
					}
				}
				
				if (detail.hasStatus()) {
					task.setStatus(detail.getStatus().toCode());
				}
				
				if (detail.hasDescription()) {
					task.setDescription(detail.getDescription());
				}
				
				task.setAssignee(assignee);
			}
		}
		
		return task;
	}
}
