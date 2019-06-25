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
	<h1>Jetty Webアプリケーションのサンプル</h1>

	<h3>JSPサンプル</h3>
	<a href="show">サーブレット→JSPを表示する</a>
	<br>
	<h3>Web APIのサンプル</h3>
	<a href="api?message=hello">API呼び出しをする</a>
	<br>
	<h3>SSEをつかった Server Pushのサンプル</h3>
	<a href="sse.html">PUSH通知を受け取る</a>
	<br>
</body>
</html>