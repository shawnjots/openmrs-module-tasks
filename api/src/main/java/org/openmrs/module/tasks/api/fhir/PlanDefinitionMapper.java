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
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.hl7.fhir.r4.model.Reference;
import org.openmrs.ProviderRole;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.tasks.Priority;
import org.openmrs.module.tasks.SystemTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Mapper to convert between SystemTask entity and FHIR PlanDefinition resource. SystemTask
 * represents a task template that can be instantiated as a Task (CarePlan).
 */
@Component("tasks.PlanDefinitionMapper")
public class PlanDefinitionMapper {
	
	private static final Logger log = LoggerFactory.getLogger(PlanDefinitionMapper.class);
	
	private static final String ACTIVITY_PRIORITY_EXTENSION_URL = "http://openmrs.org/fhir/StructureDefinition/activity-priority";
	
	private static final String PRACTITIONER_ROLE_TYPE = "PractitionerRole";
	
	/**
	 * Converts a SystemTask entity to a FHIR PlanDefinition resource.
	 * 
	 * @param systemTask the SystemTask entity
	 * @return the FHIR PlanDefinition resource
	 */
	public PlanDefinition toPlanDefinition(SystemTask systemTask) {
		if (systemTask == null) {
			return null;
		}
		
		PlanDefinition planDefinition = new PlanDefinition();
		
		// Set ID
		planDefinition.setId(systemTask.getUuid());
		
		// Set status based on retired flag
		if (Boolean.TRUE.equals(systemTask.getRetired())) {
			planDefinition.setStatus(Enumerations.PublicationStatus.RETIRED);
		} else {
			planDefinition.setStatus(Enumerations.PublicationStatus.ACTIVE);
		}
		
		// Set name (machine-readable) and title (human-readable)
		planDefinition.setName(systemTask.getName());
		planDefinition.setTitle(systemTask.getTitle());
		
		// Set description
		if (StringUtils.isNotBlank(systemTask.getDescription())) {
			planDefinition.setDescription(systemTask.getDescription());
		}
		
		// Set date metadata
		if (systemTask.getDateCreated() != null) {
			planDefinition.setDate(systemTask.getDateCreated());
		}
		
		// Create an action for the task template
		PlanDefinition.PlanDefinitionActionComponent action = new PlanDefinition.PlanDefinitionActionComponent();
		action.setTitle(systemTask.getTitle());
		
		if (StringUtils.isNotBlank(systemTask.getDescription())) {
			action.setDescription(systemTask.getDescription());
		}
		
		// Add priority as extension on action
		if (systemTask.getPriority() != null) {
			Extension priorityExtension = new Extension();
			priorityExtension.setUrl(ACTIVITY_PRIORITY_EXTENSION_URL);
			priorityExtension.setValue(new org.hl7.fhir.r4.model.CodeType(systemTask.getPriority().name()
			        .toLowerCase(Locale.ROOT)));
			action.addExtension(priorityExtension);
		}
		
		// Add default assignee role as participant
		if (systemTask.getDefaultAssigneeProviderRoleId() != null) {
			ProviderRole providerRole = Context.getProviderService().getProviderRole(
			    systemTask.getDefaultAssigneeProviderRoleId());
			if (providerRole != null) {
				PlanDefinition.PlanDefinitionActionParticipantComponent participant = new PlanDefinition.PlanDefinitionActionParticipantComponent();
				participant.setType(PlanDefinition.ActionParticipantType.PRACTITIONER);
				
				CodeableConcept roleConcept = new CodeableConcept();
				Coding coding = new Coding();
				coding.setSystem(PRACTITIONER_ROLE_TYPE);
				coding.setCode(providerRole.getUuid());
				
				if (StringUtils.isNotBlank(providerRole.getName())) {
					coding.setDisplay(providerRole.getName());
				}
				
				roleConcept.addCoding(coding);
				participant.setRole(roleConcept);
				action.addParticipant(participant);
			}
		}
		
		// Add reason/rationale to action
		if (StringUtils.isNotBlank(systemTask.getRationale())) {
			CodeableConcept reason = new CodeableConcept();
			reason.setText(systemTask.getRationale());
			action.addReason(reason);
		}
		
		planDefinition.addAction(action);
		
		return planDefinition;
	}
	
	/**
	 * Converts a FHIR PlanDefinition resource to a SystemTask entity. Note: This is primarily for
	 * potential future use; CSV is the source of truth.
	 * 
	 * @param planDefinition the FHIR PlanDefinition resource
	 * @return the SystemTask entity
	 */
	public SystemTask toSystemTask(PlanDefinition planDefinition) {
		if (planDefinition == null) {
			return null;
		}
		
		SystemTask systemTask = new SystemTask();
		
		if (planDefinition.hasId()) {
			systemTask.setUuid(planDefinition.getId());
		}
		
		if (planDefinition.hasName()) {
			systemTask.setName(planDefinition.getName());
		}
		
		if (planDefinition.hasTitle()) {
			systemTask.setTitle(planDefinition.getTitle());
		}
		
		if (planDefinition.hasDescription()) {
			systemTask.setDescription(planDefinition.getDescription());
		}
		
		if (planDefinition.hasStatus()) {
			systemTask.setRetired(planDefinition.getStatus() == Enumerations.PublicationStatus.RETIRED);
		}
		
		// Extract priority and participant from action
		if (planDefinition.hasAction() && !planDefinition.getAction().isEmpty()) {
			PlanDefinition.PlanDefinitionActionComponent action = planDefinition.getActionFirstRep();
			
			// Extract priority from extension
			if (action.hasExtension()) {
				for (Extension extension : action.getExtension()) {
					if (ACTIVITY_PRIORITY_EXTENSION_URL.equals(extension.getUrl()) && extension.hasValue()) {
						String priorityValue = null;
						if (extension.getValue() instanceof org.hl7.fhir.r4.model.CodeType) {
							priorityValue = ((org.hl7.fhir.r4.model.CodeType) extension.getValue()).getValue();
						}
						if (StringUtils.isNotBlank(priorityValue)) {
							try {
								systemTask.setPriority(Priority.valueOf(priorityValue.toUpperCase(Locale.ROOT)));
							}
							catch (IllegalArgumentException e) {
								log.warn("Invalid priority value: {}", priorityValue);
							}
						}
					}
				}
			}
			
			// Extract default assignee role from participant
			if (action.hasParticipant()) {
				for (PlanDefinition.PlanDefinitionActionParticipantComponent participant : action.getParticipant()) {
					if (participant.hasRole() && participant.getRole().hasCoding()) {
						for (Coding coding : participant.getRole().getCoding()) {
							if (PRACTITIONER_ROLE_TYPE.equals(coding.getSystem()) && coding.hasCode()) {
								Integer roleId = resolveProviderRoleId(coding.getCode());
								if (roleId != null) {
									systemTask.setDefaultAssigneeProviderRoleId(roleId);
									break;
								}
							}
						}
					}
				}
			}
			
			// Extract rationale from action.reason
			if (action.hasReason() && !action.getReason().isEmpty()) {
				CodeableConcept reason = action.getReasonFirstRep();
				if (reason.hasText()) {
					systemTask.setRationale(reason.getText());
				}
			}
		}
		
		return systemTask;
	}
	
	/**
	 * Resolves a ProviderRole ID from a ProviderRole UUID.
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
}
