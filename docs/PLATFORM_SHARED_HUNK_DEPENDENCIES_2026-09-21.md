# 公共重叠片段、已有成果承接与编译依赖说明

日期：2026-09-21。基线：**r11＋F3收尾＋b4平台已验收交付基线**；非正式发布候选版本、未批准上线。

本清单只读提取当前 `git diff --no-ext-diff --no-textconv --unified=0` 相对HEAD `9c12f6f893105632c4de24ad8bb4f6f43092f84e` 的坐标，配合工作区字节SHA-256和diff文本SHA-256定位片段。新文件没有HEAD差异，明确标为整份新增。**行号不是持久身份；文件指纹漂移后必须重新核对，不能盲目套用旧坐标。** 没有暂存试拆、生成补丁文件或改变行尾。

对应 [docs/PLATFORM_DELIVERY_MASTER_INDEX_2026-09-21.md](<D:/ERP_WMS/BC warehouse/2G/docs/PLATFORM_DELIVERY_MASTER_INDEX_2026-09-21.md>) 和 [docs/PLATFORM_PROPOSED_COMMIT_WHITELIST_2026-09-21.md](<D:/ERP_WMS/BC warehouse/2G/docs/PLATFORM_PROPOSED_COMMIT_WHITELIST_2026-09-21.md>)。用户已确认所列提交边界；此前逐片段复核后H类40文件通过，H23曾因E1暂缓；本轮用户已授权原样承接E1/E2，H23状态更新为可提交（仍待实际提交批准）。以下状态均不构成暂存/提交授权，也不代表审查了其他任务的未展示分支。它们包含已验收安全依赖，不能因本任务不开展ERP开发就直接删掉或遗漏。

## 1. 跨任务核准单元

| 核准单元 | 内容 | 决定/责任边界 |
| --- | --- | --- |
| C1 公共认证 | JWT、claims、过滤器、权限入口、续用与交接 | 平台提出安全片段；ERP认证归属任务核对其已有修复；不做分支合并 |
| C2 租户页面安全接入 | app/AuthProvider、状态边界、缓存分区及测试 | 仅G1必需联动；非租户登录体验或业务页面开发 |
| C3 公共事务/任务 | CompanyScope、RLS事务管理、ActiveCompanyTaskRunner、Tenant映射 | 与V4_85～88及统一状态规则绑定，不单独裁剪 |
| C4 Shopify/Webhook | 可靠接收、去重/业务事务、连接/凭据/缓存及测试 | 平台安全与既有ERP集成共同核准；不改完整业务核对范围 |
| C5 关闭门禁 | 清理Executor/Service/Scheduler、普通activate入口 | 保留延期入口关闭和双人恢复约束，不授权清理 |
| C6 已有ERP片段 | TenantProvisioningService的saveAndFlush修复 | 用户已批准平台原样承接为已有依赖；不计为平台新增贡献，来源可核实范围见§5 |
| C7 公共构建/交接 | pom输出目录、CODEX_HANDOFF运维登记 | 独立审查必要片段，不修改依赖/公共默认行为 |
| C8 地址测试 | 全文件未跟踪，已有16项测试＋平台第23行兼容修复 | 用户已另行授权整文件原样承接进C1；不全部认领为平台贡献，保持F3原快照完整 |

## 2. 41个公共文件的精确片段

### H01 · [docs/CODEX_HANDOFF.md](<D:/ERP_WMS/BC warehouse/2G/docs/CODEX_HANDOFF.md>)

- 建议归属组：G4；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`fbaf228f2726dcc9dd1e2da1bba1ecf9c117c236cebdffc47e676eb59c5e8423`。
- 相对HEAD的零上下文diff SHA-256：`ab5bf4ab0406defd813486a65c5dbf350e9ab04f572b3c6b574ae62c3a1ee736`。

片段说明：第5～6行新增PROD-OPS-01统一运维门禁登记；只是一条共享交接说明，不代表平台已完成生产运维。

依赖/协同说明：已确认范围内仅审查这条统一运维登记；不据此认领运维任务成果，本轮不更新原交接文档。

```diff
@@ -4,0 +5,2 @@
```

### H02 · [frontend/src/app.tsx](<D:/ERP_WMS/BC warehouse/2G/frontend/src/app.tsx>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`cc2138dab82f9cef1133d0012e90c4df80846f6e0e89043e22528b1864ec5658`。
- 相对HEAD的零上下文diff SHA-256：`a95165b0ff8fbb5aa4b2070e2eaf901ef754c7c74f247c006764431162733c32`。

片段说明：导入TenantAccessBoundary；ProtectedRoute包裹业务入口；AuthProvider的onSessionScopeChange清空query缓存。

依赖/协同说明：与TenantAccessBoundary、sessionCacheScope及AuthProvider共同核准；这是暂停/身份切换安全依赖，不是接手ERP页面开发。

```diff
@@ -10,0 +11 @@ import { AuthProvider, useAuth } from './auth/AuthProvider';
@@ -123 +124 @@ function ProtectedRoute(): JSX.Element {
@@ -297 +298 @@ export function App(): JSX.Element {
```

### H03 · [frontend/src/auth/AuthProvider.tsx](<D:/ERP_WMS/BC warehouse/2G/frontend/src/auth/AuthProvider.tsx>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`e0373f0035f9289a783b16a7a2dbe934342e753b77b465b99fcfa179bece1ac7`。
- 相对HEAD的零上下文diff SHA-256：`0ef33d7ecec0a5dee87856859da3547ccc451bdefbf9f67475b3633258112679`。

片段说明：引入sessionCacheScope、可选onSessionScopeChange和replaceSession；登录、交接、角色切换、刷新、退出及storage事件走该函数。保留已有“不让未登录标签自动进入ERP”的逻辑和租户原时限。

依赖/协同说明：租户认证归属任务核对与其最新刷新/角色切换/交接修复是否重叠，不整体覆盖、不把平台30分钟规则套给ERP。

```diff
@@ -41,0 +42 @@ import { createAuthSession, mergeRoleSwitchSession } from './session';
@@ -52,0 +54,4 @@ interface AuthContextValue {
@@ -92 +97 @@ function releaseRefreshLease(owner: string): void {
@@ -104,0 +110,8 @@ export function AuthProvider({ children }: PropsWithChildren): JSX.Element {
@@ -108,2 +121,2 @@ export function AuthProvider({ children }: PropsWithChildren): JSX.Element {
@@ -115 +128 @@ export function AuthProvider({ children }: PropsWithChildren): JSX.Element {
@@ -117 +130 @@ export function AuthProvider({ children }: PropsWithChildren): JSX.Element {
@@ -123 +136 @@ export function AuthProvider({ children }: PropsWithChildren): JSX.Element {
@@ -125 +138 @@ export function AuthProvider({ children }: PropsWithChildren): JSX.Element {
@@ -139,2 +152,2 @@ export function AuthProvider({ children }: PropsWithChildren): JSX.Element {
@@ -188 +201 @@ export function AuthProvider({ children }: PropsWithChildren): JSX.Element {
@@ -195 +208 @@ export function AuthProvider({ children }: PropsWithChildren): JSX.Element {
@@ -224 +237 @@ export function AuthProvider({ children }: PropsWithChildren): JSX.Element {
@@ -234 +247 @@ export function AuthProvider({ children }: PropsWithChildren): JSX.Element {
@@ -240 +253 @@ export function AuthProvider({ children }: PropsWithChildren): JSX.Element {
```

### H04 · [pom.xml](<D:/ERP_WMS/BC warehouse/2G/pom.xml>)

- 建议归属组：G4；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`bd369b7547102dd780fce6c7f872f8854e5f6a6d78b589487408633fac5b54a4`。
- 相对HEAD的零上下文diff SHA-256：`d6c60a95e817726f392d5439c2679944a2e3edd04a20e0ddbadd784599af5ed8`。

