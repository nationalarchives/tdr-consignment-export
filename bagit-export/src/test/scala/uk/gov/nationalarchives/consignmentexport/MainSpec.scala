package uk.gov.nationalarchives.consignmentexport

import cats.effect.unsafe.implicits.global
import com.github.tomakehurst.wiremock.stubbing.{ServeEvent, StubMapping}
import org.scalatest.Assertion
import uk.gov.nationalarchives.consignmentexport.ConsignmentStatus.{StatusType, StatusValue}

import java.io.File
import java.util.UUID
import scala.io.Source.fromResource
import scala.jdk.CollectionConverters._
import scala.sys.process._
import scala.util.Try

class MainSpec extends ExternalServiceSpec {

  private val taskTokenValue = "taskToken1234"
  val standardInfo: ConsignmentTypeInfo = ConsignmentTypeInfo("standard", "", "test-output-bucket", "publish_success_request_body")
  val overrideInfo: ConsignmentTypeInfo = ConsignmentTypeInfo("standard override", "override_consignment_type_", "test-output-bucket", "override_publish_success_request_body")
  val judgmentInfo: ConsignmentTypeInfo = ConsignmentTypeInfo("judgment", "judgment_", "test-output-bucket-judgment", "publish_judgment_success_request_body")
  private val consignmentTypes: List[ConsignmentTypeInfo] = List(standardInfo, overrideInfo, judgmentInfo)

  "the export job" should s"delete the directories after export is completed" in {
    setUpValidExternalServices("get_consignment_for_export_empty_folders.json")

    val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
    val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

    putFile(s"$consignmentId/$fileId")

    Try(Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync())

    val directory: List[String] = new File(scratchDirectory).list.toList

    directory should have size 0
  }

