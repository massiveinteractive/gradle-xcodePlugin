package org.openbakery.util

import java.util.regex.Matcher
import java.util.regex.Pattern

class SignatureUtil {

	private static final Pattern PATTERN = ~/^\s{4}friendlyName:\s(?<friendlyName>[^\n]+)/

	static String getCertificateFriendlyName(File certificateFile,
											 String certificatePassword) throws RuntimeException {
		return Optional.ofNullable(decryptCertificate(certificateFile, certificatePassword)
				.split(System.getProperty("line.separator"))
				.find { PATTERN.matcher(it).matches() })
				.map { PATTERN.matcher(it) }
				.filter { Matcher it -> it.matches() }
				.map { Matcher it ->
			return it.group("friendlyName")
		}
		.orElseThrow {
			new IllegalArgumentException("Failed to resolve the code signing identity from the certificate ")
		}
	}

	static String decryptCertificate(File certificateFile,
									 String certificatePassword) throws RuntimeException {
		assert certificateFile.exists(): "The certificate file does not exists"

		ProcessBuilder builder = new ProcessBuilder("openssl",
				"pkcs12",
				"-nokeys",
				"-in", certificateFile.absolutePath,
				"-passin", "pass:${certificatePassword}")

		builder.redirectOutput()

		final Process process = builder.start()
		process.waitFor()

		String result
		if (process.exitValue() == 0) {
			result = process.getInputStream().text.trim()
		} else {
			throw new RuntimeException(process.getErrorStream().text.trim())
		}

		return result
	}
}
