# 第三方软件与模型说明

本项目的本地 AI 功能包含或下载以下第三方组件。此文件不改变各组件原有许可，完整条款以各上游仓库所附 `LICENSE`、`NOTICE` 为准。

## MNN 3.6.1

- 项目：MNN
- 上游：https://github.com/alibaba/MNN
- 版权归原作者及贡献者所有
- 许可：Apache License 2.0
- 本项目以 `arm64-v8a` 预编译动态库形式分发，并保存 JNI 适配源码与可复现构建脚本。

## Qwen3.5-2B

- 项目：Qwen3.5-2B
- 上游：https://huggingface.co/Qwen/Qwen3.5-2B
- 版权与商标归 Qwen/Alibaba 及相关权利人所有
- 模型页面标示许可：Apache License 2.0

## Qwen3.5-2B-MNN

- 项目：由 `taobao-mnn` / MNN 团队转换的 `Qwen3.5-2B-MNN`
- 上游：https://modelscope.cn/models/MNN/Qwen3.5-2B-MNN
- 固定 revision：`b9ae8c8f3da3fceb4278b558a747286b8a087dbe`
- 模型页面标示许可：Apache License 2.0
- 权重不随 APK 分发，由用户明确操作后下载到应用专属目录。

Apache License 2.0 正文：https://www.apache.org/licenses/LICENSE-2.0
