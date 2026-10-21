// EchoTimer 根构建脚本。
// 插件版本集中声明在此处，子模块只 apply、不重复写版本号。
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
