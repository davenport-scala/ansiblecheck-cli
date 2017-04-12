package tech.christopherdavenport.ansiblecheck
import scalanative.native._, stdlib._, stdio._


object AnsibleCheckApp {

  def main(args: Array[String]): Unit = {

    val currentLocation = "/home/davenpcm/Documents/AnsibleProjects/Roles/ansible-role-universal-java"

    val systemdRunOpts = "--privileged --volume=/sys/fs/cgroup:/sys/fs/cgroup:ro"
    val initRunOpts = ""

    val systemDInit = "/lib/systemd/systemd"
    val initInit = "/sbin/init"

    val distribution = "archlinux"
    val distributionVersion = "latest"

    val containerName = s"ansiblecheck$distribution$distributionVersion"

    val commands = docker.sequenceAll(currentLocation, systemdRunOpts, distribution, distributionVersion, systemDInit, containerName)

    val pull = ansiblechecknative.system(docker.pull(distribution, distributionVersion))
    println(s"Pull - Status Code - $pull")
    val run = ansiblechecknative.system(docker.run(currentLocation, systemdRunOpts, distribution, distributionVersion, systemDInit, containerName))
    println(s"Run  - Status Code - $run")
    val syntaxCheck = ansiblechecknative.system(docker.exec(containerName, "test.yml", "--syntax-check"))
    println(s"SyntaxCheck - Status Code - $syntaxCheck")
    val initial = ansiblechecknative.system(docker.exec(containerName, "test.yml", ""))
    println(s"Initial - Status Code - $initial")

    val stop = ansiblechecknative.system(docker.stop(containerName))
    println(s"Stop - Status Code - $stop")
    val rm = ansiblechecknative.system(docker.rm(containerName))
    println(s"Rm   - Status Code - $rm")
  }

}

@extern
object unistd{
  def getcwd(ptr: Ptr[CChar], size: CSize): Ptr[CChar] = extern

}

@extern
object libc {
  def system(command: CString): CInt = extern
}

object ansiblechecknative {
  def system(string: String): Int = libc.system(toCString(string))
  def getcwd(): String = {
    val buffer = stackalloc[CChar](256)

    unistd.getcwd(buffer, sizeof[CChar]).toString
  }
}

object docker {

  def pull(
            distribution: String,
            distributionVersion: String
          ): String =
  s"""docker pull ansiblecheck/ansiblecheck:$distribution-$distributionVersion""".stripMargin

  def run(
             pwd: String,
             runOpts: String,
             distribution: String,
             distributionVersion: String,
             init: String,
             containerID: String
           ): String =
    s"""docker run --detach --name $containerID --volume=$pwd:/etc/ansible/roles/role_under_test:ro $runOpts ansiblecheck/ansiblecheck:$distribution-$distributionVersion $init""".stripMargin

  def stop(containerID: String): String =
    s"""docker stop $containerID"""

  def rm(containerID: String, force: Boolean = false): String = {
    val forceStr = if (force) "--force " else ""
    s"""docker rm $forceStr $containerID"""
  }

  def exec(containerID: String, testFile: String, command: String): String =
    s"""docker exec --tty $containerID env TERM=xterm ansible-playbook /etc/ansible/roles/role_under_test/tests/$testFile $command"""

  def sequenceAll(pwd: String, runOpts: String, distribution: String, distributionVersion: String, init: String, containerID: String): List[String] = {
    List(
      pull(distribution, distributionVersion),
      run(pwd, runOpts, distribution, distributionVersion, init, containerID),
      stop(containerID),
      rm(containerID)
    )
  }


}