片段说明：新增wms.build.directory属性（默认仍为原target）并接到build.directory；没有依赖版本改动。

依赖/协同说明：已按本次确认范围核对此输出目录适配；不是专用POM报告目录修复的替代，也不允许改公共依赖。

```diff
@@ -29,0 +30,2 @@
@@ -191,0 +194 @@
```

### H05 · [src/main/java/com/wms/system/controller/AuthController.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/controller/AuthController.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`130a6b5d723125a2a55a9d3e3cb96aa1029340c74256c2095e6ec1f2b7765723`。
- 相对HEAD的零上下文diff SHA-256：`ee4046d455a7808552b2112e74b028e637f91b54e6ae745e0ba576bbaab61387`。

片段说明：在既有令牌续用分支新增用户enabled/deleted/securityVersion/username复核，失败AUTH_SESSION_INVALIDATED。

依赖/协同说明：G1与租户认证既有规则交点；保留既有刷新/角色切换校验，不能整段重写。

```diff
@@ -572,0 +573,5 @@ public class AuthController {
```

### H06 · [src/main/java/com/wms/system/controller/ShopifyWebhookController.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/controller/ShopifyWebhookController.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`904f55959e7f7c873514206d42978867e8de0d9f55f65435beb033284e26d065`。
- 相对HEAD的零上下文diff SHA-256：`0af47e9213a82dfb2a38a11d1c18a66518e244dfa05c9d7a4f344339f3b7c600`。

片段说明：有界读取正文、超限413；可靠保存才能200；容量429、身份冲突409、存储/处理不可用503。

依赖/协同说明：与Inbox可靠暂存和Service枚举原子承接，不能仅提交Controller或把超限恢复为成功ACK。

```diff
@@ -14,2 +14,2 @@ import org.springframework.web.bind.annotation.*;
@@ -31,0 +32 @@ public class ShopifyWebhookController {
@@ -39,2 +40,7 @@ public class ShopifyWebhookController {
@@ -49,0 +56,3 @@ public class ShopifyWebhookController {
```

### H07 · [src/main/java/com/wms/system/entity/ChannelRawEvent.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/entity/ChannelRawEvent.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`7bc945c6b79efd9103caeeeb6086d6fca5bbd6b67c0d152018d9c8802e3225df`。
- 相对HEAD的零上下文diff SHA-256：`a54684b0662746ff6ec0eb22e64d6afddcbf5c043cd1bd04aba3e948e1f4d841`。

片段说明：新增sourceInboxId，把原始事件与专用可信暂存收据关联。

依赖/协同说明：依赖V4_86及ChannelRawEventService写入/复用逻辑；不是创建另一个通用任务中心。

```diff
@@ -98,0 +99,3 @@ public class ChannelRawEvent extends BaseEntity {
```

### H08 · [src/main/java/com/wms/system/entity/IntegrationConfig.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/entity/IntegrationConfig.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`4bb5445c70005946d1bd469314033e5c91cdd28ec12a890a9f8e05200b89ab3f`。
- 相对HEAD的零上下文diff SHA-256：`a5286d328f8305819d530faab6052f820f8be5ff3abae8c03df2c673d80e5606`。

片段说明：apiKey/clientSecret/accessToken添加凭据转换器、ToString排除和JsonIgnore；相关列映射改TEXT。

依赖/协同说明：同TenantCredentialConverter/Crypto、V4_86及连接安全服务协调；部署密钥另批，不改真实凭据。

```diff
@@ -49,0 +50,2 @@ public class IntegrationConfig extends BaseEntity {
@@ -62 +64 @@ public class IntegrationConfig extends BaseEntity {
@@ -64 +66,3 @@ public class IntegrationConfig extends BaseEntity {
@@ -71 +75,3 @@ public class IntegrationConfig extends BaseEntity {
```

### H09 · [src/main/java/com/wms/system/integration/ShopifyApiClient.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/integration/ShopifyApiClient.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`b71cd127712515c506aa5613fffb735b6c17e05bc930621852bf2e87256f7ea3`。
- 相对HEAD的零上下文diff SHA-256：`963aff476cb26d9471eeff0f6c56ad4a760af2252ad0b8b12a261ffd752683eb`。

片段说明：注入连接安全服务；外部调用前后复核业务许可，迟到返回不能越过暂停边界。

依赖/协同说明：与ShopifyApiClientTest及TenantConnectionSafetyService一起保留，ERP集成任务核准公共片段。

```diff
@@ -49,0 +50 @@ public class ShopifyApiClient {
@@ -165 +166,4 @@ public class ShopifyApiClient {
```

### H10 · [src/main/java/com/wms/system/integration/ShopifyTokenProvider.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/integration/ShopifyTokenProvider.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`491342aab1d32cd395664130271fc180fe64de453526cbd330b51089a7895bbc`。
- 相对HEAD的零上下文diff SHA-256：`61f442a532987a48784a3dea6e4182c1bf9fd44ff62be8d8fb8ab102ceac6a1a`。

片段说明：注入连接安全服务；缓存按租户会话版本分区/失效，申请令牌前后复核，invalidate同时清理epoch。

依赖/协同说明：不得删掉恢复后旧缓存不能复活的约束；不扩大为完整集成中心。

```diff
@@ -48,0 +49 @@ public class ShopifyTokenProvider {
@@ -50,0 +52 @@ public class ShopifyTokenProvider {
@@ -56,0 +59,4 @@ public class ShopifyTokenProvider {
@@ -82,0 +89,2 @@ public class ShopifyTokenProvider {
@@ -92,0 +101 @@ public class ShopifyTokenProvider {
```

### H11 · [src/main/java/com/wms/system/repository/ChannelRawEventRepository.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/repository/ChannelRawEventRepository.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`47b0fee91123393814d69154f813cdad8d8598649ae54e49558250a5bd402cfb`。
- 相对HEAD的零上下文diff SHA-256：`d0660a697bdd046a2764b7ba00e91c8bf8305d046c31f42a9458ac9ec29dd931`。

片段说明：新增按webhookEventId读取，以及按已完成状态集合判断重复。

依赖/协同说明：依赖RawEventService的事务及去重语义，不用“已留底”冒充“业务已提交”。

```diff
@@ -39,0 +40,2 @@ public interface ChannelRawEventRepository extends JpaRepository<ChannelRawEvent
```

### H12 · [src/main/java/com/wms/system/security/DynamicAuthorizationManager.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/security/DynamicAuthorizationManager.java>)

- 建议归属组：G1＋G3；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`c9c67db7374eee5e8f8bb4f40ee3bd213aebdc1e48a79498c7c50fb8cdda8c11`。
- 相对HEAD的零上下文diff SHA-256：`887b210e1c472c28c331a5baf069674c53eca7ee07d53513d10aa10ba1cc4bb8`。

片段说明：新文件行73：仅放行分组cookie入口/api/platform/auth/browser-session（G3，不认证用户）；行98及114：company-access/status加入已认证/改密允许入口（G1）。

依赖/协同说明：按两个功能片段核准，同时保留ERP任务已有多路径匹配/权限修复。不能把cookie入口变成认证绕过。

```diff
@@ -72,0 +73 @@ public class DynamicAuthorizationManager implements AuthorizationManager<Request
@@ -96,0 +98 @@ public class DynamicAuthorizationManager implements AuthorizationManager<Request
@@ -111,0 +114 @@ public class DynamicAuthorizationManager implements AuthorizationManager<Request
```

### H13 · [src/main/java/com/wms/system/security/JwtAuthenticationFilter.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/security/JwtAuthenticationFilter.java>)

- 建议归属组：G1＋G2；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`9e373a2c0d5478ace67f1b6fba4df251001948e967918601aab0da898c05dfe2`。
- 相对HEAD的零上下文diff SHA-256：`0c49b9cf43f7b072a837513a0b3747d4688053981ff279134df28d9d475dd41b`。

