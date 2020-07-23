package alfred.jenkins

import org.scalatest.Inside
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import scala.util.Success

class AlfredEnvironmentSpec extends AnyFlatSpec with Matchers with Inside {

  it should "Collect the alfred environment variables" in {
    val data = Map(
      AlfredEnvironment.AlfredVersionKey          -> "1.2.3",
      AlfredEnvironment.AlfredVersionBuildKey     -> "+1234ddffsf",
      AlfredEnvironment.AlfredWorkflowBundleIdKey -> "com.elijordan.foo.bar",
      AlfredEnvironment.AlfredWorkflowCacheKey    -> "~/Library/Caches/Alfred/foo.bar/",
      AlfredEnvironment.AlfredWorkflowDataKey     -> "~/Library/Application Support/Alfred/foo.bar",
      AlfredEnvironment.AlfredWorkflowNameKey     -> "This is the workflow name",
      AlfredEnvironment.AlfredWorkflowUidKey      -> "123456789",
      AlfredEnvironment.AlfredDebugKey            -> "1"
    )

    inside(AlfredEnvironment.fromMap(data)) {
      case Success(env) => {
        env.alfredVersion mustBe "1.2.3"
        env.alfredVersionBuild mustBe "+1234ddffsf"
        env.workflowBundleId mustBe "com.elijordan.foo.bar"
        env.workflowCacheDir mustBe "~/Library/Caches/Alfred/foo.bar/"
        env.workflowDataDir mustBe "~/Library/Application Support/Alfred/foo.bar"
        env.workflowName mustBe "This is the workflow name"
        env.workflowUid mustBe "123456789"
        env.debugMode mustBe true
      }

    }
  }
}
