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

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.CarePlan.CarePlanActivityComponent;
import org.hl7.fhir.r4.model.CarePlan.CarePlanActivityDetailComponent;
import org.hl7.fhir.r4.model.CarePlan.CarePlanActivityKind;
import org.hl7.fhir.r4.model.Reference;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.api.translators.PatientReferenceTranslator;
import org.openmrs.module.fhir2.api.translators.PractitionerReferenceTranslator;
import org.openmrs.module.tasks.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Mapper to convert between Task entity and FHIR CarePlan resource. Each Task corresponds to a
 * CarePlan with one activity, with task details stored in activity.detail.
 */
@Component("tasks.CarePlanMapper")
public class CarePlanMapper {
	
	private static final Logger log = LoggerFactory.getLogger(CarePlanMapper.class);
	
	private static final String PRACTITIONER_ROLE_TYPE = "PractitionerRole";
	
	private static final Map<CarePlanActivityKind, String> KIND_TO_RESOURCE_TYPE = new EnumMap<>(CarePlanActivityKind.class);
	
	static {
		KIND_TO_RESOURCE_TYPE.put(CarePlanActivityKind.APPOINTMENT, "Appointment");
		KIND_TO_RESOURCE_TYPE.put(CarePlanActivityKind.COMMUNICATIONREQUEST, "CommunicationRequest");
		KIND_TO_RESOURCE_TYPE.put(CarePlanActivityKind.DEVICEREQUEST, "DeviceRequest");
		KIND_TO_RESOURCE_TYPE.put(CarePlanActivityKind.MEDICATIONREQUEST, "MedicationRequest");
		KIND_TO_RESOURCE_TYPE.put(CarePlanActivityKind.NUTRITIONORDER, "NutritionOrder");
		KIND_TO_RESOURCE_TYPE.put(CarePlanActivityKind.SERVICEREQUEST, "ServiceRequest");
		KIND_TO_RESOURCE_TYPE.put(CarePlanActivityKind.TASK, "Task");
		KIND_TO_RESOURCE_TYPE.put(CarePlanActivityKind.VISIONPRESCRIPTION, "VisionPrescription");
	}
	
	private final PatientReferenceTranslator patientReferenceTranslator;
	
	private final PractitionerReferenceTranslator<Provider> practitionerReferenceTranslator;
	
	@Autowired
	public CarePlanMapper(PatientReferenceTranslator patientReferenceTranslator,
	    PractitionerReferenceTranslator<Provider> practitionerReferenceTranslator) {
		this.patientReferenceTranslator = patientReferenceTranslator;
		this.practitionerReferenceTranslator = practitionerReferenceTranslator;
	}
	
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
		
		if (task.getPatient() != null) {
			carePlan.setSubject(patientReferenceTranslator.toFhirResource(task.getPatient()));
		}
		
		CarePlanActivityComponent activity = new CarePlanActivityComponent();
		Reference reference = buildActivityReference(task.getKind());
		if (reference != null) {
			activity.setReference(reference);
		}
		
		CarePlanActivityDetailComponent detail = new CarePlanActivityDetailComponent();
		
		if (task.getStatus() != null) {
			detail.setStatus(task.getStatus());
		}
		
		if (task.getDescription() != null) {
			detail.setDescription(task.getDescription());
		}
		
		if (task.getAssignee() != null) {
			Reference performerRef = practitionerReferenceTranslator.toFhirResource(task.getAssignee());
			if (performerRef != null) {
			detail.addPerformer(performerRef);
		}
		}
		
		if (StringUtils.isNotBlank(task.getAssigneeRoleUuid())) {
			Reference rolePerformer = buildPractitionerRoleReference(task.getAssigneeRoleUuid());
			if (rolePerformer != null) {
				detail.addPerformer(rolePerformer);
			}
		}
		
		if (!detail.isEmpty()) {
		activity.setDetail(detail);
		}
		
		carePlan.addActivity(activity);
		