片段说明：行46、90～98、164～173、224～225：平台后端会话检查、异常拒绝、数据存储不可用503（G2）；行128～129：复用租户准入已验证claims（G1）。

依赖/协同说明：G1与G2共用文件；平台后端不可用必须fail-closed，且不得丢弃租户原认证逻辑。

```diff
@@ -45,0 +46 @@ public class JwtAuthenticationFilter extends OncePerRequestFilter {
@@ -88,0 +90,9 @@ public class JwtAuthenticationFilter extends OncePerRequestFilter {
@@ -118 +128,2 @@ public class JwtAuthenticationFilter extends OncePerRequestFilter {
@@ -152,0 +164,8 @@ public class JwtAuthenticationFilter extends OncePerRequestFilter {
@@ -153,0 +173 @@ public class JwtAuthenticationFilter extends OncePerRequestFilter {
@@ -203,0 +224,2 @@ public class JwtAuthenticationFilter extends OncePerRequestFilter {
```

### H14 · [src/main/java/com/wms/system/security/JwtProperties.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/security/JwtProperties.java>)

- 建议归属组：G2；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`553ba39e912d8d6cb234568068fb91ad3f97c7c3bd377080666e22486015273e`。
- 相对HEAD的零上下文diff SHA-256：`489e580bf0fb22f3c1844bb06f7c716886973e9e54b347fd97ce149501f59559`。

片段说明：仅对platformExpiration增加最大43,200,000毫秒约束。

依赖/协同说明：这是平台12小时绝对上限，不修改租户expiration/续期规则。

```diff
@@ -31,0 +32 @@ public class JwtProperties {
```

### H15 · [src/main/java/com/wms/system/security/JwtUtil.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/security/JwtUtil.java>)

- 建议归属组：G1＋G2；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`d97a0ab667eeae17397ca74dd9505b4603f74e4b6eb98ccf3b1c51b2831f4219`。
- 相对HEAD的零上下文diff SHA-256：`40807f484e61fe7087d999888a3d4c15fba98b30e15cd83a27e0f3d5b06c89af`。

片段说明：行34、75～88、102～103、188～189：tenant_session_version签发/解析及上下文校验（G1）；行140～160、165、207～210：平台sessionId及绝对起止时间签发/解析（G2）。

依赖/协同说明：两个功能片段互有共同API依赖；仅签名不等于后端会话登记，不能为了提交拆分删掉任一方检查。

```diff
@@ -33,0 +34 @@ public class JwtUtil {
@@ -73,0 +75,10 @@ public class JwtUtil {
@@ -76,0 +88 @@ public class JwtUtil {
@@ -90 +102,2 @@ public class JwtUtil {
@@ -126,0 +140,9 @@ public class JwtUtil {
@@ -135 +157,2 @@ public class JwtUtil {
@@ -137 +160 @@ public class JwtUtil {
@@ -142 +165 @@ public class JwtUtil {
@@ -165 +188,2 @@ public class JwtUtil {
@@ -183 +207,4 @@ public class JwtUtil {
```

### H16 · [src/main/java/com/wms/system/security/PlatformJwtClaims.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/security/PlatformJwtClaims.java>)

- 建议归属组：G2；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`8a2f6cbe28d7feed010dc406dc84867e20e0975e800d234f3cd4fb956874f752`。
- 相对HEAD的零上下文diff SHA-256：`251e80320904e9b80a504f06480682d72d18c33505852636aa4e85815e4ee1c7`。

片段说明：增加平台sessionId、issuedAt、expiresAt及兼容构造器。

依赖/协同说明：文件位于公共security包，但片段需求归G2；与平台JWT和SessionService一起核准。

```diff
@@ -9 +9,4 @@ public record PlatformJwtClaims(
@@ -10,0 +14,3 @@ public record PlatformJwtClaims(
```

### H17 · [src/main/java/com/wms/system/security/SecurityConfig.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/security/SecurityConfig.java>)

- 建议归属组：G1＋G2；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`5ecfc1fc92daf998a5b0f3893300a7cde522a19ac6235ee7fab7a825f3705455`。
- 相对HEAD的零上下文diff SHA-256：`2da7317180703ff9187f7979c84f7df94e4f9d80c04fbe129b7bf6b0adea4864`。

片段说明：增加租户准入Filter，并确保resolution→admission→JWT的链路（G1）；更新Bearer与平台持久会话说明（G2）。

依赖/协同说明：需与TenantFilterRegistrationConfig共同保留，防止重复容器注册或顺序变化。

```diff
@@ -63,0 +64 @@ public class SecurityConfig {
@@ -102 +103 @@ public class SecurityConfig {
@@ -121 +122,2 @@ public class SecurityConfig {
```

### H18 · [src/main/java/com/wms/system/security/TenantJwtClaims.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/security/TenantJwtClaims.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`6d2f0ddb33c81748d7d1cd8eb2eb209689799d49e35c7183e6e88d029f6f0f27`。
- 相对HEAD的零上下文diff SHA-256：`f67caeba5e703f85ebac300784b48e771c2c350585deedc974d9274071aa2dcf`。

片段说明：增加tenantSessionVersion并保留兼容构造；旧版本1不能穿越首次暂停。

依赖/协同说明：G1必要租户会话失效依赖，不修改租户ERP日常登录体验。

```diff
@@ -14 +14,2 @@ public record TenantJwtClaims(
@@ -25 +26,6 @@ public record TenantJwtClaims(
```

### H19 · [src/main/java/com/wms/system/service/ChannelRawEventService.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/service/ChannelRawEventService.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`42351b2fb3c5892f5a7a77070e9f0ba7eb5ffb1ee81eee184e76365e5325fcd3`。
- 相对HEAD的零上下文diff SHA-256：`e56d4c8acc71d49cc74cbc84c4b50b0fc012284478ab545df8ea3516bb5d2879`。

片段说明：收据关联sourceInboxId；状态写入由REQUIRES_NEW改为REQUIRED；按确切事件复用并按终态判断去重。

依赖/协同说明：这是公共事务语义变化，必须由平台/集成归属共同核准；不允许裁剪成“处理标记独立提交而业务回滚”。

```diff
@@ -20,3 +20,3 @@ import java.util.Map;
@@ -81,0 +82 @@ public class ChannelRawEventService {
@@ -88 +89 @@ public class ChannelRawEventService {
@@ -90 +91 @@ public class ChannelRawEventService {
@@ -101 +102 @@ public class ChannelRawEventService {
@@ -103 +104 @@ public class ChannelRawEventService {
@@ -115 +116 @@ public class ChannelRawEventService {
@@ -117 +118 @@ public class ChannelRawEventService {
@@ -128 +129 @@ public class ChannelRawEventService {
@@ -130 +131 @@ public class ChannelRawEventService {
@@ -145,0 +147,11 @@ public class ChannelRawEventService {
@@ -153,0 +166 @@ public class ChannelRawEventService {
@@ -164 +177,2 @@ public class ChannelRawEventService {
```

### H20 · [src/main/java/com/wms/system/service/ShopifyWebhookService.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/service/ShopifyWebhookService.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`3077c981a70b1535138564897dca9f33009c0b6c715c4bfbfb11211a2919d63d`。
- 相对HEAD的零上下文diff SHA-256：`948f58ad77bcf77e391873b64a9cb70c96361e8c71d0298ae1bda7338e8ad73e`。

片段说明：可信接收改走Inbox；handleForCompany仅允许持久化worker在BUSINESS准入上下文调用；处理失败保持可重试，日志/错误码脱敏，题材长度有界。

依赖/协同说明：与Controller、Inbox worker、RawEvent事务及现有ERP订单处理衔接，不能把失败连接/处理显示为全部恢复成功。修改/取消/退款最终核对范围仍待决定。

