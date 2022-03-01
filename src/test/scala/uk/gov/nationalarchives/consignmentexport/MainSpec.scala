package uk.gov.nationalarchives.consignmentexport

import java.io.File
import java.nio.file.Files
import java.util.UUID
import com.github.tomakehurst.wiremock.stubbing.{ServeEvent, StubMapping}
import org.apache.commons.codec.digest.DigestUtils
import org.scalatest.Assertion
import uk.gov.nationalarchives.consignmentexport.Utils.PathUtils

import scala.io.Source
import scala.io.Source.fromResource
import scala.jdk.CollectionConverters._
import scala.sys.process._

class MainSpec extends ExternalServiceSpec {

  private val taskTokenValue = "taskToken1234"
  private val standardOutputBucket = "test-output-bucket"
  private val judgmentOutputBucket = "test-output-bucket-judgment"

  "the export job" should "export the correct tar and checksum file to the correct s3 bucket for a 'standard' consignment type" in {
    setUpValidExternalServices("get_consignment_for_export.json")

    val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
    val consignmentRef = "consignmentReference-1234"
    val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

    putFile(s"$consignmentId/$fileId")

    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()
    checkStepFunctionSuccessCalled()
    val objects = outputBucketObjects(standardOutputBucket).map(_.key())

    objects.size should equal(2)
    objects.head should equal(s"$consignmentRef.tar.gz")
    objects.last should equal(s"$consignmentRef.tar.gz.sha256")
  }

  "the export job" should "export the correct tar and checksum file to the correct s3 bucket for a 'judgment' consignment type" in {
    setUpValidExternalServices("get_judgment_consignment_for_export.json")

    val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
    val consignmentRef = "consignmentReference-1234"
    val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

    putFile(s"$consignmentId/$fileId")

    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()
    checkStepFunctionSuccessCalled("publish_judgment_success_request_body")
    val objects = outputBucketObjects(judgmentOutputBucket).map(_.key())

    objects.size should equal(2)
    objects.head should equal(s"$consignmentRef.tar.gz")
    objects.last should equal(s"$consignmentRef.tar.gz.sha256")
  }

  "the export job" should "export a valid tar and checksum file for a 'standard' consignment type" in {
    setUpValidExternalServices("get_consignment_for_export.json")

    val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
    val consignmentRef = "consignmentReference-1234"
    val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

    putFile(s"$consignmentId/$fileId")

    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()

    checkStepFunctionSuccessCalled()

    val downloadDirectory = s"$scratchDirectory/download"
    new File(s"$downloadDirectory").mkdirs()
    getObject(s"$consignmentRef.tar.gz", s"$downloadDirectory/result.tar.gz".toPath, standardOutputBucket)
    getObject(s"$consignmentRef.tar.gz.sha256", s"$downloadDirectory/result.tar.gz.sha256".toPath, standardOutputBucket)

    val exitCode = Seq("sh", "-c", s"tar -tf $downloadDirectory/result.tar.gz > /dev/null").!
    exitCode should equal(0)

    val source = Source.fromFile(new File(s"$downloadDirectory/result.tar.gz.sha256"))
    val checksum = source.getLines().toList.head.split(" ").head

    val expectedChecksum = DigestUtils.sha256Hex(Files.readAllBytes(s"$downloadDirectory/result.tar.gz".toPath))

    checksum should equal(expectedChecksum)
    source.close()
  }

  "the export job" should "export a valid tar and checksum file for a 'judgment' consignment type" in {
    setUpValidExternalServices("get_judgment_consignment_for_export.json")

    val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
    val consignmentRef = "consignmentReference-1234"
    val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

    putFile(s"$consignmentId/$fileId")

    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()

    checkStepFunctionSuccessCalled("publish_judgment_success_request_body")

    val downloadDirectory = s"$scratchDirectory/download"
    new File(s"$downloadDirectory").mkdirs()
    getObject(s"$consignmentRef.tar.gz", s"$downloadDirectory/result.tar.gz".toPath, judgmentOutputBucket)
    getObject(s"$consignmentRef.tar.gz.sha256", s"$downloadDirectory/result.tar.gz.sha256".toPath, judgmentOutputBucket)

    val exitCode = Seq("sh", "-c", s"tar -tf $downloadDirectory/result.tar.gz > /dev/null").!
    exitCode should equal(0)

    val source = Source.fromFile(new File(s"$downloadDirectory/result.tar.gz.sha256"))
    val checksum = source.getLines().toList.head.split(" ").head

    val expectedChecksum = DigestUtils.sha256Hex(Files.readAllBytes(s"$downloadDirectory/result.tar.gz".toPath))

    checksum should equal(expectedChecksum)
    source.close()
  }

