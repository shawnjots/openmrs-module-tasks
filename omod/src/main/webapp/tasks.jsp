<%@ include file="/WEB-INF/template/include.jsp"%>

<%@ include file="/WEB-INF/template/header.jsp"%>

<h2><spring:message code="tasks.title" /></h2>

<br/>
<table>
  <tr>
   <th>Provider Id</th>
   <th>Identifier</th>
   <th>Name</th>
  </tr>
  <c:forEach var="provider" items="${providers}">
      <tr>
        <td>${provider.providerId}</td>
        <td>${provider.identifier}</td>
        <td>${provider.person.personName.fullName}</td>
      </tr>
  </c:forEach>
</table>

<%@ include file="/WEB-INF/template/footer.jsp"%>