```diff
@@ -61,0 +62 @@ public class ShopifyWebhookService {
@@ -63 +64,8 @@ public class ShopifyWebhookService {
@@ -90,18 +98,8 @@ public class ShopifyWebhookService {
@@ -110 +108,2 @@ public class ShopifyWebhookService {
@@ -114,0 +114,3 @@ public class ShopifyWebhookService {
@@ -143 +145 @@ public class ShopifyWebhookService {
@@ -150,3 +152,4 @@ public class ShopifyWebhookService {
@@ -182 +185,4 @@ public class ShopifyWebhookService {
@@ -200 +206 @@ public class ShopifyWebhookService {
```

### H21 · [src/main/java/com/wms/system/signup/model/SessionHandoffCode.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/signup/model/SessionHandoffCode.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`fd73e9d7bd5e7eeb67d03206180b6c5c163eb6051aba9c38ae56c62a0aaff308`。
- 相对HEAD的零上下文diff SHA-256：`b901806c63cdd2051f9022d4764d95cb18312263ba15f66aeca9d6c61cc71619`。

片段说明：新增tenant_session_version非空字段及初值。

依赖/协同说明：依赖V4_85和交接服务的版本绑定，不能单独提交模型而遗漏迁移。

```diff
@@ -30,0 +31,4 @@ public class SessionHandoffCode {
```

### H22 · [src/main/java/com/wms/system/signup/service/SessionHandoffService.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/signup/service/SessionHandoffService.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`ab2f8e5903e26065a5a45c6e966ac239136c44822f88668264703ddca66f5199`。
- 相对HEAD的零上下文diff SHA-256：`11c72656dc952e3c0301360ef31fb81f4c76b501b0479d6293c1b8947626ca8f`。

片段说明：消费交接码前打开租户BUSINESS准入，再用TransactionTemplate进入事务；pin交接码绑定的版本。

依赖/协同说明：涉及既有事务边界；与ERP登录交接修复逐片段核准，不能把安全暂停后的旧交接码重新启用。

```diff
@@ -37,0 +38 @@ public class SessionHandoffService {
@@ -39 +39,0 @@ public class SessionHandoffService {
@@ -40,0 +41,8 @@ public class SessionHandoffService {
@@ -51,0 +60,2 @@ public class SessionHandoffService {
```

### H23 · [src/main/java/com/wms/system/signup/service/TenantProvisioningService.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/signup/service/TenantProvisioningService.java>)

- 建议归属组：G1；状态：**可提交〔E1授权原样承接〕；不认领已有ERP修复贡献，未授权实际提交**。
- 工作区SHA-256：`69e9de7a590bffbd2003860aecd9e25d75ab107b6a57812f98a664d53cde4d8b`。
- 相对HEAD的零上下文diff SHA-256：`943109076346a90e33756a9d54e323e353b3c5ff2b871f751c7b87aaead33130`。

片段说明：G1片段：行92～96开户PROVISIONING准入；行232～235校验ACTIVE并锁定租户；行243/252绑定交接会话版本。**单独ERP片段：行167～168，由save改为saveAndFlush，注释明确保留ERP A2复合外键修复。**

依赖/协同说明：用户本轮明确授权为保全已验收版本原样承接ERP A2片段；与r11冻结字节一致，不删除/重写、不做ERP分支合并。具体原提交/原作者未核实，不推测填补；已知证据见§5。其余G1安全片段已审查。

```diff
@@ -92,2 +92,5 @@ public class TenantProvisioningService {
@@ -164 +167,2 @@ public class TenantProvisioningService {
@@ -227,0 +232,4 @@ public class TenantProvisioningService {
@@ -234,0 +243 @@ public class TenantProvisioningService {
@@ -242,0 +252 @@ public class TenantProvisioningService {
```

### H24 · [src/main/java/com/wms/system/tenant/config/TenantFilterRegistrationConfig.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/tenant/config/TenantFilterRegistrationConfig.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`557131864fbf40cba5e744671258bfab8c7b319da3b69d26d9dac4c1ee66966a`。
- 相对HEAD的零上下文diff SHA-256：`717d3352d0377a0ae5ee6d5f73c4b62d012f453408d4f8c9e1c8e55ad6b9cbad`。

片段说明：禁用准入Filter的普通容器自动注册，改由Spring Security链唯一管理。

依赖/协同说明：与SecurityConfig顺序一同核准，不放宽安全检查。

```diff
@@ -11,0 +12,8 @@ public class TenantFilterRegistrationConfig {
```

### H25 · [src/main/java/com/wms/system/tenant/context/CompanyScope.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/tenant/context/CompanyScope.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`ebd5fa947831d804b07b00159b4160b381aca757a193bb2fe7832f60ce743444`。
- 相对HEAD的零上下文diff SHA-256：`f453088cc8d255578c5b78c92be28ece74d7d22b835f23338506de42b9e1126e`。

片段说明：结合TenantExecutionScope校验Host/主体租户一致，受控场景可从准入上下文取得tenantId。

依赖/协同说明：平台受控读取/导出及业务上下文共用；不能只保留fallback而丢掉一致性检查。

```diff
@@ -38,0 +39,7 @@ public final class CompanyScope {
```

### H26 · [src/main/java/com/wms/system/tenant/model/ChannelWebhookRoute.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/tenant/model/ChannelWebhookRoute.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`0eb84fdef2f8bce00a6401284af26c49ac0441973f50e2796e6a20baafbff919`。
- 相对HEAD的零上下文diff SHA-256：`645654c567186595dd862fda1df8c2e05751d6fbfdc6d53d658e5acb4556dd2b`。

片段说明：签名密钥映射TEXT、加密转换及JsonIgnore。

依赖/协同说明：与IntegrationConfig及V4_86同批依赖；不读取/迁移真实凭据。

```diff
@@ -34 +34,3 @@ public class ChannelWebhookRoute {
```

### H27 · [src/main/java/com/wms/system/tenant/model/Tenant.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/tenant/model/Tenant.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`44b372334178de09bd756003308e03a132b9654ab6ae678ba982f22037f13e5b`。
- 相对HEAD的零上下文diff SHA-256：`d0fe3c2f5e6207a5ffc5ad7ba935d2d11454706d19dba305e8b9502bfda1ab73`。

片段说明：新增sessionVersion、暂停类型/时间/原因及安全事件ID映射。

依赖/协同说明：依赖V4_85及安全状态服务，租户目录/UI、后台检查需同版本。

```diff
@@ -45,0 +46,16 @@ public class Tenant {
```

### H28 · [src/main/java/com/wms/system/tenant/persistence/CompanyRlsJpaTransactionManager.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/tenant/persistence/CompanyRlsJpaTransactionManager.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`cffce068c5cbfd8613cea4d365d880f8490d327e61df8a6fcb557ff812d5fa13`。
- 相对HEAD的零上下文diff SHA-256：`39542e16f0d2ab8e0b5f757b1bd57a97c740196cbd265188e1dee46a76444907`。

片段说明：事务准入调用bcwms_check_tenant_activity，传租户/版本/用途/写入性质并pin版本；将已知失效/暂停错误映射为401/423。

依赖/协同说明：核心公共事务/RLS路径，本轮仅按已确认的G1片段审查，并非整体接管ERP事务层；依赖迁移函数，不得裁剪。

```diff
@@ -6,0 +7,2 @@ import com.wms.system.tenant.context.TenantContextHolder;
@@ -57,0 +60,14 @@ public final class CompanyRlsJpaTransactionManager implements PlatformTransactio
@@ -64,0 +81,4 @@ public final class CompanyRlsJpaTransactionManager implements PlatformTransactio
```

### H29 · [src/main/java/com/wms/system/tenant/service/TenantDataPurgeExecutor.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/tenant/service/TenantDataPurgeExecutor.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`90b9ef6b61f9a3ac648e1c5d0b2edf5f055c16a2a387c85602014236e0153523`。
- 相对HEAD的零上下文diff SHA-256：`d6d016c68515a7263633249de902daa7e6bec19906163934d557fe264e0c7542`。

片段说明：execute入口新增requirePurgeReleased。

