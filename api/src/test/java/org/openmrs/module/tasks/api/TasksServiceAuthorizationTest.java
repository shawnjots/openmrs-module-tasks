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

import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.Role;
import org.openmrs.User;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.UserService;
import org.openmrs.api.context.Context;
import org.openmrs.module.tasks.SystemTask;
import org.openmrs.module.tasks.Task;
import org.openmrs.module.tasks.TaskKind;
import org.openmrs.module.tasks.TaskStatus;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies that the @Authorized annotations on TasksService methods are actually enforced by the
 * Spring proxy + AuthorizationAdvice interceptor chain. Each test logs in as a low-privilege user
 * missing the relevant privilege and asserts APIAuthenticationException.
 */
public class TasksServiceAuthorizationTest extends BaseModuleContextSensitiveTest {
	
	private static final String LOW_PRIVILEGE_USERNAME = "tasks-authz-test-user";
	
	private static final String LOW_PRIVILEGE_PASSWORD = "Tasks-authz-test-1";
	
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
	
	@Autowired
	UserService userService;
	
	@Autowired
	PersonService personService;
	
	private User lowPrivilegeUser;
	
	private Patient seedPatient;
	
	@Before
	public void loginAsLowPrivilegeUser() {
		seedPatient = patientService.getPatient(2);
		lowPrivilegeUser = createUserWithoutTasksPrivileges();
		Context.logout();
		Context.authenticate(LOW_PRIVILEGE_USERNAME, LOW_PRIVILEGE_PASSWORD);
	}
	
	@After
	public void restoreAuthentication() {
		Context.logout();
		Context.authenticate("admin", "test");
	}
	
	@Test(expected = APIAuthenticationException.class)
	public void getTaskByUuid_withoutViewPrivilege_shouldThrow() {
		tasksService.getTaskByUuid("any-uuid");
	}
	
	@Test(expected = APIAuthenticationException.class)
	public void getTasksByPatientId_withoutViewPrivilege_shouldThrow() {
		tasksService.getTasksByPatientId(2);
	}
	
	@Test(expected = APIAuthenticationException.class)
	public void getTasksByPatientIdIncludeVoided_withoutViewPrivilege_shouldThrow() {
		tasksService.getTasksByPatientId(2, true);
	}
	
	@Test(expected = APIAuthenticationException.class)
	public void getActiveTasksByPatientId_withoutViewPrivilege_shouldThrow() {
		tasksService.getActiveTasksByPatientId(2);
	}
	
	@Test(expected = APIAuthenticationException.class)
	public void saveTask_withoutManagePrivilege_shouldThrow() {
		Task task = new Task();
		task.setPatient(seedPatient);
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setKind(TaskKind.APPOINTMENT);
		tasksService.saveTask(task);
	}
	
	@Test(expected = APIAuthenticationException.class)
	public void voidTask_withoutDeletePrivilege_shouldThrow() {
		Task task = new Task();
		tasksService.voidTask(task, "reason");
	}
	
	@Test(expected = APIAuthenticationException.class)
	public void purgeTask_withoutDeletePrivilege_shouldThrow() {
		Task task = new Task();
		tasksService.purgeTask(task);
	}
	
	@Test(expected = APIAuthenticationException.class)
	public void getSystemTaskByUuid_withoutViewPrivilege_shouldThrow() {
		tasksService.getSystemTaskByUuid("any-uuid");
	}
	
	@Test(expected = APIAuthenticationException.class)
	public void getAllSystemTasks_withoutViewPrivilege_shouldThrow() {
		tasksService.getAllSystemTasks(false);
	}
	
	@Test(expected = APIAuthenticationException.class)
	public void saveSystemTask_withoutManagePrivilege_shouldThrow() {
		tasksService.saveSystemTask(new SystemTask());
	}
	
	@Test(expected = APIAuthenticationException.class)
	public void retireSystemTask_withoutManagePrivilege_shouldThrow() {
		tasksService.retireSystemTask(new SystemTask(), "reason");
	}
	
	@Test
	public void getTaskByUuid_withViewPrivilege_shouldNotThrow() {
		Context.logout();
		Context.authenticate("admin", "test");
		
		Task task = tasksService.getTaskByUuid("does-not-exist");
		
		assertThat(task, is(nullValue()));
	}
	
	private User createUserWithoutTasksPrivileges() {
		User existing = userService.getUserByUsername(LOW_PRIVILEGE_USERNAME);
		if (existing != null) {
			return existing;
		}
		
		Person person = new Person();
		PersonName name = new PersonName("Tasks", null, "Authz");
		person.addName(name);
		person.setGender("F");
		personService.savePerson(person);
		
		User user = new User();
		user.setUsername(LOW_PRIVILEGE_USERNAME);
		user.setPerson(person);
		
		Role emptyRole = userService.getRole("Tasks Authz Test Role");
		if (emptyRole == null) {
			emptyRole = new Role();
			emptyRole.setRole("Tasks Authz Test Role");
			emptyRole.setDescription("Role with no task privileges for authorization tests");
			emptyRole = userService.saveRole(emptyRole);
		}
		user.addRole(emptyRole);
		
		return userService.createUser(user, LOW_PRIVILEGE_PASSWORD);
	}
}
