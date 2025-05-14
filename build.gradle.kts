plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-jdk14:2.0.16")
    implementation("org.slf4j:jcl-over-slf4j:2.0.16")
    implementation("org.slf4j:log4j-over-slf4j:2.0.16")
    implementation("com.github.zhkl0228:impersonator-okhttp:1.0.8")
}

tasks {
    compileJava {
        options.release = 11
    }
    jar {
        manifest {
            attributes["Main-Class"] = "org.Main"
        }
        val dependencies = configurations
            .runtimeClasspath
            .get()
            .map(::zipTree)
        from(dependencies)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        exclude("META-INF/BC1024KE.RSA", "META-INF/BC1024KE.SF", "META-INF/BC1024KE.DSA")
        exclude("META-INF/BC2048KE.RSA", "META-INF/BC2048KE.SF", "META-INF/BC2048KE.DSA")
        exclude("META-INF/*.RSA", "META-INF/*.DSA", "META-INF/*.SF")
    }
}
