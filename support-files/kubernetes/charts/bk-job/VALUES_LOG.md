# chart values 更新日志
## 0.4.0-rc.63
1.增加特性开关相关配置

```shell script
## job-file-gateway文件网关服务配置
job:
  features:
    # 是否开启文件管理特性，容器化环境默认开启
    fileManage: 
      enabled: true
    # 是否兼容ESB API 的 bk_biz_id 参数
    bkBizIdCompatible:
      enabled: true
    # 是否对接GSE2.0。 如果需要对接GSE1.0，设置job.features.gseV2.enabled=false
    gseV2:
      enabled: true
      # 特性启用策略，当enabled=true的时候生效；如果不配置，那么仅判断enabled字段
      strategy:
        # 特性策略ID, 当前支持ResourceScopeToggleStrategy/WeightToggleStrategy（按照业务(集)特性开启策略
        id: ResourceScopeToggleStrategy
        # 特性策略初始化参数，kv结构，具体传入参数根据不同的StrategyId变化
        params: {}
```

2. 新增GSE1.0 控制开关

```
gse:
  # 是否初始化 GSE1.0 Client。如果需要对接GSE1.0(job.features.gseV2.enabled=false), 必须设置gse.enabled=true
  enabled: true
```

3. 新增GSE API Gateway URL

```
# 蓝鲸 GSE API Gateway url。格式:{网关访问地址}/{网关环境}，网关访问地址、网关环境的取值见bk-gse网关API文档。
bkGseApiGatewayUrl: "https://bk-gse.apigw.com/prod"
```

   

## 0.3.0-rc.46

1.增加服务启动顺序控制相关配置

```shell script
## 服务下Pod等待依赖的其他服务Pod完成启动的init任务配置
waitForDependServices:
  image:
    # 镜像拉取仓库根地址
    registry: "hub.bktencent.com"
    # 镜像拉取仓库组织与镜像名称
    repository: "blueking/job-tools-k8s-startup-controller"
    # 镜像标签
    tag: "{{APP_VERSION}}"
    # 镜像拉取策略
    pullPolicy: IfNotPresent
  # 是否开启服务启动顺序控制，不开启则所有服务并行启动，默认不开启
  enabled: false
  # 服务间的依赖关系定义，多个依赖关系用逗号分隔
  # 例如：(A:B,C),(B:D)表示服务A必须在服务B与服务C启动完成后才启动，服务B必须在服务D启动完成后才启动
  # 全量服务名称：job-analysis,job-backup,job-crontab,job-execute,job-file-gateway,job-file-worker-headless,
  #            bk-job-gateway,job-logsvr,job-manage,bk-job-frontend
  # 说明：bk-job-gateway与bk-job-frontend为对其他产品暴露的服务，因此有bk-前缀，job-file-worker-headless为无头服务，因此有-headless后缀
  dependencies: (job-execute:job-manage,job-logsvr),(bk-job-frontend:job-analysis,job-backup,job-crontab,job-execute,job-file-gateway,bk-job-gateway,job-manage)
  # 依赖服务的Pod启动完成后需要拥有的label（label与value中请勿包含英文逗号/括号）
  expectPodLabels:
    # 所有依赖服务都必须拥有的label，多个label间用英文逗号分隔
    # 格式：label1=value1,label2=value2,...
    common: "bk.job.image/tag={{APP_VERSION}}"
    # 为每个依赖服务单独定义的必须拥有的label，多个服务间用英文括号及英文逗号分隔
    # 格式：(job-manage:label1=value1,label2=value2),(job-execute:label3=value3),(...)
    service: ""
  # 日志级别：默认INFO，可选DEBUG/WARN/ERROR
  logLevel: "INFO"
  # 资源要求与限制
  resources:
    limits:
      cpu: 1024m
      memory: 1Gi
    requests:
      cpu: 125m
      memory: 256Mi
```

## 0.3.0-rc.37
1.增加文件网关系统file-worker调度标签相关配置

```shell script
## job-file-gateway文件网关服务配置
fileGatewayConfig:
  # 用于确定调度范围的worker标签
  workerTags:
    # 能够调度的worker标签白名单，逗号分隔
    white: "k8s"
    # 不能调度的worker标签黑名单，逗号分隔
    black: ""
## job-file-worker文件源接入点配置
fileWorkerConfig:
  # 标签，逗号分隔
  tags: "k8s"
```

## 0.3.0-rc.31
1.增加Trace及数据上报至APM相关配置
```shell script
## Trace配置
job:
  trace:
    report:
      # 是否上报Trace数据至监控平台APM应用，默认不上报
      enabled: false
      # 监控平台中目标APM应用的PUSH URL
      pushUrl: ""
      # 监控平台中目标APM应用的SecureKey
      secureKey: ""
      # Trace数据上报比率，取值范围为0~1，根据作业平台与监控平台负载适当调节该比率
      ratio: 0.1
```
2.fileWorker对应的Service端口默认值设置为与pod端口一致，避免混淆
```shell script
## job-file-worker文件源接入点配置
fileWorkerConfig:
  service:
    port: 19810
```

## 0.2.2-rc.1
1.增加权限中心系统ID配置：
```shell script
## 权限中心配置
iam:
  # 作业平台注册到IAM的系统ID
  systemId: bk_job
```

## 0.1.53
1.增加CMDB供应商配置：
```shell script
## 对接蓝鲸CMDB参数配置
cmdb:
  # 供应商，默认为0
  supplierAccount: 0
```
2.增加各模块是否启用开关配置：
```shell script
## job-gateway网关配置
gatewayConfig:
  # 模块是否启用，默认启用
  enabled: true
## job-manage作业管理配置
manageConfig:
  # 模块是否启用，默认启用
  enabled: true
## job-execute执行引擎配置
executeConfig:
  # 模块是否启用，默认启用
  enabled: true
## job-logsvr日志服务配置
logsvrConfig:
  # 模块是否启用，默认启用
  enabled: true
## job-backup备份服务配置
backupConfig:
  # 模块是否启用，默认启用
  enabled: true
## job-analysis统计分析服务配置
analysisConfig:
  # 模块是否启用，默认启用
  enabled: true
## job-file-gateway文件网关服务配置
fileGatewayConfig:
  # 模块是否启用，默认启用
  enabled: true
## job-file-worker文件源接入点配置
fileWorkerConfig:
  # 模块是否启用，默认启用
  enabled: true
## job-frontend前端配置
frontendConfig:
  # 模块是否启用，默认启用
  enabled: true
## migration迁移任务配置
migration:
  # 模块是否启用，默认启用
  enabled: true
```
3.更新微服务默认镜像版本。


## 0.1.52
1.更新微服务默认镜像版本；
2.更新k8s-wait-for默认镜像版本；  
**3.增加登录配置**  
```shell script
## 登录配置
login:
  ## 自定义登录配置
  custom:
    # 是否对接自定义登录地址，默认不开启（使用蓝鲸集成的统一登录）
    enabled: false
    # 页面登录地址
    loginUrl: "http://login.example.com/login/"
    # 获取用户信息的接口地址
    apiUrl: "http://login.example.com/api/"
    # 完成页面登录后前端通过Cookie提交的凭据的Key，也是后台向apiUrl获取用户信息提交的凭据的Key
    tokenName: "bk_token"
```
