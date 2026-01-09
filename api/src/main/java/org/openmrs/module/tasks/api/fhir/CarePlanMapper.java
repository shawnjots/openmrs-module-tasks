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
import org.openmrs.module.fhir2.api.translators.PatientReferenceTranslator;
import org.openmrs.module.fhir2.api.translators.PractitionerReferenceTranslator;
import org.openmrs.module.tasks.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
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
	
	private static final String ENCOUNTER_EXTENSION_URL = "http://hl7.org/fhir/StructureDefinition/encounter-associatedEncounter";
	
	private static final String ACTIVITY_DUE_KIND_EXTENSION_URL = "http://openmrs.org/fhir/StructureDefinition/activity-dueKind";
	
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
		
		if (task.getStatus() == CarePlan.CarePlanActivityStatus.COMPLETED) {
			carePlan.setStatus(CarePlan.CarePlanStatus.COMPLETED);
		} else {
			carePlan.setStatus(CarePlan.CarePlanStatus.ACTIVE);
		}
		
		carePlan.setIntent(CarePlan.CarePlanIntent.PLAN);
		
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
		Reference reference = buildActivityReference(task.getKind());
		if (reference != null) {
			activity.setReference(reference);
		}
		
		CarePlanActivityDetailComponent detail = new CarePlanActivityDetailComponent();
		
		if (Boolean.TRUE.equals(task.getVoided())) {
			detail.setStatus(CarePlan.CarePlanActivityStatus.CANCELLED);
		} else if (task.getStatus() != null) {
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
		
		// Handle due date
		Period scheduledPeriod = new Period();
		String dueKindValue = null;
		
		if (task.getDueDateType() == DueDateType.DATE && task.getDueDate() != null) {
			// DATE type: start = end = specific date
			scheduledPeriod.setStart(task.getDueDate());
			scheduledPeriod.setEnd(task.getDueDate());
			dueKindValue = "date";
		} else if (task.getDueDateType() == DueDateType.THIS_VISIT || task.getDueDateType() == DueDateType.NEXT_VISIT) {
			// Visit-based types: end = visit end date (when available)
			dueKindValue = task.getDueDateType() == DueDateType.THIS_VISIT ? "this-visit" : "next-visit";
			
			Visit referenceVisit = task.getDueDateReferenceVisit();
			if (referenceVisit != null) {
				// Add encounter extension for reference visit
				if (referenceVisit.getUuid() != null) {
					Extension encounterExtension = new Extension();
					encounterExtension.setUrl(ENCOUNTER_EXTENSION_URL);
					Reference encounterRef = new Reference();
					encounterRef.setReference("Encounter/" + task.getDueDateReferenceVisit().getUuid());
					encounterExtension.setValue(encounterRef);
					detail.addExtension(encounterExtension);
				}

				// Set start to visit start date (or task creation date if visit start not available)
				Date visitStartDate = referenceVisit.getStartDatetime();
				if (visitStartDate != null) {
					scheduledPeriod.setStart(visitStartDate);
				} else if (task.getDateCreated() != null) {
					scheduledPeriod.setStart(task.getDateCreated());
				}
				
				Date visitEndDate = null;
				
				if (task.getDueDateType() == DueDateType.THIS_VISIT) {
					// For THIS_VISIT, the reference visit IS the due visit
					visitEndDate = task.getDueDateReferenceVisit().getStopDatetime();
				} else if (task.getDueDateType() == DueDateType.NEXT_VISIT) {
					// For NEXT_VISIT, find the visit that follows the reference visit
					Visit nextVisit = findNextVisitAfterReference(task.getPatient(), task.getDueDateReferenceVisit());
					if (nextVisit != null) {
						// Set start to next visit start if available
						if (nextVisit.getStartDatetime() != null) {
							scheduledPeriod.setStart(nextVisit.getStartDatetime());
						}
						// Set end date if next visit has ended
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
		
		// Always set scheduledPeriod (may have no end for ongoing visits, but should have start)
		if (scheduledPeriod.hasStart() || scheduledPeriod.hasEnd() || dueKindValue != null) {
			detail.setScheduled(scheduledPeriod);
		}
		
		// Add due-kind extension if we have a due date type
		if (dueKindValue != null) {
			Extension dueKindExtension = new Extension();
			dueKindExtension.setUrl(ACTIVITY_DUE_KIND_EXTENSION_URL);
			dueKindExtension.setValue(new org.hl7.fhir.r4.model.CodeType(dueKindValue));
			detail.addExtension(dueKindExtension);
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
		task.setDueDate(null);
		task.setDueDateType(null);
		task.setDueDateReferenceVisit(null);
		task.setRationale(null);
		
		if (carePlan.hasActivity() && !carePlan.getActivity().isEmpty()) {
			CarePlanActivityComponent activity = carePlan.getActivityFirstRep();
			
			Optional.ofNullable(resolveKindFromActivity(activity)).ifPresent(task::setKind);
			
			if (activity.hasDetail()) {
				CarePlanActivityDetailComponent detail = activity.getDetail();
				
				if (detail.hasStatus()) {
					CarePlan.CarePlanActivityStatus status = detail.getStatus();
					task.setStatus(status);
					
					if (status == CarePlan.CarePlanActivityStatus.CANCELLED) {
						task.setVoided(true);
						if (task.getDateVoided() == null) {
							task.setDateVoided(new Date());
						}
					}
				}
				
				if (detail.hasDescription()) {
					task.setDescription(detail.getDescription());
				}
				
				// Handle due date: read from activity-dueKind extension first
				String dueKindValue = null;
				Visit visitFromExtension = null;
				
				if (detail.hasExtension()) {
					for (Extension extension : detail.getExtension()) {
						if (ACTIVITY_DUE_KIND_EXTENSION_URL.equals(extension.getUrl()) && extension.hasValue()) {
							IBaseDatatype value = extension.getValue();
							if (value instanceof org.hl7.fhir.r4.model.CodeType) {
								dueKindValue = ((org.hl7.fhir.r4.model.CodeType) value).getValue();
							} else if (value instanceof StringType) {
								dueKindValue = ((StringType) value).getValue();
							}
						} else if (ENCOUNTER_EXTENSION_URL.equals(extension.getUrl()) && extension.hasValue()) {
							IBaseDatatype value = extension.getValue();
							if (value instanceof Reference) {
								Reference encounterRef = (Reference) value;
								visitFromExtension = resolveVisitFromEncounterReference(encounterRef);
							}
						}
					}
				}
				
				// Set due date type based on extension
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
				
				// Read due date from scheduledPeriod or other scheduled types
				if (detail.hasScheduledPeriod() && detail.getScheduledPeriod().hasEnd()) {
					task.setDueDate(detail.getScheduledPeriod().getEnd());
					// If no dueKind extension was found, assume DATE type
					if (task.getDueDateType() == null) {
						task.setDueDateType(DueDateType.DATE);
					}
				} else if (detail.hasScheduled()) {
					IBaseDatatype scheduledElement = detail.getScheduled();
					if (scheduledElement instanceof DateTimeType) {
						task.setDueDate(((DateTimeType) scheduledElement).getValue());
						if (task.getDueDateType() == null) {
							task.setDueDateType(DueDateType.DATE);
						}
					} else if (scheduledElement instanceof DateType) {
						task.setDueDate(((DateType) scheduledElement).getValue());
						if (task.getDueDateType() == null) {
							task.setDueDateType(DueDateType.DATE);
						}
					} else if (scheduledElement instanceof Timing) {
						Timing timing = (Timing) scheduledElement;
						if (!timing.getEvent().isEmpty()) {
							task.setDueDate(timing.getEvent().get(0).getValue());
							if (task.getDueDateType() == null) {
								task.setDueDateType(DueDateType.DATE);
							}
						} else if (timing.getRepeat() != null && timing.getRepeat().hasBoundsPeriod()
						        && timing.getRepeat().getBoundsPeriod().hasEnd()) {
							task.setDueDate(timing.getRepeat().getBoundsPeriod().getEnd());
							if (task.getDueDateType() == null) {
								task.setDueDateType(DueDateType.DATE);
							}
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
	
	private Reference buildAuthorReference(User creator) {
		if (creator == null) {
			return null;
		}
		
		try {
			ProviderService providerService = Context.getProviderService();
			Person person = creator.getPerson();
			if (person != null) {
				java.util.Collection<Provider> providers = providerService.getProvidersByPerson(person, false);
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
	 * Resolves a Visit entity from an Encounter reference in FHIR extension.
	 * In OpenMRS, Encounter/{uuid} can reference either an Encounter or a Visit (resolved by UUID).
	 * This method tries to resolve as a Visit first, then as an Encounter.
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
			
			// First, try to resolve as a Visit directly
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
			
			// If not found as Visit, try to resolve as Encounter and get its Visit
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
		
		try {
			java.util.List<Visit> visits = null;
			
			// Try to get visits directly from VisitService first
			try {
				org.openmrs.api.VisitService visitService = Context.getVisitService();
				java.lang.reflect.Method method = visitService.getClass().getMethod("getVisits", Patient.class, Boolean.TYPE, Boolean.TYPE);
				visits = (java.util.List<Visit>) method.invoke(visitService, patient, false, true);
			} catch (NoSuchMethodException | java.lang.reflect.InvocationTargetException e) {
				// Try alternative method signatures
				try {
					org.openmrs.api.VisitService visitService = Context.getVisitService();
					// Try getVisitsByPatient which might be available
					java.lang.reflect.Method method = visitService.getClass().getMethod("getVisitsByPatient", Patient.class);
					visits = (java.util.List<Visit>) method.invoke(visitService, patient);
				} catch (NoSuchMethodException | java.lang.reflect.InvocationTargetException e2) {
					// If that doesn't work, try getting encounters and extracting visits
					try {
						EncounterService encounterService = Context.getEncounterService();
						java.util.List<Encounter> encounters = encounterService.getEncountersByPatient(patient);
						if (encounters != null) {
							visits = encounters.stream()
							        .map(Encounter::getVisit)
							        .filter(v -> v != null)
							        .distinct()
							        .collect(java.util.stream.Collectors.toList());
						}
					} catch (Exception ex2) {
						log.debug("Unable to get visits via encounters", ex2);
					}
				}
			}
			
			if (visits != null && !visits.isEmpty()) {
				Date refStart = referenceVisit.getStartDatetime();
				Visit nextVisit = null;
				
				for (Visit visit : visits) {
					Date visitStart = visit.getStartDatetime();
					if (visitStart != null && visitStart.after(refStart)) {
						// Make sure it's not the reference visit itself
						boolean isReference = false;
						if (visit.getVisitId() != null && referenceVisit.getVisitId() != null
						        && visit.getVisitId().equals(referenceVisit.getVisitId())) {
							isReference = true;
						} else if (visit.getUuid() != null && referenceVisit.getUuid() != null
						        && visit.getUuid().equals(referenceVisit.getUuid())) {
							isReference = true;
						}
						
						if (!isReference) {
							// Find the earliest visit after the reference visit
							if (nextVisit == null || (nextVisit.getStartDatetime() != null 
							        && visitStart.before(nextVisit.getStartDatetime()))) {
								nextVisit = visit;
							}
						}
					}
				}
				
				return nextVisit;
			}
		}
		catch (Exception ex) {
			log.warn("Unable to find next visit after reference visit {}", referenceVisit.getUuid(), ex);
		}
		
		return null;
	}
	
}
