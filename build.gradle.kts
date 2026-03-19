plugins {
    alias(libs.plugins.fabric.loom)
    
}

base {
    archivesName = properties["archives_base_name"] as String
    version = libs.versions.mod.version.get()
    group = properties["maven_group"] as String
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    // 添加 Maven Central 和 Fabric 以防万一，虽然通常 libs.versions.toml 会处理，但为了保险
    mavenCentral()
}

loom {
    // 强制开启旧版 Mixin 映射处理器
    mixin.useLegacyMixinAp.set(true)

    mixin {
        // 确保名字和你的 mixins.json 里写的一模一样
        defaultRefmapName.set("silent-aura.refmap.json")
    }
}

dependencies {
    // Fabric
    minecraft(libs.minecraft)
    mappings(variantOf(libs.yarn) { classifier("v2") })
    modImplementation(libs.fabric.loader)

    // Meteor
    modImplementation(libs.meteor.client)

    // 【核心修改】 Baritone 依赖
    // 删除了原来的 files("libs/...")
    // 改用 meteordevelopment 的完整版 Baritone。
    // 这里使用 1.11.1-SNAPSHOT，这是 Meteor 官方维护的 Baritone 分支版本
    modCompileOnly("meteordevelopment:baritone:1.21.4-SNAPSHOT")
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to libs.versions.minecraft.get()
        )

        inputs.properties(propertyMap)

        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    // 保持为你要求的 Java 17
    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        // 保持为你要求的 Java 17
        options.release.set(17) 
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
}