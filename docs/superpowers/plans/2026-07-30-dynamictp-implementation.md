# DynamicTp Global Async Executor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hand-built Spring async executor with a DynamicTp-managed native `DtpExecutor`, use local configuration in single mode, and enable Nacos hot refresh in cloud mode.

**Architecture:** `bixi-common-core` enables DynamicTp and imports one local baseline configuration that auto-creates a bean named `taskExecutor`. The root `cloud` Maven profile adds the Nacos Cloud refresher, while the `single` profile retains only the common starter. Spring's standard `@Async` executor lookup selects the native executor by its conventional bean name.

**Tech Stack:** Java 17, Spring Boot 3.4.1, Spring Cloud 2024.0.0, Spring Cloud Alibaba 2023.0.3.2, DynamicTp 1.2.2-x, JUnit 5, AssertJ, Maven.

## Global Constraints

- Remove `TaskExecutorConfiguration` and all `thread.pool.*` support; do not add a compatibility layer.
- Manage only the global Spring `@Async` executor; do not adapt Tomcat, Undertow, Quartz, or other middleware pools.
- Use DynamicTp's native auto-created `DtpExecutor`, not a wrapped `ThreadPoolTaskExecutor`.
- Name the executor `taskExecutor` so unqualified `@Async` methods use it without business-code changes.
- Single mode must start without Nacos and uses local configuration that changes only after restart.
- Cloud mode uses one optional, service-specific Nacos DTP Data ID and supports in-place refresh.
- Enable Micrometer collection and the `dynamictp` Actuator endpoint; do not configure notification platforms.
- Use test-driven development: observe each new behavior test fail for the expected reason before adding production code.

---

### Task 1: Dependency management and DynamicTp auto-configuration