  consignmentTypes.foreach {
    consignmentType =>
      "the export job" should s"export the correct tar and checksum file to the correct s3 bucket for a '${consignmentType.name}' consignment type" in {
        setUpValidExternalServices(s"get_${consignmentType.nameForFile}consignment_for_export.json")

        val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
        val consignmentRef = "consignmentReference-1234"
        val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

        putFile(s"$consignmentId/$fileId")

        Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()
        checkStepFunctionSuccessCalled(consignmentType.expectedJsonPath)
        val objects = outputBucketObjects()

        objects.size should equal(2)
        objects.head should equal(s"$consignmentRef.tar.gz.sha256")
        objects.last should equal(s"$consignmentRef.tar.gz")
      }

      "the export job" should s"export a valid tar and checksum file for a '$consignmentType' consignment type" in {
        setUpValidExternalServices(s"get_${consignmentType.nameForFile}consignment_for_export.json")

        val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
        val consignmentRef = "consignmentReference-1234"
        val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

        putFile(s"$consignmentId/$fileId")

        Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()

        checkStepFunctionSuccessCalled(consignmentType.expectedJsonPath)

        val downloadDirectory = s"$scratchDirectory/download"
        new File(s"$downloadDirectory").mkdirs()
        getObject(s"$consignmentRef.tar.gz", s"$downloadDirectory/result.tar.gz")
        getObject(s"$consignmentRef.tar.gz.sha256", s"$downloadDirectory/result.tar.gz.sha256")

        val exitCode = Seq("sh", "-c", s"tar -tf $downloadDirectory/result.tar.gz > /dev/null").!
        exitCode should equal(0)
      }

      "the export job" should s"update the export data and update the consignment status as 'Completed' in the api for a '$consignmentType' consignment type" in {
        setUpValidExternalServices(s"get_${consignmentType.nameForFile}consignment_for_export.json")

        val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
        val consignmentRef = "consignmentReference-1234"
        val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

        putFile(s"$consignmentId/$fileId")
        Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()

        checkStepFunctionSuccessCalled(consignmentType.expectedJsonPath)

        val events = wiremockGraphqlServer.getAllServeEvents.asScala
        val exportDataEvent: Option[ServeEvent] = events.find(p => p.getRequest.getBodyAsString.contains("mutation updateExportData"))

        exportDataEvent.isDefined should be(true)

        exportDataEvent.get.getRequest.getBodyAsString.contains(s""""consignmentId":"$consignmentId"""") should be(true)
        exportDataEvent.get.getRequest.getBodyAsString.contains(s""""exportLocation":"s3://${consignmentType.outputBucket}/$consignmentRef.tar.gz"""") should be(true)
        exportDataEvent.get.getRequest.getBodyAsString.contains(s""""exportVersion":"${BuildInfo.version}"""") should be(true)

        val updateConsignmentEvent: Option[ServeEvent] = events.find(p => p.getRequest.getBodyAsString.contains("mutation updateConsignmentStatus"))
        updateConsignmentEvent.isDefined should be(true)
        updateConsignmentEvent.get.getRequest.getBodyAsString should include(s""""consignmentId":"$consignmentId","statusType":"${StatusType.export}","statusValue":"${StatusValue.completed}"""")
      }

      "the export job" should s"throw an error and call step function if the api returns no files for a '$consignmentType' consignment type" in {
        setUpInvalidExternalServices(graphQlGetConsignmentMetadataNoFiles(s"get_${consignmentType.nameForFile}consignment_no_files.json"))

        val consignmentId = "069d225e-b0e6-4425-8f8b-c2f6f3263221"

        val ex = intercept[Exception] {
          Main.run(List("export", "--consignmentId", consignmentId, "--taskToken", taskTokenValue)).unsafeRunSync()
        }

        checkStepFunctionFailureCalled("publish_failure_no_files_for_consignment_request_body")
        ex.getMessage should equal(s"Consignment API returned no files for consignment $consignmentId")
      }

      "the export job" should s"throw an error and call the step function if the FFID metadata is incomplete for a '$consignmentType' consignment type" in {
        setUpInvalidExternalServices(graphQlGetConsignmentIncompleteMetadata(s"get_${consignmentType.nameForFile}consignment_incomplete_metadata.json"))

        val consignmentId = UUID.fromString("0e634655-1563-4705-be99-abb437f971e0")
        val fileId = UUID.fromString("7b19b272-d4d1-4d77-bf25-511dc6489d12")

        putFile(s"$consignmentId/$fileId")

        val ex = intercept[Exception] {
          Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()
        }

        checkStepFunctionFailureCalled("publish_failure_incomplete_file_properties_request_body")
        ex.getMessage should equal(s"FFID metadata is missing for file id $fileId")
      }

      "the export job" should s"throw an error and call the step function if the ffid metadata is missing for a '$consignmentType' consignment type" in {
        graphqlGetCustomMetadata()
        setUpInvalidExternalServices(graphQlGetConsignmentMissingFfidMetadata(s"get_${consignmentType.nameForFile}consignment_missing_ffid_metadata.json"))

        val consignmentId = UUID.fromString("2bb446f2-eb15-4b83-9c69-53b559232d84")
        val fileId = UUID.fromString("3381a880-4e9a-4663-b4c6-97dc4018835e")

        putFile(s"$consignmentId/$fileId")

        val ex = intercept[Exception] {
          Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()
        }

        checkStepFunctionFailureCalled("publish_failure_missing_ffid_metadata_request_body")
        ex.getMessage should equal(s"FFID metadata is missing for file id $fileId")
      }

      "the export job" should s"throw an error and call the step function if the antivirus metadata is missing for '$consignmentType' consignment type" in {
        setUpInvalidExternalServices(graphQlGetConsignmentMissingAntivirusMetadata(s"get_${consignmentType.nameForFile}consignment_missing_antivirus_metadata.json"))

        val consignmentId = UUID.fromString("fbb543d0-7690-4d58-837c-464d431713fc")
        val fileId = UUID.fromString("5e271e33-ae7e-4471-9f89-005c5d15c5a1")

        putFile(s"$consignmentId/$fileId")

        val ex = intercept[Exception] {
          Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()
        }

        checkStepFunctionFailureCalled("publish_failure_missing_antivirus_metadata_request_body")
        ex.getMessage should equal(s"Antivirus metadata is missing for file id $fileId")
      }

      "the export job" should s"throw an error and call the step function if no valid Keycloak user found for a '$consignmentType' consignment type" in {
        graphqlGetCustomMetadata()
        graphQlGetConsignmentMetadata(s"get_${consignmentType.nameForFile}consignment_for_export.json")
        stepFunctionPublish

        val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
        val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

        putFile(s"$consignmentId/$fileId")

        val ex = intercept[Exception] {
          Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()
        }

        checkStepFunctionFailureCalled("publish_failure_no_valid_user_request_body")
        ex.getMessage should equal(s"No valid user found $keycloakUserId: HTTP 404 Not Found")
      }

      "the export job" should s"throw an error and call the step function if an incomplete Keycloak user details found for a '$consignmentType' consignment type" in {
        graphqlGetCustomMetadata()
        graphQlGetConsignmentMetadata(s"get_${consignmentType.nameForFile}consignment_for_export.json")
        keycloakGetIncompleteUser
        stepFunctionPublish

        val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
        val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

        putFile(s"$consignmentId/$fileId")

        val ex = intercept[Exception] {
          Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()
        }

        checkStepFunctionFailureCalled("publish_failure_incomplete_user_request_body")
        ex.getMessage should equal(s"Incomplete details for user $keycloakUserId")
      }

      "the export job" should s"throw an error and call the step function if there are checksum mismatches for a '$consignmentType' consignment type" in {
        setUpInvalidExternalServices(graphQlGetIncorrectCheckSumConsignmentMetadata(s"get_${consignmentType.nameForFile}consignment_for_export_different_checksum.json"))

        val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
        val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

        putFile(s"$consignmentId/$fileId")

        val ex = intercept[Exception] {
          Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()
        }

        checkStepFunctionFailureCalled("publish_failure_checksum_mismatch_request_body")
        ex.getMessage should equal(s"Checksum mismatch for file(s): $fileId")
      }

      "the export job" should s"call the step function heartbeat endpoint for a '$consignmentType' consignment type" in {
        setUpValidExternalServices(s"get_${consignmentType.nameForFile}consignment_for_export.json")

        val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
        val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

        putFile(s"$consignmentId/$fileId")

        Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()

        wiremockSfnServer.getAllServeEvents.asScala
          .count(ev =>
            ev.getRequest.getHeader("X-Amz-Target") == s"AWSStepFunctions.SendTaskHeartbeat"
          ) should equal(1)
      }
  }

  "the export job" should s"throw an error and call the step function if no consignment metadata found" in {
    graphqlGetConsignmentMissingMetadata
    graphqlGetCustomMetadata()
    keycloakGetUser
    stepFunctionPublish

    val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
    putFile(s"$consignmentId/7b19b272-d4d1-4d77-bf25-511dc6489d12")

    val ex = intercept[Exception] {
      Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()
    }

    checkStepFunctionFailureCalled("publish_failure_no_consignment_metadata_request_body")
    ex.getMessage should equal(s"No consignment metadata found for consignment $consignmentId")
  }

  private def setUpValidExternalServices(jsonResponse: String) = {
    graphQlGetConsignmentMetadata(jsonResponse)
    graphqlGetCustomMetadata()
    graphqlUpdateConsignmentStatus
    keycloakGetUser
    stepFunctionPublish
  }

  private def setUpInvalidExternalServices(invalidStub: => StubMapping) = {
    graphqlGetCustomMetadata()
    invalidStub
    keycloakGetUser
    stepFunctionPublish
  }

  private def checkStepFunctionSuccessCalled(expectedJsonPath: String): Assertion =
    checkStepFunctionPublishCalled(expectedJsonPath, "SendTaskSuccess")

  private def checkStepFunctionFailureCalled(expectedJsonRequestFilePath: String) =
    checkStepFunctionPublishCalled(expectedJsonRequestFilePath, "SendTaskFailure")

  private def checkStepFunctionPublishCalled(expectedJsonRequestFilePath: String, method: String) = {
    wiremockSfnServer.getAllServeEvents.asScala
      .count(ev => ev.getRequest.getHeader("X-Amz-Target") == s"AWSStepFunctions.$method") should equal(1)
    val expectedRequestBody: String = fromResource(s"json/$expectedJsonRequestFilePath.json").mkString.replaceAll("\n", "")
    val eventRequestBody = wiremockSfnServer.getAllServeEvents.get(0).getRequest.getBodyAsString
    eventRequestBody should equal(expectedRequestBody)
  }
}

case class ConsignmentTypeInfo(name: String, nameForFile: String, outputBucket: String, expectedJsonPath: String)
