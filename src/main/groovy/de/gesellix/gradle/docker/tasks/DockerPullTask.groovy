package de.gesellix.gradle.docker.tasks

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DockerPullTask extends AbstractDockerTask {

  private static Logger logger = LoggerFactory.getLogger(DockerPullTask)

  @Input
  @Optional
  def imageName

  @TaskAction
  def pull() {
    logger.info "running pull..."
    dockerClient.pull(imageName)
  }
}
