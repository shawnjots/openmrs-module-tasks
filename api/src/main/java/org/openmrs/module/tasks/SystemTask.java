/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.tasks;

import org.openmrs.BaseOpenmrsMetadata;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Represents a system task (task template) that can be instantiated as a Task. System tasks are
 * loaded from CSV configuration files and exposed via the FHIR PlanDefinition API. This entity uses
 * "retire" semantics (BaseOpenmrsMetadata) rather than "void" semantics.
 */
@Entity(name = "tasks.SystemTask")
@Table(name = "tasks_systemtask")
public class SystemTask extends BaseOpenmrsMetadata {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "tasks_systemtask_id")
	private Integer id;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "priority", length = 50)
	private Priority priority;
	
	@Column(name = "default_assignee_provider_role_id")
	private Integer defaultAssigneeProviderRoleId;
	
	@Column(name = "rationale", length = 1000)
	private String rationale;
	
	@Column(name = "title", nullable = false, length = 255)
	private String title;
	
	@Override
	public Integer getId() {
		return id;
	}
	
	@Override
	public void setId(Integer id) {
		this.id = id;
	}
	
	public Priority getPriority() {
		return priority;
	}
	
	public void setPriority(Priority priority) {
		this.priority = priority;
	}
	
	public Integer getDefaultAssigneeProviderRoleId() {
		return defaultAssigneeProviderRoleId;
	}
	
	public void setDefaultAssigneeProviderRoleId(Integer defaultAssigneeProviderRoleId) {
		this.defaultAssigneeProviderRoleId = defaultAssigneeProviderRoleId;
	}
	
	public String getRationale() {
		return rationale;
	}
	
	public void setRationale(String rationale) {
		this.rationale = rationale;
	}
	
	public String getTitle() {
		return title;
	}
	
	public void setTitle(String title) {
		this.title = title;
	}
}
