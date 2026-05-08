/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.tasks;

import org.openmrs.BaseOpenmrsData;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.Visit;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import java.util.Date;

/**
 * Represents a task that corresponds to a FHIR CarePlan with one activity.
 */
@Entity(name = "tasks.Task")
@Table(name = "tasks_task")
public class Task extends BaseOpenmrsData {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "tasks_task_id")
	private Integer id;
	
	@ManyToOne
	@JoinColumn(name = "patient_id", nullable = false)
	private Patient patient;
	
	@ManyToOne
	@JoinColumn(name = "system_task_id")
	private SystemTask systemTask;
	
	@Column(name = "description", length = 1000)
	private String description;
	
	@ManyToOne
	@JoinColumn(name = "assignee_provider_id")
	private Provider assignee;
	
	@Column(name = "assignee_provider_role_id")
	private Integer assigneeProviderRoleId;
	
	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "due_date")
	private Date dueDate;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "due_date_type", length = 50)
	private DueDateType dueDateType;
	
	@ManyToOne
	@JoinColumn(name = "due_date_reference_visit_id")
	private Visit dueDateReferenceVisit;
	
	@Column(name = "rationale", length = 1000)
	private String rationale;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "status", length = 50)
	private TaskStatus status;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "kind", length = 50)
	private TaskKind kind;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "priority", length = 50)
	private Priority priority;
	
	@Override
	public Integer getId() {
		return id;
	}
	
	@Override
	public void setId(Integer id) {
		this.id = id;
	}
	
	@Override
	public String getUuid() {
		return super.getUuid();
	}
	
	@Override
	public void setUuid(String uuid) {
		super.setUuid(uuid);
	}
	
	public Patient getPatient() {
		return patient;
	}
	
	public void setPatient(Patient patient) {
		this.patient = patient;
	}
	
	public SystemTask getSystemTask() {
		return systemTask;
	}
	
	public void setSystemTask(SystemTask systemTask) {
		this.systemTask = systemTask;
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	public Provider getAssignee() {
		return assignee;
	}
	
	public void setAssignee(Provider assignee) {
		this.assignee = assignee;
	}
	
	public Integer getAssigneeProviderRoleId() {
		return assigneeProviderRoleId;
	}
	
	public void setAssigneeProviderRoleId(Integer assigneeProviderRoleId) {
		this.assigneeProviderRoleId = assigneeProviderRoleId;
	}
	
	public TaskStatus getStatus() {
		return status;
	}
	
	public void setStatus(TaskStatus status) {
		this.status = status;
	}
	
	public TaskKind getKind() {
		return kind;
	}
	
	public void setKind(TaskKind kind) {
		this.kind = kind;
	}
	
	public Date getDueDate() {
		return dueDate;
	}
	
	public void setDueDate(Date dueDate) {
		this.dueDate = dueDate;
	}
	
	public DueDateType getDueDateType() {
		return dueDateType;
	}
	
	public void setDueDateType(DueDateType dueDateType) {
		this.dueDateType = dueDateType;
	}
	
	public Visit getDueDateReferenceVisit() {
		return dueDateReferenceVisit;
	}
	
	public void setDueDateReferenceVisit(Visit dueDateReferenceVisit) {
		this.dueDateReferenceVisit = dueDateReferenceVisit;
	}
	
	public String getRationale() {
		return rationale;
	}
	
	public void setRationale(String rationale) {
		this.rationale = rationale;
	}
	
	public Priority getPriority() {
		return priority;
	}
	
	public void setPriority(Priority priority) {
		this.priority = priority;
	}
}
