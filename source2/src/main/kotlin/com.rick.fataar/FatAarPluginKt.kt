package com.rick.fataar

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Author: 嘿嘿抛物线
 * Date  : 5/16/21
 * Email : easygoingrickking@gmail.com
 * Desc  : 插件
 */
class FatAarPluginKt : Plugin<Project> {

    private var mProject: Project? = null

    override fun apply(project: Project) {
        // 引用第三方的fataar插件
        project.pluginManager.apply("com.kezong.fat-aar")

        mProject = project
        LogUtils.attach(project)

        val duplicateResHandler = DuplicateResHandler(project)
        project.afterEvaluate {
            doAfterEvaluate(duplicateResHandler)
        }
    }

    private fun doAfterEvaluate(duplicateResHandler: DuplicateResHandler?) {
        mProject?.run {
            val variants = (extensions.getByName("android") as LibraryExtension).libraryVariants
            variants?.all { variant ->
                // 创建一个task，处理重复资源
                val dealDuplicateResTask = tasks.register("DealDuplicateResTask" + variant.name) { task ->
                    task.doLast {
                        duplicateResHandler?.deleteDuplicate(variant);
                    }
                }

                // 设置该task执行的时间点
                for (tempTask in tasks) {
                    if (tempTask?.name?.startsWith("explode") == true
                            && tempTask.name.endsWith(variant.name, true)) {
                        dealDuplicateResTask.dependsOn(tempTask.name)
                    }
                }
                val javacTask = variant.javaCompileProvider
                javacTask.dependsOn(dealDuplicateResTask)
            }
        }
    }
}