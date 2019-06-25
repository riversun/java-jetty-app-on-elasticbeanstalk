<%@page language="java" contentType="text/html; charset=utf-8"
	pageEncoding="utf-8" session="true"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>

<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Example</title>
</head>

<body>
	<h3>c:importの例</h3>
	<c:import url="header.jsp" charEncoding="UTF-8" />

	<h3>requestスコープに格納されたBeanのListをc:foreachする例</h3>
	<c:forEach var="obj" items="${requestScope.personList}"
		varStatus="status">

		<c:if test="${status.index%2==0}">
			<hr>
		</c:if>

		<dl id="<c:out value="${status.index}"/>">
			<dt>
				<c:out value="${status.index}" />
				:
				<c:out value="${obj.name}" />
				${example_data}
			</dt>
		</dl>

	</c:forEach>

	<h3>c:chooseでセッション変数を参照する例</h3>
	<c:choose>
		<c:when test="${mode=='MODE_01'}">
Mode is one.
  </c:when>
		<c:when test="${mode=='MODE_02'}">
Mode is two.
  </c:when>
		<c:otherwise>
Mode is unknown.
   </c:otherwise>
	</c:choose>
	<hr>
	<h3>c:forEachの例</h3>
	<c:forEach var="i" begin="1" end="10" step="1">
		<c:out value="${i}" />
		<br />
	</c:forEach>
</body>
</html>