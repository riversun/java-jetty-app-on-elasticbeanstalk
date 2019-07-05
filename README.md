# 概要
- AWS **Elastic Beanstalk**を使えば、EC2でのOSやミドルウェアのセットアップ不要でコマンド1つでWebアプリ実行環境を構築することができます。セキュリティパッチ等も自動適用できるため運用が軽くなるメリットもある上、ベースはEC2等AWSのコンポーネントの組み合わせなので、その気になれば色々カスタマイズもできるという、とても使い勝手の良いサービスです。
- 本稿では**EB CLI**というツールをつかってコマンド1つで**Java Web アプリ**の実行環境を構築します。
- Java Webアプリは、Tomcatで動作するWARではなく、**Java SEでつくったスッピンのWebアプリ(JAR)**が対象です。  
（つまり概念さえ理解できればWebアプリFrameworkは**Spring Boot**でも**Play framework**でもかまいませんし、アプリサーバーも**Tomcat**でも**Jetty**でも**Glassfish**でもOKです）

## 環境

- アプリ・プラットフォーム
    - AWS Elastic Beanstalkの **Java 8** プラットフォーム
    - 本稿ではJSP/ServletのWebアプリをJetty9で実行
- クラウド環境
    - サービス：AWS Elastic Beanstalk (EC2 + Application Loadbalancer on VPC)
    - リージョン:東京(ap-northeast-1) →東京じゃなくてもOK

## ソースコード

本稿で紹介する全ソースコードはこちらにあります
https://github.com/riversun/java-jetty-app-on-elasticbeanstalk

# 本編
## Elastic Beanstalk コマンドラインインターフェイス（EB CLI）のインストール

Elastic BeanstalkアプリはWeb GUIをつかっても構築できるが、環境構築の自動化など本番運用を考えるとコマンドラインを使うと同じような操作を何度もせずにすみ手間もへるし、アプリ・環境の構築・変更がコマンド一発で済のでCIに組み込むなどすれば安定した運用が可能となる。


### 1.EB CLIをインストールする

EB CLIのインストールにはPythonが必要となる。
もし、PCにPythonがインストールされていなければ、
https://www.anaconda.com/distribution/
などからインストールしておく。最新版をインストールしておけばOK

Pythonがインストールが完了していれば、以下のコマンドで EB CLI をインストールできる

```shell
pip install awsebcli --upgrade --user
```

### 2.以下のコマンドでインストール終了チェック

EB CLIがインストールされ、コマンドラインで利用可能になっているかどうか、以下のコマンドで確認する

```shell
eb --version

EB CLI 3.15.2 (Python 3.7.1)
```

ちゃんとインストールできた模様

**TIPS**
Windows環境の場合、<font color=red>eb コマンドがみつからない</font>場合がある。
そのときは、

```
C:\Users\[ユーザー名]\AppData\Roaming\Python\Python37\Scripts
```

をパスに追加する


# JavaでWebアプリを作る

## Elastic Beanstalkで作るJavaアプリは FAT JAR形式で作る

Elastic BeanstalkでJava Webアプリを実行するにはいくつかの方法がある。

- warファイルをアップロード(Tomcatで実行する)
- JARファイルをアップロード(Java SE環境で実行する)

本稿では **JARファイルをアップロード** する方式を採用する。

**JARファイル**とは、Javaで作ったアプリ(Webアプリ)のソースコード＋依存ファイルを1つのJARにまとめたモノの事を言う。全部1つにしてサイズの大きなJARを作るので「**FAT JAR**」とも言う。

JARを使うメリットは、作ったWebアプリをJARファイルにさえできれば、**何でもOK**ということ。

FrameworkはPlay Frameworkでも、Spring Bootでも、Strutsでも、JSFでも良いし(もちろん素のJSP/ServletでもOK)、APサーバーもTomcatでもJettyでもGlassfishでもUndertow(Wildfly)でもOK。

**ただし**JARをつくってElastic Beanstalkで動かすには、「**Elastic Beanstalkのお作法**」があるので、そちらをみていく。

といっても、難しくはない。

## Elastic Beanstalk用のJavaソースコード構成

Web APサーバーとしてJettyを使ったJava Webアプリを考える。

まず、全体像は以下のとおり。「★」印がついているところが Elastic Beanstalkのための構成
その他はJavaのMavenプロジェクトの標準的なソースコード構成となる

