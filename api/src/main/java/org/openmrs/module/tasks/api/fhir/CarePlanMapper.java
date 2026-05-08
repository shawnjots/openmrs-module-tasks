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

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.CarePlan.CarePlanActivityComponent;
import org.hl7.fhir.r4.model.CarePlan.CarePlanActivityDetailComponent;
import org.hl7.fhir.r4.model.CarePlan.CarePlanActivityKind;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Timing;
import org.hl7.fhir.instance.model.api.IBaseDatatype;
import org.hl7.fhir.instance.model.api.IIdType;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.Provider;
import org.openmrs.ProviderRole;
import org.openmrs.User;
import org.openmrs.Visit;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.tasks.DueDateType;
import org.openmrs.module.tasks.Priority;
import org.openmrs.module.tasks.SystemTask;
import org.openmrs.module.tasks.TaskKind;
import org.openmrs.module.tasks.TaskStatus;
import org.openmrs.module.fhir2.api.translators.PatientReferenceTranslator;
import org.openmrs.module.fhir2.api.translators.PractitionerReferenceTranslator;
import org.openmrs.module.tasks.Task;
import org.openmrs.module.tasks.api.TasksService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
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
	
	private static final String ENCOUNTER_EXTENSION_URL = "http://hl7.org/fhir/StructureDefinition/encounter-associatedEncounter";
	
	private static final String ACTIVITY_DUE_KIND_EXTENSION_URL = "http://openmrs.org/fhir/StructureDefinition/activity-dueKind";
	
	private static final String ACTIVITY_PRIORITY_EXTENSION_URL = "http://openmrs.org/fhir/StructureDefinition/activity-priority";
	
	private static final String PLAN_DEFINITION_RESOURCE_TYPE = "PlanDefinition";
	
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
		if (task == null) {
			throw new IllegalArgumentException("Task must not be null");
		}
		
		CarePlan carePlan = new CarePlan();
		
		carePlan.setId(task.getUuid());
		
		carePlan.setStatus(mapTaskStatusToCarePlanStatus(task));
		
		carePlan.setIntent(CarePlan.CarePlanIntent.PLAN);
		
		if (task.getSystemTask() != null && task.getSystemTask().getUuid() != null) {
			carePlan.addInstantiatesCanonical(PLAN_DEFINITION_RESOURCE_TYPE + "/" + task.getSystemTask().getUuid());
		}
		
		if (task.getPatient() != null) {
			carePlan.setSubject(patientReferenceTranslator.toFhirResource(task.getPatient()));
		}
		
		Reference authorRef = buildAuthorReference(task.getCreator());
		if (authorRef != null) {
			carePlan.setAuthor(authorRef);
		}
		
		if (task.getDateCreated() != null) {
			carePlan.setCreated(task.getDateCreated());
		}
		
		CarePlanActivityComponent activity = new CarePlanActivityComponent();
		Reference reference = buildActivityReference(toFhirKind(task.getKind()));
		if (reference != null) {
			activity.setReference(reference);
		}
		
		CarePlanActivityDetailComponent detail = new CarePlanActivityDetailComponent();
		
		if (Boolean.TRUE.equals(task.getVoided())) {
			detail.setStatus(CarePlan.CarePlanActivityStatus.ENTEREDINERROR);
		} else if (task.getStatus() != null) {
			detail.setStatus(toFhirStatus(task.getStatus()));
		}
		
		if (task.getDescription() != null) {
			detail.setDescription(task.getDescription());
		}
		
		if (StringUtils.isNotBlank(task.getRationale())) {
			detail.addReasonCode(new CodeableConcept().setText(task.getRationale()));
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
		
		// Handle due date
		Period scheduledPeriod = new Period();
		String dueKindValue = null;
		
		if (task.getDueDateType() == DueDateType.DATE && task.getDueDate() != null) {
			// DATE type: start = end = specific date
			scheduledPeriod.setStart(task.getDueDate());
			scheduledPeriod.setEnd(task.getDueDate());
			dueKindValue = "date";
		} else if (task.getDueDateType() == DueDateType.THIS_VISIT || task.getDueDateType() == DueDateType.NEXT_VISIT) {
			dueKindValue = task.getDueDateType() == DueDateType.THIS_VISIT ? "this-visit" : "next-visit";
			
			Visit referenceVisit = task.getDueDateReferenceVisit();
			if (referenceVisit != null) {
				if (referenceVisit.getUuid() != null) {
					Extension encounterExtension = new Extension();
					encounterExtension.setUrl(ENCOUNTER_EXTENSION_URL);
					Reference encounterRef = new Reference();
					encounterRef.setReference("Encounter/" + referenceVisit.getUuid());
					encounterExtension.setValue(encounterRef);
					detail.addExtension(encounterExtension);
				}
				
				Date visitStartDate = referenceVisit.getStartDatetime();
				if (visitStartDate != null) {
					scheduledPeriod.setStart(visitStartDate);
				} else if (task.getDateCreated() != null) {
					scheduledPeriod.setStart(task.getDateCreated());
				}
				
				Date visitEndDate = null;
				
				if (task.getDueDateType() == DueDateType.THIS_VISIT) {
					visitEndDate = referenceVisit.getStopDatetime();
				} else if (task.getDueDateType() == DueDateType.NEXT_VISIT) {
					Visit nextVisit = findNextVisitAfterReference(task.getPatient(), referenceVisit);
					if (nextVisit != null) {
						if (nextVisit.getStartDatetime() != null) {
							scheduledPeriod.setStart(nextVisit.getStartDatetime());
						}
						if (nextVisit.getStopDatetime() != null) {
							visitEndDate = nextVisit.getStopDatetime();
						}
					}
				}
				
				if (visitEndDate != null) {
					scheduledPeriod.setEnd(visitEndDate);
				}
			}
		}
		
		if (scheduledPeriod.hasStart() || scheduledPeriod.hasEnd() || dueKindValue != null) {
			detail.setScheduled(scheduledPeriod);
		}
		
		if (dueKindValue != null) {
			Extension dueKindExtension = new Extension();
			dueKindExtension.setUrl(ACTIVITY_DUE_KIND_EXTENSION_URL);
			dueKindExtension.setValue(new CodeType(dueKindValue));
			detail.addExtension(dueKindExtension);
		}
		
		if (task.getPriority() != null) {
			Extension priorityExtension = new Extension();
			priorityExtension.setUrl(ACTIVITY_PRIORITY_EXTENSION_URL);
			priorityExtension.setValue(new CodeType(task.getPriority().name().toLowerCase(Locale.ROOT)));
			detail.addExtension(priorityExtension);
		}
		
		if (!detail.isEmpty()) {
			activity.setDetail(detail);
		}
		
		carePlan.addActivity(activity);
		
		return carePlan;
	}
	
	private static CarePlan.CarePlanStatus mapTaskStatusToCarePlanStatus(Task task) {
		if (Boolean.TRUE.equals(task.getVoided())) {
			return CarePlan.CarePlanStatus.ENTEREDINERROR;
		}
		
		TaskStatus taskStatus = task.getStatus();
		if (taskStatus == null) {
			return CarePlan.CarePlanStatus.ACTIVE;
		}
		
		switch (taskStatus) {
			case COMPLETED:
				return CarePlan.CarePlanStatus.COMPLETED;
			case CANCELLED:
			case STOPPED:
				return CarePlan.CarePlanStatus.REVOKED;
			case ONHOLD:
				return CarePlan.CarePlanStatus.ONHOLD;
			case ENTEREDINERROR:
				return CarePlan.CarePlanStatus.ENTEREDINERROR;
			case UNKNOWN:
				return CarePlan.CarePlanStatus.UNKNOWN;
			default:
				return CarePlan.CarePlanStatus.ACTIVE;
		}
	}
	
	private static CarePlan.CarePlanActivityStatus toFhirStatus(TaskStatus status) {
		return status == null ? null : CarePlan.CarePlanActivityStatus.valueOf(status.name());
	}
	
	private static TaskStatus fromFhirStatus(CarePlan.CarePlanActivityStatus status) {
		if (status == null || status == CarePlan.CarePlanActivityStatus.NULL) {
			return null;
		}
		try {
			return TaskStatus.valueOf(status.name());
		}
		catch (IllegalArgumentException ex) {
			log.warn("Unknown FHIR CarePlanActivityStatus '{}'; mapping to UNKNOWN", status);
			return TaskStatus.UNKNOWN;
		}
	}
	
	private static CarePlan.CarePlanActivityKind toFhirKind(TaskKind kind) {
		return kind == null ? null : CarePlan.CarePlanActivityKind.valueOf(kind.name());
	}
	
	private static TaskKind fromFhirKind(CarePlan.CarePlanActivityKind kind) {
		if (kind == null || kind == CarePlan.CarePlanActivityKind.NULL) {
			return null;
		}
		try {
			return TaskKind.valueOf(kind.name());
		}
		catch (IllegalArgumentException ex) {
			log.warn("Unknown FHIR CarePlanActivityKind '{}'; storing null", kind);
			return null;
		}
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
	public Task applyCarePlanToTask(Task task, CarePlan carePlan, Patient patient, Provider assignee,
	        String assigneeRoleUuid) {
		if (task == null) {
			task = new Task();
		}
		
		task.setPatient(patient);
		task.setAssignee(null);
		task.setAssigneeProviderRoleId(null);
		task.setDescription(null);
		task.setDueDate(null);
		task.setDueDateType(null);
		task.setDueDateReferenceVisit(null);
		task.setRationale(null);
		task.setStatus(null);
		task.setKind(null);
		task.setPriority(null);
		task.setSystemTask(null);
		
		// Parse instantiatesCanonical to set systemTask reference
		if (carePlan.hasInstantiatesCanonical()) {
			for (CanonicalType canonical : carePlan.getInstantiatesCanonical()) {
				String value = canonical.getValue();
				if (StringUtils.isNotBlank(value) && value.startsWith(PLAN_DEFINITION_RESOURCE_TYPE + "/")) {
					String systemTaskUuid = value.substring((PLAN_DEFINITION_RESOURCE_TYPE + "/").length());
					SystemTask systemTask = resolveSystemTask(systemTaskUuid);
					if (systemTask != null) {
						task.setSystemTask(systemTask);
						break;
					}
				}
			}
		}
		
		if (carePlan.hasActivity() && !carePlan.getActivity().isEmpty()) {
			CarePlanActivityComponent activity = carePlan.getActivityFirstRep();
			
			Optional.ofNullable(resolveKindFromActivity(activity)).map(CarePlanMapper::fromFhirKind)
			        .ifPresent(task::setKind);
			
			if (activity.hasDetail()) {
				CarePlanActivityDetailComponent detail = activity.getDetail();
				
				if (detail.hasStatus()) {
					// Voiding is a separate concern driven through voidTask / FHIR DELETE. An incoming
					// status — including CANCELLED — is a legitimate clinical state change and must not
					// flip task.voided.
					task.setStatus(fromFhirStatus(detail.getStatus()));
				}
				
				if (detail.hasDescription()) {
					task.setDescription(detail.getDescription());
				}
				
				if (detail.hasReasonCode()) {
					for (CodeableConcept reason : detail.getReasonCode()) {
						if (reason.hasText()) {
							task.setRationale(reason.getText());
							break;
						}
					}
				}
				
				String dueKindValue = null;
				Visit visitFromExtension = null;
				String priorityValue = null;
				
				if (detail.hasExtension()) {
					for (Extension extension : detail.getExtension()) {
						if (!extension.hasValue()) {
							continue;
						}
						String url = extension.getUrl();
						if (ACTIVITY_DUE_KIND_EXTENSION_URL.equals(url)) {
							dueKindValue = stringFromExtension(extension);
						} else if (ENCOUNTER_EXTENSION_URL.equals(url) && extension.getValue() instanceof Reference) {
							visitFromExtension = resolveVisitFromEncounterReference((Reference) extension.getValue());
						} else if (ACTIVITY_PRIORITY_EXTENSION_URL.equals(url)) {
							priorityValue = stringFromExtension(extension);
						}
					}
				}
				
				if (priorityValue != null) {
					try {
						task.setPriority(Priority.valueOf(priorityValue.toUpperCase(Locale.ROOT)));
					}
					catch (IllegalArgumentException e) {
						log.warn("Unknown priority value: {}", priorityValue);
					}
				}
				
				if ("this-visit".equals(dueKindValue)) {
					task.setDueDateType(DueDateType.THIS_VISIT);
					if (visitFromExtension != null) {
						task.setDueDateReferenceVisit(visitFromExtension);
					}
				} else if ("next-visit".equals(dueKindValue)) {
					task.setDueDateType(DueDateType.NEXT_VISIT);
					if (visitFromExtension != null) {
						task.setDueDateReferenceVisit(visitFromExtension);
					}
				} else if ("date".equals(dueKindValue)) {
					task.setDueDateType(DueDateType.DATE);
				}
				
				// For visit-anchored tasks the scheduledPeriod.end is just a derived display value (the
				// visit's stopDatetime as of write time); the source of truth on read-back is the
				// reference visit, so leave task.dueDate null.
				boolean visitAnchored = task.getDueDateType() == DueDateType.THIS_VISIT
				        || task.getDueDateType() == DueDateType.NEXT_VISIT;
				if (!visitAnchored) {
					Date resolvedDueDate = extractDueDate(detail);
					if (resolvedDueDate != null) {
						task.setDueDate(resolvedDueDate);
						if (task.getDueDateType() == null) {
							task.setDueDateType(DueDateType.DATE);
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
		return task;
	}
	
	private Reference buildActivityReference(CarePlanActivityKind kind) {
		if (kind == null || kind == CarePlanActivityKind.NULL) {
			return null;
		}
		
		String resourceType = KIND_TO_RESOURCE_TYPE.get(kind);
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
	
	private Reference buildAuthorReference(User creator) {
		if (creator == null) {
			return null;
		}
		
		try {
			ProviderService providerService = Context.getProviderService();
			Person person = creator.getPerson();
			if (person != null) {
				Collection<Provider> providers = providerService.getProvidersByPerson(person, false);
				if (providers != null && !providers.isEmpty()) {
					Provider provider = providers.iterator().next();
					Reference practitionerRef = practitionerReferenceTranslator.toFhirResource(provider);
					if (practitionerRef != null) {
						return practitionerRef;
					}
				}
			}
		}
		catch (Exception ex) {
			log.debug("Unable to resolve Provider for User {}, using display name only", creator.getUuid(), ex);
		}
		
		Reference authorRef = new Reference();
		Person person = creator.getPerson();
		if (person != null && person.getPersonName() != null) {
			String displayName = person.getPersonName().getFullName();
			if (StringUtils.isNotBlank(displayName)) {
				authorRef.setDisplay(displayName);
			}
		}
		if (!authorRef.hasDisplay()) {
			authorRef.setDisplay(creator.getUsername());
		}
		
		return authorRef;
	}
	
	private static String stringFromExtension(Extension extension) {
		IBaseDatatype value = extension.getValue();
		if (value instanceof CodeType) {
			return ((CodeType) value).getValue();
		}
		if (value instanceof StringType) {
			return ((StringType) value).getValue();
		}
		return null;
	}
	
	private static Date extractDueDate(CarePlanActivityDetailComponent detail) {
		if (detail.hasScheduledPeriod() && detail.getScheduledPeriod().hasEnd()) {
			return detail.getScheduledPeriod().getEnd();
		}
		if (!detail.hasScheduled()) {
			return null;
		}
		IBaseDatatype scheduled = detail.getScheduled();
		if (scheduled instanceof DateTimeType) {
			return ((DateTimeType) scheduled).getValue();
		}
		if (scheduled instanceof DateType) {
			return ((DateType) scheduled).getValue();
		}
		if (scheduled instanceof Timing) {
			Timing timing = (Timing) scheduled;
			if (!timing.getEvent().isEmpty()) {
				return timing.getEvent().get(0).getValue();
			}
			if (timing.getRepeat() != null && timing.getRepeat().hasBoundsPeriod()
			        && timing.getRepeat().getBoundsPeriod().hasEnd()) {
				return timing.getRepeat().getBoundsPeriod().getEnd();
			}
		}
		return null;
	}
	
	private CarePlanActivityKind resolveKindFromActivity(CarePlanActivityComponent activity) {
		if (activity.hasReference() && activity.getReference().hasType()) {
			String type = activity.getReference().getType();
			if (StringUtils.isNotBlank(type)) {
				return KIND_TO_RESOURCE_TYPE.entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(type))
				        .map(Map.Entry::getKey).findFirst().orElseGet(() -> fromTypeString(type));
			}
		}
		
		if (activity.hasDetail() && activity.getDetail().hasKind()) {
			return activity.getDetail().getKind();
		}
		
		return null;
	}
	
	private CarePlanActivityKind fromTypeString(String type) {
		String normalized = type.replace("-", "").replace("_", "");
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
	 * Resolves a Visit entity from an Encounter reference in FHIR extension. In OpenMRS,
	 * Encounter/{uuid} can reference either an Encounter or a Visit (resolved by UUID). This method
	 * tries to resolve as a Visit first, then as an Encounter.
	 *
	 * @param encounterRef the Encounter reference from FHIR extension
	 * @return the Visit entity, or null if not found
	 */
	private Visit resolveVisitFromEncounterReference(Reference encounterRef) {
		if (encounterRef == null || !encounterRef.hasReference()) {
			return null;
		}
		
		try {
			IIdType encounterId = encounterRef.getReferenceElement();
			if (encounterId == null || StringUtils.isBlank(encounterId.getIdPart())) {
				return null;
			}
			
			String uuid = encounterId.getIdPart();
			
			try {
				VisitService visitService = Context.getVisitService();
				Visit visit = visitService.getVisitByUuid(uuid);
				if (visit != null) {
					return visit;
				}
			}
			catch (Exception ex) {
				log.debug("UUID {} is not a Visit, trying as Encounter", uuid, ex);
			}
			
			EncounterService encounterService = Context.getEncounterService();
			Encounter encounter = encounterService.getEncounterByUuid(uuid);
			if (encounter != null && encounter.getVisit() != null) {
				return encounter.getVisit();
			}
		}
		catch (Exception ex) {
			log.warn("Unable to resolve Visit from Encounter reference {}", encounterRef.getReference(), ex);
		}
		
		return null;
	}
	
	/**
	 * Finds the visit that chronologically follows the reference visit for a NEXT_VISIT task.
	 *
	 * @param patient the Patient
	 * @param referenceVisit the visit when the task was created
	 * @return the next visit after the reference visit, or null if not found
	 */
	private Visit findNextVisitAfterReference(Patient patient, Visit referenceVisit) {
		if (patient == null || referenceVisit == null || referenceVisit.getStartDatetime() == null) {
			return null;
		}
		
		List<Visit> visits;
		try {
			visits = Context.getVisitService().getVisitsByPatient(patient);
		}
		catch (Exception ex) {
			log.warn("Unable to find next visit after reference visit {}", referenceVisit.getUuid(), ex);
			return null;
		}
		
		if (visits == null || visits.isEmpty()) {
			return null;
		}
		
		Date refStart = referenceVisit.getStartDatetime();
		Visit nextVisit = null;
		
		for (Visit visit : visits) {
			Date visitStart = visit.getStartDatetime();
			if (visitStart == null || !visitStart.after(refStart)) {
				continue;
			}
			
			boolean isReferenceVisit = (visit.getVisitId() != null && visit.getVisitId().equals(referenceVisit.getVisitId()))
			        || (visit.getUuid() != null && visit.getUuid().equals(referenceVisit.getUuid()));
			if (isReferenceVisit) {
				continue;
			}
			
			if (nextVisit == null
			        || (nextVisit.getStartDatetime() != null && visitStart.before(nextVisit.getStartDatetime()))) {
				nextVisit = visit;
			}
		}
		
		return nextVisit;
	}
	
	/**
	 * Resolves a SystemTask entity from its UUID.
	 *
	 * @param systemTaskUuid the SystemTask UUID
	 * @return the SystemTask entity, or null if not found
	 */
	private SystemTask resolveSystemTask(String systemTaskUuid) {
		if (StringUtils.isBlank(systemTaskUuid)) {
			return null;
		}
		try {
			TasksService tasksService = Context.getService(TasksService.class);
			return tasksService.getSystemTaskByUuid(systemTaskUuid);
		}
		catch (Exception ex) {
			log.warn("Unable to resolve system task for uuid {}", systemTaskUuid, ex);
		}
		return null;
	}
	
}