依赖/协同说明：延期清理的关闭门禁，不是本批实现真实清理；不能因“清理延期”删除这段拦截。

```diff
@@ -20,0 +21 @@ public class TenantDataPurgeExecutor {
```

### H30 · [src/main/java/com/wms/system/tenant/service/TenantDataPurgeScheduler.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/tenant/service/TenantDataPurgeScheduler.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`69e1624bd2f2c1d608f4fb3dedfa7744ca1969afeaa32c2c3cbdd5e6fab05a78`。
- 相对HEAD的零上下文diff SHA-256：`b022d09badfcf5ac716046d077fb439816a78872e5d889c5f5048ff9d6abaeb7`。

片段说明：增加purge-enabled=true条件注册。

依赖/协同说明：继续保持默认不开放；与其他硬门禁一起核准，本轮不改配置。

```diff
@@ -12,0 +13 @@ import java.time.OffsetDateTime;
```

### H31 · [src/main/java/com/wms/system/tenant/service/TenantDataPurgeService.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/tenant/service/TenantDataPurgeService.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`1e48a2d127d6fc093a298dac4ad007cd07636de2c444180b2e58fb50c66417a6`。
- 相对HEAD的零上下文diff SHA-256：`d661b87b7b7ab3b95c291876aabf5c0c0605fb2fbde1dbdc9905d6a78038b43f`。

片段说明：purgeDueCompany及prepare两处新增requirePurgeReleased。

依赖/协同说明：防网页/后台绕过延期门禁，不授权数据删除。

```diff
@@ -30,0 +31 @@ public class TenantDataPurgeService {
@@ -47,0 +49 @@ public class TenantDataPurgeService {
```

### H32 · [src/main/java/com/wms/system/tenant/service/TenantLifecycleService.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/tenant/service/TenantLifecycleService.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`39b91210eb2003df745c55602e841762666358bcec767853d20bb97a6ea60360`。
- 相对HEAD的零上下文diff SHA-256：`57a82a7e1783fd711a14252b033d73f17db44df2b278d560659a5a931cd74393`。

片段说明：activate只允许PROVISIONING，不再接受SUSPENDED。

依赖/协同说明：安全恢复必须走已确认双人流程，不能用普通activate绕过。

```diff
@@ -27 +27 @@ public class TenantLifecycleService {
```

### H33 · [src/main/java/com/wms/system/tenant/task/ActiveCompanyTaskRunner.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/tenant/task/ActiveCompanyTaskRunner.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`d4544c59c037e7c67103d02f480dbe0ab4226cc2f8c92d6a35687724e5641fdd`。
- 相对HEAD的零上下文diff SHA-256：`7c7639dfbe19d0a382847d2bfce03fcc5d8550ecd29d6d6c9f19d3109e6588c1`。

片段说明：每个租户任务在BUSINESS用途和当前sessionVersion的TenantExecutionScope内运行。

依赖/协同说明：公共后台任务最小安全接入；审批已提交不因会话退出被取消，但执行仍检查租户状态。

```diff
@@ -32 +32,2 @@ public class ActiveCompanyTaskRunner {
```

### H34 · [src/test/java/com/wms/system/integration/ShopifyApiClientTest.java](<D:/ERP_WMS/BC warehouse/2G/src/test/java/com/wms/system/integration/ShopifyApiClientTest.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`8441d9e2fed89490f7c4011b4f0d0dc411f7aa0a0ab672b32efb1c2a29a7b938`。
- 相对HEAD的零上下文diff SHA-256：`fcc90cf73a5855a2ca07b5c1353e01a0b5518073595f37421b6865a05ee11416`。

片段说明：注入安全服务mock，并新增迟到第三方响应在暂停后不能继续的断言。

依赖/协同说明：只协调平台安全兼容断言，不接手ERP集成新增功能。

```diff
@@ -35,0 +36 @@ class ShopifyApiClientTest {
@@ -88,0 +90,7 @@ class ShopifyApiClientTest {
```

### H35 · [src/test/java/com/wms/system/integration/ShopifyTokenProviderTest.java](<D:/ERP_WMS/BC warehouse/2G/src/test/java/com/wms/system/integration/ShopifyTokenProviderTest.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`1e1c484416c529bd64146c8bf54dff21cb8b25bf0b612d13aa34e38edf96b536`。
- 相对HEAD的零上下文diff SHA-256：`a9851d337154fb5a357f173bbba77b49adbe99b02e24eddcf7d221dfaa7a9270`。

片段说明：增加连接安全服务mock以适配既有构造依赖。

依赖/协同说明：保留原令牌缓存断言，不能借此重写产品行为。

```diff
@@ -31,0 +32 @@ class ShopifyTokenProviderTest {
```

### H36 · [src/test/java/com/wms/system/security/JwtAuthenticationFilterTest.java](<D:/ERP_WMS/BC warehouse/2G/src/test/java/com/wms/system/security/JwtAuthenticationFilterTest.java>)

- 建议归属组：G1＋G2；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`84f223f103a4ac39616ec35989ca06491d31b239b239b3eb3488c78aea01a89d`。
- 相对HEAD的零上下文diff SHA-256：`b460bf0c15e6b1f97c2b4828414785a91d9dc0fc11268482b5647b17d51b0e88`。

片段说明：适配PlatformSessionService注入，新增会话库不可用503及空闲超时401且控制器不可达的断言。

依赖/协同说明：与G2过滤器片段一起核准；不是仅前端倒计时验证。

```diff
@@ -40,0 +41 @@ class JwtAuthenticationFilterTest {
@@ -54 +55 @@ class JwtAuthenticationFilterTest {
@@ -201,0 +203,27 @@ class JwtAuthenticationFilterTest {
```

### H37 · [src/test/java/com/wms/system/service/ShopifyWebhookServiceTest.java](<D:/ERP_WMS/BC warehouse/2G/src/test/java/com/wms/system/service/ShopifyWebhookServiceTest.java>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`0b762984edd67fbabb74c28115d9c2f909b4237136d7aa9a29794679edb7a58a`。
- 相对HEAD的零上下文diff SHA-256：`e5e2117f7e595dfcc6c85ee54161af1399c2e014113669b109ca37932e4b1344`。

片段说明：已有业务分派断言改由受准入保护的持久化worker入口执行；处理异常断言为PROCESSING_FAILED、可重试，接收ACK与处理结果分离。

依赖/协同说明：明确这是历史已批准测试变化；保留取消/修改/退款分派覆盖，不称原断言正文完全没变，不扩大最终恢复核对方案。

```diff
@@ -121 +121 @@ class ShopifyWebhookServiceTest {
@@ -163,0 +164,7 @@ class ShopifyWebhookServiceTest {
@@ -171 +178 @@ class ShopifyWebhookServiceTest {
@@ -184 +191 @@ class ShopifyWebhookServiceTest {
@@ -199 +206 @@ class ShopifyWebhookServiceTest {
@@ -207 +214 @@ class ShopifyWebhookServiceTest {
@@ -213 +220 @@ class ShopifyWebhookServiceTest {
@@ -215,2 +222,2 @@ class ShopifyWebhookServiceTest {
@@ -240 +247 @@ class ShopifyWebhookServiceTest {
@@ -258 +265 @@ class ShopifyWebhookServiceTest {
@@ -271 +278 @@ class ShopifyWebhookServiceTest {
@@ -282 +289 @@ class ShopifyWebhookServiceTest {
@@ -298 +305 @@ class ShopifyWebhookServiceTest {
@@ -313 +320 @@ class ShopifyWebhookServiceTest {
@@ -327 +334 @@ class ShopifyWebhookServiceTest {
```

### H38 · [frontend/src/auth/TenantAccessBoundary.test.tsx](<D:/ERP_WMS/BC warehouse/2G/frontend/src/auth/TenantAccessBoundary.test.tsx>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`b780e605b38b9034700ea1a45b7b043fe7688eca1c625e15fbe30a879701a471`。