```shell:ElasticBeanstalk用Javaソースコード構成
elastic-beantalk-java-app
├── src/main/java ・・・Javaのソースコードのルートディレクトリ
│   └── myserver 　
│       └── StartServer.java
├── src/main/resources
│   └── webroot 　・・・静的WebコンテンツやJSPのルートディレクトリ
│       ├── index.html
│       └── show.jsp 
├── src/main/assembly
│   └── zip.xml ・・・★elastic beanstalkにアップロードするZIPファイルの生成ルールを記述
├── .elasticbeanstalk　・・・★Elastic Beanstalkへのデプロイ情報を格納するディレクトリ
│   └── config.yml 　・・・★Elastic Beanstalkへのデプロイ情報のYAMLファイル
├── target ・・・ビルドした結果(jarなど)が生成されるディレクトリ
├── Procfile ・・・★Elastic BeanstalkのEC2上でJARファイルを実行するためのスクリプトを記述する
└── pom.xml　・・・Maven用設定ファイル
```

ソースコードや設定ファイル等の中身については後で説明するとして、
先にこのソースコードがどのようにビルドされて Elastic Beanstalkにアップロードされるかをみておく。

## Elastic BeanstalkにアップロードするZIPファイルの構造

本稿では、手元のPCでソースコードをビルドして、それを1つのJARファイルにパッケージし、さらにJARファイル以外にもElastic Beanstalkに必要なファイル含めてZIPファイルを生成する。

