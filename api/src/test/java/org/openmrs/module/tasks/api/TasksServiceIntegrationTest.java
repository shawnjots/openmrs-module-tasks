/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.tasks.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Properties;

import org.openmrs.module.tasks.TaskKind;
import org.openmrs.module.tasks.TaskStatus;
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.tasks.SystemTask;
import org.openmrs.module.tasks.Task;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Exercises {@link TasksService} through the full Spring proxy so that
 * {@link org.openmrs.aop.RequiredDataAdvice} (wired in via {@code serviceInterceptors}) actually
 * runs. These tests guard the contract that the void/retire handlers populate the audit fields
 * (voidedBy/retiredBy in particular) that a plain unit test cannot observe.
 */
public class TasksServiceIntegrationTest extends BaseModuleContextSensitiveTest {
	
	@Override
	public Properties getRuntimeProperties() {
		Properties props = super.getRuntimeProperties();
		props.setProperty("module.allow_web_admin", "false");
		return props;
	}
	
	@Autowired
	TasksService tasksService;
	
	@Autowired
	PatientService patientService;
	
	@Test
	public void voidTask_shouldPopulateAuditFieldsViaRequiredDataAdvice() {
		Patient patient = patientService.getPatient(2);
		
		Task task = new Task();
		task.setPatient(patient);
		task.setDescription("Integration test task");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setKind(TaskKind.APPOINTMENT);
		tasksService.saveTask(task);
		
		Context.flushSession();
		String uuid = task.getUuid();
		
		tasksService.voidTask(task, "no longer needed");
		
		Context.flushSession();
		Context.clearSession();
		
		Task reloaded = tasksService.getTaskByUuid(uuid);
		User authenticatedUser = Context.getAuthenticatedUser();
		
		assertThat(reloaded, is(notNullValue()));
		assertThat(reloaded.getVoided(), is(true));
		assertThat(reloaded.getVoidReason(), is("no longer needed"));
		assertThat(reloaded.getDateVoided(), is(notNullValue()));
		assertThat(reloaded.getVoidedBy(), is(notNullValue()));
		assertThat(reloaded.getVoidedBy().getUserId(), is(authenticatedUser.getUserId()));
	}
	
	@Test
	public void retireSystemTask_shouldPopulateAuditFieldsViaRequiredDataAdvice() {
		SystemTask systemTask = new SystemTask();
		systemTask.setName("integration-test-system-task");
		systemTask.setTitle("Integration Test System Task");
		tasksService.saveSystemTask(systemTask);
		
		Context.flushSession();
		String uuid = systemTask.getUuid();
		
		tasksService.retireSystemTask(systemTask, "obsolete");
		
		Context.flushSession();
		Context.clearSession();
		
		SystemTask reloaded = tasksService.getSystemTaskByUuid(uuid);
		User authenticatedUser = Context.getAuthenticatedUser();
		
		assertThat(reloaded, is(notNullValue()));
		assertThat(reloaded.getRetired(), is(true));
		assertThat(reloaded.getRetireReason(), is("obsolete"));
		assertThat(reloaded.getDateRetired(), is(notNullValue()));
		assertThat(reloaded.getRetiredBy(), is(notNullValue()));
		assertThat(reloaded.getRetiredBy().getUserId(), is(authenticatedUser.getUserId()));
	}
	
	@Test
	public void voidTask_onAlreadyVoidedTask_shouldNotOverwriteAuditFields() {
		// The void handler is self-healing: on an already-voided object with voidedBy already set,
		// it no-ops so a second void call can't clobber the original audit trail.
		Patient patient = patientService.getPatient(2);
		
		Task task = new Task();
		task.setPatient(patient);
		task.setDescription("Integration test task");
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setKind(TaskKind.APPOINTMENT);
		tasksService.saveTask(task);
		
		tasksService.voidTask(task, "original reason");
		Context.flushSession();
		
		Task firstLoad = tasksService.getTaskByUuid(task.getUuid());
		long originalDateVoidedMs = firstLoad.getDateVoided().getTime();
		String originalVoidReason = firstLoad.getVoidReason();
		Integer originalVoidedById = firstLoad.getVoidedBy() == null ? null : firstLoad.getVoidedBy().getUserId();
		
		tasksService.voidTask(firstLoad, "second attempt");
		Context.flushSession();
		Context.clearSession();
		
		Task reloaded = tasksService.getTaskByUuid(task.getUuid());
		assertThat(reloaded.getVoidReason(), is(originalVoidReason));
		assertThat(reloaded.getDateVoided().getTime(), is(originalDateVoidedMs));
		assertThat(reloaded.getVoidedBy(), is(notNullValue()));
		assertThat(reloaded.getVoidedBy().getUserId(), is(originalVoidedById));
		assertThat(originalVoidedById, is(notNullValue()));
	}
	
}