		return carePlan;
	}
	
	/**
	 * Converts a FHIR CarePlan resource to a Task entity.
	 * 
	 * @param carePlan the FHIR CarePlan resource
	 * @param patient the Patient entity (must be resolved separately)
	 * @param assignee the Provider entity for assignee (must be resolved separately, can be null)
	 * @param assigneeRoleUuid UUID of the ProviderRole assignee (optional)
	 * @return the Task entity
	 */
	public Task toTask(CarePlan carePlan, Patient patient, Provider assignee, String assigneeRoleUuid) {
		Task task = new Task();
		
		if (carePlan.hasId()) {
			task.setUuid(carePlan.getId());
		}
		
		task.setPatient(patient);
		task.setAssignee(assignee);
		task.setAssigneeRoleUuid(StringUtils.isNotBlank(assigneeRoleUuid) ? assigneeRoleUuid : null);
		
		if (carePlan.hasActivity() && !carePlan.getActivity().isEmpty()) {
			CarePlanActivityComponent activity = carePlan.getActivityFirstRep();
			
			Optional.ofNullable(resolveKindFromActivity(activity)).ifPresent(task::setKind);
			
			if (activity.hasDetail()) {
				CarePlanActivityDetailComponent detail = activity.getDetail();
				
				if (detail.hasStatus()) {
					task.setStatus(detail.getStatus());
				}
				
				if (detail.hasDescription()) {
					task.setDescription(detail.getDescription());
				}
				
				if (detail.hasPerformer()) {
					detail.getPerformer().forEach(performer -> {
						String resourceType = getReferenceType(performer);
						if ("Practitioner".equalsIgnoreCase(resourceType)) {
							Provider provider = practitionerReferenceTranslator.toOpenmrsType(performer);
							if (provider != null) {
								task.setAssignee(provider);
							}
						} else if (PRACTITIONER_ROLE_TYPE.equalsIgnoreCase(resourceType)) {
							String roleUuid = extractRoleUuid(performer);
							if (StringUtils.isNotBlank(roleUuid)) {
								task.setAssigneeRoleUuid(roleUuid);
							}
						}
					});
					
					if (task.getAssignee() == null && assignee != null) {
				task.setAssignee(assignee);
					}
				}
			}
		}
		
		return task;
	}
	
	private Reference buildActivityReference(CarePlanActivityKind kind) {
		if (kind == null || kind == CarePlanActivityKind.NULL) {
			return null;
		}
		
		String resourceType = KIND_TO_RESOURCE_TYPE.get(kind);
		if (resourceType == null) {
			resourceType = toPascalCase(kind.getDisplay());
		}
		if (StringUtils.isBlank(resourceType)) {
			return null;
		}
		
		Reference reference = new Reference();
		reference.setType(resourceType);
		return reference;
	}
	
	private Reference buildPractitionerRoleReference(String roleUuid) {
		if (StringUtils.isBlank(roleUuid)) {
			return null;
		}
		Reference reference = new Reference();
		reference.setType(PRACTITIONER_ROLE_TYPE);
		reference.setReference(PRACTITIONER_ROLE_TYPE + "/" + roleUuid);
		resolveProviderRoleDisplay(roleUuid).ifPresent(reference::setDisplay);
		return reference;
	}
	
	private CarePlanActivityKind resolveKindFromActivity(CarePlanActivityComponent activity) {
		if (activity.hasReference() && activity.getReference().hasType()) {
			String type = activity.getReference().getType();
			if (StringUtils.isNotBlank(type)) {
				return KIND_TO_RESOURCE_TYPE.entrySet().stream()
				        .filter(entry -> entry.getValue().equalsIgnoreCase(type))
				        .map(Map.Entry::getKey)
				        .findFirst()
				        .orElseGet(() -> fromTypeString(type));
			}
		}
		
		if (activity.hasDetail() && activity.getDetail().hasKind()) {
			return activity.getDetail().getKind();
		}
		
		return null;
	}
	
	private CarePlanActivityKind fromTypeString(String type) {
		String normalized = type.replace("-", "").replace("_", "").toUpperCase(Locale.ROOT);
		for (CarePlanActivityKind kind : CarePlanActivityKind.values()) {
			if (kind == CarePlanActivityKind.NULL) {
				continue;
			}
			if (kind.name().equalsIgnoreCase(normalized)) {
				return kind;
			}
		}
		return null;
	}
	
	private String toPascalCase(String value) {
		if (StringUtils.isBlank(value)) {
			return null;
		}
		String[] tokens = value.split("\\s+");
		StringBuilder builder = new StringBuilder();
		for (String token : tokens) {
			if (token.isEmpty()) {
				continue;
			}
			builder.append(StringUtils.capitalize(token.toLowerCase(Locale.ROOT)));
		}
		return builder.toString();
	}
	
	private String getReferenceType(Reference reference) {
		if (reference == null) {
			return null;
		}
		if (reference.hasType()) {
			return reference.getType();
		}
		if (reference.getReferenceElement() != null) {
			return reference.getReferenceElement().getResourceType();
		}
		return null;
	}
	
	private String extractRoleUuid(Reference reference) {
		if (reference == null) {
			return null;
		}
		if (reference.getReferenceElement() != null && StringUtils.isNotBlank(reference.getReferenceElement().getIdPart())) {
			return reference.getReferenceElement().getIdPart();
		}
		if (reference.hasIdentifier() && reference.getIdentifier().hasValue()) {
			return reference.getIdentifier().getValue();
		}
		return null;
	}
	
	private Optional<String> resolveProviderRoleDisplay(String providerRoleUuid) {
		if (StringUtils.isBlank(providerRoleUuid)) {
			return Optional.empty();
		}
		try {
			Class<?> serviceClass = Context.loadClass("org.openmrs.module.providermanagement.api.ProviderManagementService");
			Object service = Context.getService(serviceClass);
			Method method = serviceClass.getMethod("getProviderRoleByUuid", String.class);
			Object providerRole = method.invoke(service, providerRoleUuid);
			if (providerRole != null) {
				Method getName = providerRole.getClass().getMethod("getName");
				return Optional.ofNullable((String) getName.invoke(providerRole));
			}
		}
		catch (ClassNotFoundException ex) {
			log.debug("Provider Management module not present; skipping provider role display resolution");
		}
		catch (Exception ex) {
			log.warn("Unable to resolve provider role display for uuid {}", providerRoleUuid, ex);
		}
		return Optional.empty();
	}
}