片段说明：新文件全体：租户状态边界组件断言。

依赖/协同说明：随最小显示依赖核准，保留断言，不通过排除测试制造构建成功。

新文件：整份新增，不能将“无HEAD diff”理解为无改动。内容身份以上述SHA-256为准。

### H39 · [frontend/src/auth/TenantAccessBoundary.tsx](<D:/ERP_WMS/BC warehouse/2G/frontend/src/auth/TenantAccessBoundary.tsx>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`8c1c82bfa755e48742cecf8587e5d99c8def9d5be6314de52df973701f45fc96`。

片段说明：新文件全体：调用company-access/status，按ACTIVE与businessAvailable阻断业务页面，失败关闭并允许重查/退出。

依赖/协同说明：属于G1在租户界面的必要安全显示依赖；不能扩展ERP页面修复，亦不能从已验收F3输入剔除。

新文件：整份新增，不能将“无HEAD diff”理解为无改动。内容身份以上述SHA-256为准。

### H40 · [frontend/src/auth/sessionCacheScope.test.ts](<D:/ERP_WMS/BC warehouse/2G/frontend/src/auth/sessionCacheScope.test.ts>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`0587ad25da9507a56f449875423dca98f4952c6ebf5aee563e7729b7acb9e876`。

片段说明：新文件全体：缓存分区身份与角色/版本变化断言。

依赖/协同说明：随公共前端安全接入核准，不改变ERP登录/续期时限。

新文件：整份新增，不能将“无HEAD diff”理解为无改动。内容身份以上述SHA-256为准。

### H41 · [frontend/src/auth/sessionCacheScope.ts](<D:/ERP_WMS/BC warehouse/2G/frontend/src/auth/sessionCacheScope.ts>)

- 建议归属组：G1；状态：**可提交〔内容审查〕；仅所列片段，完整批次仍待批准**。
- 工作区SHA-256：`71d7edee1d588bd33d7304dece7b64e0de67d15aed5fe3a0e00bae25a52d94e5`。

片段说明：新文件全体：公司/会话版本/安全版本/用户名/角色缓存分区提示；不验证JWT、不授予访问。

依赖/协同说明：依赖AuthProvider与app清缓存接入；服务端鉴权继续权威。

新文件：整份新增，不能将“无HEAD diff”理解为无改动。内容身份以上述SHA-256为准。

## 3. 平台内部跨组重叠（不是ERP文件认领）

下列16个文件在G2/G3或G1/G2/G3之间共用。建议会话与浏览器退出合成原子提交；`api.ts`及`api.test.ts`还同时包含G1读取/导出接口参数。可以跨组注明同一文件，不能重复提交、覆盖或为拆分删掉必要实现。表中“新增全文件”亦须保留依赖，不承诺任何中间提交已经独立编译。

| 文件 | 归属组 | 当前相对HEAD片段或新增范围 |
| --- | --- | --- |
| [frontend/src/platform/PlatformApp.tsx](<D:/ERP_WMS/BC warehouse/2G/frontend/src/platform/PlatformApp.tsx>) | G2＋G3 | `@@ -3 +3 @@`<br>`@@ -17,2 +16,0 @@`<br>`@@ -20 +18 @@`<br>`@@ -22,27 +19,0 @@`<br>`@@ -50,0 +22 @@`<br>`@@ -66,0 +39,25 @@`<br>`@@ -74 +71 @@`<br>`@@ -89,7 +86 @@`<br>`@@ -140,2 +131,7 @@`<br>`@@ -209 +205 @@`<br>`@@ -260 +256 @@`<br>`@@ -480 +476 @@`<br>`@@ -490 +486 @@` |
| [frontend/src/platform/PlatformAuthProvider.tsx](<D:/ERP_WMS/BC warehouse/2G/frontend/src/platform/PlatformAuthProvider.tsx>) | G2＋G3 | `@@ -1,2 +1,4 @@`<br>`@@ -12 +14,2 @@`<br>`@@ -16,0 +20 @@`<br>`@@ -18,0 +23,2 @@`<br>`@@ -23 +29,6 @@`<br>`@@ -33,0 +45,2 @@`<br>`@@ -39,0 +53 @@`<br>`@@ -41,0 +56,4 @@`<br>`@@ -46 +64,2 @@`<br>`@@ -47,0 +67 @@`<br>`@@ -48,0 +69 @@`<br>`@@ -52,5 +73,16 @@`<br>`@@ -59,0 +92,15 @@`<br>`@@ -60,0 +108,6 @@`<br>`@@ -63 +116,52 @@`<br>`@@ -66,2 +170,2 @@` |
| [frontend/src/platform/PlatformInvitationActivationPage.tsx](<D:/ERP_WMS/BC warehouse/2G/frontend/src/platform/PlatformInvitationActivationPage.tsx>) | G2＋G3 | `@@ -72,2 +72,8 @@` |
| [frontend/src/platform/api.test.ts](<D:/ERP_WMS/BC warehouse/2G/frontend/src/platform/api.test.ts>) | G1＋G2＋G3 | `@@ -3,0 +4,7 @@`<br>`@@ -335,0 +343,10 @@`<br>`@@ -417,0 +435,2 @@` |
| [frontend/src/platform/api.ts](<D:/ERP_WMS/BC warehouse/2G/frontend/src/platform/api.ts>) | G1＋G2＋G3 | `@@ -1 +1,2 @@`<br>`@@ -110 +111 @@`<br>`@@ -116 +117 @@`<br>`@@ -131,0 +133,2 @@`<br>`@@ -192 +195,21 @@`<br>`@@ -268,0 +292 @@`<br>`@@ -270 +294,2 @@`<br>`@@ -271,0 +297,2 @@`<br>`@@ -274 +301 @@`<br>`@@ -307,0 +335,3 @@`<br>`@@ -324 +354 @@`<br>`@@ -575,0 +606 @@`<br>`@@ -578,0 +610 @@`<br>`@@ -601 +633 @@`<br>`@@ -604 +636 @@`<br>`@@ -613 +645 @@`<br>`@@ -615,0 +648 @@` |
| [src/main/java/com/wms/system/platform/dto/PlatformSessionResponse.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/platform/dto/PlatformSessionResponse.java>) | G2＋G3 | `@@ -10,2 +10,8 @@` |
| [src/main/java/com/wms/system/platform/repository/PlatformUserRepository.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/platform/repository/PlatformUserRepository.java>) | G2＋G3 | `@@ -12,0 +13,4 @@` |
| [src/main/java/com/wms/system/platform/service/PlatformMfaService.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/platform/service/PlatformMfaService.java>) | G2＋G3 | `@@ -32 +31,0 @@`<br>`@@ -56,0 +56 @@`<br>`@@ -75 +75 @@`<br>`@@ -90,2 +90,2 @@`<br>`@@ -97 +97 @@`<br>`@@ -113 +113 @@`<br>`@@ -115 +115 @@`<br>`@@ -118 +118 @@`<br>`@@ -125 +125 @@`<br>`@@ -130 +130 @@`<br>`@@ -161 +161 @@`<br>`@@ -206 +206 @@`<br>`@@ -308 +308 @@`<br>`@@ -311 +311 @@`<br>`@@ -313 +313,4 @@` |
| [src/test/java/com/wms/system/platform/service/PlatformMfaServiceTest.java](<D:/ERP_WMS/BC warehouse/2G/src/test/java/com/wms/system/platform/service/PlatformMfaServiceTest.java>) | G2＋G3 | `@@ -42,0 +43 @@`<br>`@@ -47,0 +49,2 @@`<br>`@@ -52 +55 @@`<br>`@@ -117 +120,3 @@`<br>`@@ -136 +141 @@` |
| [frontend/src/platform/PlatformAuthProvider.test.tsx](<D:/ERP_WMS/BC warehouse/2G/frontend/src/platform/PlatformAuthProvider.test.tsx>) | G2＋G3 | 新增全文件；SHA见白名单 |
| [frontend/src/platform/PlatformInvitationActivationPage.test.tsx](<D:/ERP_WMS/BC warehouse/2G/frontend/src/platform/PlatformInvitationActivationPage.test.tsx>) | G2＋G3 | 新增全文件；SHA见白名单 |
| [src/main/java/com/wms/system/platform/controller/PlatformSessionController.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/platform/controller/PlatformSessionController.java>) | G2＋G3 | 新增全文件；SHA见白名单 |
| [src/main/java/com/wms/system/platform/service/PlatformSessionService.java](<D:/ERP_WMS/BC warehouse/2G/src/main/java/com/wms/system/platform/service/PlatformSessionService.java>) | G2＋G3 | 新增全文件；SHA见白名单 |
| [src/test/java/com/wms/system/platform/service/PlatformInvitationSessionPostgresTest.java](<D:/ERP_WMS/BC warehouse/2G/src/test/java/com/wms/system/platform/service/PlatformInvitationSessionPostgresTest.java>) | G2＋G3＋G4 | 新增全文件；SHA见白名单 |
| [src/test/java/com/wms/system/platform/service/PlatformSessionPostgresTest.java](<D:/ERP_WMS/BC warehouse/2G/src/test/java/com/wms/system/platform/service/PlatformSessionPostgresTest.java>) | G2＋G3＋G4 | 新增全文件；SHA见白名单 |
| [src/test/java/com/wms/system/platform/service/PlatformSessionServiceTest.java](<D:/ERP_WMS/BC warehouse/2G/src/test/java/com/wms/system/platform/service/PlatformSessionServiceTest.java>) | G2＋G3 | 新增全文件；SHA见白名单 |

