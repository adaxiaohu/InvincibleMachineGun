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
     // ✅ 添加这个仓库来解决 conditional-mixin 找不到的问题
    maven {
        name = "FallenBreath Maven"
        url = uri("https://maven.fallenbreath.me/releases")
    }
    mavenCentral()
    // ✅ Masa 官方仓库
    maven { url = uri("https://masa.dy.fi/maven") }
    // ✅ 必须加这个！Masa 的库强制依赖 FallenBreath 的条件混淆库
    maven { url = uri("https://maven.fallenbreath.me/releases") }
    
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://www.cursemaven.com") }
    // ... 其他仓库 ...
}

dependencies {
    // Fabric
    minecraft(libs.minecraft)
    mappings(variantOf(libs.yarn) { classifier("v2") })
    modImplementation(libs.fabric.loader)

    // Meteor Client
    modImplementation(libs.meteor.client)

    // 【关键修复】 Baritone 依赖
    // 这行代码让你的项目能找到 BaritoneAPI，从而修复那几十个报错
    // 即使你是在 1.21.11，使用 1.21.4-SNAPSHOT 的 API 进行编译通常也是兼容的
    modCompileOnly("meteordevelopment:baritone:1.21.11-SNAPSHOT")

    modCompileOnly(fileTree("libs") { include("*.jar") })
     

    
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

    // 保持你要求的 Java 21 环境
    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.add("-Xlint:all")
    }
}