ここで、JavaのソースコードをビルドしてできたFAT JARのファイル名を**eb-app-jar-with-dependencies.jar**とする。(maven-assembly-pluginで生成する、後で説明）
ElasticBeanstalkにアップロードするためのZIPファイル名を**my-jetty-app-eb.zip**とすると、そのZIPファイルの中身構造は以下となる。

```shell:my-jetty-app-eb.zipの中身
my-jetty-app-eb.zip
├── my-jetty-app-jar-with-dependencies.jar ・・・Javaのソースコードを1つのJARにパッケージしたもの
└── Procfile　・・・Elastic BeanstalkのEC2内でJARを実行するためのスクリプト
```

ZIPファイルの中には、ソースをまとめた**JARファイル**と、実行スクリプトの書いてあるファイルである**Procfile**の2つとなる。
ということで、この形式のZIPファイルをEB CLI経由でElastic Beanstalkにアップロードすればデプロイできる。
これが「**Elastic Beanstalkのお作法**」の１つということになる。

次は、Javaアプリのソースをみていく。
とくに「**Elastic Beanstalkのお作法**」に関連する部分を中心に説明する

## Javaアプリのソースコードの作り方

これからJavaアプリを動かそうとしているElastic Beanstalk環境は以下のようなもので、Java Webアプリとしてケアしておくべき点は**ポート番号 5000をListenする**ところ

![image.png](https://qiita-image-store.s3.ap-northeast-1.amazonaws.com/0/170905/afe3e55b-fe42-4e99-1ff9-a5d7e75b4d7f.png)

つまり、Javaで**ポート番号 5000をListenするサーバー**プログラムを作ってあげればひとまずOK

**サンプルコード**
ということでサンプルコードを以下のリポジトリに置いた
https://github.com/riversun/java-jetty-app-on-elasticbeanstalk

このサンプルは**Servlet/JSP/プッシュ通知**機能をJetty上動作させている

このコードをクローンすると

```
clone https://github.com/riversun/java-jetty-app-on-elasticbeanstalk.git
```

前述したとおり以下のようなソースコード構成となっている。
（一部ファイルは省略）

```shell:ElasticBeanstalk用Javaソースコード構成
elastic-beantalk-java-app
├── src/main/java ・・・Javaのソースコードのルートディレクトリ
│   └── myserver 　
│       └── StartServer.java
├── src/main/resources
│   └── webroot 　・・・静的WebコンテンツやJSPのルートディレクトリ
│       ├── index.html
│       └── show.jsp 
├── src/main/assembly
│   └── zip.xml ・・・★elastic beanstalkにアップロードするZIPファイルの生成ルールを記述
├── .elasticbeanstalk　・・・★Elastic Beanstalkへのデプロイ情報を格納するディレクトリ
│   └── config.yml 　・・・★Elastic Beanstalkへのデプロイ情報のYAMLファイル
├── target ・・・ビルドした結果(jarなど)が生成されるディレクトリ
├── Procfile ・・・★Elastic BeanstalkのEC2上でJARファイルを実行するためのスクリプトを記述する
└── pom.xml　・・・Maven用設定ファイル
```

※「★」印がついているところが Elastic Beanstalkのための構成

Javaアプリとしてのメインクラス（エントリーポイント）は **StartServer.java**となる。
これをローカル実行して http://localhost:5000/ にアクセスすれば、以下のようになり
JSPやServletなどのシンプルな実装を試すことができるサンプルとなっている。

![image.png](https://qiita-image-store.s3.ap-northeast-1.amazonaws.com/0/170905/bea249e4-d8e1-5db0-3baa-a4a7f8b27b87.png)


ここからは、ソースコードで重要なところを以下に説明する

### src/main/java myserver.StartServer.java

メインクラス。
Jettyを起動してServlet/JSPをホストしポート5000で待ち受ける


### /pom.xml

```xml:pom.xml(抜粋)
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<groupId>org.riversun</groupId>
	<artifactId>my-jetty-app</artifactId>
	<version>1.0.0</version>
	<packaging>jar</packaging>
	<name>my-jetty-app</name>
	<description>jetty app on elastic beanstalk</description>
```

```xml:pom.xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">

	<modelVersion>4.0.0</modelVersion>

	<groupId>org.riversun</groupId>
	<artifactId>my-jetty-app</artifactId>
	<version>1.0.0</version>
	<packaging>jar</packaging>
	<name>my-jetty-app</name>
	<description>jetty app on elastic beanstalk</description>

	<properties>
		<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
		<jetty-version>9.4.19.v20190610</jetty-version>
	</properties>

	<dependencies>
		<!-- for server -->
		<dependency>
			<groupId>javax.servlet</groupId>
			<artifactId>javax.servlet-api</artifactId>
			<version>3.1.0</version>
		</dependency>
		<dependency>
			<groupId>org.eclipse.jetty</groupId>
			<artifactId>jetty-annotations</artifactId>
			<version>${jetty-version}</version>
		</dependency>
		<dependency>
			<groupId>org.eclipse.jetty</groupId>
			<artifactId>jetty-webapp</artifactId>
			<version>${jetty-version}</version>
		</dependency>
		<dependency>
			<groupId>org.eclipse.jetty</groupId>
			<artifactId>apache-jsp</artifactId>
			<version>${jetty-version}</version>
			<type>jar</type>
		</dependency>

		<dependency>
			<groupId>org.eclipse.jetty</groupId>
			<artifactId>apache-jstl</artifactId>
			<version>${jetty-version}</version>
			<type>pom</type>
		</dependency>
		<dependency>
			<groupId>com.fasterxml.jackson.core</groupId>
			<artifactId>jackson-databind</artifactId>
			<version>2.9.9</version>
		</dependency>
	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-compiler-plugin</artifactId>
				<version>3.8.1</version>
				<configuration>
					<source>1.8</source>
					<target>1.8</target>
					<excludes>
						<exclude>examples/**/*</exclude>
					</excludes>
				</configuration>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-javadoc-plugin</artifactId>
				<version>3.1.0</version>
				<executions>
					<execution>
						<id>attach-javadocs</id>
						<goals>
							<goal>jar</goal>
						</goals>
					</execution>
				</executions>
				<configuration>
					<author>true</author>
					<source>1.7</source>
					<show>protected</show>
					<encoding>UTF-8</encoding>
					<charset>UTF-8</charset>
					<docencoding>UTF-8</docencoding>
					<doclint>none</doclint>
					<additionalJOption>-J-Duser.language=en</additionalJOption>
				</configuration>
			</plugin>
			<!-- add source folders -->
			<plugin>
				<groupId>org.codehaus.mojo</groupId>
				<artifactId>build-helper-maven-plugin</artifactId>
				<version>3.0.0</version>
				<executions>
					<execution>
						<phase>generate-sources</phase>
						<goals>
							<goal>add-source</goal>
						</goals>
						<configuration>
							<sources>
								<source>src/main/java</source>
							</sources>
						</configuration>
					</execution>
				</executions>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-assembly-plugin</artifactId>
				<executions>
					<!-- ソースコードをfat-jar化する -->
					<execution>
						<id>package-jar</id>
						<phase>package</phase>
						<goals>
							<goal>single</goal>
						</goals>
						<configuration>
							<appendAssemblyId>true</appendAssemblyId>
							<archive>
								<manifest>
									<mainClass>myserver.StartServer</mainClass>
								</manifest>
							</archive>
							<finalName>${project.artifactId}</finalName>
							<descriptorRefs>
								<descriptorRef>jar-with-dependencies</descriptorRef>
							</descriptorRefs>
						</configuration>
					</execution>
					<!-- Elastic Beanstalkにアップロードするzip(fat.jarや関連ファイルを含む)を作成する -->
					<execution>
						<id>package-zip</id>
						<phase>package</phase>
						<goals>
							<goal>single</goal>
						</goals>
						<configuration>
							<appendAssemblyId>true</appendAssemblyId>
							<finalName>${project.artifactId}</finalName>
							<descriptors>
								<descriptor>src/main/assembly/zip.xml</descriptor>
							</descriptors>
						</configuration>
					</execution>
				</executions>
			</plugin>
		</plugins>
		<resources>
			<resource>
				<directory>src/main/resources</directory>
			</resource>
		</resources>
	</build>
</project>
```

以下、ポイントだけみていく。

#### mavenのartifactId

以下は**artifactId**として**my-jetty-app**とした。
この後のビルドで生成されるファイル名などもこの**artifactId**の値が使われる

```xml
	<groupId>org.riversun</groupId>
	<artifactId>my-jetty-app</artifactId>
```

#### maven-assembly-pluginの設定

以下は**maven-assembly-plugin**の設定を行っている。
maven-assembly-pluginとは、配布用のjarファイルやzipファイルを作るためのmavenプラグイン。
このmaven-assembly-pluginには2つのタスクをさせている。


```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-assembly-plugin</artifactId>
  <executions>
    <!-- ソースコードをfat-jar化する -->
    <execution>
      <id>package-jar</id>
      <phase>package</phase>
      <goals>
        <goal>single</goal>
      </goals>
      <configuration>
        <appendAssemblyId>true</appendAssemblyId>
        <archive>
          <manifest>
            <mainClass>myserver.StartServer</mainClass>
          </manifest>
        </archive>
        <finalName>${project.artifactId}</finalName>
        <descriptorRefs>
          <descriptorRef>jar-with-dependencies</descriptorRef>
        </descriptorRefs>
      </configuration>
    </execution>
    <!-- Elastic Beanstalkにアップロードするzip(fat.jarや関連ファイルを含む)を作成する -->
    <execution>
      <id>package-zip</id>
      <phase>package</phase>
      <goals>
        <goal>single</goal>
      </goals>
      <configuration>
        <appendAssemblyId>true</appendAssemblyId>
        <finalName>${project.artifactId}</finalName>
        <descriptors>
          <descriptor>src/main/assembly/zip.xml</descriptor>
        </descriptors>
      </configuration>
    </execution>
  </executions>
</plugin>

```


**その１　JARファイル生成**
まず前半の設定に注目。

```xml
  <execution>
      <id>package-jar</id>
      <phase>package</phase>
      <goals>
        <goal>single</goal>
      </goals>
      <configuration>
        <appendAssemblyId>true</appendAssemblyId>
        <archive>
          <manifest>
            <mainClass>myserver.StartServer</mainClass>
          </manifest>
        </archive>
        <finalName>${project.artifactId}</finalName>
        <descriptorRefs>
          <descriptorRef>jar-with-dependencies</descriptorRef>
        </descriptorRefs>
      </configuration>
    </execution>
```

ここで記述しているのはJavaソースコードを1つのJARファイルにまとめること。
つまり、FAT JARを作るためのタスク設定となる。

``<mainClass>myserver.StartServer</mainClass>``はJARファイルを実行するときの起動クラス。

``<finalName>${project.artifactId}</finalName>``は、最終的にできあがるJARファイルのファイル名が**artifactId**で指定された値＝**my-jetty-app-[AssemblyId].jar**となる。

``<descriptorRef>jar-with-dependencies</descriptorRef>``はmavenの**dependencies**に記載した依存ライブラリも一緒にJARファイルとしてパッケージする。

``<appendAssemblyId>true</appendAssemblyId>``は、生成されるJARファイルのファイル名にAssemblyIdをつけるか否かをセットする。もしこれを**true**にすると、JARファイル名は**my-jetty-app-jar-with-dependencies.jar**となる。

ここで設定した条件でJARファイルを生成するには

```
mvn package
```

とすればよい。すると**target**ディレクトリに**my-jetty-app-jar-with-dependencies.jar**が生成される

**その2 ZIPファイルの生成**

次は後半の設定

```xml

   <execution>
      <id>package-zip</id>
      <phase>package</phase>
      <goals>
        <goal>single</goal>
      </goals>
      <configuration>
        <appendAssemblyId>true</appendAssemblyId>
        <finalName>${project.artifactId}</finalName>
        <descriptors>
          <descriptor>src/main/assembly/zip.xml</descriptor>
        </descriptors>
      </configuration>
    </execution>
```

ここで記述しているのは、上でつくったJARファイルとElastic Beanstalk関連ファイル(Procfileなど)をZIPファイルにまとめるタスクとなる。

``<finalName>${project.artifactId}</finalName>``としているところはJARのときと同じく生成されるファイル名を指定している。

ZIPの生成ルールは外だしされた設定ファイルである``<descriptor>src/main/assembly/zip.xml</descriptor>``で設定する。


### src/main/assembly/zip.xml

さて、その外だしされた zip.xmlをみてみる。

これは最終的にZIPファイルを生成するときの生成ルールとなる。

``<include>``でJARファイルとProcfileなどを指定して、Elastic Beanstalkにアップロードする形式のZIPファイルの生成方法を指示している。

```xml:zip.xml
<?xml version="1.0" encoding="UTF-8"?>
<assembly
	xmlns="http://maven.apache.org/plugins/maven-assembly-plugin/assembly/1.1.2"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/plugins/maven-assembly-plugin/assembly/1.1.2 http://maven.apache.org/xsd/assembly-1.1.2.xsd">
	<id>eb</id>
	<baseDirectory>/</baseDirectory>
	<formats>
		<format>zip</format>
	</formats>
	<fileSets>
		<fileSet>
			<directory>${project.basedir}</directory>
			<outputDirectory>/</outputDirectory>
			<includes>
				<include>Procfile</include>
				<include>Buildfile</include>
				<include>.ebextensions</include>
			</includes>
		</fileSet>
		<fileSet>
			<directory>${project.build.directory}</directory>
			<outputDirectory>/</outputDirectory>
			<includes>
				<include>my-jetty-app-jar-with-dependencies.jar</include>
				<!-- <include>*.jar</include> -->
			</includes>
		</fileSet>
	</fileSets>
</assembly>

```

最終的に生成されるZIPファイルのファイル名は**my-jetty-app-[id].zip**となる。
zip.xmlでは、``<id>eb</id>``と指定しているので、生成されるZIPファイル名は**my-jetty-app-eb.zip**となる。

まとめると必要パッケージを生成するためのmavenコマンド ``mvn package`` を実行すると**my-jetty-app-jar-with-dependencies.jar**と**my-jetty-app-eb.zip**の両方が**target**ディレクトリ以下に生成されることになる。

ここは、あとで実際にdeployファイルを生成するところでもう一度確認する。

### /Procfile

ProcfileはElastic BeanstalkのEC2内でJARを実行するためのファイル。

Elastic BeanstalkのEC2上でアプリを起動するためのコマンドを``web:``以下に記載する。

```shell:Procfile
web: java -jar my-jetty-app-jar-with-dependencies.jar
```

↓のように起動オプションを記述してもOK

```
web: java -jar my-jetty-app-jar-with-dependencies.jar -Xms256m
```

詳細は[公式](https://docs.aws.amazon.com/ja_jp/elasticbeanstalk/latest/dg/java-se-procfile.html)参照



### .elasticbeanstalk/config.yml

config.ymlにはデプロイ情報を記述する
ここにはElastic Beansalkにデプロイするファイル``target/my-jetty-app-eb.zip``を指定している。

```yaml:config.yml
deploy:
  artifact: target/my-jetty-app-eb.zip
```

Elastic Beanstalkにデプロイする際に、このファイルが参照される。

いまは``deploy:artifact:``しか記述していないが、これからEB CLIをつかってElastic Beanstalkのデプロイ設定をしていく過程でこの**config.yml**に必要な値が追加されていく。


# EB CLIを使ってアプリをデプロイする

ファイルの意味をざっくり理解できたところで、さっそくElastic Beanstalkにアプリをデプロイする。

## EB CLIを使ってElastic BeanstalkアプリケーションをAWS上に作る

**1.アプリのディレクトリに移動する**

```shell
cd java-jetty-app-on-elasticbeanstalk
```

**2.Elastic BeanstalkにJava用の箱＊を作る**
(＊ 箱＝アプリケーション)

そのためのEB CLIのコマンドは以下の形式となる。

<table><tr><td>
eb init <b>アプリ名</b> --region <b>リージョン名</b> --platform <b>プラットフォーム</b>
</td></tr>
</table>

ここではアプリ名を**my-eb-app**、リージョンは東京リージョン(**ap-northeast-1**)、プラットフォームを**java-8**とする

コマンドは以下のようになる。

```shell
eb init my-eb-app --region ap-northeast-1 --platform java-8
```

すると

```shell
Application my-eb-app has been created.
```

というメッセージがでて、
AWS Elasticbeanstalk上にアプリケーションの箱ができる

さっそくWebコンソールで確認すると箱(アプリケーション)ができている

https://ap-northeast-1.console.aws.amazon.com/elasticbeanstalk/home

![image.png](https://qiita-image-store.s3.ap-northeast-1.amazonaws.com/0/170905/031e4728-b656-a310-0a05-36b5572898e8.png)

さて、ソースコードにある**/.elasticbeanstalk/config.yml**を見てみる。

上記コマンドで、**config.yml**も更新され、以下のようになっている。

```yaml:config.yml

branch-defaults:
  master:
    environment: null
deploy:
  artifact: target/my-jetty-app-eb.zip
global:
  application_name: my-eb-app
  branch: null
  default_ec2_keyname: null
  default_platform: java-8
  default_region: ap-northeast-1
  include_git_submodules: true
  instance_profile: null
  platform_name: null
  platform_version: null
  profile: eb-cli
  repository: null
  sc: git
  workspace_type: Application

```

**TIPS**
**------------**

もし、<font color=red>Credential情報が登録されていなければ</font>、以下のように**aws-access-id**と**aws-secret-key**を聞かれるので入力する。

```shell
eb init my-eb-app --region ap-northeast-1 --platform java-8
You have not yet set up your credentials or your credentials are incorrect
You must provide your credentials.
(aws-access-id): xxxxx
(aws-secret-key): xxxxx
Application my-eb-app has been created.
```

1回入力すれば、``[user]/.aws``ディレクトリ以下にconfigファイルができるので、次からは聞かれない。

**------------**



## デプロイ用のパッケージを作る

さきほどみてきたように、Elastic Beanstalkにデプロイするための**ZIP**ファイルを生成する。

ZIP生成はMavenでやるので``maven package``コマンドをたたく

(cleanもついでにやっておくと、コマンドは以下のとおり）

```
mvn clean package
```

以下のようになり、無事 **target/**ディレクトリに**my-jetty-app-eb.zip**が生成できた。

```
[INFO] --- maven-assembly-plugin:2.2-beta-5:single (package-zip) @ my-jetty-app ---
[INFO] Reading assembly descriptor: src/main/assembly/zip.xml
[INFO] Building zip: jetty-app-on-eb\target\my-jetty-app-eb.zip
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 32.984 s
[INFO] Finished at: 2019-07-01T15:55:14+09:00
[INFO] Final Memory: 37M/449M
[INFO] ------------------------------------------------------------------------
```

## Elastic Beanstalkにデプロイし環境を構築する

いまから、つくったZIPファイルをEB CLIをつかってElastic Beanstalkにデプロイする

Elastic Beanstalkにデプロイするには、**eb create**コマンドを使う

**eb createコマンドの使い方**

eb createコマンドは以下のように指定する。

<table><tr><td>
eb create <b>環境の名前</b> [オプション][オプション]・・・[オプション]
</td></tr></table>

オプションは以下のとおり

<table>
<tr><td>オプション</td><td>説明</td></tr>
<tr><td>--cname</td><td>URLのCNAMEを指定。<br>
 例 CNAMEにmy-jetty-app-test を指定すると<br>
http://my-jetty-app-test.ap-northeast-1.elasticbeanstalk.com/<br>
としてアクセスできる</td></tr>
<tr><td>--instance_type</td><td>インスタンスタイプ。<br>
t2シリーズを指定する場合はVPC必須</td></tr>
<tr><td>--elb-type</td><td>ロードバランサーのタイプ<br>
「application」を指定すると<br>
アプリケーションロードバランサーになる。<br>
あとで、独自ドメインやHTTPS対応するときにも<br>
ロードバランサーあると楽</td></tr>
<tr><td>--vpc.id</td><td>VPCのID</td></tr>
<tr><td>--vpc.elbpublic</td><td>ロードバランサーを<br>
パブリックサブネットに置く</td></tr>
<tr><td>--vpc.elbsubnets</td><td>ロードバランサーのサブネットIDを指定。<br>
複数指定するときはカンマ区切り</td></tr>
<tr><td>--vpc.publicip</td><td>Elastic BeanstalkのEC2を<br>
パブリックサブネットに置く</td></tr>
<tr><td>--vpc.ec2subnets</td><td>Elastic Beanstalkの<br>
EC2のサブネットIDを指定。 <br>
複数指定するときはカンマ区切り</td></tr>
</table>

詳しくは[公式](https://docs.aws.amazon.com/ja_jp/elasticbeanstalk/latest/dg/eb3-create.html)参照

それでは、
コマンドラインから以下を実行する。

```
eb create my-jetty-app-test --cname my-jetty-app-test --instance_type t2.small --elb-type application --vpc.id vpc-xxxxxxxxxxxxxxxxx --vpc.elbpublic --vpc.elbsubnets subnet-xxxxxxxxxxxxxxxxx,subnet-yyyyyyyyyyyyyyyyy --vpc.publicip --vpc.ec2subnets subnet-xxxxxxxxxxxxxxxxx,subnet-yyyyyyyyyyyyyyyyy
```

上記は実際のID等はダミーだが、環境名**my-jetty-app-test**、cnameが**my-jetty-app-test**でEC2のインスタンスタイプが**t2.small**、ELB(ロードバランサー)が**application**ロードバランサー、VPCのIDが**vpc-xxxxxxxxxxxxxxxxx**としてさきほどのZIPファイルをデプロイするコマンドとなる。

（どのZIPファイルがアップロードされるかは、**.elasticbeanstalk/config.yml**で指定されているのでそれが参照される）

また、**--vpc.elbpublic**、**--vpc.publicip**はELB(ロードバランサー)とElastic BeanstalkのEC2が指定したVPC内のパブリックサブネットで実行されることを示している。**--vpc.elbsubnets**と**--vpc.ec2subnets**それぞれおなじパブリック・サブネット（２つ）を指定してある。ELBはパブリックにアクセスできないとアプリにアクセスできないのでパブリックサブネットに置く。EC2側はパブリックサブネットに置く方法とプライベートサブネットにおく方法がある。本稿の例では、パブリックサブネットにおいているが、自動生成されたセキュリティグループによってELBからしかアクセスできないようになっている。ただし、パブリックIPは割り当てられる。
（よりセキュアにするにはEC2はプライベートサブネットに置くなど[工夫](https://aws.amazon.com/jp/premiumsupport/knowledge-center/public-load-balancer-private-ec2/)ができるが、これはElastic BeanstalkというよりEC2,VPCまわりのセキュリティイシューなのでここでは割愛とする。）

さて、上記コマンドを実行すると、以下のようにアップロードから環境構築までが自動的に実行される。


```

Uploading: [##################################################] 100% Done...

Environment details for: my-jetty-app-test
  Application name: my-eb-app
  Region: ap-northeast-1
  Deployed Version: app-1d5f-190705_00000
  Environment ID: e-2abc
  Platform: arn:aws:elasticbeanstalk:ap-northeast-1::platform/Java 8 running on 64bit Amazon Linux/2.8.6
  Tier: WebServer-Standard-1.0
  CNAME: my-jetty-app-test.ap-northeast-1.elasticbeanstalk.com
  Updated: 2019-07-01 07:08:01.989000+00:00

Printing Status:
2019-07-01 07:08:00    INFO    createEnvironment is starting.
2019-07-01 07:08:02    INFO    Using elasticbeanstalk-ap-northeast-1-000000000000 as Amazon S3 storage bucket for environment data.
2019-07-01 07:08:23    INFO    Created target group named: arn:aws:elasticloadbalancing:ap-northeast-1:000000000000:targetgroup/awseb-AWSEB-LMAAAA/000000000
2019-07-01 07:08:23    INFO    Created security group named: sg-11111111111111111
2019-07-01 07:08:39    INFO    Created security group named: sg-22222222222222222
2019-07-01 07:08:39    INFO    Created Auto Scaling launch configuration named: awseb-e-xxxxxxxxxx-stack-AWSEBAutoScalingLaunchConfiguration-3V
2019-07-01 07:09:41    INFO    Created Auto Scaling group named: awseb-e-xxxxxxxxxx-stack-AWSEBAutoScalingGroup-XXXXXXXXXXXX
2019-07-01 07:09:41    INFO    Waiting for EC2 instances to launch. This may take a few minutes.
2019-07-01 07:09:41    INFO    Created Auto Scaling group policy named: arn:aws:autoscaling:ap-northeast-1:000000000000:scalingPolicy:4e:autoScalingGroupName/awseb-e-
xxxxxxxxxx-stack-AWSEBAutoScalingGroup-XXXXXXXXXXXX:policyName/awseb-e-xxxxxxxxxx-stack-AWSEBAutoScalingScaleUpPolicy-FA
2019-07-01 07:09:42    INFO    Created Auto Scaling group policy named: arn:aws:autoscaling:ap-northeast-1:000000000000:scalingPolicy:8a:autoScalingGroupName/awseb-e-
xxxxxxxxxx-stack-AWSEBAutoScalingGroup-XXXXXXXXXXXX:policyName/awseb-e-xxxxxxxxxx-stack-AWSEBAutoScalingScaleDownPolicy-SSZW
2019-07-01 07:09:57    INFO    Created CloudWatch alarm named: awseb-e-xxxxxxxxxx-stack-AWSEBCloudwatchAlarmHigh-H0N
2019-07-01 07:09:57    INFO    Created CloudWatch alarm named: awseb-e-xxxxxxxxxx-stack-AWSEBCloudwatchAlarmLow-BX
2019-07-01 07:10:34    INFO    Created load balancer named: arn:aws:elasticloadbalancing:ap-northeast-1:000000000000:loadbalancer/app/awseb-AWSEB-1BQ
2019-07-01 07:10:34    INFO    Created Load Balancer listener named: arn:aws:elasticloadbalancing:ap-northeast-1:000000000000:listener/app/awseb-AWSEB-1BQ
2019-07-01 07:11:05    INFO    Application available at my-jetty-app-test.ap-northeast-1.elasticbeanstalk.com.
2019-07-01 07:11:05    INFO    Successfully launched environment: my-jetty-app-test

```

環境構築が開始されると、Webコンソールでも状況を把握することができる。

デプロイできた模様

![image.png](https://qiita-image-store.s3.ap-northeast-1.amazonaws.com/0/170905/c0c56a90-e7fd-ef37-c1ba-e8c9d95381dd.png)


![image.png](https://qiita-image-store.s3.ap-northeast-1.amazonaws.com/0/170905/bde20dcd-f663-6fad-ba60-8e7b9bf771aa.png)

無事アプリが http://my-jetty-app-test.ap-northeast-1.elasticbeanstalk.com/ にデプロイされた模様

アクセスすると、

![image.png](https://qiita-image-store.s3.ap-northeast-1.amazonaws.com/0/170905/7550c4b2-20a1-ac8f-d657-93ef7d4fa8fe.png)

ちゃんとJSPやServletも動いている模様

![image.png](https://qiita-image-store.s3.ap-northeast-1.amazonaws.com/0/170905/dcdb86bc-dbbd-1301-a794-788d6b272522.png)

# まとめ
- Java SEをつかったWebアプリをEB CLIをつかって、AWS Elastic Beanstalkにデプロイするまでの手順をハンズオン方式で紹介しました
- 紹介した全ソースコードはこちらです  
https://github.com/riversun/java-jetty-app-on-elasticbeanstalk.git
