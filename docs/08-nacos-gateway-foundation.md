# Nacos 与 Gateway 渐进式拆分基础

## 目标

这一阶段不搬迁现有业务模块。现有应用作为 `school-bus-core` 运行在 8081，新增的
`school-bus-gateway` 运行在 8080。两者都注册到 Nacos，Gateway 使用
`lb://school-bus-core` 根据服务名发现并转发实例。

```text
Vue :5173 -> Gateway :8080 -> Nacos service discovery -> Core :8081
```

JWT 仍由 Core 的 Spring Security 验证。Gateway 保留 `Authorization`，并删除客户端伪造的
`X-User-Id`、`X-User-Roles` 和 `X-Authenticated-User`，避免把不可信身份头传给下游。

## 版本

- Java 21
- Spring Boot 3.5.16
- Spring Cloud 2025.0.0
- Spring Cloud Alibaba 2025.0.0.0
- Nacos Server 3.0.3

## 启动顺序

### 1. 启动 Nacos

```powershell
docker compose -f cloud/docker-compose.yml up -d
```

Nacos 控制台：`http://localhost:18080/`。Nacos 2.4.0 以后不再内置管理员密码，
第一次启动必须初始化管理员。开发环境使用下面的幂等脚本初始化账号并发布配置：

```powershell
.\scripts\cloud\initialize-nacos.ps1 -AdminPassword nacos
```

脚本不会输出 access token，也不会把密码写入 Git；`nacos` 仅是本地演示密码，生产环境必须通过
环境变量传入强密码并部署在可信内网。

容器名为 `school-bus-nacos-3`，避免与旧版 Nacos 1.x 开发容器冲突。

脚本在 Nacos 的 `DEFAULT_GROUP` 中发布三个 YAML 配置：

- Data ID `school-bus-core.yml`，内容来自 `cloud/nacos-config/school-bus-core.yml`
- Data ID `school-bus-gateway.yml`，内容来自 `cloud/nacos-config/school-bus-gateway.yml`
- Data ID `school-bus-transport-query.yml`，内容来自 `cloud/nacos-config/school-bus-transport-query.yml`

下一阶段只读拆分见 `docs/09-transport-query-strangler.md`：Gateway 将
`GET /api/v1/trips` 与 `GET /api/v1/trips/{tripNumber}/seats` 精确路由到
`school-bus-transport-query`，其余 `/api/**` 仍走 core。

示例配置可以不发布；因为应用使用 `optional:nacos:`，本地默认值仍可启动。发布后可通过
Actuator `info.cloud.config-source=nacos` 验证配置确实来自 Nacos。

### 2. 启动现有业务服务

仍需先启动 MySQL、Redis 和 RabbitMQ，然后运行：

```powershell
$env:SPRING_PROFILES_ACTIVE='local,cloud'
$env:NACOS_CONFIG_ENABLED='true'
$env:NACOS_DISCOVERY_ENABLED='true'
mvn spring-boot:run
```

Core 监听 `http://localhost:8081`，在 Nacos 中注册名为 `school-bus-core`。

### 3. 启动 Gateway

另开一个终端：

```powershell
cd cloud/gateway-service
mvn spring-boot:run
```

Gateway 监听 `http://localhost:8080`，在 Nacos 中注册名为 `school-bus-gateway`。

### 4. 验收

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/api/v1/trips
```

第二个请求的完整链路必须是 Gateway 根据 Nacos 中的 `school-bus-core` 实例转发到 8081，
而不是写死 `http://localhost:8081`。

## 为什么先这样拆

先引入独立网关与注册中心，可以验证服务注册、发现、负载均衡和统一入口，同时不破坏已经
通过测试的 IAM、Transport、Booking 和 Payment 事务边界。下一阶段再按照业务边界逐个迁移，
而不是一次性把单体拆散。

## 面试表述

> 我先把模块化单体作为 core 服务注册到 Nacos，再新增基于 WebFlux 的 Spring Cloud Gateway。
> Gateway 使用 `lb://school-bus-core` 做服务发现和客户端负载均衡，前端仍使用 8080 统一入口。
> Nacos 同时承载外部化配置，2025.x 使用 `spring.config.import`，没有继续采用旧的
> `bootstrap.yml`。这一步属于绞杀者式渐进拆分：先建立流量入口和服务治理基础，再迁移业务边界。

## 当前边界

- 目前只有一个 Core 实例，已具备负载均衡能力但尚未证明多实例分流。
- Gateway 尚未迁移 JWT 校验；Core 仍是认证责任方。
- Docker Compose 的 Nacos 是单机开发环境，不是生产集群。
- 业务模块还没有变成独立数据库、独立进程或独立部署单元。
