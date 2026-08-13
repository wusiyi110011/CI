package com.wsy.ci.llm

/**
 * 任务级路由器：Default/Api 走 API，Local 只走本地，Off/非法值返回可识别错误。
 * 任何本地失败均原样返回，不会静默 fallback 到 API。
 */
class LlmRouter(
    private val settings: LlmRouteProvider,
    private val api: LlmGateway,
    private val local: LlmGateway,
) : LlmGateway {
    override suspend fun isAvailable(task: LlmTaskType): Boolean = when (val route = settings.routeSelection(task)) {
        LlmRoute.Default,
        is LlmRoute.Api -> api.isAvailable(task)
        LlmRoute.Local -> local.isAvailable(task)
        LlmRoute.Off,
        is LlmRoute.Invalid -> false
    }

    override suspend fun complete(
        task: LlmTaskType,
        systemPrompt: String,
        userPrompt: String,
    ): LlmResult = when (val route = settings.routeSelection(task)) {
        LlmRoute.Default,
        is LlmRoute.Api -> api.complete(task, systemPrompt, userPrompt)
        LlmRoute.Local -> local.complete(task, systemPrompt, userPrompt)
        LlmRoute.Off -> failure(LlmErrorCode.ROUTE_OFF, "「${task.label}」已关闭模型调用")
        is LlmRoute.Invalid -> failure(
            LlmErrorCode.ROUTE_INVALID,
            "「${task.label}」的模型路由无效：${route.raw}",
        )
    }

    override suspend fun completeImage(task: LlmTaskType, request: LlmImageRequest): LlmResult =
        when (val route = settings.routeSelection(task)) {
            LlmRoute.Default,
            is LlmRoute.Api -> api.completeImage(task, request)
            LlmRoute.Local -> local.completeImage(task, request)
            LlmRoute.Off -> failure(LlmErrorCode.ROUTE_OFF, "「${task.label}」已关闭模型调用")
            is LlmRoute.Invalid -> failure(
                LlmErrorCode.ROUTE_INVALID,
                "「${task.label}」的模型路由无效：${route.raw}",
            )
        }

    private fun failure(code: LlmErrorCode, message: String): LlmResult.Failure =
        LlmResult.Failure(message, LlmError(code, message, LlmErrorSource.ROUTER))
}