G1平台导出本身还有Service、Worker、Job/Repository与暂停理由/审计的纵向依赖，不能只提交页面。平台SessionService与Controller、MFA成功登记、claims、V4_89/V4_90及浏览器绑定须保持同版本。分组不是部署隔离承诺。

## 4. 非提交项与迁移/证据依赖

### 4.1 地址测试

[frontend/src/test/addressLength.audit.test.tsx](<D:/ERP_WMS/BC warehouse/2G/frontend/src/test/addressLength.audit.test.tsx>) 当前整个文件未跟踪。唯一平台授权修改是第23行：

```tsx
fireEvent.click(screen.getByRole('button', { name: /^Save$/ }));
```

这是getByRole查询兼容修复，不是地址业务规则修复。用户本轮明确授权原样承接整个已有文件，包括全部16项用例/断言与这一行修复，列入C1；不把原测试全部认领为平台贡献，不从F3快照删除、不排除正式检查。文件字节与F3一致；原提交/原作者无法核实。授权承接不等于本轮已授权暂存或提交。

### 4.2 迁移依赖与当前未包含内容

V4_85～88为G1；V4_89为G2；V4_90为G3并依赖V4_89。完整94个资源清单、已验8条历史和全部校验和见主索引。无生产迁移授权。

既有交接记录列明ERP分支V4_73～84及：
- `beforeMigrate__assignable_permission_convergence.sql`
- `afterMigrate__assignable_permission_convergence.sql`
- `afterMigrateError__assignable_permission_constraint_recovery.sql`

不在当前平台工作区。其完整候选顺序、回调兼容、生产库历史由另行批准的整合/运维任务核准；本任务不复制、不合并、不改编号。没有用r11空库验收覆盖尚未形成的统一迁移序列。

### 4.3 工具/原始证据依赖：已核对的实际读取链

下表说明现有工具的外部输入，不是新增运行清单。文件名保留r2/r7/s1/b1等历史名称；当前绑定/生成内容才确定批次。**本轮不运行这些入口，提交它们不授权再启动任何历史环境。**

| 现有工具/入口 | 实际依赖及读取关系 | 提交与复现边界 |
| --- | --- | --- |
| `platform-r11-backend-snapshot.mjs`、`platform-r11-plan.mjs`、`platform-r11-database-batch.mjs`、`platform-r11-environment.mjs` | r10输入/构建/失败记录，query-diagnostics-20260919/a2修复证据；r11 snapshot/source、冻结Maven发行包/缓存、build/a1、136项classpath及专用POM | 原始记录、JAR/依赖仓库不在拟提交表；绝对路径和旧批次存在性守卫不支持干净检出直接重跑 |
| `platform-case-catalog.mjs`（并经`platform-r9-ledger.mjs`进入封存入口） | 明确固定 `docs/releases/platform-export-fixture-repair-20260918/a3/input-manifest.json` 和 `verification-results.json` 的哈希，核验已批准参数化用例名称/来源 | 只复用名称而非成绩，但这些历史原件仍是执行依赖，不能只提交解析器就称可独立复现 |
| `platform-frontend-f1-build.mjs`（现绑定F3）、`platform-frontend-f3-closeout.mjs` | F1/F2中断记录、F3源码/原工具/报告/期望清单/dist及安装元数据；r11原产物；`target/platform-validation-tools/pnpm-11.16.0`工具和来源回执 | F3补验有固定报告哈希，不会凭空重建证据；本轮不安装或重建。完整node_modules不入Git |
| `platform-b1-binding.mjs`、`platform-b1-prepare.mjs`、`platform-b1-catalog.mjs` | 当前为b4；读取r11冻结模板/依赖/后端classes、F3/dist、旧b1 input和snapshot以及b1准备/修复、b2、b3的保留证据目录；生成专用Java与Node内容 | 旧入口仅作为依赖，不授权运行旧环境。新准备器也有已运行目录拒绝重用守卫 |
| `platform-b1-batch.mjs`、`platform-browser-runtime-safety.test.mjs` | 直接导入 `./platform-b1-environment.mjs`；该模块不在当前src/test/scripts，由prepare从r11环境模板派生到b4 snapshot | 这两个源文件不是可在源码目录直接运行的独立入口；必须配合已说明的冻结/生成步骤，禁止回退搜索旧目录 |
| 专用目录中的 `PlatformB1Harness.java`、`PlatformB1Contract.java`等5个程序 | prepare通过共享生成函数派生 `PlatformB1Isolation.java`、`PlatformB1DatabasePreparer.java`；运行验收仍有r11/F3历史依赖 | T1源码边界已闭合；本轮用全新Maven产品classes显式编译包装器，不借旧classes，见§4.4 |
| 历史r2～r10的`*-backend-snapshot.mjs`/`*-database-batch.mjs`，s1/s2准备/启停入口，以及各`*-verification.mjs`修复入口 | 各自固定旧批次的target/docs/releases/工具版本/期望哈希，往往有前一轮修复证据依赖 | 保留已核准测试依赖与取证来源，不作为本基线的“一键重跑”命令；现有逐文件白名单界定哪些源码纳入 |
| `PlatformBrowserHarness.java`、其旧Probe/ScopeDatabase/V490Upgrade，`platform-session-browser-server.mjs`、`platform-session-browser-stop.ps1`、`platform-session-closeout-regression.ps1`、`platform-session-continuation-regression.ps1` | 旧会话浏览器验收目录、classes、运行参数/专用private路径 | 保留源码不保留原密码/JWT/MFA等私有材料；未来复现需另批新目标及新合成认证材料，不能恢复已清除凭据 |
| `platform-precommit-closeout.mjs`、`platform-browser-java-sources.test.mjs` | 本轮T1固定a2验证入口；需r11 Maven/冻结缓存、b4源码与旧a1失败证据；源目录/构建目录均独立，拒绝覆盖 | 只证明本轮编译闭包及受影响无数据库检查；不是干净检出的一键完整复现工具，不启动环境 |
| `platform-process-lifecycle.mjs`、身份/查询脚本、报告解析器、`fixtures/PlatformPipeInheritance.cs`等共享工具 | 源码相邻模块、系统PowerShell/.NET/Java/Node；被上述冻结入口调用时还依赖其传入的绝对路径、清单和证据 | 共享模块本身并不证明历史批次可复现；保留相对源码依赖，没有为归档另建框架 |