  "the export job" should "create directories for empty folders" in {
    setUpValidExternalServices("get_consignment_for_export.json")

    val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
    val consignmentRef = "consignmentReference-1234"
    val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

    putFile(s"$consignmentId/$fileId")

    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()

    val downloadDirectory = s"$scratchDirectory/download"
    new File(s"$downloadDirectory").mkdirs()
    getObject(s"$consignmentRef.tar.gz", s"$downloadDirectory/result.tar.gz".toPath, standardOutputBucket)

    Seq("sh", "-c", s"tar -tf $downloadDirectory/result.tar.gz > /dev/null").!
    val exportId: String = new File(scratchDirectory).list.toList.find(_ != "download").head
    val basePath = s"$scratchDirectory/$exportId/$consignmentRef/data/"
    val firstEmptyDirectory = new File(s"$basePath/empty")
    val secondEmptyDirectory = new File(s"$basePath/empty2/empty3")

    firstEmptyDirectory.exists should be(true)
    firstEmptyDirectory.isDirectory should be (true)
    firstEmptyDirectory.list().length should be (0)

    secondEmptyDirectory.exists() should be(true)
    secondEmptyDirectory.isDirectory should be (true)
    secondEmptyDirectory.list().length should be (0)
  }

  "the export job" should "update the export location in the api for a 'standard' consignment type" in {
    setUpValidExternalServices("get_consignment_for_export.json")

    val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
    val consignmentRef = "consignmentReference-1234"
    val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

    putFile(s"$consignmentId/$fileId")
    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()

    checkStepFunctionSuccessCalled()

    val exportLocationEvent: Option[ServeEvent] = wiremockGraphqlServer.getAllServeEvents.asScala
      .find(p => p.getRequest.getBodyAsString.contains("mutation updateExportLocation"))

    exportLocationEvent.isDefined should be(true)

    exportLocationEvent.get.getRequest.getBodyAsString.contains(s""""consignmentId":"$consignmentId"""") should be(true)
    exportLocationEvent.get.getRequest.getBodyAsString.contains(s""""exportLocation":"s3://$standardOutputBucket/$consignmentRef.tar.gz"""") should be(true)
  }

  "the export job" should "update the export location in the api for a 'judgment' consignment type" in {
    setUpValidExternalServices("get_judgment_consignment_for_export.json")

    val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
    val consignmentRef = "consignmentReference-1234"
    val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

    putFile(s"$consignmentId/$fileId")
    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()

    checkStepFunctionSuccessCalled("publish_judgment_success_request_body")

    val exportLocationEvent: Option[ServeEvent] = wiremockGraphqlServer.getAllServeEvents.asScala
      .find(p => p.getRequest.getBodyAsString.contains("mutation updateExportLocation"))

    exportLocationEvent.isDefined should be(true)

    exportLocationEvent.get.getRequest.getBodyAsString.contains(s""""consignmentId":"$consignmentId"""") should be(true)
    exportLocationEvent.get.getRequest.getBodyAsString.contains(s""""exportLocation":"s3://$judgmentOutputBucket/$consignmentRef.tar.gz"""") should be(true)
  }

  "the export job" should "throw an error if the api returns no files for a 'standard' consignment type" in {
    setUpInvalidExternalServices(graphQlGetConsignmentMetadataNoFiles("get_consignment_no_files.json"))

    val consignmentId = "069d225e-b0e6-4425-8f8b-c2f6f3263221"

    val ex = intercept[Exception] {
      Main.run(List("export", "--consignmentId", consignmentId, "--taskToken", taskTokenValue)).unsafeRunSync()
    }

    checkStepFunctionFailureCalled("publish_failure_no_files_for_consignment_request_body")
    ex.getMessage should equal(s"Consignment API returned no files for consignment $consignmentId")
  }

  "the export job" should "throw an error if the api returns no files for a 'judgment' consignment type" in {
    setUpInvalidExternalServices(graphQlGetConsignmentMetadataNoFiles("get_judgment_consignment_no_files.json"))

    val consignmentId = "069d225e-b0e6-4425-8f8b-c2f6f3263221"

    val ex = intercept[Exception] {
      Main.run(List("export", "--consignmentId", consignmentId, "--taskToken", taskTokenValue)).unsafeRunSync()
    }

    checkStepFunctionFailureCalled("publish_failure_no_files_for_consignment_request_body")
    ex.getMessage should equal(s"Consignment API returned no files for consignment $consignmentId")
  }

  "the export job" should "throw an error if the file metadata is incomplete for a 'standard' consignment type" in {
    setUpInvalidExternalServices(graphQlGetConsignmentIncompleteMetadata("get_consignment_incomplete_metadata.json"))

    val consignmentId = UUID.fromString("0e634655-1563-4705-be99-abb437f971e0")
    val fileId = UUID.fromString("7b19b272-d4d1-4d77-bf25-511dc6489d12")

    putFile(s"$consignmentId/$fileId")

    val ex = intercept[Exception] {
      Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()
    }

    checkStepFunctionFailureCalled("publish_failure_incomplete_file_properties_request_body")
    ex.getMessage should equal(s"$fileId is missing the following properties: foiExemptionCode, heldBy, language, rightsCopyright, sha256ClientSideChecksum")
  }

  "the export job" should "throw an error if the file metadata is incomplete for a 'judgment' consignment type" in {
    setUpInvalidExternalServices(graphQlGetConsignmentIncompleteMetadata("get_judgment_consignment_incomplete_metadata.json"))

    val consignmentId = UUID.fromString("0e634655-1563-4705-be99-abb437f971e0")
    val fileId = UUID.fromString("7b19b272-d4d1-4d77-bf25-511dc6489d12")

    putFile(s"$consignmentId/$fileId")

    val ex = intercept[Exception] {
      Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()
    }

    checkStepFunctionFailureCalled("publish_failure_incomplete_file_properties_request_body")
    ex.getMessage should equal(s"$fileId is missing the following properties: foiExemptionCode, heldBy, language, rightsCopyright, sha256ClientSideChecksum")
  }

  "the export job" should "throw an error if the ffid metadata is missing for a 'standard' consignment type" in {
    setUpInvalidExternalServices(graphQlGetConsignmentMissingFfidMetadata("get_consignment_missing_ffid_metadata.json"))

    val consignmentId = UUID.fromString("2bb446f2-eb15-4b83-9c69-53b559232d84")
    val fileId = UUID.fromString("3381a880-4e9a-4663-b4c6-97dc4018835e")

    putFile(s"$consignmentId/$fileId")

    val ex = intercept[Exception] {
      Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()
    }

    checkStepFunctionFailureCalled("publish_failure_missing_ffid_metadata_request_body")
    ex.getMessage should equal(s"FFID metadata is missing for file id $fileId")
  }

  "the export job" should "throw an error if the ffid metadata is missing for a 'judgment' consignment type" in {
    setUpInvalidExternalServices(graphQlGetConsignmentMissingFfidMetadata("get_judgment_consignment_missing_ffid_metadata.json"))

    val consignmentId = UUID.fromString("2bb446f2-eb15-4b83-9c69-53b559232d84")
    val fileId = UUID.fromString("3381a880-4e9a-4663-b4c6-97dc4018835e")

    putFile(s"$consignmentId/$fileId")

    val ex = intercept[Exception] {
      Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()
    }

    checkStepFunctionFailureCalled("publish_failure_missing_ffid_metadata_request_body")
    ex.getMessage should equal(s"FFID metadata is missing for file id $fileId")
  }

  "the export job" should "throw an error if the antivirus metadata is missing for 'standard' consignment type" in {
    setUpInvalidExternalServices(graphQlGetConsignmentMissingAntivirusMetadata("get_consignment_missing_antivirus_metadata.json"))

    val consignmentId = UUID.fromString("fbb543d0-7690-4d58-837c-464d431713fc")
    val fileId = UUID.fromString("5e271e33-ae7e-4471-9f89-005c5d15c5a1")

    putFile(s"$consignmentId/$fileId")

    val ex = intercept[Exception] {
      Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()
    }

    checkStepFunctionFailureCalled("publish_failure_missing_antivirus_metadata_request_body")
    ex.getMessage should equal(s"Antivirus metadata is missing for file id $fileId")
  }

  "the export job" should "throw an error if the antivirus metadata is missing for 'judgment' consignment type" in {
    setUpInvalidExternalServices(graphQlGetConsignmentMissingAntivirusMetadata("get_judgment_consignment_missing_antivirus_metadata.json"))

    val consignmentId = UUID.fromString("fbb543d0-7690-4d58-837c-464d431713fc")
    val fileId = UUID.fromString("5e271e33-ae7e-4471-9f89-005c5d15c5a1")

    putFile(s"$consignmentId/$fileId")

    val ex = intercept[Exception] {
      Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()
    }

    checkStepFunctionFailureCalled("publish_failure_missing_antivirus_metadata_request_body")
    ex.getMessage should equal(s"Antivirus metadata is missing for file id $fileId")
  }

  "the export job" should "throw an error if no consignment metadata found" in {
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

  "the export job" should "throw an error if no valid Keycloak user found for a 'standard' consignment type" in {
    graphQlGetConsignmentMetadata("get_consignment_for_export.json")
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

  "the export job" should "throw an error if no valid Keycloak user found for a 'judgment' consignment type" in {
    graphQlGetConsignmentMetadata("get_judgment_consignment_for_export.json")
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

  "the export job" should "throw an error if an incomplete Keycloak user details found for a 'standard' consignment type" in {
    graphQlGetConsignmentMetadata("get_consignment_for_export.json")
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

  "the export job" should "throw an error if an incomplete Keycloak user details found for a 'judgment' consignment type" in {
    graphQlGetConsignmentMetadata("get_judgment_consignment_for_export.json")
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

  "the export job" should "throw an error if there are checksum mismatches for a 'standard' consignment type" in {
    setUpInvalidExternalServices(graphQlGetIncorrectCheckSumConsignmentMetadata("get_consignment_for_export_different_checksum.json"))

    val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
    val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

    putFile(s"$consignmentId/$fileId")

    val ex = intercept[Exception] {
      Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()
    }

    checkStepFunctionFailureCalled("publish_failure_checksum_mismatch_request_body")
    ex.getMessage should equal(s"Checksum mismatch for file(s): $fileId")
  }

  "the export job" should "throw an error if there are checksum mismatches for a 'judgment' consignment type" in {
    setUpInvalidExternalServices(graphQlGetIncorrectCheckSumConsignmentMetadata("get_judgment_consignment_for_export_different_checksum.json"))

    val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
    val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

    putFile(s"$consignmentId/$fileId")

    val ex = intercept[Exception] {
      Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()
    }

    checkStepFunctionFailureCalled("publish_failure_checksum_mismatch_request_body")
    ex.getMessage should equal(s"Checksum mismatch for file(s): $fileId")
  }

  "the export job" should "call the step function heartbeat endpoint for a 'standard' consignment type" in {
    setUpValidExternalServices("get_consignment_for_export.json")

    val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
    val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

    putFile(s"$consignmentId/$fileId")

    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()

    wiremockSfnServer.getAllServeEvents.asScala
      .count(ev =>
        ev.getRequest.getHeader("X-Amz-Target") == s"AWSStepFunctions.SendTaskHeartbeat"
      ) should equal(1)
  }

  "the export job" should "call the step function heartbeat endpoint for a 'judgment' consignment type" in {
    setUpValidExternalServices("get_consignment_for_export.json")

    val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
    val fileId = "7b19b272-d4d1-4d77-bf25-511dc6489d12"

    putFile(s"$consignmentId/$fileId")

    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", taskTokenValue)).unsafeRunSync()

    wiremockSfnServer.getAllServeEvents.asScala
      .count(ev =>
        ev.getRequest.getHeader("X-Amz-Target") == s"AWSStepFunctions.SendTaskHeartbeat"
      ) should equal(1)
  }
  private def setUpValidExternalServices(jsonResponse: String) = {
    graphQlGetConsignmentMetadata(jsonResponse)
    keycloakGetUser
    stepFunctionPublish
  }

  private def setUpInvalidExternalServices(invalidStub: => StubMapping) = {
    invalidStub
    keycloakGetUser
    stepFunctionPublish
  }

  private def checkStepFunctionSuccessCalled(expectedJsonPath: String = "publish_success_request_body"): Assertion =
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
