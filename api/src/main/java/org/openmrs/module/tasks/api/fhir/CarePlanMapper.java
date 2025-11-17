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
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Timing;
import org.hl7.fhir.instance.model.api.IBaseDatatype;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.ProviderRole;
import org.openmrs.Visit;
import org.openmrs.api.ProviderService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.api.translators.PatientReferenceTranslator;
import org.openmrs.module.fhir2.api.translators.PractitionerReferenceTranslator;
import org.openmrs.module.tasks.DueDateType;
import org.openmrs.module.tasks.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
	
	private static final String ENCOUNTER_ASSOCIATED_ENCOUNTER_EXTENSION_URL = "http://hl7.org/fhir/StructureDefinition/encounter-associatedEncounter";
	
	private static final String SCHEDULED_STRING_THIS_VISIT = "this visit";
	
	private static final String SCHEDULED_STRING_NEXT_VISIT = "next visit";
	
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
		
		if (StringUtils.isNotBlank(task.getRationale())) {
			carePlan.setDescription(task.getRationale());
		}
		
		if (task.getAssignee() != null) {
			Reference performerRef = practitionerReferenceTranslator.toFhirResource(task.getAssignee());
			if (performerRef != null) {
				detail.addPerformer(performerRef);
			}
		}
		
		if (task.getAssigneeProviderRoleId() != null) {
			String roleUuid = resolveProviderRoleUuid(task.getAssigneeProviderRoleId());
			if (StringUtils.isNotBlank(roleUuid)) {
				Reference rolePerformer = buildPractitionerRoleReference(roleUuid);
				if (rolePerformer != null) {
					detail.addPerformer(rolePerformer);
				}
			}
		}
		
		// Handle due date based on type
		if (task.getDueDateType() != null) {
			if (task.getDueDateType() == DueDateType.THIS_VISIT || task.getDueDateType() == DueDateType.NEXT_VISIT) {
				// Use scheduledString for visit-based due dates
				String scheduledString = task.getDueDateType() == DueDateType.THIS_VISIT 
				        ? SCHEDULED_STRING_THIS_VISIT 
				        : SCHEDULED_STRING_NEXT_VISIT;
				detail.setScheduled(new org.hl7.fhir.r4.model.StringType(scheduledString));
				
				// Add encounter extension if visit reference exists
				if (task.getDueDateReferenceVisit() != null) {
					Extension encounterExtension = new Extension();
					encounterExtension.setUrl(ENCOUNTER_ASSOCIATED_ENCOUNTER_EXTENSION_URL);
					Reference encounterRef = new Reference();
					encounterRef.setReference("Encounter/" + task.getDueDateReferenceVisit().getUuid());
					encounterExtension.setValue(encounterRef);
					detail.addExtension(encounterExtension);
				}
			} else if (task.getDueDateType() == DueDateType.DATE && task.getDueDateDate() != null) {
				// Use scheduledPeriod for actual dates
				Period period = new Period();
				period.setEnd(task.getDueDateDate());
				detail.setScheduled(period);
			}
		} else if (task.getDueDateDate() != null) {
			// Fallback for backward compatibility: if no type is set but date exists, treat as DATE
			Period period = new Period();
			period.setEnd(task.getDueDateDate());
			detail.setScheduled(period);
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
		return applyCarePlanToTask(new Task(), carePlan, patient, assignee, assigneeRoleUuid);
	}
	
	/**
	 * Applies values from a CarePlan resource to the provided Task.
	 * 
	 * @param task the Task to update; if {@code null}, a new Task is created
	 * @param carePlan the source CarePlan
	 * @param patient the patient associated with the task
	 * @param assignee the resolved provider (optional)
	 * @param assigneeRoleUuid the resolved provider role UUID (optional)
	 * @return the updated Task instance
	 */
	public Task applyCarePlanToTask(Task task, CarePlan carePlan, Patient patient, Provider assignee, String assigneeRoleUuid) {
		if (task == null) {
			task = new Task();
		}
		
		if (carePlan.hasId()) {
			task.setUuid(carePlan.getId());
		}
		
		task.setPatient(patient);
		task.setAssignee(null);
		task.setAssigneeProviderRoleId(null);
		task.setDueDateDate(null);
		task.setDueDateType(null);
		task.setDueDateReferenceVisit(null);
		task.setRationale(null);
		
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
				
				// Handle due date conversion
				if (detail.hasScheduled()) {
					IBaseDatatype scheduledElement = detail.getScheduled();
					
					// Check for scheduledString (visit-based due dates)
					if (scheduledElement instanceof org.hl7.fhir.r4.model.StringType) {
						String scheduledString = ((org.hl7.fhir.r4.model.StringType) scheduledElement).getValue();
						if (SCHEDULED_STRING_THIS_VISIT.equalsIgnoreCase(scheduledString)) {
							task.setDueDateType(DueDateType.THIS_VISIT);
						} else if (SCHEDULED_STRING_NEXT_VISIT.equalsIgnoreCase(scheduledString)) {
							task.setDueDateType(DueDateType.NEXT_VISIT);
						}
						
						// Extract visit reference from extension
						if (detail.hasExtension()) {
							for (Extension ext : detail.getExtension()) {
								if (ENCOUNTER_ASSOCIATED_ENCOUNTER_EXTENSION_URL.equals(ext.getUrl())
								        && ext.hasValue() && ext.getValue() instanceof Reference) {
									Reference encounterRef = (Reference) ext.getValue();
									String encounterUuid = extractEncounterUuid(encounterRef);
									if (StringUtils.isNotBlank(encounterUuid)) {
										Visit visit = resolveVisitByUuid(encounterUuid);
										if (visit != null) {
											task.setDueDateReferenceVisit(visit);
										}
									}
								}
							}
						}
					}
					// Check for scheduledPeriod (actual date)
					else if (scheduledElement instanceof Period) {
						Period period = (Period) scheduledElement;
						if (period.hasEnd()) {
							task.setDueDateType(DueDateType.DATE);
							task.setDueDateDate(period.getEnd());
						}
					}
					// Fallback for other scheduled types
					else if (scheduledElement instanceof DateTimeType) {
						task.setDueDateType(DueDateType.DATE);
						task.setDueDateDate(((DateTimeType) scheduledElement).getValue());
					} else if (scheduledElement instanceof DateType) {
						task.setDueDateType(DueDateType.DATE);
						task.setDueDateDate(((DateType) scheduledElement).getValue());
					} else if (scheduledElement instanceof Timing) {
						Timing timing = (Timing) scheduledElement;
						if (!timing.getEvent().isEmpty()) {
							task.setDueDateType(DueDateType.DATE);
							task.setDueDateDate(timing.getEvent().get(0).getValue());
						} else if (timing.getRepeat() != null && timing.getRepeat().hasBoundsPeriod()
						        && timing.getRepeat().getBoundsPeriod().hasEnd()) {
							task.setDueDateType(DueDateType.DATE);
							task.setDueDateDate(timing.getRepeat().getBoundsPeriod().getEnd());
						}
					}
				}
				
				if (detail.hasPerformer()) {
					for (Reference performer : detail.getPerformer()) {
						String resourceType = getReferenceType(performer);
						if ("Practitioner".equalsIgnoreCase(resourceType)) {
							Provider provider = practitionerReferenceTranslator.toOpenmrsType(performer);
							if (provider != null) {
								task.setAssignee(provider);
							}
						} else if (PRACTITIONER_ROLE_TYPE.equalsIgnoreCase(resourceType)) {
							String roleUuid = extractRoleUuid(performer);
							if (StringUtils.isNotBlank(roleUuid)) {
								Integer providerRoleId = resolveProviderRoleId(roleUuid);
								if (providerRoleId != null) {
									task.setAssigneeProviderRoleId(providerRoleId);
								}
							}
						}
					}
				}
			}
		}
		
		if (task.getAssignee() == null && assignee != null) {
			task.setAssignee(assignee);
		}
		if (task.getAssigneeProviderRoleId() == null && StringUtils.isNotBlank(assigneeRoleUuid)) {
			Integer providerRoleId = resolveProviderRoleId(assigneeRoleUuid);
			if (providerRoleId != null) {
				task.setAssigneeProviderRoleId(providerRoleId);
			}
		}
		if (carePlan.hasDescription()) {
			task.setRationale(StringUtils.defaultIfBlank(carePlan.getDescription(), null));
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
			String resourceType = reference.getReferenceElement().getResourceType();
			if (resourceType != null) {
				return resourceType;
			}
			// Fallback: try to extract from reference string (e.g., "PractitionerRole/uuid")
			String ref = reference.getReference();
			if (ref != null && ref.contains("/")) {
				return ref.substring(0, ref.indexOf("/"));
			}
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
			ProviderService providerService = Context.getProviderService();
			ProviderRole providerRole = providerService.getProviderRoleByUuid(providerRoleUuid);
			if (providerRole != null) {
				return Optional.ofNullable(providerRole.getName());
			}
		}
		catch (Exception ex) {
			log.warn("Unable to resolve provider role display for uuid {}", providerRoleUuid, ex);
		}
		return Optional.empty();
	}
	
	/**
	 * Resolves a ProviderRole ID from a ProviderRole UUID.
	 * 
	 * @param providerRoleUuid the ProviderRole UUID
	 * @return the ProviderRole ID, or null if not found
	 */
	private Integer resolveProviderRoleId(String providerRoleUuid) {
		if (StringUtils.isBlank(providerRoleUuid)) {
			return null;
		}
		try {
			ProviderService providerService = Context.getProviderService();
			ProviderRole providerRole = providerService.getProviderRoleByUuid(providerRoleUuid);
			if (providerRole != null) {
				return providerRole.getProviderRoleId();
			}
		}
		catch (Exception ex) {
			log.warn("Unable to resolve provider role ID for uuid {}", providerRoleUuid, ex);
		}
		return null;
	}
	
	/**
	 * Resolves a ProviderRole UUID from a ProviderRole ID.
	 * 
	 * @param providerRoleId the ProviderRole ID
	 * @return the ProviderRole UUID, or null if not found
	 */
	private String resolveProviderRoleUuid(Integer providerRoleId) {
		if (providerRoleId == null) {
			return null;
		}
		try {
			ProviderService providerService = Context.getProviderService();
			ProviderRole providerRole = providerService.getProviderRole(providerRoleId);
			if (providerRole != null) {
				return providerRole.getUuid();
			}
		}
		catch (Exception ex) {
			log.warn("Unable to resolve provider role UUID for id {}", providerRoleId, ex);
		}
		return null;
	}
	
	/**
	 * Extracts the encounter UUID from a Reference.
	 * 
	 * @param encounterRef the encounter reference
	 * @return the encounter UUID, or null if not found
	 */
	private String extractEncounterUuid(Reference encounterRef) {
		if (encounterRef == null) {
			return null;
		}
		if (encounterRef.getReferenceElement() != null && StringUtils.isNotBlank(encounterRef.getReferenceElement().getIdPart())) {
			return encounterRef.getReferenceElement().getIdPart();
		}
		// Handle "Encounter/uuid" format
		if (encounterRef.hasReference()) {
			String ref = encounterRef.getReference();
			if (ref != null && ref.startsWith("Encounter/")) {
				return ref.substring("Encounter/".length());
			}
		}
		return null;
	}
	
	/**
	 * Resolves a Visit entity by UUID.
	 * 
	 * @param visitUuid the Visit UUID
	 * @return the Visit entity, or null if not found
	 */
	private Visit resolveVisitByUuid(String visitUuid) {
		if (StringUtils.isBlank(visitUuid)) {
			return null;
		}
		try {
			VisitService visitService = Context.getVisitService();
			Visit visit = visitService.getVisitByUuid(visitUuid);
			return visit;
		}
		catch (Exception ex) {
			log.warn("Unable to resolve visit for uuid {}", visitUuid, ex);
		}
		return null;
	}
}
