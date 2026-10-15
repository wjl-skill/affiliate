# Vue 管理控制台生产化指南

## 目标

`platform-frontend` 是 Nuxt 3 管理控制台。页面数据必须来自当前租户的后端 API，接口异常时显示错误状态和重试入口，不展示示例业务记录，也不以本地状态代替持久化结果。

## 运行配置

通过环境变量配置 API 地址：

```bash
NUXT_PUBLIC_API_BASE_URL=https://api.example.com
```

`composables/useApi.ts` 会为每次请求注入：

- `X-Tenant-ID`：当前租户标识，保存在浏览器 `localStorage` 的 `affiliate.currentTenant`；
- `Authorization: Bearer ...`：来自 `affiliate.accessToken` 的登录令牌；
- `Accept: application/json` 与 JSON 请求体的 `Content-Type`。

HTTP 错误会统一转换为包含状态码和请求 ID 的异常。列表页将数据清空并显示错误，用户可使用“重试”再次请求。

## 管理端点

| 页面 | 查询接口 | 写接口 |
| --- | --- | --- |
| 总览 | `GET /api/v1/affiliate/dashboard/overview` | — |
| Offer | `GET /api/v1/affiliate/offers` | `POST /api/v1/affiliate/offers` |
| SmartLink | `GET /api/v1/affiliate/smartlinks` | `POST /api/v1/affiliate/smartlinks` |
| 渠道客 | `GET /api/v1/affiliate/partners` | `POST /api/v1/affiliate/partners` |
| 转化审核 | `GET /api/v1/affiliate/conversions` | `POST .../{id}/approve`、`POST .../{id}/reject` |
| 财务发票 | `GET /api/v1/affiliate/invoices` | `POST /invoices/generate`、`POST /invoices/{id}/pay` |
| Sub-ID | `GET /api/v1/affiliate/analytics/subid` | — |
| 反作弊 | `GET /api/v1/affiliate/antifraud/stats` | 黑名单 `POST/DELETE` |
| 批量打款 | `GET /api/v1/billing/fx/rates`、`GET /payouts/batch/current` | 批次打款与 Tipalti/Payoneer 导出 |
| Cohort | `GET /api/v1/reports/cohort` | — |
| 系统配置 | `/api/v1/system/users`、`roles`、`menus`、`permissions`、`s3`、`domains` | 对应资源的 POST、状态、授权、删除接口 |

## 写操作约定

页面只有在 API 返回成功后才关闭弹窗、刷新列表和显示成功提示。失败时保留表单内容并显示后端错误信息。审批、打款、DNS 验证、设置默认项和黑名单变更都必须重新读取后端状态，避免浏览器状态与数据库不一致。

## 报表约定

Sub-ID 和 Cohort 页面在日期范围变更时重新查询；空结果显示为空表，不填充示例记录。CSV 导出使用后端批次导出接口，导出失败必须提示错误。

## 发布前检查

```bash
rtk npm run build --prefix platform-frontend
rtk git diff --check
rtk rg -n -F -e "setTimeout" -e "Math.random" -e "本地暂存" platform-frontend/pages
```