**Files:**
- Modify: `pom.xml`
- Modify: `bixi-common/bixi-common-core/pom.xml`
- Create: `bixi-common/bixi-common-core/src/test/java/com/lotus/bixi/common/core/config/DynamicTpConfigurationTest.java`
- Create: `bixi-common/bixi-common-core/src/main/java/com/lotus/bixi/common/core/config/DynamicTpConfiguration.java`
- Create: `bixi-common/bixi-common-core/src/main/resources/dynamic-tp-config.yml`
- Modify: `bixi-common/bixi-common-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Delete: `bixi-common/bixi-common-core/src/main/java/com/lotus/bixi/common/core/config/TaskExecutorConfiguration.java`

**Interfaces:**
- Consumes: Spring Boot auto-configuration imports and project `YamlPropertySourceFactory`.
- Produces: a Spring bean named `taskExecutor` of type `DtpExecutor`, `DtpRegistry.getDtpExecutor("taskExecutor")`, and an Actuator `DtpEndpoint`.

- [ ] **Step 1: Add dependency scaffolding**

Add `<dynamic-tp.version>1.2.2-x</dynamic-tp.version>` to the root properties and import:

```xml
<dependency>
    <groupId>org.dromara.dynamictp</groupId>
    <artifactId>dynamic-tp-dependencies</artifactId>
    <version>${dynamic-tp.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

Add this dependency to `bixi-common-core`:

```xml
<dependency>
    <groupId>org.dromara.dynamictp</groupId>
    <artifactId>dynamic-tp-spring-boot-starter-common</artifactId>
</dependency>
```

- [ ] **Step 2: Read the test rules and write all failing behavior tests**

Read `superpowers/test-driven-development/writing-good-tests.md`, then create
`DynamicTpConfigurationTest`.

Use an `ApplicationContextRunner` that loads
`DtpBootBeanConfiguration` and the wished-for `DynamicTpConfiguration`, then asserts:

```java
assertThat(context).hasSingleBean(DtpExecutor.class);
assertThat(context).hasBean("taskExecutor");
assertThat(context.getBean("taskExecutor")).isSameAs(DtpRegistry.getDtpExecutor("taskExecutor"));
assertThat(context).hasSingleBean(DtpEndpoint.class);
```

Add assertions against the executor:

```java
assertThat(executor.getCorePoolSize()).isEqualTo(2);
assertThat(executor.getMaximumPoolSize()).isEqualTo(8);
assertThat(executor.getQueue()).isInstanceOf(VariableLinkedBlockingQueue.class);
assertThat(executor.getQueue().remainingCapacity()).isEqualTo(1024);
assertThat(executor.getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
assertThat(executor.getThreadFactory().newThread(() -> { }).getName()).startsWith("Bixi-Async-");
assertThat(executor.isNotifyEnabled()).isFalse();
assertThat(executor.isWaitForTasksToCompleteOnShutdown()).isTrue();
assertThat(executor.getAwaitTerminationSeconds()).isEqualTo(60);
```

Add an inner test configuration:

```java
@Configuration(proxyBeanMethods = false)
@EnableAsync
static class AsyncTestConfiguration {

    @Bean
    AsyncProbe asyncProbe() {
        return new AsyncProbe();
    }
}

static class AsyncProbe {

    @Async
    public CompletableFuture<String> currentThreadName() {
        return CompletableFuture.completedFuture(Thread.currentThread().getName());
    }
}
```

Add tests that:

- call `currentThreadName().get(5, TimeUnit.SECONDS)` and require a
  `Bixi-Async-` prefix;
- use `withSystemProperties` for the three `BIXI_ASYNC_*` values and require
  core 3, max 6, and capacity 2048;
- call `DtpRegistry.refresh(DtpExecutorProps)` with core 3, max 6, and capacity
  2048, then require the same bean instance to expose the new values;
- call refresh with core 9 and max 4, then require the previous valid values to
  remain unchanged.

- [ ] **Step 3: Run the test and verify RED**

Run:

```bash
mvn -pl bixi-common/bixi-common-core -am \
  -Dtest=DynamicTpConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `DynamicTpConfiguration` does not exist.

- [ ] **Step 4: Add the minimal DynamicTp auto-configuration**

Create:

```java
package com.lotus.bixi.common.core.config;

import com.lotus.bixi.common.core.factory.YamlPropertySourceFactory;
import org.dromara.dynamictp.spring.annotation.EnableDynamicTp;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.PropertySource;

@EnableDynamicTp
@AutoConfiguration
@PropertySource(value = "classpath:dynamic-tp-config.yml", factory = YamlPropertySourceFactory.class)
public class DynamicTpConfiguration {
}
```

Replace the old auto-configuration import with `DynamicTpConfiguration`.

Create `dynamic-tp-config.yml` with:

```yaml
dynamictp:
  enabled: true
  enabledCollect: true
  collectorTypes: micrometer
  monitorInterval: 5
  executors:
    - threadPoolName: taskExecutor
      threadPoolAliasName: Bixi全局异步线程池
      executorType: common
      corePoolSize: ${BIXI_ASYNC_CORE_POOL_SIZE:2}
      maximumPoolSize: ${BIXI_ASYNC_MAX_POOL_SIZE:8}
      queueCapacity: ${BIXI_ASYNC_QUEUE_CAPACITY:1024}
      queueType: VariableLinkedBlockingQueue
      rejectedHandlerType: CallerRunsPolicy
      keepAliveTime: 60
      threadNamePrefix: Bixi-Async-
      allowCoreThreadTimeOut: false
      waitForTasksToCompleteOnShutdown: true
      awaitTerminationSeconds: 60
      notifyEnabled: false

management:
  endpoints:
    web:
      exposure:
        include: health,info,dynamictp
```

Delete `TaskExecutorConfiguration`.

- [ ] **Step 5: Run the test and verify GREEN**

Run the Task 1 test command again.

Expected: all auto-configuration, default configuration, environment override,
unqualified `@Async`, valid refresh, and invalid refresh tests pass.

- [ ] **Step 6: Commit Task 1**

```bash
git add pom.xml bixi-common/bixi-common-core
git commit -m "feat: replace async executor with DynamicTp"
```

---

### Task 2: Cloud Nacos refresh wiring and profile isolation

**Files:**
- Modify: `pom.xml`
- Modify: `bixi-auth/src/main/resources/application.yml`
- Modify: `bixi-gateway/src/main/resources/application.yml`
- Modify: `bixi-module/bixi-ai-biz/src/main/resources/application.yml`
- Modify: `bixi-module/bixi-generator/src/main/resources/application.yml`
- Modify: `bixi-module/bixi-monitor/src/main/resources/application.yml`
- Modify: `bixi-module/bixi-quartz/src/main/resources/application.yml`
- Modify: `bixi-module/bixi-upms-biz/src/main/resources/application.yml`
- Modify: `bixi-module/bixi-workflow-biz/src/main/resources/application.yml`

**Interfaces:**
- Consumes: the existing Maven `cloud` and `single` profiles and each service's `spring.config.import`.
- Produces: Nacos refresh support in cloud builds and no DynamicTp Nacos Cloud Starter in single builds.

- [ ] **Step 1: Add the cloud-only Nacos starter**

Inside the root `cloud` profile add:

```xml
<dependencies>
    <dependency>
        <groupId>org.dromara.dynamictp</groupId>
        <artifactId>dynamic-tp-spring-cloud-starter-nacos</artifactId>
    </dependency>
</dependencies>
```

- [ ] **Step 2: Add each optional service-specific DTP Data ID**

Append this item to `spring.config.import` in all eight cloud service YAML files:

```yaml
- optional:nacos:${spring.application.name}-dtp-@profiles.active@.yml
```

Do not add it to `bixi-single`.

- [ ] **Step 3: Verify configuration syntax and imports**

Run:

```bash
rg -l 'optional:nacos:\\$\\{spring\\.application\\.name\\}-dtp-@profiles\\.active@\\.yml' \
  bixi-auth/src/main/resources/application.yml \
  bixi-gateway/src/main/resources/application.yml \
  bixi-module/*/src/main/resources/application.yml
```

Expected: exactly the eight files listed in this task.

- [ ] **Step 4: Verify Maven profile isolation**

Run:

```bash
mvn -Pcloud -pl bixi-auth dependency:tree \
  -Dincludes=org.dromara.dynamictp:dynamic-tp-spring-cloud-starter-nacos
```

Expected: the Nacos DynamicTp starter is present.

Run:

```bash
mvn -Psingle -pl bixi-single dependency:tree \
  -Dincludes=org.dromara.dynamictp:dynamic-tp-spring-cloud-starter-nacos
```

Expected: no matching dependency is present.

- [ ] **Step 5: Run complete verification**

Run:

```bash
mvn -pl bixi-common/bixi-common-core -am test
mvn -Pcloud -DskipTests package
mvn -Psingle -DskipTests package
rg -n 'TaskExecutorConfiguration|thread\\.pool\\.' . \
  --glob '*.java' --glob '*.yml' --glob '*.yaml' --glob '*.properties'
git diff --check
```

Expected: both builds and all tests pass; the final `rg` returns no matches; diff
check reports no whitespace errors.

- [ ] **Step 6: Commit Task 2**

```bash
git add pom.xml bixi-auth bixi-gateway bixi-module
git commit -m "feat: enable Nacos refresh for DynamicTp"
```

---

### Task 3: Documentation and final verification

**Files:**
- Modify: `.docs/modules/bixi-common/bixi-common-core/README.md`

**Interfaces:**
- Consumes: the final dependency/configuration names and verified runtime behavior.
- Produces: operator-facing instructions for local overrides and cloud Nacos configuration.

- [ ] **Step 1: Document the supported modes**

Add a DynamicTp section covering:

- local environment variables `BIXI_ASYNC_CORE_POOL_SIZE`,
  `BIXI_ASYNC_MAX_POOL_SIZE`, and `BIXI_ASYNC_QUEUE_CAPACITY`;
- the service-specific Nacos Data ID naming rule;
- the requirement to publish a complete `dynamictp.executors[0]` object;
- the `/actuator/dynamictp` endpoint;
- the fact that Quartz's own pool is not managed.

- [ ] **Step 2: Run final verification from a clean command invocation**

Run the full Task 2 verification sequence again and inspect `git status --short`.

Expected: only intentional documentation changes remain after prior commits, and all
verification commands pass.

- [ ] **Step 3: Commit documentation**

```bash
git add .docs/modules/bixi-common/bixi-common-core/README.md
git commit -m "docs: document DynamicTp operation"
```
