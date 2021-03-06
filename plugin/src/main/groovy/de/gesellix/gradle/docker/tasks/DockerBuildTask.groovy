package de.gesellix.gradle.docker.tasks

import de.gesellix.docker.client.builder.BuildContextBuilder
import org.gradle.api.Task
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

class DockerBuildTask extends DockerTask {

    def buildContextDirectory

    @Input
    @Optional
    def imageName

    @Input
    @Optional
    InputStream buildContext

    @InputDirectory
    @Optional
    File getBuildContextDirectory() {
        buildContextDirectory ? project.file(this.buildContextDirectory) : null
    }

    @Input
    @Optional
    def buildParams

    @Input
    @Optional
    def enableBuildLog = false

    def tarOfBuildcontextTask
    File targetFile

    def imageId

    DockerBuildTask() {
        description = "Build an image from a Dockerfile"
        group = "Docker"

//    addValidator(new TaskValidator() {
//      @Override
//      void validate(TaskInternal task, Collection<String> messages) {
//        if (getBuildContextDirectory() && getBuildContext()) {
//          messages.add("Please provide only one of buildContext and buildContextDirectory")
//        }
//        if (!getBuildContextDirectory() && !getBuildContext()) {
//          messages.add("Please provide either buildContext or buildContextDirectory")
//        }
//      }
//    })
    }

    @Override
    Task configure(Closure closure) {
        def configureResult = super.configure(closure)
        if (getBuildContextDirectory()) {
            configureTarBuildContextTask()
            configureResult.getDependsOn().each { parentTaskDependency ->
                if (tarOfBuildcontextTask != parentTaskDependency) {
                    tarOfBuildcontextTask.mustRunAfter parentTaskDependency
                }
            }
        }
        return configureResult
    }

    private def configureTarBuildContextTask() {
        if (tarOfBuildcontextTask == null) {
            targetFile = new File(getTemporaryDir(), "buildContext_${getNormalizedImageName()}.tar.gz")
            tarOfBuildcontextTask = project.task([group: getGroup()], "tarBuildcontextFor${name.capitalize()}")
            tarOfBuildcontextTask.doFirst {
                (targetFile as File).parentFile.mkdirs()
            }
            tarOfBuildcontextTask.doLast {
                BuildContextBuilder.archiveTarFilesRecursively(getBuildContextDirectory(), targetFile)
            }
            tarOfBuildcontextTask.outputs.file(targetFile.absolutePath)
            tarOfBuildcontextTask.outputs.upToDateWhen { false }
            dependsOn tarOfBuildcontextTask
        }
    }

    @TaskAction
    def build() {
        logger.info "docker build"

        if (getBuildContextDirectory()) {
            // only one of buildContext and buildContextDirectory shall be provided
            assert !getBuildContext()

            assert tarOfBuildcontextTask
            logger.info "temporary buildContext: ${targetFile}"
            buildContext = new FileInputStream(targetFile as File)
        }

        // at this point we need the buildContext
        assert getBuildContext()

        // Add tag to build params
        def buildParams = getBuildParams() ?: [rm: true]
        if (getImageName()) {
            buildParams.putIfAbsent("rm", true)
            if (buildParams.t) {
                logger.warn "Overriding build parameter \"t\" with imageName as both were given"
            }
            buildParams.t = getImageName() as String
        }

        // TODO this one needs some beautification
        if (getEnableBuildLog()) {
            imageId = getDockerClient().buildWithLogs(getBuildContext(), buildParams).imageId
        } else {
            imageId = getDockerClient().build(getBuildContext(), buildParams)
        }

        return imageId
    }

    def getNormalizedImageName() {
        if (!getImageName()) {
            return UUID.randomUUID().toString()
        }
        return getImageName().replaceAll("\\W", "_")
    }
}