归档位置/负责人未定，完整冻结副本、构建包、Maven缓存、数据库与原始日志保持原地；不搬移、不删除、不上传。Git只存已核准源码/必要工具/说明与索引。认证材料不进入Git或归档包；不因缺私有材料而还原历史密码。第三方工具版本和系统环境还需未来复现时重新核验。

### 4.4 T1：专用源码边界及生成/编译接入已经验证

此前审查发现：浏览器包装器位于普通 `src/test/java`，依赖只在冻结目录生成的PlatformB1Isolation，公共Maven并不生成该类。该历史发现仍成立，但本轮按用户授权已解决当前交付的编译边界：

1. **仅迁移专用程序，不删除业务测试。** 以下5份Java文件移至 `src/test/tools/browseracceptance/java/com/wms/browseracceptance/`：PlatformB1Harness、PlatformB1Contract、PlatformBackendDiagnostic、PlatformBackendDiagnosticProbe、PlatformJvmEntryProbe。它们没有JUnit测试注解，原文件字节及全部安全守卫/16项契约不变；旧路径原为未跟踪新增文件。既有普通测试不移出检查范围。
2. **实际入口共用生成函数。** [platform-b1-prepare.mjs](<D:/ERP_WMS/BC warehouse/2G/src/test/scripts/platform-b1-prepare.mjs>)用明确的BROWSER_JAVA_SOURCE_DIR读上述源码，并调用 `browserJavaSources(template)`派生两个类。生成算法原样抽出，不增加回退；新完整性测试与真正prepare使用同一函数。两个生成Java源与b4冻结源逐字节一致。
3. **旧工具读取关系同步。** [platform-backend-diagnostic-verification.mjs](<D:/ERP_WMS/BC warehouse/2G/src/test/scripts/platform-backend-diagnostic-verification.mjs>)和[platform-browser-runtime-safety-verification.mjs](<D:/ERP_WMS/BC warehouse/2G/src/test/scripts/platform-browser-runtime-safety-verification.mjs>)仅更新专用Java路径，已检查语法；没有重跑其历史批次，未触及旧封存文件。两项以前为R类引用，本轮因必要路径接入明确纳入C2，指纹见原白名单。
4. **公共Maven无改动。** 沿用固定Maven 3.9.9/Java 17.0.17，实际执行原POM的 `test-compile`。仅用现有输出目录参数指定新目录，没有skip或新增excludes，没有让公共Maven引用历史target。
5. **专用编译完全显式。** 新a2中编译5个原样程序＋2个生成类，classpath为本轮新Maven classes和已核验第三方JAR，不复制旧class。新产生18个class，聚合SHA与b4的 `fc53056f2b864fd44746052b5929649efce24e321e7fef8f5d7587fc84d65760`一致。

新增工具仅为 [platform-precommit-closeout.mjs](<D:/ERP_WMS/BC warehouse/2G/src/test/scripts/platform-precommit-closeout.mjs>) 的本批有界无数据库验证，以及 [platform-browser-java-sources.test.mjs](<D:/ERP_WMS/BC warehouse/2G/src/test/scripts/platform-browser-java-sources.test.mjs>) 的12项针对性检查；没有扩建通用框架。

### 4.5 实际验证、失败保留及版本对应

- 输出根：`target/platform-precommit-closeout-20260921/a2`；普通源码在maven-source、工具冻结源在snapshot、普通编译在maven-build、专用class在classes。证据在 `docs/releases/platform-precommit-closeout-20260921/a2`。
- a1：Maven/专用编译及16项Java契约通过；新增Node契约11/12，Windows路径分隔符比较失败，FAILED原件保留。随后仅修正新契约的路径规范化，并在a2完整重验；没有将a1通过项拼入a2。
- a2：实际Maven测试编译覆盖**206个普通测试Java文件**，编译器inputFiles.lst与冻结清单逐项相符；其中**189个含JUnit声明的文件**指纹保持不变。这是编译范围，不是189项测试执行成绩。
- a2：专用javac、**16项Java契约、154项Node检查、5项语法检查**全部通过。154=新增T1 12＋完整性13＋启动安全25＋脱敏诊断38＋运行安全35＋邀请边界19＋环境契约12；失败/跳过/取消/todo均为0。真实合成JVM检查使用JDK-only入口，没有启动Spring、JDBC或HTTP服务。
- 编译预算分别300000ms，Java契约60000ms，Node套件120000ms，版本/语法各15000ms；冻结前固定、运行中未加时。Maven离线运行，冻结缓存复制到新私有验证输出，不改共享缓存、依赖版本或锁文件。
- 输入ID `8698605682f36c9894b8d1b97c2256b75153df77a2e950f37a39f016fdfeefda`；普通编译清单聚合 `3d9d7e929750b8c763e5f6544fcd63861dd4ecc413d0f338a2320e22fb516286`；专用class聚合见§4.4。精确命令、过程回执与各文件SHA保留在a2/input.json、各command JSON、ordinary-compile-assessment.json、result.json。
- r11的679项产品源码/POM与951项产品编译清单/JAR、F3源码/dist、b4冻结源码/class及原记录都已前后复核未变。因此沿用原验收，不重跑r11/F3/b4；本次编译/契约通过不代替数据库或人工验收。

## 5. 两项已有成果的承接决定与提交边界

| 项目 | 本轮决定 | 能核实的来源与未知项 |
| --- | --- | --- |
| E1 / C6 / H23 | 用户已授权平台原样承接saveAndFlush及其完整文件，进入C1；不认领为新增平台贡献 | 当前/冻结r11 SHA `69e9de7a590bffbd2003860aecd9e25d75ab107b6a57812f98a664d53cde4d8b`；源码ERP A2注释，P3-M1实施记录第18行列为既有未提交修复。本地全部可见Git引用的精确内容历史未找到独立提交；原提交/原作者无法核实 |
| E2 / C8 | 用户已授权原样承接地址测试完整文件及平台第23行修复，进入C1；保留全部16项 | 当前/冻结F3 SHA `1b51209d1b0678505be0436bc92edd775652a5932affa784116c12057c7b024e`；F3收尾报告及清单。文件未跟踪，本地可见Git历史未查得来源提交；原提交/原作者无法核实 |
| T1 | 当前源码布局/生成/编译闭包已实测通过 | 按§4.4～4.5，仅改测试工具；旧产品与验收证据不变 |

两项承接在本轮开始/结束均无漂移；没有ERP分支合并或新ERP功能开发。未知作者信息不补写，也不把源码注释当成独立提交证明。

最终建议仍为两次本地提交，精确文件/哈希在原白名单：**C1 134项完整平台产品、公共依赖和配套测试（含E1/E2原样承接）→ C2 186项其余工具和说明**。必要安全依赖不裁剪；文件总数320，原始证据/构建包/凭据不混入Git。两次提交并非可分别部署；本轮未暂存构造提交中间态，不作已验证中间态的承诺。

## 6. 本轮实际边界与停止点

E1/E2已取得原样承接授权，T1已完成真实无数据库验证；没有待修复的本批失败门禁。等待用户另行按最终320项白名单批准本地提交；如果届时内容/指纹漂移，先报告，不能自动认领其他任务修改。

仅发生5项测试工具源码路径迁移、3项脚本接入修改、2项新增针对性工具/契约以及现有三份说明更新。产品、普通业务测试、数据库约束/迁移、依赖版本/锁文件和公共POM未修改。没有数据库连接、环境启动、临时唤醒、暂存/提交/合并/推送或生产开放。历史a1 FAILED及r11/F3/b4等证据保持原样。

专用工具的历史依赖/受控归档问题仍按§4.3保留；恢复核对最终业务边界、正式配置/域名/密钥/邮件/应急流程、生产兼容及备份恢复/迁移演练仍另批确认。**本基线依然非正式发布候选版本、未批准上线。**
