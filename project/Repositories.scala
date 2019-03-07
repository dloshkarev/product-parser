import sbt._

object Repositories {
  val ruThirdPartyRepo = "nexus ru third party" at "https://nexus.esc-hq.ru/nexus/content/repositories/ru-third-party/"

  def publishRepo(snapshotRepo: MavenRepository, releaseRepo: MavenRepository, version: String): Option[MavenRepository] = {
    if (version.trim.endsWith("SNAPSHOT"))
      Some(snapshotRepo)
    else
      Some(releaseRepo)
  }
}
