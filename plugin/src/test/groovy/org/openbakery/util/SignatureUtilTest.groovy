package org.openbakery.util

import org.junit.Test
import spock.lang.Specification

import java.nio.file.Paths

class SignatureUtilTest extends Specification {

	private File certificateFile = findResource("fake_distribution.p12")
	private File fakeFile = Mock(File)

	@Test
	def "Should properly resolve the certificate friendly name"() {
		when:
		String result = SignatureUtil.getCertificateFriendlyName(certificateFile, "p4ssword")

		then:
		noExceptionThrown()

		and:
		result == "iPhone Distribution: Test Company Name (12345ABCDE)"
	}

	@Test
	def "Should fail to resolve the certificate friendly name if invalid password"() {
		when:
		SignatureUtil.getCertificateFriendlyName(certificateFile, "toto")

		then:
		RuntimeException exception = thrown RuntimeException

		and:
		exception.message == "Mac verify error: invalid password?"
	}

	@Test
	def "Should properly resolve the content of the certificate file if exists"() {
		when:
		String result = SignatureUtil.decryptCertificate(certificateFile, "p4ssword")

		then:
		noExceptionThrown()

		and:
		result.contains("localKeyID: FE 93 19 AC CC D7 C1 AC 82 97 02 C2 35 97 B6 CE 37 33 CB 4F")
	}

	@Test
	def "Should fail if the certificate password is invalid"() {
		when:
		SignatureUtil.decryptCertificate(certificateFile, "wrong")

		then:
		RuntimeException exception = thrown RuntimeException
		exception.message == "Mac verify error: invalid password?"
	}

	@Test
	def "Should throw an exception is the certificate file does not exists"() {
		setup:

		when:
		SignatureUtil.decryptCertificate(fakeFile, "p4ssword")

		then:
		AssertionError error = thrown AssertionError
		error.message.startsWith("The certificate file does not exists")
	}

	private File findResource(String name) {
		ClassLoader classLoader = getClass().getClassLoader()
		return (File) Optional.ofNullable(classLoader.getResource(name))
				.map { URL url -> url.toURI() }
				.map { URI uri -> Paths.get(uri).toFile() }
				.filter { File file -> file.exists() }
				.orElseThrow { new Exception("Resource $name cannot be found") }
	}
}
