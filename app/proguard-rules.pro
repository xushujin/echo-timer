# EchoTimer 的 R8 配置。
# 应用不使用反射、注解处理或序列化框架；Compose 与 AndroidX 自带 consumer rules，
# 因此这里只需要保留崩溃栈需要的源码信息。
-keepattributes SourceFile,LineNumberTable
